package com.agarthavision.domain.model

/**
 * Per-species egg count for one session.
 */
data class EggCount(
    val species: String,
    val count: Int,
) {
    /**
     * The canonical class name for this count's species (e.g. "Ascaris lumbricoides"),
     * resolved through [EggSpecies] alias matching. Falls back to the raw [species] label when
     * it isn't recognized, so an unmapped/"Other" label still groups and displays as itself.
     *
     * Used by [com.agarthavision.domain.usecase.records.GenerateSessionReportUseCase] so every
     * reader normalizes species labels the same single way.
     */
    fun canonicalSpecies(): String = EggSpecies.fromClassLabel(species)?.canonicalClass ?: species
}

// `canonicalEggSpecies()` went with the WHO infectivity tier (PB-16). It existed to exclude
// unrecognized labels from a tier lookup that only had thresholds for the three named species;
// with no tier to look up, a raw label groups and displays as itself through `canonicalSpecies`,
// which is all any remaining reader wants.
