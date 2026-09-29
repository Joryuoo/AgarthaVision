---
type: process
status: verified
verified: 2026-09-29
commit: feaa4803
---

# delete-sample

Taking frames out of the queue: a hard delete for a draft, a tombstone for anything verified.

**Input** — a selection of queue rows.
**Output** — unverified rows and their JPEGs gone; verified rows hidden by `deleted_at`.

**consumes** [`Sample`](../objects/Sample.md)
**produces** a tombstoned [`Sample`](../objects/Sample.md), or nothing

## Movement

1. **Select and confirm.** The verification queue's batch delete
   (`ui/verify/VerificationQueueViewModel.kt`, `DeleteSamplesConfirmDialog.kt`) hands the ids to
   `DeleteQueueItemsUseCase`.
2. **Branch per sample, on `status`.** `DeleteQueueItemsUseCase::invoke` reads each row including
   deleted ones, skips any already tombstoned, then:
   - **`flagged`** (never verified) → `DeleteFlaggedSampleUseCase`: deletes the JPEG, then the row.
     C8's local exception — nothing has been asserted about a draft. This includes a frame still
     queued for inference: the queue's conditional UPDATEs make a late model result a no-op.
   - **anything else** (verified) → `SampleDao::tombstoneSample` sets `deleted_at` and puts
     `status` back to `verified`, so the row re-enters the push set.
   The branch uses the same predicate the queue buckets use, so a mixed selection just takes both
   paths.
3. **Push once.** After the batch, if anything was tombstoned, `SyncPendingDataUseCase` runs once.
4. **Report honestly.** The result is a `DeleteSummary` of hard-deleted and tombstoned counts.

**Gap, code wins: step 3 does not carry the tombstone.** The push upserts
`SampleRemoteDataSource.kt::SampleInsertRow`, which has no `deleted_at`, so Postgres and every
other device keep the sample live. See [`Sample`](../objects/Sample.md).

## Why this shape

A verified sample's detections are the retraining corpus, so it is never deleted (C8). A medtech
who captured the same field twice still needs the duplicate out of the queue, the counts and the
report — the tombstone gives exactly that and nothing more. A draft has no corpus value, so it is
simply removed.

## If you change this

**Hits**
- Every query that lists or counts samples: each filters `deleted_at IS NULL`, enforced by
  `SoftDeleteGuardTest` and the `IncludingDeleted` naming rule.
- Sign-out's discard, which hard-deletes *unsynced* verified samples, tombstoned or not — it is the
  one other path that removes a verified row, and it is inside C8 only because those rows never
  reached the corpus ([`sign-in`](sign-in.md)).

**Does not hit**
- The JPEG or the Storage object of a verified sample. Both stay.
- Detections and findings of a tombstoned sample. They stay, and still sync.

## Surfaces

The verification queue screen's selection mode.

## See

`domain/usecase/verify/DeleteQueueItemsUseCase.kt`,
`domain/usecase/capture/DeleteFlaggedSampleUseCase.kt`, `data/local/dao/SampleDao.kt`.
