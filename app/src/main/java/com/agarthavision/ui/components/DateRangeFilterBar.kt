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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
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
        SingleDatePickerDialog(
            initialDate = initial,
            onDismiss = { editing = null },
            onConfirm = { picked ->
                val (newStart, newEnd) = when (end) {
                    RangeEnd.START -> picked to (endDate?.takeIf { it >= picked } ?: picked)
                    RangeEnd.END -> (startDate?.takeIf { it <= picked } ?: picked) to picked
                }
                onRangeSelected(newStart, newEnd)
                editing = null
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
        // Calendar icon — same SVG path convention as SessionsScreen / SvgIcon.kt
        SvgIcon(
            pathData = "M8 2v4M16 2v4M3 10h18M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z",
            color = textColor,
            strokeWidth = 1.5f,
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
 * Standard Material3 single-month calendar dialog. Confirm is a no-op when nothing is
 * selected so the caller never receives a null date.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SingleDatePickerDialog(
    initialDate: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate?.toUtcMillis(),
    )
    // Material3 defaults dialogs to shapes.extraLarge, which is this app's
    // 999.dp pill token - the picker renders as an ellipse without this.
    DatePickerDialog(
        onDismissRequest = onDismiss,
        shape = DialogShape,
        confirmButton = {
            TextButton(
                enabled = pickerState.selectedDateMillis != null,
                onClick = {
                    pickerState.selectedDateMillis
                        ?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                        ?.let(onConfirm)
                },
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    ) {
        DatePicker(state = pickerState)
    }
}

private val ChipDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

/** Converts a [LocalDate] to UTC-midnight epoch millis for [rememberDatePickerState]. */
private fun LocalDate.toUtcMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
