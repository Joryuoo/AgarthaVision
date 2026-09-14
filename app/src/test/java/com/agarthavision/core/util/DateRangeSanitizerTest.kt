package com.agarthavision.core.util

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DateRangeSanitizerTest {

    private val today = LocalDate.of(2026, 9, 13)

    @Test
    fun `past range passes through unchanged`() {
        val start = LocalDate.of(2026, 8, 12)
        val end = LocalDate.of(2026, 8, 19)
        assertEquals(start to end, sanitizeDateRange(start, end, today))
    }

    @Test
    fun `today is allowed at both ends`() {
        assertEquals(today to today, sanitizeDateRange(today, today, today))
    }

    @Test
    fun `future end is clamped to today`() {
        val start = LocalDate.of(2026, 9, 1)
        assertEquals(start to today, sanitizeDateRange(start, LocalDate.of(2026, 12, 25), today))
    }

    @Test
    fun `future start and end both collapse to today`() {
        val future = LocalDate.of(2027, 1, 1)
        assertEquals(today to today, sanitizeDateRange(future, future.plusDays(5), today))
    }

    @Test
    fun `inverted range is swapped so start is not after end`() {
        val early = LocalDate.of(2026, 8, 1)
        val late = LocalDate.of(2026, 8, 31)
        assertEquals(early to late, sanitizeDateRange(late, early, today))
    }

    @Test
    fun `nulls pass through untouched`() {
        assertEquals(null to null, sanitizeDateRange(null, null, today))
        val past = LocalDate.of(2026, 8, 1)
        assertEquals(past to null, sanitizeDateRange(past, null, today))
        assertEquals(null to past, sanitizeDateRange(null, past, today))
    }

    @Test
    fun `future start with null end is clamped without inventing an end`() {
        assertEquals(today to null, sanitizeDateRange(LocalDate.of(2030, 1, 1), null, today))
    }
}
