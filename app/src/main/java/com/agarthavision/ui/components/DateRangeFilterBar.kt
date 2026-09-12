package com.agarthavision.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
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

/**
 * A chip that labels the active date range and opens a [DatePickerDialog] on tap.
 * When both [startDate] and [endDate] are null the chip reads "All dates"; otherwise
 * the range is formatted as `"d MMM"` (e.g. "12 Aug – 19 Aug"; same-day shows one date).
 * A trailing ×  affordance clears the range without opening the dialog.
 *
 * The composable is stateless — the only mutable state is the dialog-open boolean.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeFilterBar(
    startDate: LocalDate?,
    endDate: LocalDate?,
    onRangeSelected: (LocalDate?, LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    val hasRange = startDate != null || endDate != null
    val bg = if (hasRange) colors.textPrimary else colors.surface
    val border = if (hasRange) colors.textPrimary else colors.borderStrong
    val textColor = if (hasRange) colors.background else colors.textSecondary

    var showDialog by remember { mutableStateOf(false) }

    val label = dateRangeLabel(startDate, endDate)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg, RoundedCornerShape(999.dp))
            .border(1.dp, border, RoundedCornerShape(999.dp))
            .clickable { showDialog = true }
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
        Text(
            text = label,
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            color = textColor,
        )
        if (hasRange) {
            Spacer(Modifier.width(6.dp))
            // Trailing clear affordance — taps clear the range without opening the dialog.
            Text(
                text = "×",
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                color = textColor,
                modifier = Modifier.clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                ) { onRangeSelected(null, null) },
            )
        }
    }

    if (showDialog) {
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = startDate?.toUtcMillis(),
            initialSelectedEndDateMillis = endDate?.toUtcMillis(),
        )
        // Material3 defaults dialogs to shapes.extraLarge, which is this app's
        // 999.dp pill token - the picker renders as an ellipse without this.
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            shape = DialogShape,
            confirmButton = {
                TextButton(
                    onClick = {
                        val start = pickerState.selectedStartDateMillis
                            ?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                        val end = pickerState.selectedEndDateMillis
                            ?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                            ?: start // If only a start is picked, use it for both.
                        onRangeSelected(start, end)
                        showDialog = false
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            },
        ) {
            // weight(1f) keeps the scrolling month list from pushing the buttons off-screen.
            DateRangePicker(state = pickerState, modifier = Modifier.weight(1f))
        }
    }
}

/** Converts a [LocalDate] to UTC-midnight epoch millis for [rememberDateRangePickerState]. */
private fun LocalDate.toUtcMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** Formats a date range pair as a human-readable chip label. */
private fun dateRangeLabel(startDate: LocalDate?, endDate: LocalDate?): String {
    val fmt = DateTimeFormatter.ofPattern("d MMM")
    return when {
        startDate == null && endDate == null -> "All dates"
        startDate == endDate -> startDate?.format(fmt) ?: "All dates"
        else -> buildString {
            startDate?.let { append(it.format(fmt)) }
            append(" – ")
            endDate?.let { append(it.format(fmt)) }
        }
    }
}
