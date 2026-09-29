package com.agarthavision.ui.dashboard.coverage

import com.agarthavision.domain.model.SpeciesFinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [foldSpecies] is the pure folding logic behind the province sheet's species breakdown and the
 * coverage card's top-species list — covered here without any Compose dependency.
 */
class CoverageComponentsTest {

    private fun finding(name: String, count: Int, ratio: Float) =
        SpeciesFinding(name = name, count = count, ratio = ratio, formattedPercentage = "${(ratio * 100).toInt()}%")

    @Test
    fun `empty list folds to empty list`() {
        assertEquals(emptyList<SpeciesFinding>(), foldSpecies(emptyList()))
    }

    @Test
    fun `fewer than keep species are all kept, no Other appended`() {
        val species = listOf(
            finding("Ascaris lumbricoides", 10, 0.6f),
            finding("Trichuris trichiura", 5, 0.4f),
        )

        val result = foldSpecies(species, keep = 3)

        assertEquals(species, result)
    }

    @Test
    fun `exactly keep species are all kept, no Other appended`() {
        val species = listOf(
            finding("Ascaris lumbricoides", 10, 0.5f),
            finding("Trichuris trichiura", 5, 0.3f),
            finding("Hookworm", 3, 0.2f),
        )

        val result = foldSpecies(species, keep = 3)

        assertEquals(species, result)
    }

    @Test
    fun `more than keep species fold the remainder into a trailing Other with summed count and ratio`() {
        val species = listOf(
            finding("Ascaris lumbricoides", 10, 0.40f),
            finding("Trichuris trichiura", 5, 0.20f),
            finding("Hookworm", 3, 0.12f),
            finding("Some Unknown Label", 4, 0.16f),
            finding("Another Unknown", 3, 0.12f),
        )

        val result = foldSpecies(species, keep = 3)

        assertEquals(4, result.size)
        assertEquals(listOf("Ascaris lumbricoides", "Trichuris trichiura", "Hookworm"), result.take(3).map { it.name })
        val other = result.last()
        assertEquals("Other", other.name)
        assertEquals(7, other.count)
        assertEquals(0.28f, other.ratio, 0.0001f)
        // formattedPercentage truncates via toInt(), not rounds: 0.28f * 100 = 28.000...% -> "28%"
        assertEquals("28%", other.formattedPercentage)
    }

    @Test
    fun `an unknown or OTHER entry appearing early does not consume a kept slot`() {
        val species = listOf(
            finding("Some Unknown Label", 1, 0.05f),
            finding("Ascaris lumbricoides", 10, 0.50f),
            finding("Trichuris trichiura", 5, 0.25f),
            finding("Hookworm", 4, 0.20f),
        )

        val result = foldSpecies(species, keep = 3)

        // All 3 known species must be kept despite the unknown entry sorting first in the input.
        assertEquals(listOf("Ascaris lumbricoides", "Trichuris trichiura", "Hookworm"), result.take(3).map { it.name })
        val other = result.last()
        assertEquals("Other", other.name)
        assertEquals(1, other.count)
    }

    @Test
    fun `zero otherCount means no Other entry is appended even with unknowns present`() {
        val species = listOf(
            finding("Ascaris lumbricoides", 10, 0.6f),
            finding("Trichuris trichiura", 5, 0.4f),
            finding("Mystery Species", 0, 0f),
        )

        val result = foldSpecies(species, keep = 3)

        assertTrue(result.none { it.name == "Other" })
        assertEquals(2, result.size)
    }

    @Test
    fun `percentage truncates rather than rounds`() {
        // 0.289f * 100 = 28.9 -> toInt() truncates to 28, not 29.
        val species = listOf(
            finding("Ascaris lumbricoides", 1, 0.01f),
            finding("Trichuris trichiura", 1, 0.01f),
            finding("Hookworm", 1, 0.01f),
            finding("Unknown A", 5, 0.289f),
        )

        val result = foldSpecies(species, keep = 3)

        assertEquals("28%", result.last().formattedPercentage)
    }
}
