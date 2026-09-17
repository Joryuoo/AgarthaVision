package com.agarthavision.data.local.mapper

import com.agarthavision.data.local.entity.ReportEntity
import com.agarthavision.domain.model.Report
import com.agarthavision.domain.model.ReportSyncStatus
import com.agarthavision.domain.model.ReportType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.Instant

private val stringListType = object : TypeToken<List<String>>() {}.type

/**
 * [Report.epgPerSpecies] always comes back empty. `reports.epg_per_species_json` is gone as
 * of Room 13 and `epg_per_species` with it in `0001_init.sql`, because a stored EPG is a
 * derived figure that goes stale the moment a finding is corrected.
 *
 * Nothing reads it. The CSV and PDF builders take their per-species EPG from
 * `ReportMetadata.epgPerSpecies`, which `GenerateSessionReportUseCase` computes fresh at
 * generation time from the findings themselves.
 */
fun ReportEntity.toDomain(gson: Gson): Report {
    val positives: List<String> = runCatching {
        gson.fromJson<List<String>>(positiveSpeciesJson, stringListType)
    }.getOrNull().orEmpty()
    return Report(
        id = reportId,
        sessionId = sessionId,
        userId = userId,
        reportType = ReportType.fromValue(reportType),
        generatedAt = Instant.ofEpochMilli(generatedAt),
        totalSamples = totalSamples,
        totalEggsConfirmed = totalEggsConfirmed,
        positiveSpecies = positives,
        epgPerSpecies = emptyMap(),
        csvFilePath = csvFilePath,
        pdfFilePath = pdfFilePath,
        supabaseStatus = ReportSyncStatus.fromValue(supabaseStatus),
    )
}

fun Report.toEntity(gson: Gson): ReportEntity =
    ReportEntity(
        reportId = id,
        sessionId = sessionId,
        userId = userId,
        reportType = reportType.value,
        generatedAt = generatedAt.toEpochMilli(),
        totalSamples = totalSamples,
        totalEggsConfirmed = totalEggsConfirmed,
        positiveSpeciesJson = gson.toJson(positiveSpecies),
        csvFilePath = csvFilePath,
        pdfFilePath = pdfFilePath,
        supabaseStatus = supabaseStatus.value,
        createdAt = generatedAt.toEpochMilli(),
    )
