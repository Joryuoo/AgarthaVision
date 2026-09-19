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
 * Mirrors [ReportCsvBuilder]'s constructor shape so both report artifacts are assembled from the
 * same inputs in [GenerateSessionReportUseCase]. `samples` and `detectionsBySample` are accepted
 * for that parity and to leave room for a future per-sample appendix; the Phase 1 PDF renders
 * only the aggregate header and per-species table below.
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
            generatedBy = metadata.generatedBy,
            generatedAt = metadata.generatedAt,
            deviceId = metadata.session.deviceId,
            totalSamples = metadata.totalSamples,
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
                )
            }
            .sortedBy { it.speciesDisplayName }
    }
}
