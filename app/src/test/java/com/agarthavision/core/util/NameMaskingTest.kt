package com.agarthavision.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class NameMaskingTest {

    @Test
    fun `maskWord masks middle characters`() {
        assertEquals("G****d", NameMasking.maskWord("Gerald"))
        assertEquals("C**z", NameMasking.maskWord("Cruz"))
        assertEquals("J**n", NameMasking.maskWord("John"))
        assertEquals("L*e", NameMasking.maskWord("Lee"))
        assertEquals("L*", NameMasking.maskWord("Li"))
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
        assertEquals("M**y-J**e", NameMasking.maskWord("Mary-Jane"))
    }

    @Test
    fun `maskName masks full displayName with comma and initial`() {
        assertEquals("C**z, G****d M.", NameMasking.maskName("Cruz, Gerald M."))
        assertEquals("C**z, G****d", NameMasking.maskName("Cruz, Gerald"))
    }

    @Test
    fun `maskName preserves codename unmasked`() {
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
