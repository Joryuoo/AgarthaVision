package com.agarthavision.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZonedDateTime

class HomePeriodTest {

    private val zone = CLINICAL_ZONE
    private val fixedInstant = Instant.parse("2026-09-27T10:00:00Z") // 18:00 Manila time

    @Test
    fun `window bounds for each period match expected day spans`() {
        val millisPerDay = 24 * 60 * 60 * 1000L

        for (period in HomePeriod.entries) {
            val windows = period.windows(fixedInstant, zone)
            val expectedDuration = period.days * millisPerDay

            assertEquals(expectedDuration, windows.current.endMillis - windows.current.startMillis)
            assertEquals(expectedDuration, windows.previous.endMillis - windows.previous.startMillis)
            assertEquals(windows.current.startMillis, windows.previous.endMillis)
        }
    }

    @Test
    fun `buckets are contiguous and size 7 for all periods`() {
        for (period in HomePeriod.entries) {
            val windows = period.windows(fixedInstant, zone)
            assertEquals(7, windows.buckets.size)

            for (i in 0..5) {
                assertEquals(
                    "Bucket $i end must equal bucket ${i + 1} start for $period",
                    windows.buckets[i].endMillis,
                    windows.buckets[i + 1].startMillis,
                )
            }
        }
    }

    @Test
    fun `buckets cover current window for 7 and 30 days and end at current end for today`() {
        val todayWindows = HomePeriod.TODAY.windows(fixedInstant, zone)
        assertEquals(todayWindows.current.endMillis, todayWindows.buckets.last().endMillis)

        val sevenDayWindows = HomePeriod.LAST_7_DAYS.windows(fixedInstant, zone)
        assertEquals(sevenDayWindows.current.startMillis, sevenDayWindows.buckets.first().startMillis)
        assertEquals(sevenDayWindows.current.endMillis, sevenDayWindows.buckets.last().endMillis)

        val thirtyDayWindows = HomePeriod.LAST_30_DAYS.windows(fixedInstant, zone)
        assertEquals(thirtyDayWindows.current.startMillis, thirtyDayWindows.buckets.first().startMillis)
        assertEquals(thirtyDayWindows.current.endMillis, thirtyDayWindows.buckets.last().endMillis)
    }

    @Test
    fun `manila midnight boundary transitions correctly`() {
        val justBeforeMidnight = ZonedDateTime.of(2026, 9, 27, 23, 59, 50, 0, zone).toInstant()
        val justAfterMidnight = ZonedDateTime.of(2026, 9, 28, 0, 0, 10, 0, zone).toInstant()

        val beforeWindows = HomePeriod.TODAY.windows(justBeforeMidnight, zone)
        val afterWindows = HomePeriod.TODAY.windows(justAfterMidnight, zone)

        val millisPerDay = 24 * 60 * 60 * 1000L
        assertEquals(millisPerDay, afterWindows.current.startMillis - beforeWindows.current.startMillis)
        assertEquals(millisPerDay, afterWindows.current.endMillis - beforeWindows.current.endMillis)
        assertEquals(beforeWindows.current.endMillis, afterWindows.current.startMillis)
    }
}
