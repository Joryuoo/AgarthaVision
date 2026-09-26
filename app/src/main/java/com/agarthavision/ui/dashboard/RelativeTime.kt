package com.agarthavision.ui.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.agarthavision.R
import com.agarthavision.domain.model.CLINICAL_ZONE
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MILLIS_PER_SECOND = 1_000L
private const val MILLIS_PER_MINUTE = 60 * MILLIS_PER_SECOND
private const val MILLIS_PER_HOUR = 60 * MILLIS_PER_MINUTE
private const val MILLIS_PER_DAY = 24 * MILLIS_PER_HOUR
private const val SEVEN_DAYS_MILLIS = 7 * MILLIS_PER_DAY

/**
 * Formats [epochMillis] relative to [nowMillis]:
 * - Under 1 minute: "Just now".
 * - Minutes, hours, and days ago using plurals.
 * - Older than 7 days: a short date (e.g. "MMM d").
 */
@Composable
fun relativeTimeText(epochMillis: Long, nowMillis: Long): String {
    val diff = (nowMillis - epochMillis).coerceAtLeast(0L)
    return when {
        diff < MILLIS_PER_MINUTE -> stringResource(R.string.time_just_now)
        diff < MILLIS_PER_HOUR -> {
            val minutes = (diff / MILLIS_PER_MINUTE).toInt().coerceAtLeast(1)
            pluralStringResource(R.plurals.time_minutes_ago, minutes, minutes)
        }
        diff < MILLIS_PER_DAY -> {
            val hours = (diff / MILLIS_PER_HOUR).toInt().coerceAtLeast(1)
            pluralStringResource(R.plurals.time_hours_ago, hours, hours)
        }
        diff < SEVEN_DAYS_MILLIS -> {
            val days = (diff / MILLIS_PER_DAY).toInt().coerceAtLeast(1)
            pluralStringResource(R.plurals.time_days_ago, days, days)
        }
        else -> {
            val instant = Instant.ofEpochMilli(epochMillis)
            val formatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
                .withZone(CLINICAL_ZONE)
            formatter.format(instant)
        }
    }
}
