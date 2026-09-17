package com.agarthavision.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.agarthavision.data.local.entity.PatientEntity
import com.agarthavision.data.local.entity.PatientUserEntity
import kotlinx.coroutines.flow.Flow

/**
 * Reads and writes patients, and the join rows that make them visible.
 *
 * **There is no delete.** Removing a patient is an admin-side action; no client path offers
 * it and none should be added here.
 *
 * Every read is scoped through `patient_users`, mirroring the Supabase RLS policy rather
 * than reimplementing a different rule locally. Filtering on `created_by` instead would
 * hide a patient an admin had shared with this medtech.
 *
 * None of these queries touch `samples`, so `SoftDeleteGuardTest` has nothing to enforce
 * here and this file is deliberately not in its `daoFiles` list. If a future method does
 * join `samples`, add it there in the same change.
 *
 * `TooManyFunctions` is suppressed for the same reason [SessionDao] suppresses it: one
 * table's queries belong in one `@Dao`, and splitting them across two interfaces to satisfy
 * a count would be inconsistent with every other DAO here for no functional benefit.
 */
@Suppress("TooManyFunctions")
@Dao
interface PatientDao {

    /**
     * One page of the signed-in medtech's patients, newest first, optionally filtered.
     *
     * [query] matches lastname, firstname or barangay name. The barangay join is against
     * the bundled `psgc_barangays` reference table, which is why searching by barangay
     * works with the radio off. A blank query matches everything — the `:query = ''`
     * short-circuit keeps the plan simple rather than relying on `LIKE '%%'`.
     */
    @Transaction
    @Query(
        """
        SELECT p.* FROM patients p
        INNER JOIN patient_users pu ON pu.patient_id = p.patient_id
        LEFT JOIN psgc_barangays b ON b.code = p.psgc_barangay_code
        WHERE pu.user_id = :userId
          AND (
            :query = ''
            OR p.lastname  LIKE '%' || :query || '%'
            OR p.firstname LIKE '%' || :query || '%'
            OR b.name      LIKE '%' || :query || '%'
          )
        ORDER BY p.lastname ASC, p.firstname ASC
        LIMIT :limit OFFSET :offset
        """,
    )
    fun observePatients(
        userId: String,
        query: String,
        limit: Int,
        offset: Int,
    ): Flow<List<PatientEntity>>

    /** Total matching [observePatients], for the pager's page count. */
    @Query(
        """
        SELECT COUNT(*) FROM patients p
        INNER JOIN patient_users pu ON pu.patient_id = p.patient_id
        LEFT JOIN psgc_barangays b ON b.code = p.psgc_barangay_code
        WHERE pu.user_id = :userId
          AND (
            :query = ''
            OR p.lastname  LIKE '%' || :query || '%'
            OR p.firstname LIKE '%' || :query || '%'
            OR b.name      LIKE '%' || :query || '%'
          )
        """,
    )
    fun observePatientCount(userId: String, query: String): Flow<Int>

    @Query("SELECT * FROM patients WHERE patient_id = :patientId")
    suspend fun getPatientById(patientId: String): PatientEntity?

    @Query("SELECT * FROM patients WHERE patient_id = :patientId")
    fun observePatientById(patientId: String): Flow<PatientEntity?>

    /**
     * Patients whose remote row does not exist or is stale.
     *
     * A patient is editable, so this returns rows that have already synced once and been
     * edited since; the remote write is an upsert for exactly that reason.
     */
    @Query("SELECT * FROM patients WHERE supabase_status IN ('pending', 'sync_failed')")
    suspend fun getPatientsPendingSync(): List<PatientEntity>

    @Query("UPDATE patients SET supabase_status = :status WHERE patient_id = :patientId")
    suspend fun updateSyncStatus(patientId: String, status: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPatient(patient: PatientEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPatients(patients: List<PatientEntity>)

    @Update
    suspend fun updatePatient(patient: PatientEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkPatientToUser(link: PatientUserEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkPatientsToUsers(links: List<PatientUserEntity>)

    @Query("SELECT * FROM patient_users WHERE user_id = :userId")
    suspend fun getLinksForUser(userId: String): List<PatientUserEntity>

    /**
     * Creates a patient and links its creator in one transaction.
     *
     * Remotely the `on_patient_created` trigger does this; locally it has to be explicit,
     * and it has to be atomic. A patient inserted without its link is invisible to the
     * medtech who just created it, because every read above goes through `patient_users`.
     */
    @Transaction
    suspend fun insertPatientWithCreatorLink(patient: PatientEntity, linkedAt: Long) {
        upsertPatient(patient)
        linkPatientToUser(
            PatientUserEntity(
                patientId = patient.patientId,
                userId = patient.createdBy,
                linkedAt = linkedAt,
            ),
        )
    }
}
