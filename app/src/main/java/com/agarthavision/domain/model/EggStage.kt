package com.agarthavision.domain.model

enum class EggStage(
    val displayName: String,
    val applicableSpecies: Set<EggSpecies> = setOf(
        EggSpecies.ASCARIS,
        EggSpecies.TRICHURIS,
        EggSpecies.HOOKWORM,
    ),
) {
    CORTICATED_FERTILIZED("Corticated Fertilized"),
    CORTICATED_UNFERTILIZED("Corticated Unfertilized"),
    DECORTICATED_FERTILIZED("Decorticated Fertilized"),
    DECORTICATED_UNFERTILIZED("Decorticated Unfertilized"),
    ;

    @Suppress("UNUSED_PARAMETER")
    fun getDisplayName(species: EggSpecies? = null): String = displayName

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
