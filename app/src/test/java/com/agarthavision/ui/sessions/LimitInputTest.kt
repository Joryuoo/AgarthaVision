package com.agarthavision.ui.sessions

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure tests for [limitInput], the `LengthFilter`-style cap used by the session text fields. */
class LimitInputTest {

    @Test
    fun `proposed within cap passes through untouched`() {
        assertEquals("abc", limitInput(previous = "ab", proposed = "abc", maxLength = 5))
    }

    @Test
    fun `appending past the cap keeps the leading characters`() {
        assertEquals("abcde", limitInput(previous = "abc", proposed = "abcdefg", maxLength = 5))
    }

    @Test
    fun `pasting into an empty field truncates the paste to the cap`() {
        assertEquals("a".repeat(40), limitInput(previous = "", proposed = "a".repeat(45), maxLength = 40))
    }

    @Test
    fun `inserting into a full field leaves it unchanged`() {
        val full = "b".repeat(40)
        assertEquals(full, limitInput(previous = full, proposed = "x$full", maxLength = 40))
        assertEquals(full, limitInput(previous = full, proposed = "${full}x", maxLength = 40))
    }

    @Test
    fun `inserting in the middle keeps the tail and trims the inserted piece`() {
        assertEquals("abXYcd", limitInput(previous = "abcd", proposed = "abXYZcd", maxLength = 6))
    }

    @Test
    fun `replacing a selection is bounded like an insert`() {
        // "abcd" with "bc" selected, replaced by a long paste.
        assertEquals("aXYZWd", limitInput(previous = "abcd", proposed = "aXYZWVUd", maxLength = 6))
    }

    @Test
    fun `deleting never runs into the cap`() {
        assertEquals("ad", limitInput(previous = "abcd", proposed = "ad", maxLength = 2))
    }
}
