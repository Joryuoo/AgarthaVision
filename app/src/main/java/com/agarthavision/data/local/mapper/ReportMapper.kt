package com.agarthavision.data.local.mapper

import com.agarthavision.data.local.entity.ReportEntity
import com.agarthavision.domain.model.LpfDensity
import com.agarthavision.domain.model.Report
import com.agarthavision.domain.model.ReportSyncStatus
import com.agarthavision.domain.model.ReportType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.Instant

private val stringListType = object : TypeToken<List<String>>() {}.type
private val stringLpfDensityMapType = object : TypeToken<Map<String, LpfDensity>>() {}.type

/**
 * `reports.epg_per_species_json` is gone as of Room 13, and `epg_per_species` with it in
 * `0001_init.sql`. EPG is eggs-per-gram via Kato-Katz; Philippine medtechs use direct
 * smear, so the x24 multiplier was wrong for the method in use (86d4a6jxw).
 *
 * [Report.lpfPerSpecies] takes its place and *is* persisted, because a report is a record
 * of what was found at generation time. Correcting a finding afterwards does not rewrite a
 * report that already went out — PB-20 makes that explicit.
 */
fun ReportEntity.toDomain(gson: Gson): Report {
    val positives: List<String> = runCatching {
        gson.fromJson<List<String>>(positiveSpeciesJson, stringListType)
    }.getOrNull().orEmpty()
    val lpf: Map<String, LpfDensity> = runCatching {
        gson.fromJson<Map<String, LpfDensity>>(lpfPerSpeciesJson, stringLpfDensityMapType)
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
        lpfPerSpecies = lpf,
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
        lpfPerSpeciesJson = gson.toJson(lpfPerSpecies),
        csvFilePath = csvFilePath,
        pdfFilePath = pdfFilePath,
        supabaseStatus = supabaseStatus.value,
        createdAt = generatedAt.toEpochMilli(),
    )
