package com.agarthavision.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
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
     * One page of the signed-in medtech's patients, by name, optionally filtered.
     *
     * [query] matches lastname or firstname. A blank query matches everything — the
     * `:query = ''` short-circuit keeps the plan simple rather than relying on `LIKE '%%'`.
     * Barangay filtering is handled via [barangayCode], not free-text search.
     */
    @Suppress("LongParameterList")
    @Transaction
    @Query(
        """
        SELECT p.* FROM patients p
        INNER JOIN patient_users pu ON pu.patient_id = p.patient_id
        WHERE pu.user_id = :userId
          AND (
            :query = ''
            OR p.lastname  LIKE '%' || :query || '%' ESCAPE '\'
            OR p.firstname LIKE '%' || :query || '%' ESCAPE '\'
          )
          AND (:sex IS NULL OR p.sex = :sex)
          AND (:barangayCode IS NULL OR p.psgc_barangay_code = :barangayCode)
          AND (:minBirthdate IS NULL OR p.birthdate >= :minBirthdate)
          AND (:maxBirthdate IS NULL OR p.birthdate <= :maxBirthdate)
        ORDER BY
          CASE WHEN :sort = 'RECENT' THEN p.updated_at END DESC,
          CASE WHEN :sort = 'LAST_NAME' THEN p.lastname END ASC,
          CASE WHEN :sort = 'FIRST_NAME' THEN p.firstname END ASC,
          p.lastname ASC, p.firstname ASC
        LIMIT :limit OFFSET :offset
        """,
    )
    fun observePatients(
        userId: String,
        query: String,
        limit: Int,
        offset: Int = 0,
        sort: String = "RECENT",
        sex: String? = null,
        barangayCode: String? = null,
        minBirthdate: Long? = null,
        maxBirthdate: Long? = null,
    ): Flow<List<PatientEntity>>

    /** Total matching [observePatients], for the pager's page count. */
    @Suppress("LongParameterList")
    @Query(
        """
        SELECT COUNT(*) FROM patients p
        INNER JOIN patient_users pu ON pu.patient_id = p.patient_id
        WHERE pu.user_id = :userId
          AND (
            :query = ''
            OR p.lastname  LIKE '%' || :query || '%' ESCAPE '\'
            OR p.firstname LIKE '%' || :query || '%' ESCAPE '\'
          )
          AND (:sex IS NULL OR p.sex = :sex)
          AND (:barangayCode IS NULL OR p.psgc_barangay_code = :barangayCode)
          AND (:minBirthdate IS NULL OR p.birthdate >= :minBirthdate)
          AND (:maxBirthdate IS NULL OR p.birthdate <= :maxBirthdate)
        """,
    )
    fun observePatientCount(
        userId: String,
        query: String,
        sex: String? = null,
        barangayCode: String? = null,
        minBirthdate: Long? = null,
        maxBirthdate: Long? = null,
    ): Flow<Int>

    @Query("SELECT * FROM patients WHERE patient_id = :patientId")
    suspend fun getPatientById(patientId: String): PatientEntity?

    @Query("SELECT * FROM patients WHERE patient_id = :patientId")
    fun observePatientById(patientId: String): Flow<PatientEntity?>

    /**
     * Patients whose remote row does not exist or is stale, for this medtech.
     *
     * A patient is editable, so this returns rows that have already synced once and been
     * edited since; the remote write is an upsert for exactly that reason.
     *
     * **Scoped, like every other read here.** Unscoped, a sync pass on a shared device pushed
     * whichever medtech happened to be signed in as the author of every pending patient on
     * the device, including another medtech's offline work — which the server then rejects
     * and this marks `sync_failed` locally. Through `patient_users`, not `created_by`, so a
     * patient an admin shared with this medtech still syncs.
     */
    @Query(
        """
        SELECT p.* FROM patients p
        INNER JOIN patient_users pu ON pu.patient_id = p.patient_id
        WHERE pu.user_id = :userId
          AND p.supabase_status IN ('pending', 'sync_failed')
        ORDER BY p.created_at ASC
        """,
    )
    suspend fun getPatientsPendingSync(userId: String): List<PatientEntity>

    /**
     * Hard-deletes a patient and, by cascade, its `patient_users` links.
     *
     * `sessions.patient_id` is NO_ACTION rather than CASCADE, so a patient that still owns a
     * session cannot be deleted — SQLite raises a constraint violation. The caller checks
     * first; see `DiscardUnsyncedDataUseCase`.
     */
    @Query("DELETE FROM patients WHERE patient_id = :patientId")
    suspend fun deletePatient(patientId: String)

    /**
     * Live count of this medtech's patients awaiting upload. Drives the Settings
     * Data & Sync section, alongside the session, sample and report rows.
     *
     * Scoped through `patient_users` like every other read here, not through `created_by`:
     * a patient an admin shared with this medtech is theirs to sync too.
     */
    @Query(
        """
        SELECT COUNT(*) FROM patients p
        INNER JOIN patient_users pu ON pu.patient_id = p.patient_id
        WHERE pu.user_id = :userId AND p.supabase_status = 'pending'
        """,
    )
    fun observePendingCount(userId: String): Flow<Int>

    /** Live count of this medtech's patients whose last sync attempt failed. */
    @Query(
        """
        SELECT COUNT(*) FROM patients p
        INNER JOIN patient_users pu ON pu.patient_id = p.patient_id
        WHERE pu.user_id = :userId AND p.supabase_status = 'sync_failed'
        """,
    )
    fun observeFailedCount(userId: String): Flow<Int>

    @Query("UPDATE patients SET supabase_status = :status WHERE patient_id = :patientId")
    suspend fun updateSyncStatus(patientId: String, status: String)

    /**
     * Writes a patient, inserting or updating in place.
     *
     * **`@Upsert`, not `@Insert(REPLACE)`, and that is not a style choice.** SQLite resolves
     * a REPLACE conflict by *deleting* the existing row and inserting a new one, and
     * `patient_users.patient_id` is a foreign key with `onDelete = CASCADE` that Room
     * enforces. So a REPLACE of an existing patient silently deletes its link rows, and
     * every read here resolves visibility through that join — the patient stays on the device
     * and becomes invisible to the medtech who owns it.
     *
     * `@Upsert` compiles to INSERT-then-UPDATE and never deletes, so no cascade fires.
     * `PatientRepositoryImplTest` pins this; it fails on REPLACE.
     *
     * This is the only `@Upsert` in the codebase. Its REPLACE neighbours are correct for
     * tables nothing cascades from — do not "fix" this one to match them.
     */
    @Upsert
    suspend fun upsertPatient(patient: PatientEntity)

    /** Bulk [upsertPatient], with the same reason for being an `@Upsert`. */
    @Upsert
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
