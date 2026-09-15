package com.agarthavision.data.supabase

import com.agarthavision.data.local.entity.ReportEntity
import com.agarthavision.domain.model.ReportSyncStatus
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import java.time.Instant
import javax.inject.Inject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Writes persisted session reports to Supabase Postgres. Row-only sync — the
 * CSV file stays local on the device; only the metadata + aggregate stats are
 * mirrored to `public.reports`.
 */
open class ReportRemoteDataSource @Inject constructor(
    private val supabase: SupabaseClient,
    private val gson: Gson,
) {
    /**
     * Inserts the report row matching `0008_reports.sql` + `0011_reports_pdf_and_lpf.sql`.
     *
     * @throws IllegalStateException when no Supabase user session is available.
     */
    open suspend fun upsertReport(report: ReportEntity) {
        val userId = supabase.auth.currentUserOrNull()?.id
            ?: error("A Supabase user session is required to sync reports.")
        supabase.postgrest[REPORTS_TABLE].insert(report.toInsertRow(userId))
    }

    private fun ReportEntity.toInsertRow(userId: String): ReportInsertRow {
        val positives: List<String> = runCatching {
            gson.fromJson<List<String>>(positiveSpeciesJson, stringListType)
        }.getOrNull().orEmpty()
        val epg: Map<String, Int> = runCatching {
            gson.fromJson<Map<String, Int>>(epgPerSpeciesJson, stringIntMapType)
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
            epgPerSpecies = epg.toJsonObject(),
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

    private fun Map<String, Int>.toJsonObject(): JsonObject =
        JsonObject(mapValues<String, Int, JsonElement> { (_, count) -> JsonPrimitive(count) })

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
        @SerialName("epg_per_species")
        val epgPerSpecies: JsonObject,
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
        @SerialName("epg_per_species") val epgPerSpecies: JsonObject,
        @SerialName("csv_file_path") val csvFilePath: String? = null,
        @SerialName("pdf_file_path") val pdfFilePath: String? = null,
    )

    private fun ReportRow.toEntity(): ReportEntity {
        val generatedAtMs = Instant.parse(generatedAt).toEpochMilli()
        val positiveSpeciesJson = gson.toJson(positiveSpecies)
        val epgMap = epgPerSpecies.mapValues { (_, v) -> (v as JsonPrimitive).content.toInt() }
        val epgPerSpeciesJson = gson.toJson(epgMap)
        return ReportEntity(
            reportId = id,
            sessionId = sessionId,
            userId = userId,
            reportType = reportType,
            generatedAt = generatedAtMs,
            totalSamples = totalSamples,
            totalEggsConfirmed = totalEggsConfirmed,
            positiveSpeciesJson = positiveSpeciesJson,
            epgPerSpeciesJson = epgPerSpeciesJson,
            csvFilePath = csvFilePath,
            pdfFilePath = pdfFilePath,
            supabaseStatus = ReportSyncStatus.SYNCED.value,
            createdAt = generatedAtMs,
        )
    }

    private companion object {
        private const val REPORTS_TABLE = "reports"
        private val stringListType = object : TypeToken<List<String>>() {}.type
        private val stringIntMapType = object : TypeToken<Map<String, Int>>() {}.type
    }
}
