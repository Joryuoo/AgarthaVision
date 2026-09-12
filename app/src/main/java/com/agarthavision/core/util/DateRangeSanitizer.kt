package com.agarthavision.core.util

import java.time.LocalDate

/**
 * Normalises a user-picked inclusive date range before it reaches a query.
 *
 * Sessions can only have started in the past, so any end in the future is clamped to
 * [today]; a range that ends up inverted after clamping is swapped so `start <= end`
 * always holds. Nulls pass through untouched — a half-open range is the caller's
 * concern (the picker never produces one).
 */
fun sanitizeDateRange(
    start: LocalDate?,
    end: LocalDate?,
    today: LocalDate = LocalDate.now(),
): Pair<LocalDate?, LocalDate?> {
    val clampedStart = start?.coerceAtMost(today)
    val clampedEnd = end?.coerceAtMost(today)
    return if (clampedStart != null && clampedEnd != null && clampedStart > clampedEnd) {
        clampedEnd to clampedStart
    } else {
        clampedStart to clampedEnd
    }
}
