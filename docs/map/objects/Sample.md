---
type: object
status: verified
verified: 2026-09-30
commit: b64271d2
entity: app/src/main/java/com/agarthavision/data/local/entity/SampleEntity.kt
---

# Sample

**One sentence.** One microscope frame that a human has looked at — a captured JPEG plus its
traceability metadata. Product calls it a "capture" or "frame"; the table and the Room entity
are both `samples`.

## Why this shape

A sample is the traceability unit: UUID, timestamp, device id, and session id are bound at
capture so any finding can be traced back to a slide, a patient encounter, and a device. It carries no
species and no count — those are children, because one frame can hold several eggs and the
medtech may disagree with the model about each one independently.

**This is where Room and Postgres diverge most.** Room stores the pre-verification state, the
inference queue, and several workflow-only flags that were deliberately never given remote
columns; Postgres stores only what a verified, uploaded sample looks like.

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

*Deliberately absent:* `gps_latitude`, `gps_longitude`, `gps_accuracy` (`0001_init.sql:205-208`).
The fix recorded where the smear was read (the lab), not where the patient lives. Geospatial mapping
keys on `patients.psgc_barangay_code`.

**Room** (`SampleEntity`, schema v23). PK column is `sample_id`. Foreign key to `SessionEntity`
with `onDelete = ForeignKey.NO_ACTION` per C8. Room-only or Room-different fields, each a
property of `SampleEntity`:

| Field | Note |
|---|---|
| `user_id` | **nullable** in Room, a leftover of the removed claim-at-login flow. Every new row gets an owner at capture (`PersistFlaggedFrameUseCase::invoke`) |
| `device_id` | Room-only. Postgres keeps device ownership on `sessions.device_id` |
| `timestamp` | Room's name for the capture instant; epoch millis. Maps to `captured_at` |
| `image_path` | Room-only. The on-device file. Postgres has no such column |
| `storage_path` | **nullable** in Room until upload succeeds; NOT NULL remotely |
| `status` | **Room/domain only.** `flagged`/`verified`/`synced`/`sync_failed` (`domain/model/SampleStatus.kt`). No Postgres column |
| `predictions_json` | Room-only form of the model's output — the local store of [`Prediction`](Prediction.md). Kept through verification; pushed as `predictions` rows and restored from them on pull. A pull never overwrites it with null (`SampleDao::updatePredictionsJson`) |
| `image_width` / `image_height` | Room-only, from the inference response |
| `is_edited` | Room-only. Set when findings change after verification; drives the "edited" mark on Session Detail and Sample Detail |
| `inference_state` | Room-only. `queued` / `in_inference` / `ready` / `manual`. **Read it through `SampleMapper.kt::effectiveInferenceState`**, never raw: `is_manual` wins, because rows from before v23 and rows pulled from Supabase carry the `ready` default. See [`infer`](../processes/infer.md) |
| `inference_attempts` | Room-only. Failed passes of both engines; at the queue's limit the frame becomes manual. On the row so a crash loop cannot reset it |
| `deleted_at` | Epoch millis. Null means live. Pulled from Supabase; **not pushed** — see below |

**The tombstone.** A verified sample is never hard-deleted — C8, and `detections` doubles as the
retraining corpus. But a medtech who captured the same egg twice needs the duplicate gone from
the queue, the counts and the report. `deleted_at` does exactly that and nothing more: the
detections stay, the findings rows stay, the local JPEG stays, the Storage object stays, and the
bucket still has no DELETE policy (`0001_init.sql:538-540`). Unverified frames are different —
they are hard-deleted on-device, which is C8's existing local exception
(`DeleteQueueItemsUseCase` decides which of the two a delete is).

**Drift, code wins: the tombstone is local-only today.** `SyncSampleUseCase::invoke` reads the
sample *including deleted* so that "a tombstoned sample still has to push its tombstone", but
`SampleRemoteDataSource.kt::SampleInsertRow` has no `deleted_at` field, so the upsert never
carries it. A tombstoned sample disappears on the device that tombstoned it and stays live in
Postgres and on every other device. A pull does honour a remote `deleted_at`
(`SampleRemoteDataSource.kt::SampleRow`).

**The rule this creates.** *Every* query that lists or counts samples must filter
`deleted_at is null`. Miss one and a deleted duplicate reappears in a report. This is enforced
by a naming rule plus `SoftDeleteGuardTest`: a DAO method that SELECTs over `samples` must carry
the predicate unless its name ends in `IncludingDeleted`. The stronger form — a `@DatabaseView`
over live rows, with every list query reading the view — is the intended end state; it was
deferred because it changes DAO return types and ripples into `SampleMapper` and every
`@Embedded` projection.

The insert row is the definitive list of what actually crosses the wire —
`data/supabase/SampleRemoteDataSource.kt::SampleInsertRow`. `image_path`, `status`, `device_id`,
`is_edited`, the inference queue columns, the prediction cache and `deleted_at` are all absent
from it.

`schema.ts` documents both the Postgres schema and the Room-only extensions.

## Connected to

- **Owned by** [`Session`](Session.md) and, remotely, [`Profile`](Profile.md).
- **Read by** the author and — since `0007_patient_shared_history.sql:161-163` —
  every medtech assigned to the patient. A super admin reads `samples_deidentified` instead,
  without `user_note` (`0012_deidentified_reads.sql:74`,
  `0013_super_admin_reads_deidentified.sql:53`); detections, findings and predictions stay
  readable to them (`:80-108`). Locally, Session Detail's list and the report read a colleague's samples on
  an assigned patient (`SampleDao::observeSamplesForSession`, `GetSessionSamplesUseCase`,
  `GetSampleDetailUseCase`); the push queue and the flagged queue stay author-only.
- **Owns** [`Prediction`](Prediction.md), 1 → many, CASCADE
  (`supabase/migrations/0004_predictions.sql:43`). Only a model frame has any.
- **Owns** [`Detection`](Detection.md), 1 → many, CASCADE
  (`supabase/migrations/0001_init.sql:226`). Zero detections is legal.
- **Owns** [`Finding`](Finding.md), 1 → many, CASCADE
  (`supabase/migrations/0001_init.sql:274`). Zero findings is legal and
  meaningful — it is how a clean field is recorded.
- **Points at** [`StorageObject`](StorageObject.md), 1 → 0..1, via `storage_path`. Local-only
  samples have none.
- **Looks like but is not** `FlaggedFrame` (`domain/model/FlaggedFrame.kt`). That is the
  in-flight capture object carrying raw JPEG bytes and predictions. It is reconstructed
  *from* a flagged `SampleEntity` (`FlaggedFrameStore.kt::toFlaggedFrame`), not stored as one.

## If you change this

**Hits**
- `SyncSampleUseCase` and the insert row — anything that must reach Postgres has to be added
  to `SampleInsertRow` or it is silently dropped (`SampleRemoteDataSource.kt::toInsertRow`).
- Every `SampleDao` query filtering `status != 'flagged'`. The flagged/verified split is
  enforced in query text, not by a type — `grep -n "status != 'flagged'"` over `SampleDao.kt`
  lists them.
- The inference queue's `SampleDao` methods (`requeueInterruptedInference` through
  `cancelInference`). Each transition is a conditional UPDATE, so a new state or column has to
  keep every WHERE clause honest.
- The confirmed detections count, which joins samples
  (`DetectionDao.kt::getConfirmedEggCountsForSession`).
- The Patients list Recent sort (`PatientDao::observePatients`), which reads `samples.timestamp`
  and `samples.verified_at` (filtering `deleted_at IS NULL`) to sort active patients to the top.
- The report pipeline (`domain/usecase/records/GenerateSessionReportUseCase.kt`,
  `domain/usecase/records/ReportPdfBuilder.kt`).
- The Room database version (`core/database/AgarthaDatabase.kt`) and a hand-written
  `Migration` in `core/database/Migrations.kt` — see [effects](../effects/CONTEXT.md).

**Does not hit**
- The Storage object path. Changing `storage_path` in Room does **not** move or rename the
  file. The path is recomputed at upload from the live auth user id and the sample id
  (`SampleRemoteDataSource.kt::syncSample`), and the RLS policy validates the first folder
  segment against `auth.uid()` (`supabase/migrations/0001_init.sql:506-511`). Editing the
  column just desynchronises the pointer from the object.
- Detection bounding boxes. They are stored per-detection in source-image pixels and are not
  rescaled if you change the sample's `image_width` / `image_height`.

## Surfaces

Written by `PersistFlaggedFrameUseCase` at capture, moved through the inference queue by
`InferenceQueueRepositoryImpl`, updated by `SubmitVerificationUseCase`, tombstoned by
`DeleteQueueItemsUseCase`, and marked synced by `SyncSampleUseCase`. Read by the verification
queue, Patients, Session Detail, Sample Detail, Home, the confirmed detections count, and the
PDF report.

## See

`supabase/migrations/0001_init.sql:181-203`, `data/local/entity/SampleEntity.kt`,
`data/supabase/SampleRemoteDataSource.kt::SampleInsertRow`, `schema.ts` (`Sample`).
