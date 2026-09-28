package com.agarthavision.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.agarthavision.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agarthavision.ui.icons.AgarthaIcons
import com.agarthavision.ui.icons.DateRange
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.DialogShape
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Which end of the range a picker dialog is editing. */
private enum class RangeEnd { START, END }

/**
 * Two chips ("From" / "To") that bound an inclusive date range. Each opens a standard
 * single-month [DatePicker] — the month grid with ‹ › arrows — rather than the
 * vertically scrolling [androidx.compose.material3.DateRangePicker]. A trailing ×
 * chip clears both ends.
 *
 * The range is always kept well-formed for the DAO (start ⇒ end non-null, start ≤ end):
 * picking one end when the other is unset mirrors it; picking one end past the other
 * drags the other along.
 *
 * The composable is stateless — the only mutable state is which dialog is open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeFilterBar(
    startDate: LocalDate?,
    endDate: LocalDate?,
    onRangeSelected: (LocalDate?, LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<RangeEnd?>(null) }
    val hasRange = startDate != null || endDate != null

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DateChip(
            prefix = "From",
            date = startDate,
            onClick = { editing = RangeEnd.START },
        )
        DateChip(
            prefix = "To",
            date = endDate,
            onClick = { editing = RangeEnd.END },
        )
        if (hasRange) {
            ClearChip(onClick = { onRangeSelected(null, null) })
        }
    }

    editing?.let { end ->
        val initial = if (end == RangeEnd.START) startDate ?: endDate else endDate ?: startDate
        val dialogTitle = if (end == RangeEnd.START) "Select start date" else "Select end date"
        SingleDatePickerDialog(
            title = dialogTitle,
            initialDate = initial,
            onDismiss = { editing = null },
            onConfirm = { picked ->
                when (end) {
                    RangeEnd.START -> {
                        val newEnd = endDate?.takeIf { it >= picked } ?: picked
                        onRangeSelected(picked, newEnd)
                        editing = RangeEnd.END
                    }
                    RangeEnd.END -> {
                        val newStart = startDate?.takeIf { it <= picked } ?: picked
                        onRangeSelected(newStart, picked)
                        editing = null
                    }
                }
            },
        )
    }
}

@Composable
private fun DateChip(
    prefix: String,
    date: LocalDate?,
    onClick: () -> Unit,
) {
    val colors = AgarthaTheme.colors
    val isSet = date != null
    val bg = if (isSet) colors.textPrimary else colors.surface
    val border = if (isSet) colors.textPrimary else colors.borderStrong
    val textColor = if (isSet) colors.background else colors.textSecondary
    val label = date?.format(ChipDateFormat)?.let { "$prefix $it" } ?: prefix

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg, RoundedCornerShape(999.dp))
            .border(1.dp, border, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = AgarthaIcons.DateRange,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = textColor)
    }
}

@Composable
private fun ClearChip(onClick: () -> Unit) {
    val colors = AgarthaTheme.colors
    Text(
        text = "×",
        style = MaterialTheme.typography.labelMedium,
        color = colors.textSecondary,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, colors.borderStrong, RoundedCornerShape(999.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 7.dp),
    )
}

/**
 * Standard Material3 single-month calendar dialog with high-contrast buttons and title.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SingleDatePickerDialog(
    title: String,
    initialDate: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val colors = AgarthaTheme.colors
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate?.toUtcMillis(),
        selectableDates = remember { PastOrTodayDates(LocalDate.now()) },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        shape = DialogShape,
        colors = androidx.compose.material3.DatePickerDefaults.colors(
            containerColor = colors.surfaceHigh,
        ),
        confirmButton = {
            val isEnabled = pickerState.selectedDateMillis != null
            TextButton(
                enabled = isEnabled,
                onClick = {
                    pickerState.selectedDateMillis
                        ?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                        ?.let(onConfirm)
                },
            ) {
                Text(
                    text = stringResource(R.string.date_range_picker_ok),
                    color = if (isEnabled) {
                        if (colors.isDark) colors.accent else colors.brandFill
                    } else {
                        colors.textTertiary
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.date_range_picker_cancel),
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                )
            }
        },
    ) {
        DatePicker(
            state = pickerState,
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(start = 24.dp, top = 20.dp, end = 24.dp),
                )
            },
            colors = androidx.compose.material3.DatePickerDefaults.colors(
                containerColor = colors.surfaceHigh,
                titleContentColor = colors.textPrimary,
                headlineContentColor = colors.textPrimary,
                weekdayContentColor = colors.textSecondary,
                subheadContentColor = colors.textPrimary,
                yearContentColor = colors.textPrimary,
                currentYearContentColor = colors.accent,
                selectedYearContentColor = colors.onBrandFill,
                selectedYearContainerColor = colors.brandFill,
                dayContentColor = colors.textPrimary,
                selectedDayContentColor = colors.onBrandFill,
                selectedDayContainerColor = colors.brandFill,
                todayContentColor = colors.accent,
                todayDateBorderColor = colors.accent,
            ),
        )
    }
}

/**
 * Greys out every day after [today] in the calendar grid so a future date cannot be
 * picked at all. The ViewModels clamp as well, so this is belt-and-braces.
 */
@OptIn(ExperimentalMaterial3Api::class)
private class PastOrTodayDates(private val today: LocalDate) : SelectableDates {
    private val todayUtcMillis = today.toUtcMillis()
    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayUtcMillis
    override fun isSelectableYear(year: Int): Boolean = year <= today.year
}

private val ChipDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

/** Converts a [LocalDate] to UTC-midnight epoch millis for [rememberDatePickerState]. */
private fun LocalDate.toUtcMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
