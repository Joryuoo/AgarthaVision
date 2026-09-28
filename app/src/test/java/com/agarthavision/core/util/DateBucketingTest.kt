package com.agarthavision.core.util

import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class DateBucketingTest {

    // Fixed "now": 2026-09-28T15:00:00Z, well inside the clinical-zone day.
    private val now = Instant.parse("2026-09-28T15:00:00Z")
    private val zone = ZoneOffset.UTC
    private val todayStart = startOfTodayMillis(now, zone)
    private val sevenDaysAgo = sevenDaysAgoMillis(now)

    @Test
    fun `startOfTodayMillis is midnight in the given zone`() {
        assertEquals(Instant.parse("2026-09-28T00:00:00Z").toEpochMilli(), todayStart)
    }

    @Test
    fun `sevenDaysAgoMillis is exactly seven days before now`() {
        assertEquals(now.toEpochMilli() - 7L * 24 * 60 * 60 * 1000, sevenDaysAgo)
    }

    @Test
    fun `timestamp exactly at todayStart classifies as TODAY`() {
        assertEquals(DateBucket.TODAY, classifyDateBucket(todayStart, todayStart, sevenDaysAgo))
    }

    @Test
    fun `timestamp one millisecond before todayStart classifies as THIS_WEEK`() {
        assertEquals(
            DateBucket.THIS_WEEK,
            classifyDateBucket(todayStart - 1, todayStart, sevenDaysAgo),
        )
    }

    @Test
    fun `timestamp exactly at the seven day boundary classifies as THIS_WEEK`() {
        assertEquals(
            DateBucket.THIS_WEEK,
            classifyDateBucket(sevenDaysAgo, todayStart, sevenDaysAgo),
        )
    }

    @Test
    fun `timestamp one millisecond before the seven day boundary classifies as EARLIER`() {
        assertEquals(
            DateBucket.EARLIER,
            classifyDateBucket(sevenDaysAgo - 1, todayStart, sevenDaysAgo),
        )
    }

    @Test
    fun `timestamp much older than seven days classifies as EARLIER, not dropped`() {
        // Regression test for the bug this file fixes: items older than the 7-day window used to
        // fall through unclassified (and get silently dropped) when no date filter was active.
        val yearOld = now.minusSeconds(365L * 24 * 60 * 60).toEpochMilli()
        assertEquals(DateBucket.EARLIER, classifyDateBucket(yearOld, todayStart, sevenDaysAgo))
    }

    @Test
    fun `future timestamp classifies as TODAY since it is at or after todayStart`() {
        // classifyDateBucket has no upper bound: anything >= todayStartMillis is TODAY, including
        // instants in the future. This matches current intended behavior — there's no "future"
        // bucket, and a clock-skewed or not-yet-synced record should still surface, not disappear.
        val future = now.plusSeconds(60 * 60 * 24 * 30).toEpochMilli()
        assertEquals(DateBucket.TODAY, classifyDateBucket(future, todayStart, sevenDaysAgo))
    }

    @Test
    fun `startOfTodayMillis defaults to the clinical zone`() {
        // Asia/Manila is UTC+8, so 2026-09-28T15:00:00Z is already 2026-09-28T23:00 there —
        // still the same calendar day, so the default-zone boundary should match the UTC one
        // computed above for this particular instant.
        assertEquals(todayStart, startOfTodayMillis(now) + 8L * 60 * 60 * 1000)
    }
}
