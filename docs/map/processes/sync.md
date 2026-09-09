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

`SyncPendingDataUseCase` is the trigger-based sweep. It runs on login success, on app start
while authenticated, and when connectivity returns.

1. **Skip cleanly** when there is no cached identity, no live auth session, or no network —
   returning `SyncSummary.Skipped`, not a failure
   (`domain/usecase/sync/SyncPendingDataUseCase.kt:61-64`).
2. **Push in FK-safe order**: sessions, then samples, then reports
   (`domain/usecase/sync/SyncPendingDataUseCase.kt:66-74`). A per-row failure marks that row and
   does not abort the pass.

Session pushes are upserts so a pending row can be re-sent carrying an `ended_at` set while
offline (`data/supabase/SessionRemoteDataSource.kt:38-40`). Report pushes are row-only — the
CSV never leaves the device (`data/supabase/SyncReportUseCase.kt:25-35`).

## Claim before sync

A row with `user_id = NULL` cannot satisfy the remote NOT NULL constraint, so login claims
first: unowned non-exempt sessions, cascading to their samples and reports, all idempotent
because only `user_id IS NULL` rows are touched
(`domain/usecase/auth/ClaimLocalDataUseCase.kt:33-53`, `data/local/dao/SampleDao.kt:151-158`).
Only then does the sync pass run. Claim-exempt sessions are never pushed
(`core/session/SessionManager.kt:147-149`).

## Hits

- **The insert row is the contract.** A new column that is not added to `SampleInsertRow`,
  `SessionInsertRow`, or `ReportInsertRow` never reaches Postgres, with no error
  (`data/supabase/SampleRemoteDataSource.kt:100-152`,
  `data/supabase/SessionRemoteDataSource.kt:74-90`,
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

- **Retries.** There are none. A `sync_failed` row waits for the next trigger; there is no
  backoff, no scheduler, and **no `Worker`** — WorkManager is a declared dependency with no
  implementation (`app/build.gradle.kts:164`, and see `../../features.md`). The durable queue
  is Phase 2.
- **Recording.** Losing Supabase mid-session does not stop capture. Only losing the inference
  container does — see [`infer`](infer.md).
- **Deletion.** Sync only inserts and upserts. Nothing here can remove a remote row or a
  Storage object (`../../constraints.md` C8).
- **Repeat samples.** `is_repeat` has no Postgres column and is not in the insert row — the
  flag stays local by design (`supabase/migrations/0006_sample_is_manual.sql:9-11`).
