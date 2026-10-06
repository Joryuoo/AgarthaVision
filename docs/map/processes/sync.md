---
type: process
status: verified
verified: 2026-10-05
commit: 33bced13
---

# sync

Pushing local rows to Supabase, and pulling them back. Trigger-based, run by WorkManager,
best-effort.

**Input** — local rows and a live Supabase session.
**Output** — remote rows, a Storage object per sample, and updated local sync state.

**consumes** [`Patient`](../objects/Patient.md), [`Session`](../objects/Session.md),
[`Sample`](../objects/Sample.md), [`Prediction`](../objects/Prediction.md),
[`Detection`](../objects/Detection.md), [`Report`](../objects/Report.md)
**produces** [`StorageObject`](../objects/StorageObject.md)

## Movement — one sample

Started at the end of [`validate`](validate.md), but not awaited by it
(`data/sync/BackgroundSamplePush.kt`): the push runs in a process-lifetime scope, then asks for a
catch-up pass, in that order — asking first would run a second push of the same sample beside it.

1. **Load** the sample — *including* a tombstoned one — with its detections and findings
   (`SyncSampleUseCase::invoke`). A missing sample fails immediately; an already-`synced` one
   returns at once.
2. **Push the parent first.** `SyncSessionUseCase` runs for the sample's session (and through it
   the patient) before anything else, so the sample insert cannot race its own FK; a parent
   failure fails the sample loudly (86d4c2q2m).
3. **Resize** the JPEG to 640×640 at quality 80, skipping the work if it is already that size
   (`SyncSampleUseCase.kt::resizeToSyncJpeg`).
4. **Require auth.** `SampleRemoteDataSource` throws if there is no live Supabase session — the
   cached identity is not sufficient here (`SampleRemoteDataSource.kt::syncSample`).
5. **Upload** to `samples/{auth-user-id}/{sample_id}.jpg` with `upsert = true`. The user id
   comes from the live session, not from the Room row, which is what keeps the object key
   inside the RLS-permitted folder.
6. **Upsert the sample row, then its predictions, then its detections, then replace its
   findings** — the FK chain, in one call (`SampleRemoteDataSource.kt::syncSample`). Findings are
   delete-then-insert for the sample, so a species removed on re-edit disappears remotely too. Predictions are built from
   `predictions_json` and written insert-if-absent, because a model's output never changes and
   `predictions` grants no UPDATE policy. Each detection carries `prediction_id` for the
   prediction at its own ordinal, null for an egg the medtech added. When the device holds no
   model output for the sample, `prediction_id` is **left out of the payload** rather than sent
   as null, so a link the server already has survives. Verdicts are uppercased on the way out.
   Every write is idempotent, so a pass that fails part-way converges on retry.
7. **Record the outcome.** Success writes `status = synced` and the returned storage path;
   failure writes `sync_failed` and logs a `[SyncFailed]` line with a failure class
   (`SyncSampleUseCase::invoke`). Nothing is rolled back and nothing is retried here.

**Gap, code wins: a tombstone does not reach Postgres.** Step 1 reads the tombstoned row so it
can "push its tombstone", but `SampleInsertRow` has no `deleted_at`, so the upsert never carries
it. See [`Sample`](../objects/Sample.md).

## Movement — the catch-up pass

`SyncPendingDataUseCase` is the trigger-based sweep. It runs as a background pass after login success (see Triggers), on app start,
and after every local write.

0. **Check the account first** (14zcqntjph8). `SyncWorker::doWork` runs
   `EnforceAccountAccessUseCase` before the push. If the server refuses the account, the phone's
   synced data is removed, it is signed out, and the pass ends there; otherwise nothing changes. See
   [`sign-in`](sign-in.md), "the server refuses the account". The post-login pass runs
   this like any other, which is harmless: the medtech has just signed in.
1. **Skip cleanly** when there is no cached identity, no live auth session, or no network —
   returning `SyncSummary.Skipped`, not a failure
   (`SyncPendingDataUseCase::invoke`).
2. **Push in FK-safe order**: **patients → sessions → samples → reports**
   (`SyncPendingDataUseCase::invoke`). Samples are read *including deleted*. Patients go first because
   `sessions.patient_id` references `patients(id)`. A per-row failure marks that row and
   does not abort the pass.

A report push is the row, then its PDF into the `reports` bucket; a file the device no
longer holds is skipped rather than failing the row (`data/supabase/SyncReportUseCase.kt`).
PDF-only since 86d4be47c — a report never carries a CSV to push.
`RestoreReportFilesUseCase` pulls the file back on first open on another device.
Because login is mandatory on first launch, every entity has an owner from creation and no
deferred claiming step is needed.

## Movement — the pull, and whose rows come down

`FetchRemoteDataUseCase` pulls patients → sessions → samples (with predictions, detections and
findings) → reports, then the frames.

1. **The pull scopes itself; RLS is the second line** (14zcqntjt3p). The device holds the
   user's own rows plus the full history of every patient their own `patient_users` rows name,
   colleagues' rows included (`0007_patient_shared_history.sql`). That is what a medtech's
   policies return, and every fetch asks for exactly that, because the same tables give an org
   admin (console `admin/0002`) their whole laboratory, and a super admin every patient link,
   report and detection (`0013_super_admin_reads_deidentified.sql` took the rest):
   - links filtered to the user (`PatientRemoteDataSource.kt::fetchPatientLinks`), then those
     patients by id (`::fetchPatients`);
   - sessions, samples and reports each fetched as "own" plus "under these parents"
     (`SessionRemoteDataSource.kt::fetchOwnSessions` / `::fetchSessionsForPatients`, and the same
     pair on `SampleRemoteDataSource.kt` and `ReportRemoteDataSource.kt`), ids sent 100 at a
     time. The parents are read from the device after the step before has written them
     (`PatientDao::getLinksForUser`, `SessionDao::getSessionIdsOnLinkedPatients`), so a step
     that failed still leaves the next one a scope;
   - a row that arrives in both halves is handled once;
   - colleagues' names by id, for the authors on those patients only
     (`ColleagueDao::getColleagueIdsOnLinkedPatients`).

   A colleague's row lands `synced` and is never edited here, so the E4 guard lets every later
   pull refresh it.
2. **Revoked assignments leave.** After every link page arrives, a local link the server no
   longer returns is removed — unless its patient is still waiting to push, whose creator link
   the server has not seen yet (`FetchRemoteDataUseCase.kt::removeRevokedLinks`). Only the
   access row goes; the patient and its records stay (C8).
3. **Orphans are skipped.** The server keeps showing an author their own sessions of a patient
   they were unassigned from, so a session whose patient is not on the device, and a sample or
   report whose session is not, is logged and skipped rather than written
   (`PatientDao::patientExists`, `SessionDao::sessionExists`). Room enforces both foreign keys,
   and one such row used to fail its whole entity type on every pass.
4. **Label clashes are settled after every page.** A pulled session whose label another local
   session of the patient holds is written last, with the same `-XXXX` suffix the server's
   0009 trigger uses, so this device's own renamed row lands first and a colleague's row keeps
   the label the server left it (`FetchRemoteDataUseCase.kt::pullSessions`). A clash never
   fails or skips a row.
5. **Pushes stay author-only.** Every `get…PendingSync` query filters on `user_id`, so a
   colleague's row can never enter the push queue.

## Frames on the device — the image cache

The pull brings rows; `CacheSampleImagesUseCase` brings the JPEGs they point at, in the same
pass, after the rows are down (86d4by5n9). Fetching on open was not enough: editing a sample
means placing a box against its frame, so a sample with no image cannot be corrected at all,
and the barangay with no signal is exactly where that bites.

**The retention policy, and it is a decision rather than a side effect.** A byte budget —
`MAX_CACHE_BYTES`, 256 MB — spent newest-verification-first. The pull fills from the top of
that list and the eviction trims from the bottom, so a device that cannot hold everything holds
the most recent work. "Every verified sample ever" bounds nothing, and a device that fills up
in the field is its own outage; a last-N-days or per-patient rule bounds nothing either on a
device that works one barangay all quarter.

Two rules the budget never breaks:

- **An unpushed sample is never evicted.** Its local JPEG is the only copy in existence until
  it reaches Storage. `SampleDao.getCacheableSamples` filters on a non-empty `storage_path`, so
  such a sample cannot reach the eviction loop at all.
- **Eviction removes a copy, never a record.** The row, its detections and the Storage object
  all survive (C8); only this device's duplicate goes, and re-syncing brings it back.

`MAX_IMAGES_PER_PASS` caps one pass at 50 frames, so a first sign-in against a year of history
does not spend an hour of radio. The rest come down on later passes, and until they do
`FetchType.SAMPLE_IMAGES` sits in `FetchSummary.Ran.failed`, which is what stops the Settings
card reading "All synced" on a device holding rows it cannot open.

`markCompleted` is deliberately **not** gated on images: it answers "did this account's rows
arrive", and a Storage object that is gone for good must not pin the badge at NOT_YET_SYNCED
forever on a device that really does have every row.

Whether a frame is held is answered by the disk — `SampleImageStore.pathFor` is derived from
the user and sample ids — not by `samples.image_path`, which arrives empty on every pulled row
and is repaired from the disk rather than trusted.


## Hits

- **The pull restores `predictions_json`.** The server's sample row has no such column, so a
  pulled row keeps the device's own copy, and `pullChildRowsFor` then folds a whole set of
  `predictions` rows back into it — fetched before detections, so a failure writes no detection
  without its model output. A set with a missing ordinal is ignored
  (`FetchRemoteDataUseCase.kt::withLocalPredictions`, `::pullChildRowsFor`).
- **Deploy order.** `0004_predictions.sql` must be applied before a build carrying this change
  syncs: until it is, every sample push and pull fails on the missing table. Older builds are
  unaffected by the migration — they never name the new column, and supabase-kt ignores
  unknown keys on read.
- **The insert row is the contract.** A new column that is not added to `PatientInsertRow`,
  `SessionInsertRow`, `SampleInsertRow`, or `ReportInsertRow` never reaches Postgres, with no error
  (`data/supabase/PatientRemoteDataSource.kt`,
  `data/supabase/SessionRemoteDataSource.kt`,
  `data/supabase/SampleRemoteDataSource.kt`,
  `data/supabase/ReportRemoteDataSource.kt`).
- **RLS, reads.** Since 0007 every clinical table and both buckets are readable by anyone
  assigned to the patient, through `is_linked_to_patient`, `can_read_session` and
  `can_read_sample` (`0007_patient_shared_history.sql:44-93`). The phone's session-scoped reads
  mirror it with an `EXISTS` over `patient_users` (`SampleDao::observeSamplesForSession`,
  `DetectionDao::getConfirmedEggCountsForSession`, `SampleSpeciesFindingDao::getFindingsForSession`,
  `ReportDao::observeReportsForSession`, `SampleDao::getCacheableSamples`).
- **RLS, writes.** Every insert must satisfy ownership: `auth.uid() = created_by` on patients
  (`0001_init.sql:369`), `auth.uid() = user_id` on sessions, samples and reports
  (`0001_init.sql:405`, `:418`, `:497`), and detections and findings are checked through the
  parent sample (`0001_init.sql:431-491`). Sync only ever runs authenticated.
- **Four separate sync-state enums** — `PatientSyncStatus`, `SessionSyncStatus`,
  `SampleStatus`, `ReportSyncStatus` — all managing pending/synced/sync_failed states. Changing the
  vocabulary means changing all four plus every raw query that names a value.
- **The pull side skips a bad row too, not just the push side.** A patient report whose patient
  hasn't landed on this device yet fails its FK on insert; `FetchRemoteDataUseCase.pullReports`
  catches `SQLiteConstraintException` per row rather than letting it abort the rest of the page,
  the same convention `upsertSessionReconcilingLabel` already used for sessions. The skipped row
  is retried on the next pull once its patient exists locally — one unlinked patient must not
  block every other report in the same account from downloading.

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
  insert and update, session start, verification submit, report generate. Most go
  through `SyncScheduler.requestSync()`, which is idempotent: one unique work name with
  `ExistingWorkPolicy.KEEP`, so several writes in a minute collapse into one pass. Login is
  no longer a direct awaited full pass. `CompleteSignInUseCase` awaits only
  `FetchRemoteDataUseCase::pullPatientsOnly` (PB-08a: patients on the device before leaving the
  clinic) and then calls `SyncScheduler.requestSyncAfterSignIn()`, which enqueues under the same
  unique name with `ExistingWorkPolicy.APPEND_OR_REPLACE`. KEEP would be wrong here: on first
  install the app-start pass can still be running or queued during sign-in, having read "no
  identity" and finished as `Skipped`, and KEEP would drop the post-login request behind it so no
  pass would run after login. APPEND_OR_REPLACE chains after the in-flight pass instead.

  **On the target fleet this will not always run.** MIUI gates background work behind a
  per-app "Background autostart" permission that is off by default, and Xiaomi handsets are
  what the medtechs carry. Nothing is load-bearing on it: the queue is durable in Room, and a
  missed pass is caught by the next foreground trigger.
- **Never expedited.** Below API 31 WorkManager implements an expedited request as a
  foreground service and calls `getForegroundInfo()`, whose `CoroutineWorker` default throws
  `IllegalStateException("Not implemented")`. `minSdk` is 26 and the fleet runs Android 11, so
  `setExpedited` killed every pass before `doWork` ran. A foreground service is also the one
  thing this deliberately avoids.
- **Recording.** Losing Supabase mid-session does not stop capture, and neither does losing the
  inference container — see [`infer`](infer.md).
- **Deletion.** Sync inserts and upserts, with one exception: a sample's findings rows are
  deleted and re-inserted, because a count is a current statement (C8 reading in
  [`Finding`](../objects/Finding.md)). Nothing here can remove a sample, a detection, a
  prediction or a Storage object (`../../constraints.md` C8).
- **Repeat samples.** Gone as of 86d4ab4vm — duplicates are deleted, not flagged. While it
  existed, `is_repeat` had no Postgres column and was not in the insert row — the
  flag stays local by design (`supabase/migrations/legacy-dev/0006_sample_is_manual.sql:9-11`).
