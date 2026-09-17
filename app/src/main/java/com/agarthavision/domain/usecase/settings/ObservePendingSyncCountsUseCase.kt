package com.agarthavision.domain.usecase.settings

import com.agarthavision.data.local.dao.PatientDao
import com.agarthavision.data.local.dao.ReportDao
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.data.local.dao.SessionDao
import com.agarthavision.domain.model.PendingSyncCounts
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Observes live pending/failed sync counts across patients, sessions, samples, and reports
 * for the signed-in medtech, backing the Settings Data & Sync section.
 *
 * Note: this reaches into DAOs directly, following the existing precedent of the sync
 * and verify use cases (see [com.agarthavision.domain.usecase.sync.SyncPendingDataUseCase]).
 * Consolidation into a shared local-sync abstraction is deferred to the Phase 2
 * WorkManager work — see CONTEXT.md.
 */
class ObservePendingSyncCountsUseCase @Inject constructor(
    private val patientDao: PatientDao,
    private val sessionDao: SessionDao,
    private val sampleDao: SampleDao,
    private val reportDao: ReportDao,
) {
    /** Stream of [PendingSyncCounts] for [userId]. */
    operator fun invoke(userId: String): Flow<PendingSyncCounts> =
        combine(
            patientDao.observePendingCount(userId),
            sessionDao.observePendingCount(userId),
            sampleDao.observePendingCount(userId),
            reportDao.observePendingCount(userId),
            patientDao.observeFailedCount(userId),
            sessionDao.observeFailedCount(userId),
            sampleDao.observeFailedCount(userId),
            reportDao.observeFailedCount(userId),
        ) { values ->
            PendingSyncCounts(
                pendingPatients = values[0],
                pendingSessions = values[1],
                pendingSamples = values[2],
                pendingReports = values[3],
                failed = values[4] + values[5] + values[6] + values[7],
            )
        }
}
