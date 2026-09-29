package com.agarthavision.core.util

import com.agarthavision.domain.model.CLINICAL_ZONE
import java.time.Instant
import java.time.ZoneId

/**
 * Shared Today / This Week / Earlier bucketing math for time-stamped list items (reports,
 * patients). Both `RecordsScreen` and `PatientsScreen` used to compute this inline, with each
 * copy drifting from the other and from the "now" the owning ViewModel actually emitted — see
 * ticket investigation for details. This file is the single source of truth for the boundaries
 * and the three-way classification.
 */
enum class DateBucket { TODAY, THIS_WEEK, EARLIER }

private const val SEVEN_DAYS_MILLIS = 7 * 24 * 60 * 60 * 1000L

/**
 * Start-of-day boundary for [now] in [zone] (defaults to the app's clinical zone), as epoch
 * millis. A timestamp at or after this instant is "today".
 */
fun startOfTodayMillis(now: Instant, zone: ZoneId = CLINICAL_ZONE): Long =
    now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

/**
 * Cutoff for "this week", as epoch millis: a rolling 7 days back from [now] — **not** a calendar
 * Monday-Sunday week. That's intentional and matches existing Patients/Reports behavior; it's
 * spelled out here so it doesn't silently regress to a calendar week later.
 */
fun sevenDaysAgoMillis(now: Instant): Long = now.toEpochMilli() - SEVEN_DAYS_MILLIS

/**
 * Classifies [timestampMillis] into [DateBucket.TODAY], [DateBucket.THIS_WEEK], or
 * [DateBucket.EARLIER] given the [todayStartMillis]/[sevenDaysAgoMillis] boundaries (see
 * [startOfTodayMillis] and [sevenDaysAgoMillis]). Every timestamp lands in exactly one bucket —
 * there is no branch that silently drops an item (the bug this replaced).
 */
fun classifyDateBucket(
    timestampMillis: Long,
    todayStartMillis: Long,
    sevenDaysAgoMillis: Long,
): DateBucket = when {
    timestampMillis >= todayStartMillis -> DateBucket.TODAY
    timestampMillis >= sevenDaysAgoMillis -> DateBucket.THIS_WEEK
    else -> DateBucket.EARLIER
}
