# sync

Pushing local rows to Supabase. Foreground, trigger-based, best-effort.

**Input** — local rows and a live Supabase session.
**Output** — remote rows, a Storage object per sample, and updated local sync state.

**consumes** [`Session`](../objects/Session.md), [`Sample`](../objects/Sample.md),
[`Detection`](../objects/Detection.md), [`Report`](../objects/Report.md)
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
and after every local write. **Not when connectivity returns** - nothing observes
`ConnectivityObserver` to start a pass, and the worker's network constraint only gates a pass
already requested. See the trigger list below, which is the authoritative one.

1. **Skip cleanly** when there is no cached identity, no live auth session, or no network —
   returning `SyncSummary.Skipped`, not a failure
   (`domain/usecase/sync/SyncPendingDataUseCase.kt:61-64`).
2. **Push in FK-safe order**: sessions, then samples, then reports
   (`domain/usecase/sync/SyncPendingDataUseCase.kt:66-74`). A per-row failure marks that row and
   does not abort the pass.

Session pushes are upserts so a pending row can be re-sent carrying an `ended_at` set while
offline (`data/supabase/SessionRemoteDataSource.kt:38-40`). Report pushes are row-only — the
CSV never leaves the device (`data/supabase/SyncReportUseCase.kt:25-35`).

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

## Claim before sync

A row with `user_id = NULL` cannot satisfy the remote NOT NULL constraint, so login claims
first: unowned non-exempt sessions, cascading to their samples and reports, all idempotent
because only `user_id IS NULL` rows are touched
(`domain/usecase/auth/ClaimLocalDataUseCase.kt:33-53`, `data/local/dao/SampleDao.kt:151-158`).
Only then does the sync pass run. Claim-exempt sessions are never pushed
(`core/session/SessionManager.kt:133-135`).

## Hits

- **The insert row is the contract.** A new column that is not added to `SampleInsertRow`,
  `SessionInsertRow`, or `ReportInsertRow` never reaches Postgres, with no error
  (`data/supabase/SampleRemoteDataSource.kt:100-152`,
  `data/supabase/SessionRemoteDataSource.kt:76-93`,
  `data/supabase/ReportRemoteDataSource.kt:61-83`).
- **RLS.** Every insert must satisfy `auth.uid() = user_id`
  (`supabase/migrations/0001_init.sql:99-117`), and detections are checked through the parent
  sample (`supabase/migrations/0001_init.sql:133-139`). Sync only ever runs authenticated and
  post-claim, which is why no Supabase schema change was needed for offline mode.
- **Three separate sync-state enums** — `SampleStatus`, `SessionSyncStatus`,
  `ReportSyncStatus` — all with the same three states and different homes. Changing the
  vocabulary means changing all three plus every raw query that names a value
  (`domain/model/SessionSyncStatus.kt:10-12` acknowledges this).

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
