package com.agarthavision.domain.usecase.records

import com.agarthavision.domain.model.Detection
import com.agarthavision.domain.model.ReportConfig
import com.agarthavision.domain.model.ReportMetadata
import com.agarthavision.domain.model.ReportPdfDocument
import com.agarthavision.domain.model.ReportPdfHeader
import com.agarthavision.domain.model.Sample
import javax.inject.Inject

/**
 * Builds the pure-data [ReportPdfDocument] handed to
 * [com.agarthavision.domain.repository.ReportPdfRenderer].
 *
 * `samples` and `detectionsBySample` are accepted to populate the per-sample appendix
 * when [ReportConfig.includeSampleImages] is enabled; the current PDF renders the patient
 * block, the aggregate header, the per-species table, and the optional sample appendix.
 */
class ReportPdfBuilder @Inject constructor() {
    @Suppress("LongParameterList")
    fun build(
        metadata: ReportMetadata,
        samples: List<Sample>,
        detectionsBySample: Map<String, List<Detection>>,
        config: ReportConfig = ReportConfig(),
        generatedByEmail: String? = null,
        organizationName: String? = deriveOrganizationFromEmail(generatedByEmail),
    ): ReportPdfDocument {
        val appendixSamples = if (config.includeSampleImages) {
            buildAppendixSamples(
                samples = samples,
                detectionsBySample = detectionsBySample,
                defaultSessionLabel = metadata.session.label ?: metadata.session.id,
            )
        } else {
            emptyList()
        }
        val totalPages = if (config.includeSampleImages) {
            1 + appendixPageCount(appendixSamples.size)
        } else {
            1
        }
        val header = ReportPdfHeader(
            reportId = metadata.reportId,
            sessionId = metadata.session.id,
            sessionLabel = metadata.session.label,
            patientName = metadata.patient.name,
            patientSex = metadata.patient.sex,
            patientAgeYears = metadata.patient.ageYears,
            barangayLabel = metadata.patient.barangayLabel,
            fieldsExamined = metadata.totalSamples,
            generatedByName = metadata.generatedByName,
            generatedByEmail = generatedByEmail,
            organizationName = organizationName,
            generatedAt = metadata.generatedAt,
            totalEggsConfirmed = metadata.totalEggsConfirmed,
            positiveSpecies = metadata.positiveSpecies,
            includeSampleImages = config.includeSampleImages,
            totalPages = totalPages,
        )
        return ReportPdfDocument(
            header = header,
            speciesRows = buildReportSpeciesRows(metadata.lpfPerSpecies),
            appendixSamples = appendixSamples,
        )
    }
}
