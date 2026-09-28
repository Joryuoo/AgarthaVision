package com.agarthavision.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins [escapeLike], which every free-text `LIKE` needle now goes through.
 *
 * The cases that matter are the ones a medtech can actually type into a search box and get a
 * silently wrong result from — not exotic input.
 */
class SqlLikeTest {

    @Test
    fun `an ordinary name passes through untouched`() {
        assertEquals("Dela Cruz", escapeLike("Dela Cruz"))
    }

    @Test
    fun `underscore is escaped so it cannot match a single character`() {
        // Without this, searching "Dela_Cruz" also matches "Dela Cruz", "DelaXCruz" and so on.
        assertEquals("""Dela\_Cruz""", escapeLike("Dela_Cruz"))
    }

    @Test
    fun `percent is escaped so it cannot match everything`() {
        assertEquals("""\%""", escapeLike("%"))
    }

    @Test
    fun `backslash is escaped first, so an escape it introduces is not escaped again`() {
        // Escaping `%` before `\` would turn this into `\\\%` — a literal backslash followed
        // by a live wildcard, which is the bug the ordering exists to prevent.
        assertEquals("""\\\%""", escapeLike("""\%"""))
    }

    @Test
    fun `a blank needle stays blank`() {
        // The patient and species queries short-circuit on blank, and this must not make one.
        assertEquals("", escapeLike(""))
    }
}
