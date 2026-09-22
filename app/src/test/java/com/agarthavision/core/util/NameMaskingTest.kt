package com.agarthavision.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class NameMaskingTest {

    @Test
    fun `maskWord masks with bullets and fixed length`() {
        assertEquals("E••O", NameMasking.maskWord("Escolano"))
        assertEquals("B•N", NameMasking.maskWord("Ben"))
        assertEquals("J••H", NameMasking.maskWord("Joseph"))
        assertEquals("G••D", NameMasking.maskWord("Gerald"))
        assertEquals("C••Z", NameMasking.maskWord("Cruz"))
        assertEquals("J••N", NameMasking.maskWord("John"))
        assertEquals("L•E", NameMasking.maskWord("Lee"))
        assertEquals("L•", NameMasking.maskWord("Li"))
        assertEquals("A", NameMasking.maskWord("A"))
        assertEquals("", NameMasking.maskWord(""))
    }

    @Test
    fun `maskWord preserves initials with dot`() {
        assertEquals("M.", NameMasking.maskWord("M."))
        assertEquals("A.", NameMasking.maskWord("A."))
    }

    @Test
    fun `maskWord handles hyphenated words`() {
        assertEquals("M••Y-J••E", NameMasking.maskWord("Mary-Jane"))
    }

    @Test
    fun `maskName masks full displayName with comma and initial`() {
        assertEquals("E••O, B•N J••H", NameMasking.maskName("Escolano, Ben Joseph"))
        assertEquals("C••Z, G••D M.", NameMasking.maskName("Cruz, Gerald M."))
        assertEquals("C••Z, G••D", NameMasking.maskName("Cruz, Gerald"))
    }

    @Test
    fun `maskName preserves codename unmasked`() {
        assertEquals("VISION-M22-001", NameMasking.maskName("VISION-M22-001"))
        assertEquals("M24-001", NameMasking.maskName("M24-001"))
        assertEquals("F05-002", NameMasking.maskName("F05-002"))
        assertEquals("M00-001", NameMasking.maskName("M00-001"))
    }

    @Test
    fun `maskName handles empty or blank string`() {
        assertEquals("", NameMasking.maskName(""))
        assertEquals("   ", NameMasking.maskName("   "))
    }
}
