package com.agarthavision.domain.usecase.records

import com.agarthavision.domain.model.Detection
import com.agarthavision.domain.model.DetectionVerdict
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.Patient
import com.agarthavision.domain.model.PatientNameOption
import com.agarthavision.domain.model.ReportPdfSampleItem
import com.agarthavision.domain.model.Sample

/**
 * Derives an organization name from a medtech's email address by extracting the domain name.
 * Returns null if [email] is null, blank, or does not contain a valid '@' domain part.
 */
fun deriveOrganizationFromEmail(email: String?): String? {
    if (email.isNullOrBlank() || !email.contains('@')) return null
    val domain = email.substringAfter('@').trim()
    return domain.takeIf { it.isNotBlank() && !it.contains('@') }
}

/**
 * Formats a patient's name for report rendering according to the selected [PatientNameOption].
 */
fun formatPatientNameForReport(
    patient: Patient,
    option: PatientNameOption,
    excludedLabel: String = "Excluded",
): String = when (option) {
    PatientNameOption.SHOW -> patient.displayName
    PatientNameOption.CENSOR -> patient.maskedDisplayName
    PatientNameOption.EXCLUDE -> excludedLabel
}

/**
 * Builds the per-sample appendix list for PDF reports.
 */
fun buildAppendixSamples(
    samples: List<Sample>,
    detectionsBySample: Map<String, List<Detection>>,
    sessionLabelById: Map<String, String?> = emptyMap(),
    defaultSessionLabel: String? = null,
    noParasitesSummary: String = "No parasites detected",
): List<ReportPdfSampleItem> {
    return samples.mapIndexed { index, sample ->
        val confirmedDetections = detectionsBySample[sample.id].orEmpty()
            .filter { it.verdict == DetectionVerdict.CONFIRMED }
        val eggCount = confirmedDetections.size
        val findingsSummary = if (eggCount == 0) {
            noParasitesSummary
        } else {
            confirmedDetections.map { det ->
                val label = det.expertClass ?: det.classLabel
                EggSpecies.fromClassLabel(label)?.canonicalClass ?: label
            }.distinct().joinToString(", ")
        }
        ReportPdfSampleItem(
            sampleId = sample.id,
            sessionLabel = sessionLabelById[sample.sessionId] ?: defaultSessionLabel ?: sample.sessionId,
            fieldIndex = index + 1,
            imagePath = sample.filePath.takeIf { it.isNotBlank() },
            eggCount = eggCount,
            findingsSummary = findingsSummary,
        )
    }
}

/**
 * Number of sample cards rendered per appendix page in PDF reports.
 */
const val SAMPLES_PER_APPENDIX_PAGE = 4

/**
 * Calculates the number of appendix pages required for [sampleCount] samples.
 */
fun appendixPageCount(sampleCount: Int): Int =
    if (sampleCount <= 0) 0 else (sampleCount + SAMPLES_PER_APPENDIX_PAGE - 1) / SAMPLES_PER_APPENDIX_PAGE

