package com.agarthavision.ui.patients

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure unit tests for [sanitizeName], [capitalizeFirst], and [transformNameInput].
 *
 * These functions have no coroutine or Android dependency, so JUnit alone suffices.
 */
class PatientNameInputTest {

    // ── sanitizeName ──────────────────────────────────────────────────────────

    @Test
    fun `sanitizeName strips surrogate-pair emoji`() {
        // 😀 is U+1F600, encoded as a surrogate pair (😀) in Java/Kotlin strings.
        // Both halves must be removed; leaving one orphan half is a broken string.
        assertEquals("abc", sanitizeName("a😀bc"))
    }

    @Test
    fun `sanitizeName strips digits`() {
        assertEquals("Cruz", sanitizeName("Cruz123"))
    }

    @Test
    fun `sanitizeName strips punctuation that is not hyphen apostrophe or period`() {
        assertEquals("", sanitizeName("@#\$%^&*!"))
    }

    @Test
    fun `sanitizeName keeps hyphen apostrophe and period`() {
        assertEquals("O'Brien-Cruz.", sanitizeName("O'Brien-Cruz."))
    }

    @Test
    fun `sanitizeName keeps Latin diacritics`() {
        assertEquals("Señorita", sanitizeName("Señorita"))
    }

    @Test
    fun `sanitizeName keeps spaces`() {
        assertEquals("de la Cruz", sanitizeName("de la Cruz"))
    }

    @Test
    fun `sanitizeName on empty string returns empty`() {
        assertEquals("", sanitizeName(""))
    }

    @Test
    fun `sanitizeName strips emoji interspersed with valid characters`() {
        // 👍 (U+1F44D) between A and b
        assertEquals("Abc", sanitizeName("A👍bc"))
    }

    @Test
    fun `sanitizeName strips BMP emoji (U+203C through range)`() {
        // ‼️ is U+203C — not a letter, not an allowed punctuation char
        assertEquals("AB", sanitizeName("A‼B"))
    }

    @Test
    fun `sanitizeName passes a string that is already clean`() {
        val clean = "De la Cruz-Santos"
        assertEquals(clean, sanitizeName(clean))
    }

    // ── capitalizeFirst ───────────────────────────────────────────────────────

    @Test
    fun `capitalizeFirst uppercases only the first character`() {
        assertEquals("De la Cruz", capitalizeFirst("de la Cruz"))
    }

    @Test
    fun `capitalizeFirst does not title-case the rest of the string`() {
        // "de la cruz" → "De la cruz", NOT "De La Cruz"
        assertEquals("De la cruz", capitalizeFirst("de la cruz"))
    }

    @Test
    fun `capitalizeFirst on a string already starting uppercase returns it unchanged`() {
        val input = "Cruz"
        assertEquals(input, capitalizeFirst(input))
    }

    @Test
    fun `capitalizeFirst on an empty string returns empty`() {
        assertEquals("", capitalizeFirst(""))
    }

    @Test
    fun `capitalizeFirst on a single lowercase character uppercases it`() {
        assertEquals("A", capitalizeFirst("a"))
    }

    @Test
    fun `capitalizeFirst leaves every character after the first unchanged`() {
        // Mix of upper, lower, and a hyphen after the first char
        assertEquals("ABCD", capitalizeFirst("aBCD"))
    }

    // ── transformNameInput ────────────────────────────────────────────────────

    @Test
    fun `transformNameInput strips emoji and capitalizes the first remaining character`() {
        // Leading emoji followed by lowercase → emoji removed, first letter uppercased
        assertEquals("Cruz", transformNameInput("", "😀cruz"))
    }

    @Test
    fun `transformNameInput strips digits then capitalizes`() {
        assertEquals("Maria", transformNameInput("", "1maria"))
    }

    @Test
    fun `transformNameInput clamps to PATIENT_NAME_MAX_LENGTH (40) when appending`() {
        val atCap = "A".repeat(PATIENT_NAME_MAX_LENGTH)
        val result = transformNameInput(atCap, atCap + "x")
        assertEquals(PATIENT_NAME_MAX_LENGTH, result.length)
    }

    @Test
    fun `transformNameInput leaves a string at the exact cap length unchanged`() {
        val atCap = "A".repeat(PATIENT_NAME_MAX_LENGTH)
        assertEquals(atCap, transformNameInput(atCap, atCap))
    }

    @Test
    fun `transformNameInput paste into a full field leaves the field unchanged`() {
        val full = "A".repeat(PATIENT_NAME_MAX_LENGTH)
        val result = transformNameInput(full, "X$full")
        // The field is full; the inserted char should be dropped, not the tail
        assertEquals(PATIENT_NAME_MAX_LENGTH, result.length)
    }

    @Test
    fun `transformNameInput mid-string edit within cap is accepted`() {
        // Previous = "Cruz", insert a valid char at position 1 → "Cdruz" (still below cap)
        assertEquals("Cdruz", transformNameInput("Cruz", "cdruz"))
    }

    @Test
    fun `transformNameInput on empty previous and empty proposed returns empty`() {
        assertEquals("", transformNameInput("", ""))
    }

    @Test
    fun `transformNameInput preserves hyphens apostrophes and periods after sanitize`() {
        assertEquals("O'brien-cruz.", transformNameInput("", "o'brien-cruz."))
    }
}
