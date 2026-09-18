package com.agarthavision.data.supabase

import android.util.Log
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
    suspend operator fun invoke(patientId: String): Result<Unit> {
        val patient = patientDao.getPatientById(patientId)
            ?: return Result.failure(IllegalArgumentException("Patient $patientId does not exist."))

        return runCatching {
            remoteDataSource.upsertPatient(patient)
            patientDao.updateSyncStatus(patientId, PatientSyncStatus.SYNCED.value)
        }.onFailure {
            Log.e(TAG, "Sync patient failed for $patientId", it)
            patientDao.updateSyncStatus(patientId, PatientSyncStatus.SYNC_FAILED.value)
        }
    }

    private companion object {
        const val TAG = "SyncPatientUseCase"
    }
}
