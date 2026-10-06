package com.agarthavision.data.supabase

import com.agarthavision.core.util.Logger
import com.agarthavision.data.local.dao.PatientDao
import com.agarthavision.domain.model.PatientSyncStatus
import javax.inject.Inject

/**
 * Synchronizes one pending patient row from Room to Supabase.
 *
 * Row-only upsert (idempotent), mirroring [SyncSessionUseCase]. The upsert is not an
 * optimisation here but a requirement: a patient is editable, so the same row is pushed
 * again after every correction, and `patients_update_linked` in `0001_init.sql` exists so
 * that re-push is accepted rather than silently rejected.
 *
 * The `patient_users` link is not pushed. The `on_patient_created` trigger writes the
 * creator's row server-side.
 */
class SyncPatientUseCase @Inject constructor(
    private val patientDao: PatientDao,
    private val remoteDataSource: PatientRemoteDataSource,
) {
    /**
     * Pushes the patient row to Supabase and updates the local sync state.
     *
     * @return [Result.success] when the row reaches [PatientSyncStatus.SYNCED], otherwise
     * [Result.failure] after marking the local row [PatientSyncStatus.SYNC_FAILED].
     */
    @Suppress("ReturnCount")
    suspend operator fun invoke(patientId: String): Result<Unit> {
        val patient = patientDao.getPatientById(patientId)
        if (patient == null) {
            val errorMsg = "Patient $patientId does not exist."
            Logger.e(TAG, "[SyncFailed][Patient:$patientId][Class:MISSING_ENTITY] $errorMsg")
            return Result.failure(IllegalArgumentException(errorMsg))
        }

        if (patient.supabaseStatus == PatientSyncStatus.SYNCED.value) {
            return Result.success(Unit)
        }

        return runCatching {
            remoteDataSource.upsertPatient(patient)
            patientDao.updateSyncStatus(patientId, PatientSyncStatus.SYNCED.value)
        }.onFailure { throwable ->
            val failureClass = classifyFailure(throwable)
            Logger.e(
                TAG,
                "[SyncFailed][Patient:$patientId][Class:$failureClass] " +
                    "Marking status SYNC_FAILED. Error: ${throwable.message}",
                throwable,
            )
            patientDao.updateSyncStatus(patientId, PatientSyncStatus.SYNC_FAILED.value)
        }
    }

    private fun classifyFailure(throwable: Throwable): String {
        val msg = throwable.message.orEmpty()
        return when {
            throwable is java.net.UnknownHostException || throwable is java.io.IOException ->
                "NETWORK_ERROR"
            throwable is IllegalStateException && msg.contains("session", ignoreCase = true) ->
                "UNAUTHENTICATED"
            msg.contains("foreign key constraint", ignoreCase = true) ->
                "FOREIGN_KEY_VIOLATION"
            else ->
                throwable.javaClass.simpleName.ifBlank { "UNKNOWN_ERROR" }
        }
    }

    private companion object {
        const val TAG = "SyncPatientUseCase"
    }
}
