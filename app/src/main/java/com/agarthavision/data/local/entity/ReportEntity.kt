package com.agarthavision.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for a persisted session report.
 *
 * Mirrors the Supabase `reports` table from migration `0008_reports.sql`, with
 * `pdf_file_path` added by `0011_reports_pdf_and_lpf.sql`.
 * Row-only sync — the CSV file itself stays local; only the metadata +
 * aggregate stats round-trip to Supabase.
 *
 * Multiple reports per session are allowed; the UI lists them ordered by
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
    ],
    indices = [Index("session_id"), Index("user_id"), Index("generated_at")],
)
data class ReportEntity(
    @PrimaryKey
    @ColumnInfo(name = "report_id")
    val reportId: String,

    @ColumnInfo(name = "session_id")
    val sessionId: String,

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
    // method in use. The per-species min–max LPF range replaces it.

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
