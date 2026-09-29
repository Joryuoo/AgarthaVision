package com.agarthavision.domain.model

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class HomePeriod(val days: Int) {
    TODAY(1),
    LAST_7_DAYS(7),
    LAST_30_DAYS(30),
}

data class TimeWindow(val startMillis: Long, val endMillis: Long) // [start, end)

data class PeriodWindows(
    val current: TimeWindow,
    val previous: TimeWindow,
    val buckets: List<TimeWindow>, // buckets.size == 7
)

const val HOME_PERIOD_BUCKET_COUNT = 7
private const val LAST_BUCKET_INDEX = HOME_PERIOD_BUCKET_COUNT - 1

/**
 * Computes the period windows for this [HomePeriod] given [now] and [zone].
 *
 * - current = [start of day (today - (days - 1)), start of tomorrow).
 * - previous = the same length, immediately before current.
 * - buckets:
 *   - TODAY and LAST_7_DAYS: 7 one-day buckets ending today.
 *   - LAST_30_DAYS: 7 equal slices of current.
 * Uses [java.time.ZonedDateTime] so DST-free day math still goes through the zone.
 */
fun HomePeriod.windows(now: Instant, zone: ZoneId): PeriodWindows {
    val zdt = now.atZone(zone)
    val startOfTomorrow = zdt.truncatedTo(ChronoUnit.DAYS).plusDays(1)
    val startOfCurrent = startOfTomorrow.minusDays(days.toLong())

    val currentWindow = TimeWindow(
        startMillis = startOfCurrent.toInstant().toEpochMilli(),
        endMillis = startOfTomorrow.toInstant().toEpochMilli(),
    )

    val currentDurationMillis = currentWindow.endMillis - currentWindow.startMillis
    val previousWindow = TimeWindow(
        startMillis = currentWindow.startMillis - currentDurationMillis,
        endMillis = currentWindow.startMillis,
    )

    val buckets = when (this) {
        HomePeriod.TODAY,
        HomePeriod.LAST_7_DAYS -> {
            (LAST_BUCKET_INDEX downTo 0).map { daysAgo ->
                val bucketStart = startOfTomorrow.minusDays((daysAgo + 1).toLong())
                val bucketEnd = startOfTomorrow.minusDays(daysAgo.toLong())
                TimeWindow(
                    startMillis = bucketStart.toInstant().toEpochMilli(),
                    endMillis = bucketEnd.toInstant().toEpochMilli(),
                )
            }
        }
        HomePeriod.LAST_30_DAYS -> {
            val totalSpan = currentDurationMillis.toDouble()
            val bucketDivisor = HOME_PERIOD_BUCKET_COUNT.toDouble()
            (0 until HOME_PERIOD_BUCKET_COUNT).map { index ->
                val sliceStart = (currentWindow.startMillis + (index * totalSpan / bucketDivisor)).toLong()
                val sliceEnd = if (index == LAST_BUCKET_INDEX) {
                    currentWindow.endMillis
                } else {
                    (currentWindow.startMillis + ((index + 1) * totalSpan / bucketDivisor)).toLong()
                }
                TimeWindow(startMillis = sliceStart, endMillis = sliceEnd)
            }
        }
    }

    require(buckets.size == HOME_PERIOD_BUCKET_COUNT) { "Buckets must have size $HOME_PERIOD_BUCKET_COUNT" }

    return PeriodWindows(
        current = currentWindow,
        previous = previousWindow,
        buckets = buckets,
    )
}
