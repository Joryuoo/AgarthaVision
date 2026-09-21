# sync

Pushing local rows to Supabase. Foreground, trigger-based, best-effort.

**Input** — local rows and a live Supabase session.
**Output** — remote rows, a Storage object per sample, and updated local sync state.

**consumes** [`Patient`](../objects/Patient.md), [`Session`](../objects/Session.md),
[`Sample`](../objects/Sample.md), [`Detection`](../objects/Detection.md), [`Report`](../objects/Report.md)
**produces** [`StorageObject`](../objects/StorageObject.md)

## Movement — one sample

Runs inline at the end of [`validate`](validate.md).

1. **Load** the sample and its detections from Room
   (`data/supabase/SyncSampleUseCase.kt:30-32`). A missing sample fails immediately.
2. **Resize** the JPEG to 640×640 at quality 80, skipping the work if it is already that size
   (`data/supabase/SyncSampleUseCase.kt:58-83`).
3. **Require auth.** `SampleRemoteDataSource` throws if there is no live Supabase session — the
   cached identity is not sufficient here (`data/supabase/SampleRemoteDataSource.kt:33-34`).
4. **Upload** to `samples/{auth-user-id}/{sample_id}.jpg` with `upsert = true`. The user id
   comes from the live session, not from the Room row, which is what keeps the object key
   inside the RLS-permitted folder (`data/supabase/SampleRemoteDataSource.kt:35-39`).
5. **Insert the sample row**, then the detection rows if any
   (`data/supabase/SampleRemoteDataSource.kt:40-43`). Verdicts are uppercased on the way out
   (`data/supabase/SampleRemoteDataSource.kt:94`).
6. **Record the outcome.** Success writes `status = synced` and the returned storage path;
   failure writes `sync_failed` (`data/supabase/SyncSampleUseCase.kt:41-48`). Nothing is
   rolled back and nothing is retried here.

## Movement — the catch-up pass

`SyncPendingDataUseCase` is the trigger-based sweep. It runs on login success, on app start,
and after every local write.

1. **Skip cleanly** when there is no cached identity, no live auth session, or no network —
   returning `SyncSummary.Skipped`, not a failure
   (`domain/usecase/sync/SyncPendingDataUseCase.kt:70-74`).
2. **Push in FK-safe order**: **patients → sessions → samples → reports**
   (`domain/usecase/sync/SyncPendingDataUseCase.kt:76-90`). Patients go first because
   `sessions.patient_id` references `patients(id)`. A per-row failure marks that row and
   does not abort the pass.

Report pushes are row-only — the PDF/CSV never leaves the device (`data/supabase/SyncReportUseCase.kt`).
Because login is mandatory on first launch, every entity has an owner from creation and no
deferred claiming step is needed.

## Hits

- **The insert row is the contract.** A new column that is not added to `PatientInsertRow`,
  `SessionInsertRow`, `SampleInsertRow`, or `ReportInsertRow` never reaches Postgres, with no error
  (`data/supabase/PatientRemoteDataSource.kt`,
  `data/supabase/SessionRemoteDataSource.kt`,
  `data/supabase/SampleRemoteDataSource.kt`,
  `data/supabase/ReportRemoteDataSource.kt`).
- **RLS.** Every insert must satisfy ownership: `auth.uid() = created_by` on patients
  (`0001_init.sql:369-371`), `auth.uid() = user_id` on sessions/samples/reports
  (`0001_init.sql:405-420`), and detections/findings are checked through the parent
  sample (`0001_init.sql:441-470`). Sync only ever runs authenticated.
- **Four separate sync-state enums** — `PatientSyncStatus`, `SessionSyncStatus`,
  `SampleStatus`, `ReportSyncStatus` — all managing pending/synced/sync_failed states. Changing the
  vocabulary means changing all four plus every raw query that names a value.

## Does not hit

- **Retries** are WorkManager's, not this use case's. A pass that fails returns
  `Result.retry()` from `data/sync/SyncWorker`, which backs off exponentially from 30s. The
  use case itself still tolerates individual row failures without failing the pass, so a
  single `sync_failed` row is not what triggers a retry — an unexpected error reading the
  queues is.

  `SyncSummary.Skipped` (unauthenticated or offline) reports **success**, not retry: there is
  no credential to acquire by trying again, and backing off against a signed-out device would
  delay the pass that matters after login.

  **Triggers:** app start, login, both "Sync now" buttons, and every local write — patient
  insert and update, session start, verification submit, report generate. All but login go
  through `SyncScheduler.requestSync()`, which is idempotent: one unique work name with
  `ExistingWorkPolicy.KEEP`, so several writes in a minute collapse into one pass. Login is
  the exception and stays a direct awaited call, because PB-08a depends on the medtech having
  their patients on the device before they leave the clinic.

  **On the target fleet this will not always run.** MIUI gates background work behind a
  per-app "Background autostart" permission that is off by default, and Xiaomi handsets are
  what the medtechs carry. Nothing is load-bearing on it: the queue is durable in Room, and a
  missed pass is caught by the next foreground trigger.
- **Never expedited.** Below API 31 WorkManager implements an expedited request as a
  foreground service and calls `getForegroundInfo()`, whose `CoroutineWorker` default throws
  `IllegalStateException("Not implemented")`. `minSdk` is 26 and the fleet runs Android 11, so
  `setExpedited` killed every pass before `doWork` ran. A foreground service is also the one
  thing this deliberately avoids.
- **Recording.** Losing Supabase mid-session does not stop capture. Only losing the inference
  container does — see [`infer`](infer.md).
- **Deletion.** Sync only inserts and upserts. Nothing here can remove a remote row or a
  Storage object (`../../constraints.md` C8).
- **Repeat samples.** Gone as of 86d4ab4vm — duplicates are deleted, not flagged. While it
  existed, `is_repeat` had no Postgres column and was not in the insert row — the
  flag stays local by design (`supabase/migrations/0006_sample_is_manual.sql:9-11`).
