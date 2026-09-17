package com.agarthavision.domain.repository

import com.agarthavision.domain.model.Patient
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for locally persisted patients.
 *
 * **Every read is scoped through the `patient_users` join, not `created_by`.** That
 * mirrors the Supabase RLS policy rather than inventing a second, different rule on the
 * client. Filtering on the creator instead would hide a patient an admin had shared with
 * this medtech, which is the entire purpose of the join table.
 *
 * **There is no delete.** Removing a patient is an admin-side action; no client path
 * offers one and none should be added here.
 *
 * Timestamps belong to the caller. [insert] and [update] persist [Patient.createdAt] and
 * [Patient.updatedAt] as given rather than stamping a clock of their own, so a test can
 * pin them and the form screen stays the single place that decides when an edit happened.
 */
interface PatientRepository {
    /**
     * One page of the signed-in medtech's patients, ordered by name.
     *
     * [query] matches lastname, firstname or barangay name; a blank query matches
     * everything. The barangay half joins the bundled PSGC reference table, so this search
     * works with the radio off.
     */
    fun observePatients(
        userId: String,
        query: String,
        limit: Int,
        offset: Int,
    ): Flow<List<Patient>>

    /** Total matching [observePatients] under the same filter, for the pager. */
    fun observePatientCount(userId: String, query: String): Flow<Int>

    /** Loads one patient by identifier, or null when it is not on this device. */
    suspend fun getPatientById(patientId: String): Patient?

    /** Observes one patient, so an edit redraws the detail screen without a refetch. */
    fun observePatientById(patientId: String): Flow<Patient?>

    /**
     * Creates a patient **and** its creator link in one transaction.
     *
     * Remotely the `on_patient_created` trigger writes the link row; locally it has to be
     * explicit, and it has to be atomic. A patient inserted without its link is invisible
     * to the medtech who just created it, because every read here resolves through
     * `patient_users`.
     */
    suspend fun insert(patient: Patient)

    /** Updates an existing patient and re-queues it for sync. */
    suspend fun update(patient: Patient)
}
