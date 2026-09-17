package com.agarthavision.domain.usecase.sync

import com.agarthavision.core.connectivity.ConnectivityObserver
import com.agarthavision.data.local.dao.PatientDao
import com.agarthavision.data.local.dao.ReportDao
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.data.local.dao.SessionDao
import com.agarthavision.data.supabase.SyncPatientUseCase
import com.agarthavision.data.supabase.SyncReportUseCase
import com.agarthavision.data.supabase.SyncSampleUseCase
import com.agarthavision.data.supabase.SyncSessionUseCase
import com.agarthavision.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Outcome of a [SyncPendingDataUseCase] pass.
 */
sealed interface SyncSummary {
    /** Skipped because the medtech is signed out or the device is offline. */
    data object Skipped : SyncSummary

    /**
     * Ran the pass. Counts are rows that pushed successfully; a per-row failure is
     * recorded on the row (marked `sync_failed`) without aborting the pass.
     */
    data class Ran(
        val patientsSynced: Int,
        val sessionsSynced: Int,
        val samplesSynced: Int,
        val reportsSynced: Int,
    ) : SyncSummary
}

/**
 * Pushes all pending local rows to Supabase in FK-safe order (patients → sessions →
 * samples → reports) for the signed-in medtech.
 *
 * Patients go first because `sessions.patient_id` references `patients(id)`: a session
 * pushed ahead of the patient it belongs to is rejected on its foreign key. Nothing local
 * catches that — Room holds its own FK, not the server's — so the order here is the only
 * thing enforcing it, and it is asserted in [SyncPendingDataUseCaseTest].
 *
 * Per ADR-007 this is the trigger-based foreground sync: it runs on login success, on app
 * start while authenticated, and when connectivity returns. It no-ops cleanly with
 * [SyncSummary.Skipped] when unauthenticated or offline. The durable WorkManager-backed
 * queue with backoff stays Phase 2 — see CONTEXT.md.
 */
// Composition-root use case wiring 10 distinct, non-overlapping DI dependencies (auth,
// connectivity, DAOs, per-entity sync use cases); each is independently meaningful and
// bundling would not simplify the real dependency graph.
@Suppress("LongParameterList")
class SyncPendingDataUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val connectivityObserver: ConnectivityObserver,
    private val patientDao: PatientDao,
    private val sessionDao: SessionDao,
    private val sampleDao: SampleDao,
    private val reportDao: ReportDao,
    private val syncPatientUseCase: SyncPatientUseCase,
    private val syncSessionUseCase: SyncSessionUseCase,
    private val syncSampleUseCase: SyncSampleUseCase,
    private val syncReportUseCase: SyncReportUseCase,
) {
    /**
     * Runs one sync pass.
     *
     * @return [Result.success] with a [SyncSummary]; individual row failures do not fail
     * the pass. [Result.failure] only on an unexpected error reading the pending queues.
     */
    suspend operator fun invoke(): Result<SyncSummary> = runCatching {
        val userId = authRepository.currentLocalUserId()
        if (userId == null || !authRepository.isAuthenticated() || !connectivityObserver.currentlyOnline()) {
            return@runCatching SyncSummary.Skipped
        }

        // Patients first: a session insert fails on sessions.patient_id otherwise.
        val patientsSynced = patientDao.getPatientsPendingSync().count { patient ->
            syncPatientUseCase(patient.patientId).isSuccess
        }
        val sessionsSynced = sessionDao.getSessionsPendingSync(userId).count { session ->
            syncSessionUseCase(session.sessionId).isSuccess
        }
        val samplesSynced = sampleDao.getSamplesPendingSyncIncludingDeleted(userId).count { sample ->
            syncSampleUseCase(sample.sampleId).isSuccess
        }
        val reportsSynced = reportDao.getReportsPendingSync(userId).count { report ->
            syncReportUseCase(report.reportId).isSuccess
        }

        SyncSummary.Ran(
            patientsSynced = patientsSynced,
            sessionsSynced = sessionsSynced,
            samplesSynced = samplesSynced,
            reportsSynced = reportsSynced,
        )
    }
}
