package com.agarthavision.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agarthavision.R
import com.agarthavision.ui.components.DateRangeFilterBar
import com.agarthavision.ui.theme.AgarthaTheme
import java.time.LocalDate
import java.time.ZoneId

/**
 * The "generate patient report" sheet: a date-range filter plus a checklist of the patient's
 * sessions. Only sessions with at least one verified sample, inside the current date range, start
 * selected — a session with none can never contribute a finding (D6), and one outside the range
 * would silently be left out of generation regardless of its checkbox, so neither is ever shown
 * checked. Both cases are shown, disabled, with the reason next to the date. Generate is disabled
 * while the selection is empty or a generation is already in flight.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PatientReportSheet(
    sheet: PatientReportSheetState,
    onDismiss: () -> Unit,
    onDateRangeSelected: (LocalDate?, LocalDate?) -> Unit,
    onToggleSession: (String) -> Unit,
    onGenerate: () -> Unit,
) {
    val colors = AgarthaTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceHigh,
        // Same drag handle and corner radius as every other sheet in the app. Without an explicit
        // shape Material3 uses `shapes.extraLarge`, which is the 999.dp pill token in this theme.
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 8.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(colors.borderStrong, RoundedCornerShape(2.dp)),
            )
        },
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            PatientReportSheetHeader(onDismiss)

            Spacer(Modifier.height(8.dp))
            DateRangeFilterBar(
                startDate = sheet.startDate,
                endDate = sheet.endDate,
                onRangeSelected = onDateRangeSelected,
            )

            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.patient_report_sheet_sessions_title),
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary,
            )

            PatientReportSessionList(sheet, onToggleSession)

            sheet.error?.let { error ->
                Spacer(Modifier.height(8.dp))
                Text(text = error, color = colors.danger, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(16.dp))
            PatientReportGenerateButton(sheet, onGenerate)
        }
    }
}

@Composable
private fun PatientReportSheetHeader(onDismiss: () -> Unit) {
    val colors = AgarthaTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.patient_report_sheet_title),
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.patient_report_sheet_close),
                tint = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun PatientReportSessionList(sheet: PatientReportSheetState, onToggleSession: (String) -> Unit) {
    val colors = AgarthaTheme.colors
    when {
        sheet.isLoadingCandidates -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = colors.accent, modifier = Modifier.size(24.dp))
        }
        sheet.candidates.isEmpty() -> Text(
            text = stringResource(R.string.patient_report_sheet_no_candidates),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        else -> Column(modifier = Modifier.heightIn(max = 320.dp)) {
            sheet.candidates.forEach { candidate ->
                PatientReportSessionRow(
                    candidate = candidate,
                    sheet = sheet,
                    isSelected = candidate.session.id in sheet.selectedSessionIds,
                    onToggle = { onToggleSession(candidate.session.id) },
                )
            }
        }
    }
}

/**
 * Whether [startedAtMillis] falls within the sheet's inclusive [startDate]/[endDate], in the
 * device's zone — either bound may be unset. Mirrors the range check
 * `SessionsViewModel.eligibleSessionIds` applies when it derives the selection.
 */
private fun isWithinReportRange(startedAtMillis: Long, startDate: LocalDate?, endDate: LocalDate?): Boolean {
    val zone = ZoneId.systemDefault()
    val startMillis = startDate?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
    val endMillis = endDate?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.minusMillis(1)?.toEpochMilli()
    return (startMillis == null || startedAtMillis >= startMillis) &&
        (endMillis == null || startedAtMillis <= endMillis)
}

@Composable
private fun PatientReportSessionRow(
    candidate: com.agarthavision.domain.usecase.records.PatientReportCandidate,
    sheet: PatientReportSheetState,
    isSelected: Boolean,
    onToggle: () -> Unit,
) {
    val colors = AgarthaTheme.colors
    val hasVerifiedSamples = candidate.verifiedSampleCount > 0
    val inRange = isWithinReportRange(candidate.session.startedAt, sheet.startDate, sheet.endDate)
    val enabled = hasVerifiedSamples && inRange
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(vertical = 4.dp),
    ) {
        Checkbox(checked = isSelected, onCheckedChange = { onToggle() }, enabled = enabled)
        Column(Modifier.weight(1f)) {
            Text(
                text = candidate.session.label ?: candidate.session.id.take(SESSION_ID_TAKE),
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) colors.textPrimary else colors.textSecondary,
            )
            val suffix = when {
                !hasVerifiedSamples -> " · " + stringResource(R.string.patient_report_sheet_session_no_verified)
                !inRange -> " · " + stringResource(R.string.patient_report_sheet_session_out_of_range)
                else -> ""
            }
            Text(
                text = formatDate(candidate.session.startedAt) + suffix,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun PatientReportGenerateButton(sheet: PatientReportSheetState, onGenerate: () -> Unit) {
    val colors = AgarthaTheme.colors
    Button(
        onClick = onGenerate,
        enabled = sheet.selectedSessionIds.isNotEmpty() && !sheet.isGenerating,
        modifier = Modifier
            .fillMaxWidth()
            .height(49.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.brandFill,
            contentColor = colors.onBrandFill,
        ),
    ) {
        if (sheet.isGenerating) {
            CircularProgressIndicator(
                color = colors.onBrandFill,
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                stringResource(R.string.patient_report_sheet_generate_button),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private const val SESSION_ID_TAKE = 8
