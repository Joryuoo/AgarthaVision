# Sample

**One sentence.** One microscope frame that a human has looked at — a captured JPEG plus its
traceability metadata. Product calls it a "capture" or "frame"; the table and the Room entity
are both `samples`.

## Why this shape

A sample is the traceability unit: UUID, timestamp, GPS, device id, and session id are bound at
capture so any finding can be traced back to a slide, a place, and a device. It carries no
species and no count — those are children, because one frame can hold several eggs and the
medtech may disagree with the model about each one independently.

**This is where Room and Postgres diverge most.** Room stores the pre-verification state and
several workflow-only flags that were deliberately never given remote columns; Postgres stores
only what a verified, uploaded sample looks like.

## Shape

**Postgres** (`supabase/migrations/0001_init.sql:43-55`, plus `0002` and `0006`)

| Field | Constraint |
|---|---|
| `id` | PK, default `uuid_generate_v4()` |
| `session_id` | NOT NULL, FK → `sessions(id)`, CASCADE |
| `user_id` | **NOT NULL**, FK → `profiles(id)` |
| `captured_at` | NOT NULL — on-device capture time |
| `verified_at` | NOT NULL, default `now()` |
| `gps_latitude` / `gps_longitude` | nullable double precision |
| `gps_accuracy` | nullable real |
| `storage_path` | **NOT NULL** — the object key, `{user_id}/{sample_id}.jpg` (`0001_init.sql:52`) |
| `inference_model_version` | NOT NULL — renamed from `roboflow_model_version` by `0002_verification_fields.sql:17-18` |
| `needs_reannotation` | NOT NULL default false — `0002_verification_fields.sql:23-24` |
| `user_note` | nullable |
| `is_manual` | NOT NULL default false — `supabase/migrations/0006_sample_is_manual.sql:13-14` |
| `deleted_at` | nullable timestamptz — `supabase/migrations/0013_sample_soft_delete.sql`. Null means live. See *the tombstone* below |

**Room** (`app/src/main/java/com/agarthavision/data/local/entity/SampleEntity.kt:15-99`)

PK column is `sample_id`. Room-only or Room-different:

| Field | Note | Line |
|---|---|---|
| `user_id` | **nullable** — unowned until claimed at login | `SampleEntity.kt:29-30` |
| `device_id` | Room-only. Postgres keeps device ownership on `sessions.device_id` | `SampleEntity.kt:32-33` |
| `timestamp` | Room's name for the capture instant; epoch millis. Maps to `captured_at` | `SampleEntity.kt:35-36` |
| `image_path` | Room-only. The on-device file. Postgres has no such column | `SampleEntity.kt:41-42` |
| `storage_path` | **nullable** in Room until upload succeeds; NOT NULL remotely | `SampleEntity.kt:44-45` |
| `status` | **Room/domain only.** `flagged`/`verified`/`synced`/`sync_failed`. No Postgres column exists | `SampleEntity.kt:62-63`, `domain/model/SampleStatus.kt:13-18` |
| `is_repeat` | **Room-only, never synced.** The medtech's "already counted this one" flag; excluded from EPG | `SampleEntity.kt:83-90`, `supabase/migrations/0006_sample_is_manual.sql:9-11` |
| `predictions_json` | Room-only cache of the raw inference payload; **nulled on verify** | `SampleEntity.kt:92-93`, `data/local/dao/SampleDao.kt:104` |
| `image_width` / `image_height` | Room-only, from the inference response | `SampleEntity.kt:95-99` |
| `deleted_at` | Epoch millis, **synced**. Null means live | `SampleEntity.kt`, `supabase/migrations/0013_sample_soft_delete.sql` |

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
`data/supabase/SampleRemoteDataSource.kt:100-127`. `image_path`, `status`, `is_repeat`,
`device_id`, and the prediction cache are all absent from it.

**`schema.ts` is wrong here and the code wins.** `schema.ts:224-282` names `timestamp`,
`image_path`, `created_at`, `gps_lat`, `gps_lng`, and `gps_accuracy_m` as if they were Postgres
columns. They are not — the migration creates `captured_at`, `gps_latitude`, `gps_longitude`,
`gps_accuracy`, and no `created_at`. Those `schema.ts` names describe the Room entity.

## Connected to

- **Owned by** [`Session`](Session.md) and, remotely, [`Profile`](Profile.md).
- **Owns** [`Detection`](Detection.md), 1 → many, CASCADE
  (`supabase/migrations/0001_init.sql:61`). Zero detections is legal.
- **Owns** [`Finding`](Finding.md), 1 → many, CASCADE
  (`supabase/migrations/0012_polyparasitism_findings.sql`). Zero findings is legal and
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
  (`data/supabase/SyncSampleUseCase.kt:29-49`, `data/supabase/SampleRemoteDataSource.kt:100-127`).
- Every `SampleDao` query filtering `status != 'flagged'`. The flagged/verified split is
  enforced in query text, not by a type — `data/local/dao/SampleDao.kt:39`, `:42`, `:51`,
  `:60`, `:69`, `:78`.
- The EPG aggregate, which joins samples and excludes `is_repeat = 1`
  (`data/local/dao/DetectionDao.kt:33-52`).
- The CSV row shape (`domain/usecase/records/ReportCsvBuilder.kt`).
- The Room database version (`core/database/AgarthaDatabase.kt:34`) — and remember the
  destructive-migration fallback wipes the device.

**Does not hit**
- The Storage object path. Changing `storage_path` in Room does **not** move or rename the
  file. The path is recomputed server-side at upload from the live auth user id and the sample
  id (`data/supabase/SampleRemoteDataSource.kt:33-35`), and the RLS policy validates the first
  folder segment against `auth.uid()` (`supabase/migrations/0003_storage_rls.sql:16-19`).
  Editing the column just desynchronises the pointer from the object.
- Detection bounding boxes. They are stored per-detection in source-image pixels and are not
  rescaled if you change the sample's `image_width` / `image_height`.

## Surfaces

Written by `PersistFlaggedFrameUseCase` (`domain/usecase/capture/PersistFlaggedFrameUseCase.kt:38-60`),
updated by `SubmitVerificationUseCase` / `SubmitManualCaptureUseCase`, and by
`SyncSampleUseCase` on upload. Read by the verification queue, Records, Session Detail, Sample
Detail, the EPG aggregate, and the CSV builder.

## See

`supabase/migrations/0001_init.sql:43-55`,
`app/src/main/java/com/agarthavision/data/local/entity/SampleEntity.kt`,
`data/supabase/SampleRemoteDataSource.kt:100-127`, `schema.ts:214-282`.
