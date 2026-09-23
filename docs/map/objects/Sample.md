# Sample

**One sentence.** One microscope frame that a human has looked at — a captured JPEG plus its
traceability metadata. Product calls it a "capture" or "frame"; the table and the Room entity
are both `samples`.

## Why this shape

A sample is the traceability unit: UUID, timestamp, device id, and session id are bound at
capture so any finding can be traced back to a slide, a patient encounter, and a device. It carries no
species and no count — those are children, because one frame can hold several eggs and the
medtech may disagree with the model about each one independently.

**This is where Room and Postgres diverge most.** Room stores the pre-verification state and
several workflow-only flags that were deliberately never given remote columns; Postgres stores
only what a verified, uploaded sample looks like.

## Shape

**Postgres** (`supabase/migrations/0001_init.sql:181-203`)

| Field | Constraint |
|---|---|
| `id` | PK, default `uuid_generate_v4()` |
| `session_id` | NOT NULL, FK → `sessions(id)`, CASCADE |
| `user_id` | **NOT NULL**, FK → `profiles(id)` |
| `captured_at` | NOT NULL — on-device capture time |
| `verified_at` | NOT NULL, default `now()` |
| `storage_path` | **NOT NULL** — the object key, `{user_id}/{sample_id}.jpg` (`0001_init.sql:187`) |
| `inference_model_version` | NOT NULL |
| `needs_reannotation` | NOT NULL default false |
| `user_note` | nullable |
| `is_manual` | NOT NULL default false |
| `deleted_at` | nullable timestamptz. Null means live. See *the tombstone* below |

*Deliberately absent:* `gps_latitude`, `gps_longitude`, `gps_accuracy` were removed (`0001_init.sql:205-208`).
The fix recorded where the smear was read (the lab), not where the patient lives. Geospatial mapping
keys on `patients.psgc_barangay_code`.

**Room** (`app/src/main/java/com/agarthavision/data/local/entity/SampleEntity.kt:45-135`), schema v16

PK column is `sample_id`. Foreign key to `SessionEntity` with `onDelete = ForeignKey.NO_ACTION` per C8.
Room-only or Room-different:

| Field | Note | Line |
|---|---|---|
| `user_id` | **nullable** — unowned until claimed at login | `SampleEntity.kt:60` |
| `device_id` | Room-only. Postgres keeps device ownership on `sessions.device_id` | `SampleEntity.kt:63` |
| `timestamp` | Room's name for the capture instant; epoch millis. Maps to `captured_at` | `SampleEntity.kt:66` |
| `image_path` | Room-only. The on-device file. Postgres has no such column | `SampleEntity.kt:72` |
| `storage_path` | **nullable** in Room until upload succeeds; NOT NULL remotely | `SampleEntity.kt:75` |
| `status` | **Room/domain only.** `flagged`/`verified`/`synced`/`sync_failed`. No Postgres column exists | `SampleEntity.kt:89`, `domain/model/SampleStatus.kt:13-18` |
| `predictions_json` | Room-only cache of the raw inference payload; **nulled on verify** | `SampleEntity.kt:110`, `data/local/dao/SampleDao.kt:104` |
| `image_width` / `image_height` | Room-only, from the inference response | `SampleEntity.kt:113-116` |
| `deleted_at` | Epoch millis, **synced**. Null means live | `SampleEntity.kt:134`, `supabase/migrations/0001_init.sql:202` |

**The tombstone.** A verified sample is never hard-deleted — C8, and `detections` doubles as the
retraining corpus. But a medtech who captured the same egg twice needs the duplicate gone from
the queue, the counts and the report. `deleted_at` does exactly that and nothing more: the
detections stay, the findings rows stay, the local JPEG stays, the Storage object stays, and
`0003_storage_rls.sql:45-46` still creates no DELETE policy. Unverified frames are different —
they are hard-deleted on-device, which is C8's existing local exception.

**The rule this creates.** *Every* query that lists or counts samples must filter
`deleted_at is null`. Miss one and a deleted duplicate reappears in a report. This is enforced
by a naming rule plus `SoftDeleteGuardTest`: a DAO method that SELECTs over `samples` must carry
the predicate unless its name ends in `IncludingDeleted`. Exactly two methods are exempt, and
each announces it at every call site. The stronger form — a `@DatabaseView` over live rows, with
every list query reading the view — is the intended end state; it was deferred because it
changes DAO return types and ripples into `SampleMapper` and every `@Embedded` projection.

The insert row is the definitive list of what actually crosses the wire —
`data/supabase/SampleRemoteDataSource.kt:114-131`. `image_path`, `status`, `device_id`, and the
prediction cache are all absent from it.

`schema.ts` documents both the Postgres schema and the Room-only extensions.

## Connected to

- **Owned by** [`Session`](Session.md) and, remotely, [`Profile`](Profile.md).
- **Owns** [`Detection`](Detection.md), 1 → many, CASCADE
  (`supabase/migrations/0001_init.sql:226`). Zero detections is legal.
- **Owns** [`Finding`](Finding.md), 1 → many, CASCADE
  (`supabase/migrations/0001_init.sql:274`). Zero findings is legal and
  meaningful — it is how a clean field is recorded.
- **Points at** [`StorageObject`](StorageObject.md), 1 → 0..1, via `storage_path`. Local-only
  samples have none.
- **Looks like but is not** `FlaggedFrame` (`domain/model/FlaggedFrame.kt`). That is the
  in-flight capture object carrying raw JPEG bytes and prediction DTOs. It is reconstructed
  *from* a flagged `SampleEntity` (`data/repository/FlaggedFrameStore.kt:101-119`), not stored
  as one.

## If you change this

**Hits**
- `SyncSampleUseCase` and the insert row — anything that must reach Postgres has to be added
  to `SampleInsertRow` or it is silently dropped
  (`data/supabase/SyncSampleUseCase.kt:29-49`, `data/supabase/SampleRemoteDataSource.kt:114-131`).
- Every `SampleDao` query filtering `status != 'flagged'`. The flagged/verified split is
  enforced in query text, not by a type — `data/local/dao/SampleDao.kt:83`, `:112`, `:120`,
  `:144`, `:163`.
- The confirmed detections count, which joins samples
  (`data/local/dao/DetectionDao.kt:33-52`).
- The Patients list Recent sort (`PatientDao.observePatients`), which reads `samples.timestamp`
  and `samples.verified_at` (filtering `deleted_at IS NULL`) to sort active patients to the top.
- The report generation pipeline (`domain/usecase/reports/GenerateSessionReportUseCase.kt`
  and `domain/usecase/reports/PdfReportGenerator.kt`).
- The Room database version (`core/database/AgarthaDatabase.kt:46`) — and remember the
  destructive-migration fallback wipes the device.

**Does not hit**
- The Storage object path. Changing `storage_path` in Room does **not** move or rename the
  file. The path is recomputed server-side at upload from the live auth user id and the sample
  id (`data/supabase/SampleRemoteDataSource.kt:33-35`), and the RLS policy validates the first
  folder segment against `auth.uid()` (`supabase/migrations/0001_init.sql:357-362`).
  Editing the column just desynchronises the pointer from the object.
- Detection bounding boxes. They are stored per-detection in source-image pixels and are not
  rescaled if you change the sample's `image_width` / `image_height`.

## Surfaces

Written by `PersistFlaggedFrameUseCase` (`domain/usecase/capture/PersistFlaggedFrameUseCase.kt:38-60`),
updated by `SubmitVerificationUseCase`, and by
`SyncSampleUseCase` on upload. Read by the verification queue, Patients, Session Detail, Sample
Detail, confirmed detections count, and the PDF report generator.

## See

`supabase/migrations/0001_init.sql:181-203`,
`app/src/main/java/com/agarthavision/data/local/entity/SampleEntity.kt`,
`data/supabase/SampleRemoteDataSource.kt:114-131`, `schema.ts` (`Sample`).
