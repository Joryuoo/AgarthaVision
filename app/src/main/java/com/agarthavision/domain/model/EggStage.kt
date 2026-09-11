package com.agarthavision.domain.model

/**
 * Optional egg/parasite developmental stage attached to an expert-verified [Detection].
 *
 * Local values stay lowercase to match the mobile state model; Supabase receives the
 * uppercase [remoteValue] required by the schema CHECK constraint (see
 * `supabase/migrations/0010_verification_stage.sql`), same convention as
 * [DetectionVerdict].
 *
 * Intentionally excludes the Ascaris morphology values `corticated`, `decorticated`,
 * and `fertilized` for now (product decision, ticket 86d4a6jwy). Storage is by string
 * value rather than ordinal, so those entries — or others — can be added later without
 * breaking already-stored rows.
 */
enum class EggStage(
    val value: String,
    val remoteValue: String,
) {
    UNFERTILIZED("unfertilized", "UNFERTILIZED"),
    UNEMBRYONATED("unembryonated", "UNEMBRYONATED"),
    EMBRYONATED("embryonated", "EMBRYONATED"),
    LARVATED("larvated", "LARVATED"),
    ;

    val displayName: String
        get() = when (this) {
            UNFERTILIZED -> "Unfertilized"
            UNEMBRYONATED -> "Unembryonated"
            EMBRYONATED -> "Embryonated"
            LARVATED -> "Larvated"
        }

    companion object {
        /**
         * Parses either a local lowercase value or remote uppercase value.
         */
        fun fromValue(value: String): EggStage? =
            entries.firstOrNull { stage ->
                stage.value == value || stage.remoteValue == value
            }

        /**
         * The stages applicable to a given [species], or empty when the species has
         * no defined stage set (e.g. [EggSpecies.OTHER]).
         */
        fun validFor(species: EggSpecies): List<EggStage> =
            when (species) {
                EggSpecies.ASCARIS -> listOf(UNFERTILIZED)
                EggSpecies.TRICHURIS -> listOf(UNEMBRYONATED, EMBRYONATED)
                EggSpecies.HOOKWORM -> listOf(UNEMBRYONATED, LARVATED)
                EggSpecies.OTHER -> emptyList()
            }
    }
}
