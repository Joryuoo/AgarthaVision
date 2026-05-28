package com.agarthavision.data.local.mapper

import com.agarthavision.data.local.entity.ReportEntity
import com.agarthavision.domain.model.Report
import com.agarthavision.domain.model.ReportSyncStatus
import com.agarthavision.domain.model.ReportType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.Instant

private val stringListType = object : TypeToken<List<String>>() {}.type
private val stringIntMapType = object : TypeToken<Map<String, Int>>() {}.type

fun ReportEntity.toDomain(gson: Gson): Report {
    val positives: List<String> = runCatching {
        gson.fromJson<List<String>>(positiveSpeciesJson, stringListType)
    }.getOrNull().orEmpty()
    val epg: Map<String, Int> = runCatching {
        gson.fromJson<Map<String, Int>>(epgPerSpeciesJson, stringIntMapType)
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
        epgPerSpecies = epg,
        csvFilePath = csvFilePath,
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
        epgPerSpeciesJson = gson.toJson(epgPerSpecies),
        csvFilePath = csvFilePath,
        supabaseStatus = supabaseStatus.value,
        createdAt = generatedAt.toEpochMilli(),
    )
