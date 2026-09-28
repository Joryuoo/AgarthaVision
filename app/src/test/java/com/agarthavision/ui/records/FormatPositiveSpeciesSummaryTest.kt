package com.agarthavision.ui.records

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatPositiveSpeciesSummaryTest {

    private val emptyFallback = "No positive species"
    private val moreFormat = "+%d more"

    @Test
    fun `empty list returns emptyFallback`() {
        val result = formatPositiveSpeciesSummary(emptyList(), emptyFallback, moreFormat)
        assertEquals("No positive species", result)
    }

    @Test
    fun `one species returns single item`() {
        val result = formatPositiveSpeciesSummary(
            listOf("Ascaris lumbricoides"),
            emptyFallback,
            moreFormat,
        )
        assertEquals("Ascaris lumbricoides", result)
    }

    @Test
    fun `two species returns both comma separated`() {
        val result = formatPositiveSpeciesSummary(
            listOf("Ascaris lumbricoides", "Trichuris trichiura"),
            emptyFallback,
            moreFormat,
        )
        assertEquals("Ascaris lumbricoides, Trichuris trichiura", result)
    }

    @Test
    fun `three species returns all three comma separated`() {
        val result = formatPositiveSpeciesSummary(
            listOf("Ascaris lumbricoides", "Trichuris trichiura", "Hookworm"),
            emptyFallback,
            moreFormat,
        )
        assertEquals("Ascaris lumbricoides, Trichuris trichiura, Hookworm", result)
    }

    @Test
    fun `four species returns first two plus count remaining`() {
        val result = formatPositiveSpeciesSummary(
            listOf("Ascaris lumbricoides", "Trichuris trichiura", "Hookworm", "Schistosoma japonicum"),
            emptyFallback,
            moreFormat,
        )
        assertEquals("Ascaris lumbricoides, Trichuris trichiura, +2 more", result)
    }

    @Test
    fun `five species returns first two plus count remaining`() {
        val result = formatPositiveSpeciesSummary(
            listOf("Ascaris", "Trichuris", "Hookworm", "Schistosoma", "Enterobius"),
            emptyFallback,
            moreFormat,
        )
        assertEquals("Ascaris, Trichuris, +3 more", result)
    }
}
