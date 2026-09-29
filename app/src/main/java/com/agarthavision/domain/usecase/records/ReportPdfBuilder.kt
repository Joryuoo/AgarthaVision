package com.agarthavision.domain.usecase.records

import com.agarthavision.domain.model.Detection
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.ReportMetadata
import com.agarthavision.domain.model.ReportPdfDocument
import com.agarthavision.domain.model.ReportPdfHeader
import com.agarthavision.domain.model.ReportPdfSpeciesRow
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
            speciesRows = buildSpeciesRows(metadata),
        )
    }

    /**
     * One row per recognized [EggSpecies] present in `metadata.lpfPerSpecies`.
     *
     * Mucus, blood, and WBC findings aren't egg species — they have no [EggSpecies] entry — so
     * they can never surface here, matching this table's egg-only scope.
     *
     * The reported figure is the LPF (Low Power Field) range across the session's fields.
     */
    private fun buildSpeciesRows(metadata: ReportMetadata): List<ReportPdfSpeciesRow> {
        val knownCanonicalSpecies = EggSpecies.entries.mapNotNull { it.canonicalClass }.toSet()
        return metadata.lpfPerSpecies
            .filterKeys { it in knownCanonicalSpecies }
            .map { (canonical, density) ->
                ReportPdfSpeciesRow(
                    speciesDisplayName = canonical,
                    min = density.min,
                    max = density.max,
                    descriptor = density.descriptor,
                )
            }
            .sortedBy { it.speciesDisplayName }
    }
}
