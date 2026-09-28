package com.agarthavision.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for a patient — the unit the medtech works from.
 *
 * Mirrors the Supabase `patients` table in `supabase/migrations/0001_init.sql`. A patient
 * owns many [SessionEntity] rows; a session is one fecal smear.
 *
 * [birthdate] is stored rather than an age so the age shown on a report is recomputed per
 * encounter and cannot go stale. It is epoch millis at midnight in `Asia/Manila`
 * (`CLINICAL_ZONE`, in `domain/model/Patient.kt`) — surveillance is Philippine, so a
 * birthdate is a calendar fact in Philippine time. `PatientMapper` owns that conversion
 * and is the only place that does it.
 *
 * [psgcBarangayCode] is the canonical zero-padded 10-digit PSGC (`0102801001`). It lives on
 * the patient rather than the session because it is the unit surveillance aggregates on and
 * it does not change from one smear to the next. `barangay_prevalence()` reaches it through
 * `sessions → patients`.
 *
 * [createdBy] is provenance only and grants no access. Visibility resolves through
 * [PatientUserEntity] — see its doc.
 *
 * There is no delete path. Removing a patient is an admin-side action and no client
 * affordance or DAO method offers it.
 */
@Entity(
    tableName = "patients",
    indices = [
        Index("psgc_barangay_code"),
        Index("lastname", "firstname"),
        Index("updated_at"),
    ],
)
data class PatientEntity(
    @PrimaryKey
    @ColumnInfo(name = "patient_id")
    val patientId: String,

    @ColumnInfo(name = "lastname")
    val lastname: String,

    @ColumnInfo(name = "firstname")
    val firstname: String,

    /** Nullable on purpose — many patients do not supply one. */
    @ColumnInfo(name = "middle_name")
    val middleName: String? = null,

    /** `M` or `F`, matching the Supabase CHECK. See `domain/model/Sex.kt`. */
    @ColumnInfo(name = "sex")
    val sex: String,

    /** Epoch millis at Philippine midnight. Never a future date — the picker rejects them. */
    @ColumnInfo(name = "birthdate")
    val birthdate: Long,

    @ColumnInfo(name = "psgc_barangay_code")
    val psgcBarangayCode: String,

    @ColumnInfo(name = "created_by")
    val createdBy: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    /**
     * Room-only cloud sync state, the same pattern as sessions and samples: `pending`
     * until the Supabase row exists, then `synced`, with a `sync_failed` branch. Never a
     * Supabase column — remote presence is authoritative there.
     *
     * A patient is editable, so it syncs more than once and the remote write is an upsert.
     */
    @ColumnInfo(name = "supabase_status", defaultValue = "'pending'")
    val supabaseStatus: String = "pending",
)
