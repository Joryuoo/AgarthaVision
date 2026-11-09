package com.agarthavision.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

class RelativeAgeTest {
    private val timestamp = Instant.parse("2026-11-03T16:01:00Z")

    @Test
    fun `future and sub-minute timestamps are just now`() {
        assertAge(Duration.ofSeconds(-1), RelativeAge.JustNow)
        assertAge(Duration.ZERO, RelativeAge.JustNow)
        assertAge(Duration.ofSeconds(59), RelativeAge.JustNow)
    }

    @Test
    fun `elapsed quantities change units at the minute hour and day boundaries`() {
        assertAge(Duration.ofSeconds(60), RelativeAge.Minutes(1))
        assertAge(Duration.ofMinutes(59), RelativeAge.Minutes(59))
        assertAge(Duration.ofMinutes(60), RelativeAge.Hours(1))
        assertAge(Duration.ofHours(23), RelativeAge.Hours(23))
        assertAge(Duration.ofHours(24), RelativeAge.Days(1))
        assertAge(Duration.ofDays(6), RelativeAge.Days(6))
        assertAge(Duration.ofDays(7).minusMillis(1), RelativeAge.Days(6))
    }

    @Test
    fun `ages of at least seven days retain the original instant for the date`() {
        assertAge(Duration.ofDays(7), RelativeAge.Date(timestamp))
        assertAge(Duration.ofDays(8), RelativeAge.Date(timestamp))
    }

    private fun assertAge(elapsed: Duration, expected: RelativeAge) {
        val nowMillis = timestamp.plus(elapsed).toEpochMilli()
        assertEquals(elapsed.toString(), expected, relativeAge(timestamp.toEpochMilli(), nowMillis))
    }
}
