package com.agarthavision.domain.usecase.records

import com.agarthavision.domain.model.Detection
import com.agarthavision.domain.model.ReportMetadata
import com.agarthavision.domain.model.ReportPdfDocument
import com.agarthavision.domain.model.ReportPdfHeader
import com.agarthavision.domain.model.Sample
import javax.inject.Inject

/**
 * Builds the pure-data [ReportPdfDocument] handed to
 * [com.agarthavision.domain.repository.ReportPdfRenderer].
 *
 * `samples` and `detectionsBySample` are accepted to leave room for a future per-sample
 * appendix; the current PDF renders only the patient block, the aggregate header, and the
 * per-species table below.
 */
class ReportPdfBuilder @Inject constructor() {
    @Suppress("UnusedParameter")
    fun build(
        metadata: ReportMetadata,
        samples: List<Sample>,
        detectionsBySample: Map<String, List<Detection>>,
    ): ReportPdfDocument {
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
            generatedAt = metadata.generatedAt,
            totalEggsConfirmed = metadata.totalEggsConfirmed,
            positiveSpecies = metadata.positiveSpecies,
        )
        return ReportPdfDocument(
            header = header,
            speciesRows = buildReportSpeciesRows(metadata.lpfPerSpecies),
        )
    }
}
