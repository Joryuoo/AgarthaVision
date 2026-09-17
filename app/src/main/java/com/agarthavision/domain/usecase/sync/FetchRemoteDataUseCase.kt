package com.agarthavision.domain.usecase.sync

import android.util.Log
import com.agarthavision.core.connectivity.ConnectivityObserver
import com.agarthavision.core.sync.InitialFetchStateStore
import com.agarthavision.data.local.dao.DetectionDao
import com.agarthavision.data.local.dao.PatientDao
import com.agarthavision.data.local.dao.ReportDao
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.data.local.dao.SampleSpeciesFindingDao
import com.agarthavision.data.local.dao.SessionDao
import com.agarthavision.data.local.species.SpeciesSuggestionSeeder
import com.agarthavision.data.supabase.PatientRemoteDataSource
import com.agarthavision.data.supabase.ReportRemoteDataSource
import com.agarthavision.data.supabase.SampleRemoteDataSource
import com.agarthavision.data.supabase.SessionRemoteDataSource
import com.agarthavision.domain.model.PatientSyncStatus
import com.agarthavision.domain.model.ReportSyncStatus
import com.agarthavision.domain.model.SampleStatus
import com.agarthavision.domain.model.SessionSyncStatus
import com.agarthavision.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Outcome of a [FetchRemoteDataUseCase] pass.
 */
sealed interface FetchSummary {
    /** Skipped because the medtech is signed out or the device is offline. */
    data object Skipped : FetchSummary

    /**
     * Ran the pass. Counts are rows inserted or updated locally from the remote.
     */
    data class Ran(
        val patientsFetched: Int,
        val sessionsFetched: Int,
        val samplesFetched: Int,
        val reportsFetched: Int,
    ) : FetchSummary
}

/**
 * Pulls all remote rows from Supabase into the local Room database for the signed-in
 * medtech. Run in FK-safe order (patients → sessions → samples+detections+findings →
 * reports).
 *
 * Patients come first because `sessions.patient_id` is a foreign key onto `patients`: a
 * session row arriving before the patient it belongs to violates it locally, and Room does
 * enforce this one.
 *
 * Per the additive-only policy (D4): rows present only on the server are inserted locally;
 * locally-VERIFIED or SYNC_FAILED rows are never overwritten by a server pull (E4 guard).
 * Deleted samples (deleted_at not null) become tombstoned local rows, which existing
 * `deleted_at IS NULL` guards already hide from all lists and counts.
 *
 * [InitialFetchStateStore.markCompleted] is called only when **all four** entity types
 * succeeded; a partial failure leaves the flag unset so the badge stays NOT_YET_SYNCED
 * and the medtech can retry via "Sync now". Per E2. Claiming a complete offline cache the
 * device does not have is the failure this guards: the medtech finds out in a barangay
 * with no signal.
 */
@Suppress("LongParameterList")
class FetchRemoteDataUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val connectivityObserver: ConnectivityObserver,
    private val patientRemoteDataSource: PatientRemoteDataSource,
    private val sampleRemoteDataSource: SampleRemoteDataSource,
    private val sessionRemoteDataSource: SessionRemoteDataSource,
    private val reportRemoteDataSource: ReportRemoteDataSource,
    private val patientDao: PatientDao,
    private val sessionDao: SessionDao,
    private val sampleDao: SampleDao,
    private val detectionDao: DetectionDao,
    private val sampleSpeciesFindingDao: SampleSpeciesFindingDao,
    private val reportDao: ReportDao,
    private val initialFetchStateStore: InitialFetchStateStore,
    private val speciesSuggestionSeeder: SpeciesSuggestionSeeder,
) {
    /**
     * Runs one fetch pass.
     *
     * @return [Result.success] with a [FetchSummary]; per-entity type failures are caught
     * locally and do not abort the other types. [Result.failure] only on an unexpected
     * error outside the per-type catch blocks.
     */
    suspend operator fun invoke(): Result<FetchSummary> = runCatching {
        val userId = authRepository.currentLocalUserId()
        if (userId == null || !authRepository.isAuthenticated() || !connectivityObserver.currentlyOnline()) {
            return@runCatching FetchSummary.Skipped
        }

        // Each type runs under its own runCatching so one failing type does not abort the
        // others; the *Ok flags gate markCompleted so a partial failure stays NOT_YET_SYNCED.
        // FK-safe order: patients (root) → sessions → samples (+children) → reports.
        var patientsOk = false
        var sessionsOk = false
        var samplesOk = false
        var reportsOk = false
        var patientsFetched = 0
        var sessionsFetched = 0
        var samplesFetched = 0
        var reportsFetched = 0

        runCatching { patientsFetched = pullPatients(); patientsOk = true }
            .onFailure { error -> Log.e(TAG, "Fetch patients failed", error) }

        runCatching { sessionsFetched = pullSessions(userId); sessionsOk = true }
            .onFailure { error -> Log.e(TAG, "Fetch sessions failed", error) }

        runCatching { samplesFetched = pullSamples(userId); samplesOk = true }
            .onFailure { error -> Log.e(TAG, "Fetch samples/detections/findings failed", error) }

        runCatching { reportsFetched = pullReports(userId); reportsOk = true }
            .onFailure { error -> Log.e(TAG, "Fetch reports failed", error) }

        // Mark completed only when all four types succeeded (E2). Listed rather than chained
        // so a fifth entity type is one entry, not a longer boolean expression.
        val everyTypeSucceeded = listOf(patientsOk, sessionsOk, samplesOk, reportsOk).all { it }
        if (everyTypeSucceeded) {
            initialFetchStateStore.markCompleted(userId)
        }

        // Fold any species that arrived with this pass into the offline suggestion index, so
        // the two reference caches stay in step (PB-08a). Ungated on purpose: the seeder
        // re-derives from rows the device already holds, so a pass where only samples failed
        // still has work for it, and it never throws, so it cannot fail the pass. The
        // offline/unauthenticated returns are above, which makes this "every pass that ran".
        speciesSuggestionSeeder.refresh()

        FetchSummary.Ran(
            patientsFetched = patientsFetched,
            sessionsFetched = sessionsFetched,
            samplesFetched = samplesFetched,
            reportsFetched = reportsFetched,
        )
    }

    /**
     * Patients are the FK root — pull them before sessions. Returns rows inserted.
     *
     * The `patient_users` link rows come down in the same pass and are **not** optional:
     * `PatientDao` resolves visibility through that join, so a patient whose link is
     * missing sits on the device invisible to every query that reads it. Links are
     * inserted after the patients they reference, and with `OnConflictStrategy.IGNORE`, so
     * a re-pull is a no-op rather than a duplicate-key failure.
     *
     * E4 guard: only insert when the local row is absent or already SYNCED. A local
     * PENDING or SYNC_FAILED patient is unsynced work the medtech typed in offline, and
     * overwriting it with the server's copy loses a patient by hand.
     */
    private suspend fun pullPatients(): Int {
        var fetched = 0
        val patients = patientRemoteDataSource.fetchPatients()
        for (remote in patients) {
            val local = patientDao.getPatientById(remote.patientId)
            if (local == null || local.supabaseStatus == PatientSyncStatus.SYNCED.value) {
                patientDao.upsertPatient(remote)
                fetched++
            }
        }

        val links = patientRemoteDataSource.fetchPatientLinks()
        if (links.isNotEmpty()) {
            patientDao.linkPatientsToUsers(links)
        }
        return fetched
    }

    /** Sessions are pulled after patients, before samples and reports. Returns rows inserted. */
    private suspend fun pullSessions(userId: String): Int {
        var fetched = 0
        val sessions = sessionRemoteDataSource.fetchSessions(userId)
        for (remote in sessions) {
            val local = sessionDao.getSessionById(remote.sessionId)
            // E4 guard: only insert when absent or already synced; skip pending/sync_failed
            if (local == null || local.supabaseStatus == SessionSyncStatus.SYNCED.value) {
                sessionDao.insertSession(remote)
                fetched++
            }
        }
        return fetched
    }

    /** Paginated pull of samples plus their detections and findings. Returns samples inserted. */
    private suspend fun pullSamples(userId: String): Int {
        var fetched = 0
        var offset = 0L
        while (true) {
            val page = sampleRemoteDataSource.fetchSamples(userId, offset, PAGE_SIZE.toLong())

            // Collect only the IDs whose parent row was actually inserted (E4 guard).
            // VERIFIED / SYNC_FAILED samples are skipped here AND their child rows must
            // not be overwritten — fetching detections/findings for them would silently
            // clobber the expert verdict / expertClass payload that hasn't uploaded yet.
            val insertedSampleIds = mutableListOf<String>()
            for (remote in page) {
                // E4 guard: skip if local row is VERIFIED or SYNC_FAILED (in-progress work)
                val local = sampleDao.getSampleByIdIncludingDeleted(remote.sampleId)
                if (local == null || local.status == SampleStatus.SYNCED.value) {
                    sampleDao.insertSample(remote)
                    fetched++
                    insertedSampleIds.add(remote.sampleId)
                }
            }

            pullChildRowsFor(insertedSampleIds)

            if (page.size < PAGE_SIZE) break
            offset += PAGE_SIZE.toLong()
        }
        return fetched
    }

    /**
     * Fetch+insert detections and findings only for samples that were actually inserted.
     * Chunk the id list to stay well under server/proxy URL-length limits (E5).
     */
    private suspend fun pullChildRowsFor(insertedSampleIds: List<String>) {
        if (insertedSampleIds.isEmpty()) return
        val detections = insertedSampleIds.chunked(CHILD_BATCH_SIZE)
            .flatMap { chunk -> sampleRemoteDataSource.fetchDetections(chunk) }
        if (detections.isNotEmpty()) {
            detectionDao.insertDetections(detections)
        }
        val findings = insertedSampleIds.chunked(CHILD_BATCH_SIZE)
            .flatMap { chunk -> sampleRemoteDataSource.fetchFindings(chunk) }
        if (findings.isNotEmpty()) {
            sampleSpeciesFindingDao.insertFindings(findings)
        }
    }

    /** Reports are FK children of sessions — pull them last. Returns rows inserted. */
    private suspend fun pullReports(userId: String): Int {
        var fetched = 0
        val reports = reportRemoteDataSource.fetchReports(userId)
        for (remote in reports) {
            // E4 guard: skip if local row is pending or sync_failed
            val local = reportDao.getReportById(remote.reportId)
            if (local == null || local.supabaseStatus == ReportSyncStatus.SYNCED.value) {
                reportDao.insertReport(remote)
                fetched++
            }
        }
        return fetched
    }

    private companion object {
        const val TAG = "FetchRemoteDataUseCase"
        const val PAGE_SIZE = 500

        /**
         * Maximum number of sample UUIDs per `isIn` request for detections/findings.
         * Keeps the GET query string well under server/proxy URL-length limits.
         */
        const val CHILD_BATCH_SIZE = 100
    }
}
