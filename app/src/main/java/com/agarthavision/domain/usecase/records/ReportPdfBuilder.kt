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
     * One row per recognized [EggSpecies] present in `metadata.epgPerSpecies`.
     *
     * Mucus, blood, and WBC findings aren't egg species — they have no [EggSpecies] entry — so
     * they can never surface here, matching this table's egg-only scope.
     *
     * The reported number is EPG (eggs per gram), a temporary stand-in metric. Ticket 86d4a6jxw
     * replaces it with LPF (Low Power Field) density once that pipeline lands; see
     * [ReportPdfSpeciesRow] for the same note at the data-model level.
     */
    private fun buildSpeciesRows(metadata: ReportMetadata): List<ReportPdfSpeciesRow> {
        val knownCanonicalSpecies = EggSpecies.entries.mapNotNull { it.canonicalClass }.toSet()
        return metadata.epgPerSpecies
            .filterKeys { it in knownCanonicalSpecies }
            .map { (canonical, epg) -> ReportPdfSpeciesRow(speciesDisplayName = canonical, epg = epg) }
            .sortedBy { it.speciesDisplayName }
    }
}
