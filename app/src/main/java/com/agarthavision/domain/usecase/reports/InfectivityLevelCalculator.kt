package com.agarthavision.domain.usecase.reports

import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.InfectivityLevel

/**
 * Classifies per-species EPG readings into WHO STH intensity tiers (Kato-Katz).
 *
 * RETIRED (86d4a6jxw): LPF density does not transfer to these EPG-based thresholds.
 * This class is kept for history and will be removed in a follow-up chore.
 */
@Deprecated("WHO Kato-Katz thresholds do not apply to Direct Smear LPF density.")
object InfectivityLevelCalculator {

    private data class Thresholds(val moderate: Int, val extreme: Int)

    private val whoThresholds: Map<EggSpecies, Thresholds> = mapOf(
        EggSpecies.ASCARIS to Thresholds(moderate = 5_000, extreme = 50_000),
        EggSpecies.TRICHURIS to Thresholds(moderate = 1_000, extreme = 10_000),
        EggSpecies.HOOKWORM to Thresholds(moderate = 2_000, extreme = 4_000),
    )

    fun classify(species: EggSpecies, epg: Int): InfectivityLevel? {
        if (epg <= 0) return null
        return whoThresholds[species]?.let { thresholds ->
            when {
                epg >= thresholds.extreme -> InfectivityLevel.EXTREME
                epg >= thresholds.moderate -> InfectivityLevel.MODERATE
                else -> InfectivityLevel.LOW
            }
        }
    }

    fun sessionLevel(perSpeciesEpg: Map<EggSpecies, Int>): InfectivityLevel? =
        perSpeciesEpg.entries
            .mapNotNull { (species, epg) -> classify(species, epg) }
            .maxByOrNull { it.ordinal }
}
