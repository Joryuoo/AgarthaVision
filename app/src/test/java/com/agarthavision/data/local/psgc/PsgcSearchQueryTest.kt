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

    @Test
    fun `comma splits terms across segments`() {
        val parsed = PsgcSearchQuery.parse("lahug, city of cebu")
        assertEquals(listOf("lahug", "city", "of", "cebu"), parsed.terms)
    }

    @Test
    fun `comma narrows name ranking to the first segment`() {
        val parsed = PsgcSearchQuery.parse("lahug, city of cebu")
        assertEquals(listOf("lahug"), parsed.nameTerms)
    }

    @Test
    fun `no comma leaves name terms equal to terms`() {
        val parsed = PsgcSearchQuery.parse("lahug cebu")
        assertEquals(listOf("lahug", "cebu"), parsed.terms)
        assertEquals(parsed.terms, parsed.nameTerms)
    }

    @Test
    fun `leading comma falls back to all terms for ranking`() {
        val parsed = PsgcSearchQuery.parse(", cebu")
        assertEquals(listOf("cebu"), parsed.terms)
        assertEquals(parsed.terms, parsed.nameTerms)
    }

    @Test
    fun `trailing comma drops the empty segment`() {
        val parsed = PsgcSearchQuery.parse("lahug,")
        assertEquals(listOf("lahug"), parsed.terms)
        assertEquals(listOf("lahug"), parsed.nameTerms)
    }

    @Test
    fun `only commas yields nothing`() {
        val parsed = PsgcSearchQuery.parse(",")
        assertTrue(parsed.terms.isEmpty())
        assertTrue(parsed.nameTerms.isEmpty())
    }

    @Test
    fun `escaping still applies within comma segments`() {
        val parsed = PsgcSearchQuery.parse("100%, 50_50")
        assertEquals(listOf("100\\%", "50\\_50"), parsed.terms)
    }

    @Test
    fun `consecutive commas produce empty segments that contribute nothing`() {
        val parsed = PsgcSearchQuery.parse("lahug,,cebu")
        assertEquals(listOf("lahug", "cebu"), parsed.terms)
        assertEquals(listOf("lahug"), parsed.nameTerms)
    }

    @Test
    fun `whitespace-only segment before the first comma falls back for name ranking`() {
        val parsed = PsgcSearchQuery.parse("   , cebu")
        assertEquals(listOf("cebu"), parsed.terms)
        assertEquals(parsed.terms, parsed.nameTerms)
    }

    @Test
    fun `commas separated only by whitespace yield nothing`() {
        val parsed = PsgcSearchQuery.parse(" , , ")
        assertTrue(parsed.terms.isEmpty())
        assertTrue(parsed.nameTerms.isEmpty())
    }

    @Test
    fun `unicode segment before a comma narrows name ranking`() {
        val parsed = PsgcSearchQuery.parse("SANTO NIÑO, city of cebu")
        assertEquals(listOf("santo", "niño"), parsed.nameTerms)
        assertEquals(listOf("santo", "niño", "city", "of", "cebu"), parsed.terms)
    }

    @Test
    fun `like wildcards in the name segment are escaped in name terms too`() {
        val parsed = PsgcSearchQuery.parse("100%_pure, cebu")
        assertEquals(listOf("100\\%\\_pure"), parsed.nameTerms)
    }
}
