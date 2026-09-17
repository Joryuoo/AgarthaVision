package com.agarthavision.data.supabase

import com.agarthavision.data.local.entity.PatientEntity
import com.agarthavision.data.local.entity.PatientUserEntity
import com.agarthavision.domain.model.CLINICAL_ZONE
import com.agarthavision.domain.model.PatientSyncStatus
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Persists patients to Supabase Postgres and reads them back.
 *
 * The local Room entity uses `patient_id`, while the Supabase table's primary key is `id`;
 * this data source is the translation boundary between those shapes, as
 * [SessionRemoteDataSource] is for sessions.
 *
 * **Reads carry no `user_id` filter.** `patients_select_linked` and
 * `patient_users_select_own` in `0001_init.sql` already scope every row to the
 * authenticated caller. Re-applying the rule here would give the client a second
 * definition of visibility that could drift from the policy.
 */
class PatientRemoteDataSource @Inject constructor(
    private val supabase: SupabaseClient,
) {
    /**
     * Writes the patient row, inserting or updating on primary-key conflict.
     *
     * **Upsert, not insert.** A patient is editable, so the same row syncs more than once;
     * `patients_update_linked` exists precisely so a re-sync is not rejected. An
     * insert-only path works exactly until the first patient edit, then fails quietly.
     *
     * The `patient_users` row is **not** written from here. The `on_patient_created`
     * trigger is `security definer` and writes it server-side; a client insert races the
     * trigger and, on conflict, does nothing useful.
     */
    suspend fun upsertPatient(patient: PatientEntity) {
        supabase.postgrest[PATIENTS_TABLE].upsert(patient.toRow())
    }

    // ── Pull (read from server) ───────────────────────────────────────────────

    /** Every patient the authenticated caller can see, oldest first. */
    suspend fun fetchPatients(): List<PatientEntity> =
        supabase.postgrest[PATIENTS_TABLE].select {
            order("created_at", Order.ASCENDING)
        }.decodeList<PatientRow>().map { it.toEntity() }

    /**
     * The caller's own `patient_users` rows.
     *
     * Pulled alongside the patients themselves because `PatientDao` resolves visibility
     * through this join: a patient row with no matching link is present on the device and
     * invisible to every query that reads it.
     */
    suspend fun fetchPatientLinks(): List<PatientUserEntity> =
        supabase.postgrest[PATIENT_USERS_TABLE].select()
            .decodeList<PatientUserRow>()
            .map { it.toEntity() }

    // ── Row shapes ────────────────────────────────────────────────────────────

    /**
     * `birthdate` is a bare `date` on the server, so it crosses as a plain `yyyy-MM-dd`
     * with no zone attached. The epoch-millis form Room stores is resolved in
     * [CLINICAL_ZONE], the same frame `PatientMapper` uses, so the calendar day survives
     * the round trip.
     */
    private fun PatientEntity.toRow(): PatientRow = PatientRow(
        id = patientId,
        lastname = lastname,
        firstname = firstname,
        middleName = middleName,
        sex = sex,
        birthdate = Instant.ofEpochMilli(birthdate).atZone(CLINICAL_ZONE).toLocalDate().toString(),
        psgcBarangayCode = psgcBarangayCode,
        createdBy = createdBy,
        createdAt = Instant.ofEpochMilli(createdAt).toString(),
        updatedAt = Instant.ofEpochMilli(updatedAt).toString(),
    )

    @Serializable
    private data class PatientRow(
        @SerialName("id") val id: String,
        @SerialName("lastname") val lastname: String,
        @SerialName("firstname") val firstname: String,
        @SerialName("middle_name") val middleName: String? = null,
        @SerialName("sex") val sex: String,
        @SerialName("birthdate") val birthdate: String,
        @SerialName("psgc_barangay_code") val psgcBarangayCode: String,
        @SerialName("created_by") val createdBy: String,
        @SerialName("created_at") val createdAt: String,
        @SerialName("updated_at") val updatedAt: String,
    )

    /**
     * A row pulled from the server is by definition already there, so it lands as
     * `synced`. The E4 guard in `FetchRemoteDataUseCase` is what stops this overwriting a
     * local row still carrying unsynced work.
     */
    private fun PatientRow.toEntity(): PatientEntity = PatientEntity(
        patientId = id,
        lastname = lastname,
        firstname = firstname,
        middleName = middleName,
        sex = sex,
        birthdate = LocalDate.parse(birthdate).atStartOfDay(CLINICAL_ZONE).toInstant().toEpochMilli(),
        psgcBarangayCode = psgcBarangayCode,
        createdBy = createdBy,
        createdAt = Instant.parse(createdAt).toEpochMilli(),
        updatedAt = Instant.parse(updatedAt).toEpochMilli(),
        supabaseStatus = PatientSyncStatus.SYNCED.value,
    )

    @Serializable
    private data class PatientUserRow(
        @SerialName("patient_id") val patientId: String,
        @SerialName("user_id") val userId: String,
        @SerialName("linked_at") val linkedAt: String,
    )

    private fun PatientUserRow.toEntity(): PatientUserEntity = PatientUserEntity(
        patientId = patientId,
        userId = userId,
        linkedAt = Instant.parse(linkedAt).toEpochMilli(),
    )

    private companion object {
        const val PATIENTS_TABLE = "patients"
        const val PATIENT_USERS_TABLE = "patient_users"
    }
}
