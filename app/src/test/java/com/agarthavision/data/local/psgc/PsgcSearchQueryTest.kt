package com.agarthavision.data.local.psgc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [PsgcSearchQuery] — the folding and escaping rules the barangay search
 * depends on, with no Room in the way.
 */
class PsgcSearchQueryTest {

    @Test
    fun `splits on whitespace so word order does not matter`() {
        assertEquals(listOf("lahug", "cebu"), PsgcSearchQuery.terms("lahug cebu"))
    }

    @Test
    fun `collapses runs of whitespace`() {
        assertEquals(listOf("cebu", "city"), PsgcSearchQuery.terms("  cebu \t city \n "))
    }

    @Test
    fun `folds case including non-ascii`() {
        assertEquals(listOf("santo", "niño"), PsgcSearchQuery.terms("SANTO NIÑO"))
    }

    @Test
    fun `escapes like wildcards so they match literally`() {
        assertEquals(listOf("\\%"), PsgcSearchQuery.terms("%"))
        assertEquals(listOf("\\_"), PsgcSearchQuery.terms("_"))
        assertEquals(listOf("100\\%\\_pure"), PsgcSearchQuery.terms("100%_pure"))
    }

    @Test
    fun `escapes the escape character itself first`() {
        // A lone backslash must not turn the following character into an escape sequence.
        assertEquals(listOf("\\\\"), PsgcSearchQuery.terms("\\"))
    }

    @Test
    fun `yields nothing for a blank query`() {
        assertTrue(PsgcSearchQuery.terms("").isEmpty())
        assertTrue(PsgcSearchQuery.terms("   ").isEmpty())
    }
}
