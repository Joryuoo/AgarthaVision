package com.agarthavision.data.supabase

import com.agarthavision.data.local.entity.ReportEntity
import com.agarthavision.domain.model.LpfDensity
import com.agarthavision.domain.model.ReportSyncStatus
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import java.time.Instant
import javax.inject.Inject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Writes persisted session reports to Supabase Postgres. Row-only sync — the
 * CSV file stays local on the device; only the metadata + aggregate stats are
 * mirrored to `public.reports`.
 *
 * **`epg_per_species` is gone from both sides,** and `lpf_per_species` took its place on
 * both at once. `0001_init.sql` declares `lpf_per_species jsonb not null default '{}'` and
 * [ReportEntity] stores `lpf_per_species_json`; neither side carries EPG any more. The pair
 * has to move together — an insert naming a column the table lacks fails at runtime, not at
 * compile time, and only once a report is actually generated online.
 */
open class ReportRemoteDataSource @Inject constructor(
    private val supabase: SupabaseClient,
    private val gson: Gson,
) {
    /**
     * Writes the report row matching `public.reports` in `0001_init.sql`, or leaves the row
     * alone when the server already has it.
     *
     * **Insert-if-absent, not a true upsert.** A plain insert made every retry conflict on the
     * primary key, so a report whose row had already landed was parked in `sync_failed` and
     * re-uploaded its files on every pass without ever completing (14zcqnthrx9). A conflicting
     * id now does nothing and the write succeeds, so the caller marks the report synced.
     *
     * Nothing is lost by not updating. A report row never changes once generated: regenerating
     * mints a new id, and the only later local edits are the sync status and this device's own
     * file paths, neither of which belongs on the server. `0001_init.sql` also grants `reports`
     * no UPDATE policy, which an `ON CONFLICT DO UPDATE` would need; `DO NOTHING` needs only
     * `reports_insert_own`. Same shape as the predictions write in `SampleRemoteDataSource`.
     *
     * @throws IllegalStateException when no Supabase user session is available.
     */
    open suspend fun upsertReport(report: ReportEntity) {
        val userId = supabase.auth.currentUserOrNull()?.id
            ?: error("A Supabase user session is required to sync reports.")
        supabase.postgrest[REPORTS_TABLE].upsert(report.toInsertRow(userId)) {
            onConflict = "id"
            ignoreDuplicates = true
        }
    }

    /**
     * Uploads a generated report file to the `reports` bucket.
     *
     * [objectPath] comes from [objectPathFor], so it always starts with the owner's uid —
     * which is what the bucket's RLS matches on. `upsert` is on because regenerating a
     * report reuses its id, and a plain upload would be rejected the second time.
     */
    open suspend fun uploadReportFile(objectPath: String, bytes: ByteArray) {
        supabase.storage.from(REPORTS_BUCKET).upload(objectPath, bytes) {
            upsert = true
        }
    }

    /**
     * Downloads a report file previously uploaded by [uploadReportFile].
     *
     * Authenticated rather than signed, matching `SampleRemoteDataSource.downloadSampleImage`:
     * this runs with a live session and the bytes are read here rather than handed to a
     * renderer, so a signed URL would only add a round trip.
     *
     * Throws when the object is absent — a report generated before the bucket existed has
     * nothing stored, and the caller reports that rather than pretending it recovered.
     */
    open suspend fun downloadReportFile(objectPath: String): ByteArray =
        supabase.storage.from(REPORTS_BUCKET).downloadAuthenticated(objectPath)

    private fun ReportEntity.toInsertRow(userId: String): ReportInsertRow {
        val positives: List<String> = runCatching {
            gson.fromJson<List<String>>(positiveSpeciesJson, stringListType)
        }.getOrNull().orEmpty()
        val lpf: Map<String, LpfDensity> = runCatching {
            gson.fromJson<Map<String, LpfDensity>>(lpfPerSpeciesJson, stringLpfDensityMapType)
        }.getOrNull().orEmpty()
        return ReportInsertRow(
            id = reportId,
            sessionId = sessionId,
            userId = userId,
            reportType = reportType,
            generatedAt = Instant.ofEpochMilli(generatedAt).toString(),
            totalSamples = totalSamples,
            totalEggsConfirmed = totalEggsConfirmed,
            positiveSpecies = positives,
            lpfPerSpecies = lpf.toJsonObject(),
            csvFilePath = csvFilePath,
            pdfFilePath = pdfFilePath,
        )
    }

    // ── Pull (read from server) ────────────────────────────────────────────────

    /**
     * Fetches all reports owned by [userId], ordered by generated_at ascending.
     */
    open suspend fun fetchReports(userId: String): List<ReportEntity> =
        supabase.postgrest[REPORTS_TABLE].select {
            filter { eq("user_id", userId) }
            order("generated_at", Order.ASCENDING)
        }.decodeList<ReportRow>().map { it.toEntity() }

    private fun Map<String, LpfDensity>.toJsonObject(): JsonObject =
        buildJsonObject {
            forEach { (species, density) ->
                put(species, buildJsonObject {
                    put("min", density.min)
                    put("max", density.max)
                })
            }
        }

    @Serializable
    private data class ReportInsertRow(
        @SerialName("id")
        val id: String,
        @SerialName("session_id")
        val sessionId: String,
        @SerialName("user_id")
        val userId: String,
        @SerialName("report_type")
        val reportType: String,
        @SerialName("generated_at")
        val generatedAt: String,
        @SerialName("total_samples")
        val totalSamples: Int,
        @SerialName("total_eggs_confirmed")
        val totalEggsConfirmed: Int,
        @SerialName("positive_species")
        val positiveSpecies: List<String>,
        @SerialName("lpf_per_species")
        val lpfPerSpecies: JsonObject,
        @SerialName("csv_file_path")
        val csvFilePath: String?,
        @SerialName("pdf_file_path")
        val pdfFilePath: String?,
    )

    // ── Select DTO (read path) ────────────────────────────────────────────────

    @Serializable
    private data class ReportRow(
        @SerialName("id") val id: String,
        @SerialName("session_id") val sessionId: String,
        @SerialName("user_id") val userId: String,
        @SerialName("report_type") val reportType: String,
        @SerialName("generated_at") val generatedAt: String,
        @SerialName("total_samples") val totalSamples: Int,
        @SerialName("total_eggs_confirmed") val totalEggsConfirmed: Int,
        @SerialName("positive_species") val positiveSpecies: List<String>,
        @SerialName("lpf_per_species") val lpfPerSpecies: JsonObject,
        @SerialName("csv_file_path") val csvFilePath: String? = null,
        @SerialName("pdf_file_path") val pdfFilePath: String? = null,
    )

    private fun ReportRow.toEntity(): ReportEntity {
        val generatedAtMs = parseSupabaseInstant(generatedAt).toEpochMilli()
        val positiveSpeciesJson = gson.toJson(positiveSpecies)
        val lpfMap = lpfPerSpecies.mapValues { (_, v) ->
            val obj = v as JsonObject
            // `mean` is not read even when an older row still carries it: PB-17 made the
            // range the figure, and reviving a superseded number from storage is how the wrong
            // definition comes back.
            LpfDensity(
                min = (obj["min"] as JsonPrimitive).content.toInt(),
                max = (obj["max"] as JsonPrimitive).content.toInt(),
            )
        }
        val lpfPerSpeciesJson = gson.toJson(lpfMap)
        return ReportEntity(
            reportId = id,
            sessionId = sessionId,
            userId = userId,
            reportType = reportType,
            generatedAt = generatedAtMs,
            totalSamples = totalSamples,
            totalEggsConfirmed = totalEggsConfirmed,
            positiveSpeciesJson = positiveSpeciesJson,
            lpfPerSpeciesJson = lpfPerSpeciesJson,
            csvFilePath = csvFilePath,
            pdfFilePath = pdfFilePath,
            supabaseStatus = ReportSyncStatus.SYNCED.value,
            createdAt = generatedAtMs,
        )
    }

    companion object {
        /** Storage bucket holding generated report files. Mirrors `samples` in layout. */
        const val REPORTS_BUCKET = "reports"

        /** File extension for a report PDF, as used by [objectPathFor]. */
        const val PDF_EXTENSION = "pdf"

        /** File extension for a report CSV, as used by [objectPathFor]. */
        const val CSV_EXTENSION = "csv"

        /**
         * The object path a report's file occupies: `{userId}/{reportId}.{extension}`.
         *
         * Derived rather than stored. Both parts already live on the row, so there is no
         * column to add, no migration to apply by hand, and no way for a stored key to drift
         * out of step with the row that owns it. The leading uid is also what the bucket's
         * RLS policies match on, so a path built any other way would simply be refused.
         */
        fun objectPathFor(userId: String, reportId: String, extension: String): String =
            "$userId/$reportId.$extension"

        private const val REPORTS_TABLE = "reports"
        private val stringListType = object : TypeToken<List<String>>() {}.type
        private val stringLpfDensityMapType = object : TypeToken<Map<String, LpfDensity>>() {}.type
    }
}
