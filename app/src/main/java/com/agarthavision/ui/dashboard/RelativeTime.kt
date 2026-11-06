package com.agarthavision.ui.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

enum class RelativeTimeStyle { Long, Short }

internal sealed interface RelativeAge {
    data object JustNow : RelativeAge
    data class Minutes(val count: Int) : RelativeAge
    data class Hours(val count: Int) : RelativeAge
    data class Days(val count: Int) : RelativeAge
    data class Date(val instant: Instant) : RelativeAge
}

internal fun relativeAge(epochMillis: Long, nowMillis: Long): RelativeAge {
    val diff = (nowMillis - epochMillis).coerceAtLeast(0L)
    return when {
        diff < MILLIS_PER_MINUTE -> RelativeAge.JustNow
        diff < MILLIS_PER_HOUR -> RelativeAge.Minutes((diff / MILLIS_PER_MINUTE).toInt())
        diff < MILLIS_PER_DAY -> RelativeAge.Hours((diff / MILLIS_PER_HOUR).toInt())
        diff < SEVEN_DAYS_MILLIS -> RelativeAge.Days((diff / MILLIS_PER_DAY).toInt())
        else -> RelativeAge.Date(Instant.ofEpochMilli(epochMillis))
    }
}

/**
 * Formats [epochMillis] relative to [nowMillis] in long or compact form.
 * Ages of at least seven days use a short date in the clinical time zone.
 */
@Composable
fun relativeTimeText(
    epochMillis: Long,
    nowMillis: Long,
    style: RelativeTimeStyle = RelativeTimeStyle.Long,
): String = when (val age = relativeAge(epochMillis, nowMillis)) {
    RelativeAge.JustNow -> stringResource(R.string.time_just_now)
    is RelativeAge.Minutes -> when (style) {
        RelativeTimeStyle.Long -> pluralStringResource(R.plurals.time_minutes_ago, age.count, age.count)
        RelativeTimeStyle.Short -> stringResource(R.string.time_short_minutes_ago, age.count)
    }
    is RelativeAge.Hours -> when (style) {
        RelativeTimeStyle.Long -> pluralStringResource(R.plurals.time_hours_ago, age.count, age.count)
        RelativeTimeStyle.Short -> stringResource(R.string.time_short_hours_ago, age.count)
    }
    is RelativeAge.Days -> when (style) {
        RelativeTimeStyle.Long -> pluralStringResource(R.plurals.time_days_ago, age.count, age.count)
        RelativeTimeStyle.Short -> stringResource(R.string.time_short_days_ago, age.count)
    }
    is RelativeAge.Date -> {
        val locale = Locale.getDefault()
        val formatter = remember(locale) {
            DateTimeFormatter.ofPattern("MMM d", locale).withZone(CLINICAL_ZONE)
        }
        formatter.format(age.instant)
    }
}

