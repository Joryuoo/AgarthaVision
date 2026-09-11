package com.agarthavision.domain.usecase.reports

import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.InfectivityLevel

/**
 * Classifies per-species EPG readings into WHO STH intensity tiers (Kato-Katz), per WHO's
 * population-surveillance cutoffs (Ascaris/Trichuris/Hookworm). These bands are for
 * population-level surveillance, not individual clinical diagnosis — pending clinical
 * sign-off (Dr. Bayron) before any diagnostic framing is attached to them anywhere upstream.
 *
 * Pure domain object — no Android import, no DI — per the domain-layer purity rule.
 */
object InfectivityLevelCalculator {

    /** Moderate/extreme EPG cutoffs for one species; anything below moderate is Low. */
    private data class Thresholds(val moderate: Int, val extreme: Int)

    // WHO Kato-Katz EPG intensity cutoffs. No WHO table exists for EggSpecies.OTHER, so it is
    // deliberately absent here and excluded from classification everywhere below.
    private val whoThresholds: Map<EggSpecies, Thresholds> = mapOf(
        EggSpecies.ASCARIS to Thresholds(moderate = 5_000, extreme = 50_000),
        EggSpecies.TRICHURIS to Thresholds(moderate = 1_000, extreme = 10_000),
        EggSpecies.HOOKWORM to Thresholds(moderate = 2_000, extreme = 4_000),
    )

    /**
     * Classifies a single species' EPG reading into a WHO intensity tier.
     *
     * Returns null when [species] is [EggSpecies.OTHER] or otherwise unmapped (no WHO table
     * exists for it), or when [epg] is zero or negative — a true negative/zero reading is a
     * neutral absence, never the Low tier.
     */
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

    /**
     * The session-level badge tier: the highest tier reached by any single recognized species
     * in [perSpeciesEpg]. Null when no species is classifiable (all zero, or none recognized).
     */
    fun sessionLevel(perSpeciesEpg: Map<EggSpecies, Int>): InfectivityLevel? =
        perSpeciesEpg.entries
            .mapNotNull { (species, epg) -> classify(species, epg) }
            .maxByOrNull { it.ordinal }
}
