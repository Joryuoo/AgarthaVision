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
     * Shared by [com.agarthavision.domain.usecase.records.GenerateSessionReportUseCase] and the
     * infectivity-tier computation so both normalize species labels the same single way.
     */
    fun canonicalSpecies(): String = EggSpecies.fromClassLabel(species)?.canonicalClass ?: species

    /**
     * The recognized [EggSpecies] this count belongs to, or null when [species] doesn't match
     * any known alias. Kept distinct from [canonicalSpecies] because WHO infectivity tiers only
     * exist for recognized species — "Other"/unrecognized labels must be excluded rather than
     * silently grouped under their raw string.
     */
    fun canonicalEggSpecies(): EggSpecies? = EggSpecies.fromClassLabel(species)
}
