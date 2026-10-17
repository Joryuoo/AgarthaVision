package com.agarthavision.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for a persisted report, scoped to either one session or one patient.
 *
 * Mirrors the Supabase `reports` table from migration `0001_init.sql` (consolidated), with
 * `patient_id` and `session_ids_json` added by `0015_patient_reports.sql` for patient-scoped
 * reports (Room version 25). A session report has `sessionId` set and `patientId`/
 * `sessionIdsJson` null; a patient report has `patientId` set and `sessionId` null, with
 * `sessionIdsJson` recording the sessions pooled into it.
 *
 * Row-only sync — the PDF file itself stays local; only the metadata + aggregate stats
 * round-trip to Supabase.
 *
 * Multiple reports per session/patient are allowed; the UI lists them ordered by
 * `generatedAt` descending.
 *
 * `supabaseStatus` is Room-only and follows the same pattern as samples
 * (`pending` → `synced`, with `sync_failed` branch).
 *
 * `positiveSpeciesJson` is a Gson-serialized string because Room doesn't natively
 * store collection types; the canonical representation is `List<String>` at the
 * domain layer.
 */
@Entity(
    tableName = "reports",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["session_id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PatientEntity::class,
            parentColumns = ["patient_id"],
            childColumns = ["patient_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("session_id"), Index("patient_id"), Index("user_id"), Index("generated_at")],
)
data class ReportEntity(
    @PrimaryKey
    @ColumnInfo(name = "report_id")
    val reportId: String,

    @ColumnInfo(name = "session_id")
    val sessionId: String?,

    @ColumnInfo(name = "patient_id")
    val patientId: String? = null,

    /** JSON array of session ids pooled into this report. Patient reports only. */
    @ColumnInfo(name = "session_ids_json")
    val sessionIdsJson: String? = null,

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "report_type", defaultValue = "'session'")
    val reportType: String = "session",

    @ColumnInfo(name = "generated_at")
    val generatedAt: Long,

    @ColumnInfo(name = "total_samples")
    val totalSamples: Int,

    @ColumnInfo(name = "total_eggs_confirmed")
    val totalEggsConfirmed: Int,

    /** JSON array of canonical species names with ≥1 confirmed egg. */
    @ColumnInfo(name = "positive_species_json")
    val positiveSpeciesJson: String,

    // `epg_per_species_json` is gone as of Room 13. EPG is eggs-per-gram via Kato-Katz;
    // Philippine medtechs use direct smear, so the ×24 multiplier was wrong for the
    // method in use. The per-species min–max LPF range below replaces it.

    /** JSON object: canonical species name → LpfDensity object. */
    @ColumnInfo(name = "lpf_per_species_json")
    val lpfPerSpeciesJson: String,

    @ColumnInfo(name = "csv_file_path")
    val csvFilePath: String?,

    /** Local path (or `content://` URI) to the patient-facing PDF, mirroring [csvFilePath]. */
    @ColumnInfo(name = "pdf_file_path")
    val pdfFilePath: String?,

    @ColumnInfo(name = "supabase_status", defaultValue = "'pending'")
    val supabaseStatus: String = "pending",

    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
