package com.agarthavision.domain.usecase.home

import com.agarthavision.domain.model.HomePeriod
import com.agarthavision.domain.model.Ratio
import kotlin.math.abs
import kotlin.math.roundToInt

sealed interface Change {
    data object None : Change
    data object Flat : Change
    data class Count(val delta: Int) : Change
    data class Points(val delta: Int) : Change
}

private const val PERCENT_MULTIPLIER = 100.0

fun countChange(current: Int, previous: Int): Change = when {
    previous == 0 -> Change.None
    current == previous -> Change.Flat
    else -> Change.Count(current - previous)
}

fun ratioChange(current: Ratio, previous: Ratio): Change {
    val curVal = current.value
    val prevVal = previous.value
    if (curVal == null || prevVal == null) return Change.None
    val delta = ((curVal - prevVal) * PERCENT_MULTIPLIER).roundToInt()
    return if (delta == 0) Change.Flat else Change.Points(delta)
}

fun Change.badgeText(period: HomePeriod): String = when (this) {
    Change.None -> ""
    Change.Flat -> "Flat"
    is Change.Count -> {
        val sign = if (delta > 0) "+" else if (delta < 0) "−" else ""
        val suffix = when (period) {
            HomePeriod.TODAY -> "day"
            HomePeriod.LAST_7_DAYS -> "wk"
            HomePeriod.LAST_30_DAYS -> "30 d"
        }
        "$sign${abs(delta)} $suffix"
    }
    is Change.Points -> {
        val sign = if (delta > 0) "+" else if (delta < 0) "−" else ""
        "$sign${abs(delta)} pts"
    }
}

private fun previousPeriodLabel(period: HomePeriod): String = when (period) {
    HomePeriod.TODAY -> "the previous day"
    HomePeriod.LAST_7_DAYS -> "the previous 7 days"
    HomePeriod.LAST_30_DAYS -> "the previous 30 days"
}

fun Change.spokenText(period: HomePeriod): String {
    val prevLabel = previousPeriodLabel(period)
    return when (this) {
        Change.None -> ""
        Change.Flat -> "flat versus $prevLabel"
        is Change.Count -> {
            val direction = if (delta > 0) "up" else "down"
            "$direction ${abs(delta)} versus $prevLabel"
        }
        is Change.Points -> {
            val direction = if (delta > 0) "up" else "down"
            "$direction ${abs(delta)} points versus $prevLabel"
        }
    }
}
