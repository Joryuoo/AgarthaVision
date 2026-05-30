package com.agarthavision.domain.usecase.records

import com.agarthavision.domain.model.Detection
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.Sample
import com.agarthavision.domain.model.Session
import java.time.Instant
import javax.inject.Inject

/**
 * Formats session report CSV output with a header block and data rows.
 */
class ReportCsvBuilder @Inject constructor() {
    fun build(
        reportId: String,
        session: Session,
        generatedBy: String,
        generatedAt: Instant,
        totalSamples: Int,
        totalEggsConfirmed: Int,
        positiveSpecies: List<String>,
        epgPerSpecies: Map<String, Int>,
        samples: List<Sample>,
        detectionsBySample: Map<String, List<Detection>>,
    ): String {
        val rows = mutableListOf<String>()
        rows += buildHeaderBlock(
            reportId = reportId,
            session = session,
            generatedBy = generatedBy,
            generatedAt = generatedAt,
            totalSamples = totalSamples,
            totalEggsConfirmed = totalEggsConfirmed,
            positiveSpecies = positiveSpecies,
            epgPerSpecies = epgPerSpecies,
        )
        rows += ""
        rows += CSV_HEADER
        samples.forEach { sample ->
            val detections = detectionsBySample[sample.id].orEmpty()
            if (detections.isEmpty()) {
                rows += sample.toCsvRow(detection = null)
            } else {
                detections.forEach { detection ->
                    rows += sample.toCsvRow(detection = detection)
                }
            }
        }
        return rows.joinToString(separator = "\n", postfix = "\n\n")
    }

    private fun buildHeaderBlock(
        reportId: String,
        session: Session,
        generatedBy: String,
        generatedAt: Instant,
        totalSamples: Int,
        totalEggsConfirmed: Int,
        positiveSpecies: List<String>,
        epgPerSpecies: Map<String, Int>,
    ): List<String> {
        val positiveValue = if (positiveSpecies.isEmpty()) {
            "none"
        } else {
            positiveSpecies.joinToString(",")
        }
        val headerLines = mutableListOf(
            "# AgarthaVision Session Report",
            "# report_id: $reportId",
            "# session_id: ${session.id}",
            "# session_label: ${session.label.orEmpty()}",
            "# session_started_at: ${Instant.ofEpochMilli(session.startedAt)}",
            "# session_ended_at: ${session.endedAt?.let { Instant.ofEpochMilli(it).toString() }.orEmpty()}",
            "# device_id: ${session.deviceId}",
            "# generated_by: $generatedBy",
            "# generated_at: $generatedAt",
            "# total_samples: $totalSamples",
            "# total_eggs_confirmed: $totalEggsConfirmed",
            "# positive_species: $positiveValue",
        )
        EggSpecies.entries
            .mapNotNull { it.canonicalClass }
            .forEach { canonical ->
                val key = canonical.toEpgHeaderKey()
                val value = epgPerSpecies[canonical] ?: 0
                headerLines += "# $key: $value"
            }
        return headerLines
    }

    private fun Sample.toCsvRow(detection: Detection?): String =
        listOf(
            id,
            Instant.ofEpochMilli(timestamp).toString(),
            Instant.ofEpochMilli(verifiedAt).toString(),
            detection?.classLabel.orEmpty(),
            detection?.confidence?.toString().orEmpty(),
            detection?.expertClass.orEmpty(),
            detection?.verdict?.value.orEmpty(),
            latitude?.toString().orEmpty(),
            longitude?.toString().orEmpty(),
            accuracyMeters?.toString().orEmpty(),
            isManual.toString(),
            isRepeat.toString(),
            userNote.orEmpty(),
            inferenceModelVersion,
        ).joinToString(",") { it.csvEscape() }

    private fun String.csvEscape(): String {
        val escaped = replace("\"", "\"\"")
        return if (needsCsvQuotes()) {
            "\"$escaped\""
        } else {
            escaped
        }
    }

    private fun String.needsCsvQuotes(): Boolean =
        any { it in CSV_QUOTED_CHARS }

    private fun String.toEpgHeaderKey(): String {
        val slug = lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
        return "epg_$slug"
    }

    private companion object {
        private val CSV_QUOTED_CHARS = charArrayOf(',', '"', '\n', '\r')

        private const val CSV_HEADER =
            "sample_id,captured_at,verified_at,model_class,model_confidence,expert_class,verdict," +
                "gps_lat,gps_lng,gps_accuracy,is_manual,is_repeat,user_note,model_version"
    }
}
