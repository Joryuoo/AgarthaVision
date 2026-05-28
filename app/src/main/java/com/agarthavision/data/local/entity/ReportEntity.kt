package com.agarthavision.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for a persisted session report.
 *
 * Mirrors the Supabase `reports` table from migration `0008_reports.sql`.
 * Row-only sync — the CSV file itself stays local; only the metadata +
 * aggregate stats round-trip to Supabase.
 *
 * Multiple reports per session are allowed; the UI lists them ordered by
 * `generatedAt` descending.
 *
 * `supabaseStatus` is Room-only and follows the same pattern as samples
 * (`pending` → `synced`, with `sync_failed` branch).
 *
 * `positiveSpeciesJson` and `epgPerSpeciesJson` are Gson-serialized strings
 * because Room doesn't natively store collection types; the canonical
 * representations are `List<String>` and `Map<String, Int>` respectively at
 * the domain layer.
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

    /** JSON object: canonical species name → EPG integer. */
    @ColumnInfo(name = "epg_per_species_json")
    val epgPerSpeciesJson: String,

    @ColumnInfo(name = "csv_file_path")
    val csvFilePath: String?,

    @ColumnInfo(name = "supabase_status", defaultValue = "'pending'")
    val supabaseStatus: String = "pending",

    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
