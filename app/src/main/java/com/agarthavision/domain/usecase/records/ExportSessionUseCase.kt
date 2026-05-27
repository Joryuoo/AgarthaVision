package com.agarthavision.domain.usecase.records

import com.agarthavision.domain.model.Detection
import com.agarthavision.domain.model.Sample
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.DetectionRepository
import com.agarthavision.domain.repository.SampleRepository
import com.agarthavision.domain.repository.SessionReportRepository
import java.time.Instant
import javax.inject.Inject

/**
 * Generates and writes the Phase 1 raw CSV export for one session.
 */
class ExportSessionUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val sampleRepository: SampleRepository,
    private val detectionRepository: DetectionRepository,
    private val sessionReportRepository: SessionReportRepository,
) {
    suspend operator fun invoke(sessionId: String): Result<String> = runCatching {
        val userId = requireNotNull(authRepository.getCurrentUserId()) {
            "A logged-in medtech is required to export a session."
        }
        val samples = sampleRepository.getSamplesForSession(sessionId, userId)
        val csv = buildCsv(samples)
        sessionReportRepository.writeSessionCsv(sessionId, csv)
    }

    private suspend fun buildCsv(samples: List<Sample>): String {
        val rows = mutableListOf(CSV_HEADER)
        samples.forEach { sample ->
            val detections = detectionRepository.getDetectionsForSample(sample.id)
            if (detections.isEmpty()) {
                rows += sample.toCsvRow(detection = null)
            } else {
                detections.forEach { detection ->
                    rows += sample.toCsvRow(detection = detection)
                }
            }
        }
        return rows.joinToString(separator = "\n", postfix = "\n")
    }

    private fun Sample.toCsvRow(detection: Detection?): String =
        listOf(
            id,
            Instant.ofEpochMilli(timestamp).toString(),
            Instant.ofEpochMilli(verifiedAt).toString(),
            detection?.expertClass ?: detection?.classLabel.orEmpty(),
            detection?.confidence?.toString().orEmpty(),
            latitude?.toString().orEmpty(),
            longitude?.toString().orEmpty(),
            accuracyMeters?.toString().orEmpty(),
            storagePath.orEmpty(),
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

    private companion object {
        private val CSV_QUOTED_CHARS = charArrayOf(',', '"', '\n', '\r')

        private const val CSV_HEADER =
            "sample_id,captured_at,verified_at,class_label,confidence,gps_lat,gps_lng,gps_accuracy,storage_path"
    }
}
