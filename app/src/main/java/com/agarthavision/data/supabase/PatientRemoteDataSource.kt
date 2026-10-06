package com.agarthavision.data.supabase

import android.util.Log
import com.agarthavision.data.local.entity.PatientEntity
import com.agarthavision.data.local.entity.PatientUserEntity
import com.agarthavision.domain.model.CLINICAL_ZONE
import com.agarthavision.domain.model.PatientSyncStatus
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Persists patients to Supabase Postgres and reads them back.
 *
 * The local Room entity uses `patient_id`, while the Supabase table's primary key is `id`;
 * this data source is the translation boundary between those shapes, as
 * [SessionRemoteDataSource] is for sessions.
 *
 * **Reads are scoped by the caller, not left to RLS** (14zcqntjt3p). The phone holds the
 * patients linked to the signed-in user and nothing else, whatever their role lets them read
 * on the server: an org admin's policies (console `admin/0002`) return a whole laboratory, and
 * a super admin's `is_admin()` every patient link. So the links are filtered to the user, and
 * patients are fetched by the ids those links name. RLS stays the server's second line.
 */
class PatientRemoteDataSource @Inject constructor(
    private val supabaseProvider: dagger.Lazy<SupabaseClient>,
) {
    private val supabase: SupabaseClient get() = supabaseProvider.get()

    /**
     * Writes the patient row: insert first, update on primary-key conflict.
     *
     * **Deliberately not a single `upsert`.** PostgREST compiles an upsert to
     * `INSERT ... ON CONFLICT DO UPDATE`, and Postgres then applies the UPDATE policy's
     * `WITH CHECK` as well as the INSERT one. `patients_update_linked`
     * (`0001_init.sql:375-387`) resolves through `patient_users`, and that link row is
     * written by the `on_patient_created` trigger **after** the insert - so a brand-new
     * patient can never satisfy it, and every first push failed with
     * `new row violates row-level security policy for table "patients"`. Confirmed against
     * the live project: the same row, same token, succeeded as a plain insert and was
     * rejected as an upsert.
     *
     * Splitting the two keeps each statement inside one policy. A new patient takes the
     * insert path and satisfies `patients_insert_own` (`auth.uid() = created_by`). An edit
     * conflicts, falls through to the update, and by then the link row exists so
     * `patients_update_linked` passes. A patient is editable, so the update path is not
     * optional - an insert-only version works until the first correction.
     *
     * `sessions` and `samples` need none of this: their update policies are
     * `auth.uid() = user_id`, the same condition as their inserts, so an upsert satisfies
     * both at once. `reports` has no UPDATE policy at all and writes insert-if-absent
     * instead (see `ReportRemoteDataSource.upsertReport`). This table is the only one whose
     * UPDATE policy depends on a row a trigger writes.
     *
     * The `patient_users` row is **not** written from here. The `on_patient_created`
     * trigger is `security definer` and writes it server-side; a client insert races the
     * trigger and, on conflict, does nothing useful.
     */
    suspend fun upsertPatient(patient: PatientEntity) {
        val row = patient.toRow()
        try {
            supabase.postgrest[PATIENTS_TABLE].insert(row)
        } catch (error: RestException) {
            if (!error.isDuplicateKey()) throw error
            supabase.postgrest[PATIENTS_TABLE].update(row) {
                filter { eq("id", patient.patientId) }
            }
        }
    }

    /**
     * Whether a failed write was a primary-key collision rather than a real error.
     *
     * Matched on the SQLSTATE Postgres reports for a unique violation. supabase-kt surfaces
     * a conflict as the same `RestException` type as everything else, so there is no class
     * to catch instead; the code is checked rather than the prose because the prose is
     * localisable and names the constraint.
     */
    private fun RestException.isDuplicateKey(): Boolean =
        message?.contains(UNIQUE_VIOLATION) == true

    private val jsonDecoder = Json { ignoreUnknownKeys = true }

    // ── Pull (read from server) ───────────────────────────────────────────────

    /**
     * Fetches the patients with the given [patientIds], the ones the caller's own links name.
     * Callers chunk the ids and must guard against an empty list — isIn with no values is
     * undefined. One row per id at most, so a chunk needs no paging.
     */
    suspend fun fetchPatients(patientIds: List<String>): List<PatientEntity> =
        supabase.postgrest[PATIENTS_TABLE].select {
            filter { isIn("id", patientIds) }
        }.decodeList<JsonElement>().mapNotNull { element ->
            runCatching {
                jsonDecoder.decodeFromJsonElement<PatientRow>(element).toEntity()
            }.onFailure { error ->
                Log.w("PatientRemoteDataSource", "Skipping malformed patient row: ${error.message}")
            }.getOrNull()
        }

    /**
     * The `patient_users` rows of [userId], the signed-in user.
     *
     * Filtered here rather than by `patient_users_select_own`, which also hands an admin every
     * link in the project and an org admin every link in their laboratory (14zcqntjt3p).
     *
     * Pulled alongside the patients themselves because `PatientDao` resolves visibility
     * through this join: a patient row with no matching link is present on the device and
     * invisible to every query that reads it.
     * Inclusive range: rows [offset, offset+limit-1].
     *
     * Ordered by patient id so the pages are stable. The pull now reads the complete set to
     * remove assignments the server no longer holds, and an unordered range can repeat one row
     * and skip another between pages, which would read a live assignment as a removed one.
     */
    suspend fun fetchPatientLinks(
        userId: String,
        offset: Long = 0L,
        limit: Long = 500L,
    ): List<PatientUserEntity> =
        supabase.postgrest[PATIENT_USERS_TABLE].select {
            filter { eq("user_id", userId) }
            order("patient_id", Order.ASCENDING)
            range(offset, offset + limit - 1)
        }.decodeList<JsonElement>().mapNotNull { element ->
            runCatching {
                jsonDecoder.decodeFromJsonElement<PatientUserRow>(element).toEntity()
            }.onFailure { error ->
                Log.w("PatientRemoteDataSource", "Skipping malformed patient link row: ${error.message}")
            }.getOrNull()
        }

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

    private companion object {
        const val PATIENTS_TABLE = "patients"
        const val PATIENT_USERS_TABLE = "patient_users"

        /** Postgres SQLSTATE for a unique violation - a primary-key collision here. */
        const val UNIQUE_VIOLATION = "23505"
    }
}

@Serializable
internal data class PatientRow(
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
internal fun PatientRow.toEntity(): PatientEntity = PatientEntity(
    patientId = id,
    lastname = lastname,
    firstname = firstname,
    middleName = middleName,
    sex = sex,
    birthdate = LocalDate.parse(birthdate).atStartOfDay(CLINICAL_ZONE).toInstant().toEpochMilli(),
    psgcBarangayCode = psgcBarangayCode,
    createdBy = createdBy,
    createdAt = parseSupabaseInstant(createdAt).toEpochMilli(),
    updatedAt = parseSupabaseInstant(updatedAt).toEpochMilli(),
    supabaseStatus = PatientSyncStatus.SYNCED.value,
)

@Serializable
internal data class PatientUserRow(
    @SerialName("patient_id") val patientId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("linked_at") val linkedAt: String,
)

internal fun PatientUserRow.toEntity(): PatientUserEntity = PatientUserEntity(
    patientId = patientId,
    userId = userId,
    linkedAt = parseSupabaseInstant(linkedAt).toEpochMilli(),
)
