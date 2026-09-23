package com.agarthavision.domain.model

enum class EggStage(
    val displayName: String,
    val applicableSpecies: Set<EggSpecies>,
) {
    // Ascaris lumbricoides
    CORTICATED_FERTILIZED("Corticated Fertilized", setOf(EggSpecies.ASCARIS)),
    DECORTICATED_FERTILIZED("Decorticated Fertilized", setOf(EggSpecies.ASCARIS)),
    CORTICATED_UNFERTILIZED("Corticated Unfertilized", setOf(EggSpecies.ASCARIS)),
    DECORTICATED_UNFERTILIZED("Decorticated Unfertilized", setOf(EggSpecies.ASCARIS)),

    // Trichuris trichiura
    UNSEGMENTED("Unsegmented / Zygote", setOf(EggSpecies.TRICHURIS)),

    // Hookworm
    EARLY_CLEAVAGE("Early Cleavage / Morula", setOf(EggSpecies.HOOKWORM)),

    // Shared across species
    EMBRYONATED("Embryonated", setOf(EggSpecies.ASCARIS, EggSpecies.TRICHURIS, EggSpecies.HOOKWORM)),
    ;

    fun getDisplayName(species: EggSpecies?): String = when {
        this == EMBRYONATED && species == EggSpecies.HOOKWORM -> "Embryonated / Larvated"
        else -> displayName
    }

    companion object {
        fun forSpecies(species: EggSpecies?): List<EggStage> {
            if (species == null || species == EggSpecies.OTHER) return emptyList()
            return entries.filter { species in it.applicableSpecies }
        }

        fun fromName(name: String?): EggStage? {
            if (name.isNullOrBlank()) return null
            return entries.firstOrNull {
                it.name.equals(name, ignoreCase = true) ||
                    it.displayName.equals(name, ignoreCase = true)
            }
        }
    }
}
