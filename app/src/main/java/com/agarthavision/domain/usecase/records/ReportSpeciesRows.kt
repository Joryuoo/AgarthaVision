package com.agarthavision.domain.usecase.records

import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.LpfDensity
import com.agarthavision.domain.model.ReportPdfSpeciesRow

/**
 * One row per recognized [EggSpecies] present in [lpfPerSpecies].
 *
 * Mucus, blood, and WBC findings aren't egg species — they have no [EggSpecies] entry — so
 * they can never surface here, matching this table's egg-only scope.
 *
 * Shared by [ReportPdfBuilder] (session scope) and
 * [com.agarthavision.domain.usecase.records.PatientReportPdfBuilder] (patient scope, pooled
 * across sessions) so the same filter and sort rule produces both tables — copying it a second
 * time is exactly the two-definitions failure [com.agarthavision.domain.usecase.reports.aggregateLpfPerSpecies]
 * already warns about.
 */
internal fun buildReportSpeciesRows(lpfPerSpecies: Map<String, LpfDensity>): List<ReportPdfSpeciesRow> {
    val knownCanonicalSpecies = EggSpecies.entries.mapNotNull { it.canonicalClass }.toSet()
    return lpfPerSpecies
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
