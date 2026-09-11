package com.agarthavision.domain.usecase.reports

import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.InfectivityLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InfectivityLevelCalculatorTest {

    // --- Ascaris: Low 1-4,999 / Moderate 5,000-49,999 / Extreme >=50,000 ---

    @Test
    fun `ascaris just below moderate cutoff is low`() {
        assertEquals(InfectivityLevel.LOW, InfectivityLevelCalculator.classify(EggSpecies.ASCARIS, 4_999))
    }

    @Test
    fun `ascaris at moderate cutoff is moderate`() {
        assertEquals(InfectivityLevel.MODERATE, InfectivityLevelCalculator.classify(EggSpecies.ASCARIS, 5_000))
    }

    @Test
    fun `ascaris just below extreme cutoff is moderate`() {
        assertEquals(InfectivityLevel.MODERATE, InfectivityLevelCalculator.classify(EggSpecies.ASCARIS, 49_999))
    }

    @Test
    fun `ascaris at extreme cutoff is extreme`() {
        assertEquals(InfectivityLevel.EXTREME, InfectivityLevelCalculator.classify(EggSpecies.ASCARIS, 50_000))
    }

    // --- Trichuris: Low 1-999 / Moderate 1,000-9,999 / Extreme >=10,000 ---

    @Test
    fun `trichuris just below moderate cutoff is low`() {
        assertEquals(InfectivityLevel.LOW, InfectivityLevelCalculator.classify(EggSpecies.TRICHURIS, 999))
    }

    @Test
    fun `trichuris at moderate cutoff is moderate`() {
        assertEquals(InfectivityLevel.MODERATE, InfectivityLevelCalculator.classify(EggSpecies.TRICHURIS, 1_000))
    }

    @Test
    fun `trichuris just below extreme cutoff is moderate`() {
        assertEquals(InfectivityLevel.MODERATE, InfectivityLevelCalculator.classify(EggSpecies.TRICHURIS, 9_999))
    }

    @Test
    fun `trichuris at extreme cutoff is extreme`() {
        assertEquals(InfectivityLevel.EXTREME, InfectivityLevelCalculator.classify(EggSpecies.TRICHURIS, 10_000))
    }

    // --- Hookworm: Low 1-1,999 / Moderate 2,000-3,999 / Extreme >=4,000 ---

    @Test
    fun `hookworm just below moderate cutoff is low`() {
        assertEquals(InfectivityLevel.LOW, InfectivityLevelCalculator.classify(EggSpecies.HOOKWORM, 1_999))
    }

    @Test
    fun `hookworm at moderate cutoff is moderate`() {
        assertEquals(InfectivityLevel.MODERATE, InfectivityLevelCalculator.classify(EggSpecies.HOOKWORM, 2_000))
    }

    @Test
    fun `hookworm just below extreme cutoff is moderate`() {
        assertEquals(InfectivityLevel.MODERATE, InfectivityLevelCalculator.classify(EggSpecies.HOOKWORM, 3_999))
    }

    @Test
    fun `hookworm at extreme cutoff is extreme`() {
        assertEquals(InfectivityLevel.EXTREME, InfectivityLevelCalculator.classify(EggSpecies.HOOKWORM, 4_000))
    }

    // --- Zero / negative / OTHER exclusions ---

    @Test
    fun `zero epg is null, not low`() {
        assertNull(InfectivityLevelCalculator.classify(EggSpecies.ASCARIS, 0))
    }

    @Test
    fun `negative epg is null`() {
        assertNull(InfectivityLevelCalculator.classify(EggSpecies.ASCARIS, -5))
    }

    @Test
    fun `OTHER species is always null regardless of epg`() {
        assertNull(InfectivityLevelCalculator.classify(EggSpecies.OTHER, 1_000_000))
    }

    // --- sessionLevel: max across species, exclusions, all-zero ---

    @Test
    fun `sessionLevel returns the highest tier across recognized species`() {
        val perSpecies = mapOf(
            EggSpecies.ASCARIS to 100, // Low
            EggSpecies.TRICHURIS to 10_000, // Extreme
            EggSpecies.HOOKWORM to 2_500, // Moderate
        )
        assertEquals(InfectivityLevel.EXTREME, InfectivityLevelCalculator.sessionLevel(perSpecies))
    }

    @Test
    fun `sessionLevel excludes OTHER from consideration`() {
        val perSpecies = mapOf(
            EggSpecies.OTHER to 1_000_000,
            EggSpecies.ASCARIS to 100, // Low
        )
        assertEquals(InfectivityLevel.LOW, InfectivityLevelCalculator.sessionLevel(perSpecies))
    }

    @Test
    fun `sessionLevel is null when all species are zero`() {
        val perSpecies = mapOf(
            EggSpecies.ASCARIS to 0,
            EggSpecies.TRICHURIS to 0,
        )
        assertNull(InfectivityLevelCalculator.sessionLevel(perSpecies))
    }

    @Test
    fun `sessionLevel is null when no species classifiable`() {
        assertNull(InfectivityLevelCalculator.sessionLevel(mapOf(EggSpecies.OTHER to 500)))
    }

    @Test
    fun `sessionLevel is null for an empty map`() {
        assertNull(InfectivityLevelCalculator.sessionLevel(emptyMap()))
    }

    @Test
    fun `sessionLevel compares tiers not raw epg magnitude`() {
        // Hookworm at 4,000 is EXTREME (its own cutoff) despite a lower raw epg than the
        // Ascaris reading, which is only MODERATE at 6,000. A raw-epg comparison would wrongly
        // pick Ascaris/MODERATE; the correct answer compares tiers.
        val perSpecies = mapOf(
            EggSpecies.HOOKWORM to 4_000, // Extreme, lower raw epg
            EggSpecies.ASCARIS to 6_000, // Moderate, higher raw epg
        )
        assertEquals(InfectivityLevel.EXTREME, InfectivityLevelCalculator.sessionLevel(perSpecies))
    }

    @Test
    fun `epg of 1 is the lowest possible low tier for every mapped species`() {
        assertEquals(InfectivityLevel.LOW, InfectivityLevelCalculator.classify(EggSpecies.ASCARIS, 1))
        assertEquals(InfectivityLevel.LOW, InfectivityLevelCalculator.classify(EggSpecies.TRICHURIS, 1))
        assertEquals(InfectivityLevel.LOW, InfectivityLevelCalculator.classify(EggSpecies.HOOKWORM, 1))
    }
}
