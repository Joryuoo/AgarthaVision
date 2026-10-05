package com.agarthavision.domain.repository

/**
 * Answers whether a medtech may read records they did not write.
 *
 * The lab owns the patient, and assignment through `patient_users` is what makes a patient's
 * whole history readable: every session, sample and report on it, whoever authored them
 * (14zcqntjph5). This is the device's copy of the server rule in
 * `supabase/migrations/0007_patient_shared_history.sql`, read from the links the last pull
 * brought down.
 */
interface PatientAccessRepository {
    /** True when [userId] is assigned to the patient that [sessionId] belongs to. */
    suspend fun isAssignedToSessionPatient(sessionId: String, userId: String): Boolean
}
