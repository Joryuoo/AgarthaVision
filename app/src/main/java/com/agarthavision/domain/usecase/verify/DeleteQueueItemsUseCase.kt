package com.agarthavision.domain.usecase.verify

import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.domain.model.SampleStatus
import com.agarthavision.domain.usecase.capture.DeleteFlaggedSampleUseCase
import com.agarthavision.domain.usecase.sync.SyncPendingDataUseCase
import java.time.Instant
import javax.inject.Inject

/** What a delete actually did, so the caller can report it honestly. */
data class DeleteSummary(
    val hardDeleted: Int,
    val tombstoned: Int,
) {
    val total: Int get() = hardDeleted + tombstoned
}

/**
 * Removes samples from the queue — two different ways, decided per sample rather than by the UI.
 *
 * **An unverified frame is hard-deleted.** Nothing has been asserted about it, its JPEG is the
 * only artefact, and constraint C8's local exception explicitly permits discarding a flagged
 * frame before submission.
 *
 * **A verified sample is tombstoned.** C8 and `docs/non-negotiables.md` both say a verified
 * sample, its detections and its Storage object are never deleted, because `detections` doubles
 * as the retraining corpus — a rejection there is a labelled `FALSE_POSITIVE` row, not an
 * absence. Setting `deleted_at` gives the medtech what they actually want (the duplicate gone
 * from every queue, count and report) without destroying what the corpus needs. The JPEG stays,
 * the Storage object stays, and `0003_storage_rls.sql` still creates no DELETE policy.
 *
 * The branch is `status == FLAGGED`, which is the same predicate the queue buckets use, so there
 * is no second definition of "verified" anywhere in the app. A mixed selection simply takes both
 * branches.
 */
class DeleteQueueItemsUseCase @Inject constructor(
    private val sampleDao: SampleDao,
    private val deleteFlaggedSampleUseCase: DeleteFlaggedSampleUseCase,
    private val syncPendingDataUseCase: SyncPendingDataUseCase,
) {
    suspend operator fun invoke(sampleIds: Set<String>): Result<DeleteSummary> = runCatching {
        var hardDeleted = 0
        var tombstoned = 0

        sampleIds.forEach { sampleId ->
            // Including deleted: tombstoning something already tombstoned should be a no-op,
            // not a crash, and a concurrent delete must not fail the whole batch.
            val sample = sampleDao.getSampleByIdIncludingDeleted(sampleId) ?: return@forEach
            if (sample.deletedAt != null) return@forEach

            if (sample.status == SampleStatus.FLAGGED.value) {
                deleteFlaggedSampleUseCase(sampleId)
                hardDeleted++
            } else {
                sampleDao.tombstoneSample(
                    sampleId = sampleId,
                    deletedAt = Instant.now().toEpochMilli(),
                    // Back to VERIFIED so the row re-enters the pending-sync set and the
                    // tombstone itself reaches Supabase. Without this the delete is local-only.
                    status = SampleStatus.VERIFIED.value,
                )
                tombstoned++
            }
        }

        // Once, after the batch. Per item it would be a push per tap on a slow connection.
        if (tombstoned > 0) {
            syncPendingDataUseCase()
        }

        DeleteSummary(hardDeleted = hardDeleted, tombstoned = tombstoned)
    }
}
