package com.agarthavision.domain.usecase.settings

import com.agarthavision.data.local.dao.ReportDao
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.data.local.dao.SessionDao
import com.agarthavision.domain.model.PendingSyncCounts
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Observes live pending/failed sync counts across sessions, samples, and reports for
 * the signed-in medtech, backing the Settings Data & Sync section.
 *
 * Note: this reaches into DAOs directly, following the existing precedent of the sync
 * and verify use cases (see [com.agarthavision.domain.usecase.sync.SyncPendingDataUseCase]).
 * Consolidation into a shared local-sync abstraction is deferred to the Phase 2
 * WorkManager work — see CONTEXT.md.
 */
class ObservePendingSyncCountsUseCase @Inject constructor(
    private val sessionDao: SessionDao,
    private val sampleDao: SampleDao,
    private val reportDao: ReportDao,
) {
    /** Stream of [PendingSyncCounts] for [userId]. */
    operator fun invoke(userId: String): Flow<PendingSyncCounts> =
        combine(
            sessionDao.observePendingCount(userId),
            sampleDao.observePendingCount(userId),
            reportDao.observePendingCount(userId),
            sessionDao.observeFailedCount(userId),
            sampleDao.observeFailedCount(userId),
            reportDao.observeFailedCount(userId),
        ) { values ->
            PendingSyncCounts(
                pendingSessions = values[0],
                pendingSamples = values[1],
                pendingReports = values[2],
                failed = values[3] + values[4] + values[5],
            )
        }
}
