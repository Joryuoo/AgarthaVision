package com.agarthavision.domain.usecase.auth

import androidx.room.withTransaction
import com.agarthavision.core.database.AgarthaDatabase
import com.agarthavision.data.local.SampleImageStore
import com.agarthavision.data.local.dao.PatientDao
import com.agarthavision.data.local.dao.ReportDao
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.data.local.dao.SessionDao
import javax.inject.Inject

/** What a sign-out discarded, so the caller can report it honestly. */
data class DiscardedUnsynced(
    val patients: Int,
    val sessions: Int,
    val samples: Int,
    val reports: Int,
) {
    val total: Int get() = patients + sessions + samples + reports
}

/**
 * Drops the signing-out medtech's unsynced work.
 *
 * Sync is automatic — a saved patient, a started session and a verified sample each ask for a
 * pass. So anything still unsynced at sign-out is there because the upload could not happen: no
 * signal, or a barangay on a slow connection. Leaving it queued would be a promise the app
 * cannot keep, because the queue is scoped by `user_id`: it would sit on the device invisible to
 * the next medtech and never reach the central database unless the original account returned to
 * this exact handset. Discarding it is what makes the sign-out dialog's warning true.
 *
 * **Unsynced is a subtree, not four independent lists.** A patient that never uploaded cannot
 * have uploaded sessions, and those sessions cannot have uploaded samples — the remote schema
 * enforces it (`0001_init.sql:183`, `samples.session_id … references public.sessions(id)`). The
 * four `get…PendingSync` queries this reuses are the same ones `SyncPendingDataUseCase` pushes
 * from, so the set removed here is exactly the set that would have been uploaded.
 *
 * **Samples are deleted, not tombstoned, and that stays inside C8.** C8 protects the retraining
 * corpus: the remote `detections` rows and the Storage objects `0003_storage_rls.sql` refuses to
 * create a DELETE policy for. A sample that never synced has neither — no Storage object was
 * ever created and no detection row ever reached the corpus, so there is nothing for a tombstone
 * to preserve. Tombstoning would also be actively wrong here: a tombstoned row stays in the push
 * set so the tombstone itself can sync, and on the next login it would try to upload against a
 * session that never existed server-side, failing that foreign key forever.
 *
 * Local Room declares no foreign key from `samples` to `sessions` even though the server does,
 * so none of this cascades on its own. Each level is removed explicitly, deepest first.
 */
class DiscardUnsyncedDataUseCase @Inject constructor(
    private val database: AgarthaDatabase,
    private val patientDao: PatientDao,
    private val sessionDao: SessionDao,
    private val sampleDao: SampleDao,
    private val reportDao: ReportDao,
    private val sampleImageStore: SampleImageStore,
) {
    /** Discards everything [userId] owns that has not reached Supabase. */
    suspend operator fun invoke(userId: String): DiscardedUnsynced {
        val reports = reportDao.getReportsPendingSync(userId)
        val sessions = sessionDao.getSessionsPendingSync(userId)
        val patients = patientDao.getPatientsPendingSync(userId)
        // Including tombstoned: a tombstone that never reached the server is itself unsynced,
        // and the sample it hides never got there either.
        val samples = sampleDao.getSamplesPendingSyncIncludingDeleted(userId)

        val deletedPatients = database.withTransaction {
            reports.forEach { reportDao.deleteReport(it.reportId) }

            // Detections and findings cascade off each sample locally.
            samples.forEach { sampleDao.deleteSample(it.sampleId) }

            sessions.forEach { session ->
                // Frames flagged but never verified are drafts, and C8's local exception
                // permits discarding one before submission. They are not in any pending count
                // because they were never destined for upload, but leaving them behind a
                // deleted session would strand rows nothing can reach.
                sampleDao.deleteFlaggedSamplesForSession(session.sessionId, userId)
                sessionDao.deleteSession(session.sessionId)
            }

            // A patient that still owns a session is left alone rather than failing the whole
            // sign-out on the NO_ACTION foreign key. That needs a patient which reached
            // Supabase once and later failed a re-push while its sessions stayed synced, so
            // the record the medtech cares about is already on the server.
            patients.count { patient ->
                val stillInUse = sessionDao.countSessionsForPatient(patient.patientId) > 0
                if (!stillInUse) patientDao.deletePatient(patient.patientId)
                !stillInUse
            }
        }

        // After the transaction commits, never inside it: a half-second of file IO should not
        // hold a write lock, and a JPEG that outlives its row is inert where a row that
        // outlives its JPEG renders as a broken sample.
        samples.forEach { sampleImageStore.deleteJpeg(it.imagePath) }

        return DiscardedUnsynced(
            patients = deletedPatients,
            sessions = sessions.size,
            samples = samples.size,
            reports = reports.size,
        )
    }
}
