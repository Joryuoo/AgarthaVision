@file:Suppress("LongParameterList", "TooManyFunctions", "CyclomaticComplexMethod")

package com.agarthavision.ui.sessions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agarthavision.R
import com.agarthavision.domain.model.PatientNameOption
import com.agarthavision.domain.model.ReportConfig
import com.agarthavision.domain.usecase.records.PatientReportCandidate
import com.agarthavision.ui.components.LocalAppLocked
import com.agarthavision.ui.components.SingleDatePickerDialog
import com.agarthavision.ui.icons.AgarthaIcons
import com.agarthavision.ui.icons.Check
import com.agarthavision.ui.icons.Close
import com.agarthavision.ui.icons.DateRange
import com.agarthavision.ui.icons.Description
import com.agarthavision.ui.records.PdfPreviewViewport
import com.agarthavision.ui.records.RECOMMENDED_MIN_SAMPLES
import com.agarthavision.ui.records.ReportDiscardConfirmDialog
import com.agarthavision.ui.records.ReportOptionsSection
import com.agarthavision.ui.records.ReportPreviewSummaryCard
import com.agarthavision.ui.records.ReportSheetStep
import com.agarthavision.ui.records.ReportSheetTestTags
import com.agarthavision.ui.records.SampleCountAcknowledgmentCard
import com.agarthavision.ui.records.SampleCountWarningBanner
import com.agarthavision.ui.theme.AgarthaTheme
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object PatientReportSheetTestTags {
    const val SHEET = "patient_report_sheet"
    const val GENERATE_BUTTON = "patient_report_generate_button"
    const val SELECT_ALL = "patient_report_select_all"
}

/** State backing the patient-report bottom sheet reached from this screen (14zcqntj2uz). */
data class PatientReportSheetState(
    val patientDisplayName: String = "",
    val patientMaskedName: String = "",
    val candidates: List<PatientReportCandidate> = emptyList(),
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val selectedIds: Set<String> = emptySet(),
    val step: ReportSheetStep = ReportSheetStep.CONFIGURATION,
    val config: ReportConfig = ReportConfig(),
    val previewPdfBytes: ByteArray? = null,
    val isLoadingPreview: Boolean = false,
    val isWarningAcknowledged: Boolean = false,
    val isGenerating: Boolean = false,
    val isLoadingCandidates: Boolean = false,
    val error: String? = null,
    val selectedSessionIds: Set<String> = selectedIds,
    val isPatientCodename: Boolean = false,
    val initialStartDate: LocalDate? = null,
    val initialEndDate: LocalDate? = null,
    val initialSelectedIds: Set<String>? = null,
    val initialConfig: ReportConfig = ReportConfig(),
    val showDiscardConfirm: Boolean = false,
) {
    val effectiveSelectedIds: Set<String>
        get() = if (selectedIds.isNotEmpty()) selectedIds else selectedSessionIds

    val resolvedInitialSelectedIds: Set<String>
        get() = initialSelectedIds ?: candidates
            .filter { it.verifiedSampleCount > 0 }
            .mapTo(mutableSetOf()) { it.session.id }

    val hasUnsavedChanges: Boolean
        get() = startDate != initialStartDate ||
            endDate != initialEndDate ||
            config != initialConfig ||
            (!isLoadingCandidates && effectiveSelectedIds != resolvedInitialSelectedIds)

    val includedSessionCount: Int
        get() = candidates.count { it.session.id in effectiveSelectedIds }

    val includedSampleCount: Int
        get() = candidates.filter { it.session.id in effectiveSelectedIds }.sumOf { it.verifiedSampleCount }

    val requiresWarningAck: Boolean
        get() = includedSampleCount in 1 until RECOMMENDED_MIN_SAMPLES

    val canGeneratePdf: Boolean
        get() = includedSessionCount > 0 && !isGenerating && (!requiresWarningAck || isWarningAcknowledged)

    val estimatedPages: Int
        get() = if (config.includeSampleImages && includedSampleCount > 0) {
            1 + ((includedSampleCount + 3) / 4)
        } else {
            1
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PatientReportSheetState) return false
        if (patientDisplayName != other.patientDisplayName) return false
        if (patientMaskedName != other.patientMaskedName) return false
        if (candidates != other.candidates) return false
        if (startDate != other.startDate) return false
        if (endDate != other.endDate) return false
        if (selectedIds != other.selectedIds) return false
        if (selectedSessionIds != other.selectedSessionIds) return false
        if (step != other.step) return false
        if (config != other.config) return false
        if (!previewPdfBytes.contentEquals(other.previewPdfBytes)) return false
        if (isLoadingPreview != other.isLoadingPreview) return false
        if (isWarningAcknowledged != other.isWarningAcknowledged) return false
        if (isGenerating != other.isGenerating) return false
        if (isLoadingCandidates != other.isLoadingCandidates) return false
        if (error != other.error) return false
        if (isPatientCodename != other.isPatientCodename) return false
        if (initialStartDate != other.initialStartDate) return false
        if (initialEndDate != other.initialEndDate) return false
        if (initialSelectedIds != other.initialSelectedIds) return false
        if (initialConfig != other.initialConfig) return false
        if (showDiscardConfirm != other.showDiscardConfirm) return false
        return true
    }

    override fun hashCode(): Int {
        var result = patientDisplayName.hashCode()
        result = 31 * result + patientMaskedName.hashCode()
        result = 31 * result + candidates.hashCode()
        result = 31 * result + (startDate?.hashCode() ?: 0)
        result = 31 * result + (endDate?.hashCode() ?: 0)
        result = 31 * result + selectedIds.hashCode()
        result = 31 * result + selectedSessionIds.hashCode()
        result = 31 * result + step.hashCode()
        result = 31 * result + config.hashCode()
        result = 31 * result + (previewPdfBytes?.contentHashCode() ?: 0)
        result = 31 * result + isLoadingPreview.hashCode()
        result = 31 * result + isWarningAcknowledged.hashCode()
        result = 31 * result + isGenerating.hashCode()
        result = 31 * result + isLoadingCandidates.hashCode()
        result = 31 * result + (error?.hashCode() ?: 0)
        result = 31 * result + isPatientCodename.hashCode()
        result = 31 * result + (initialStartDate?.hashCode() ?: 0)
        result = 31 * result + (initialEndDate?.hashCode() ?: 0)
        result = 31 * result + (initialSelectedIds?.hashCode() ?: 0)
        result = 31 * result + initialConfig.hashCode()
        result = 31 * result + showDiscardConfirm.hashCode()
        return result
    }
}

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
    patientName: String? = null,
    onDismiss: () -> Unit,
    onDateRangeSelected: (LocalDate?, LocalDate?) -> Unit,
    onSelectAll: () -> Unit,
    onToggleSession: (String) -> Unit,
    onGenerate: () -> Unit,
    onConfigChange: (ReportConfig) -> Unit = {},
    onAcknowledgeWarning: (Boolean) -> Unit = {},
    onPreview: () -> Unit = {},
    onBackToConfig: () -> Unit = {},
    modifier: Modifier = Modifier,
    onRequestDismiss: () -> Unit = {},
    onDismissDiscard: () -> Unit = {},
) {
    val colors = AgarthaTheme.colors
    var localShowDiscardConfirm by remember { mutableStateOf(false) }
    LaunchedEffect(sheet.hasUnsavedChanges) {
        if (!sheet.hasUnsavedChanges) {
            localShowDiscardConfirm = false
        }
    }
    if (LocalAppLocked.current) return
    val requestDismiss = {
        if (sheet.hasUnsavedChanges) {
            localShowDiscardConfirm = true
            onRequestDismiss()
        } else {
            onDismiss()
        }
    }
    val currentHasUnsavedChanges by rememberUpdatedState(sheet.hasUnsavedChanges)
    val currentRequestDismiss by rememberUpdatedState(requestDismiss)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { targetValue ->
            if (targetValue == SheetValue.Hidden && currentHasUnsavedChanges) {
                currentRequestDismiss()
                false
            } else {
                true
            }
        },
    )

    ModalBottomSheet(
        onDismissRequest = requestDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceHigh,
        // Same drag handle and corner radius as every other sheet in the app.
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 8.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(colors.borderStrong, RoundedCornerShape(2.dp)),
            )
        },
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        modifier = modifier,
    ) {
        PatientReportSheetContent(
            sheet = sheet,
            patientName = patientName,
            onDismiss = onDismiss,
            onDateRangeSelected = onDateRangeSelected,
            onSelectAll = onSelectAll,
            onToggleSession = onToggleSession,
            onGenerate = onGenerate,
            onConfigChange = onConfigChange,
            onAcknowledgeWarning = onAcknowledgeWarning,
            onPreview = onPreview,
            onBackToConfig = onBackToConfig,
            onRequestDismiss = onRequestDismiss,
            onDismissDiscard = onDismissDiscard,
            externalShowDiscardConfirm = localShowDiscardConfirm,
            onExternalShowDiscardConfirmChange = { localShowDiscardConfirm = it },
        )
    }
}

@Composable
internal fun PatientReportSheetContent(
    sheet: PatientReportSheetState,
    patientName: String? = null,
    onDismiss: () -> Unit,
    onDateRangeSelected: (LocalDate?, LocalDate?) -> Unit,
    onSelectAll: () -> Unit,
    onToggleSession: (String) -> Unit,
    onGenerate: () -> Unit,
    onConfigChange: (ReportConfig) -> Unit = {},
    onAcknowledgeWarning: (Boolean) -> Unit = {},
    onPreview: () -> Unit = {},
    onBackToConfig: () -> Unit = {},
    modifier: Modifier = Modifier,
    onRequestDismiss: () -> Unit = {},
    onDismissDiscard: () -> Unit = {},
    externalShowDiscardConfirm: Boolean = false,
    onExternalShowDiscardConfirmChange: (Boolean) -> Unit = {},
) {
    val colors = AgarthaTheme.colors
    var previewCurrentPage by rememberSaveable { mutableIntStateOf(1) }
    var previewTotalPages by rememberSaveable { mutableIntStateOf(sheet.estimatedPages) }
    var internalShowDiscardConfirm by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    LaunchedEffect(sheet.estimatedPages, sheet.previewPdfBytes) {
        previewCurrentPage = 1
        previewTotalPages = sheet.estimatedPages
    }
    LaunchedEffect(sheet.hasUnsavedChanges) {
        if (!sheet.hasUnsavedChanges) {
            internalShowDiscardConfirm = false
        }
    }
    if (LocalAppLocked.current) return
    val requestDismiss = {
        if (sheet.hasUnsavedChanges) {
            internalShowDiscardConfirm = true
            onExternalShowDiscardConfirmChange(true)
            onRequestDismiss()
        } else {
            onDismiss()
        }
    }
    BackHandler(enabled = true, onBack = requestDismiss)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(PatientReportSheetTestTags.SHEET)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        when (sheet.step) {
            ReportSheetStep.CONFIGURATION -> {
                PatientReportSheetHeader(
                    candidateCount = sheet.candidates.size,
                    patientName = patientName,
                    onDismiss = requestDismiss,
                )

                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.patient_report_sheet_date_range),
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                )

                Spacer(Modifier.height(8.dp))
                PatientReportDateCards(
                    startDate = sheet.startDate,
                    endDate = sheet.endDate,
                    onRangeSelected = onDateRangeSelected,
                )

                Spacer(Modifier.height(8.dp))
                PatientReportQuickRanges(
                    startDate = sheet.startDate,
                    endDate = sheet.endDate,
                    onRangeSelected = onDateRangeSelected,
                )

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.patient_report_sheet_sessions_title),
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    )
                    Text(
                        text = stringResource(R.string.patient_report_select_all),
                        color = colors.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(onClick = onSelectAll)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                            .testTag(PatientReportSheetTestTags.SELECT_ALL),
                    )
                }

                Spacer(Modifier.height(8.dp))
                PatientReportSessionList(sheet = sheet, onToggleSession = onToggleSession)

                Spacer(Modifier.height(16.dp))
                val patientDisplayName = sheet.patientDisplayName.ifBlank { patientName.orEmpty() }
                val patientNamePreview = when (sheet.config.patientNameOption) {
                    PatientNameOption.SHOW ->
                        patientDisplayName.ifBlank { stringResource(R.string.report_option_name_show) }
                    PatientNameOption.CENSOR ->
                        sheet.patientMaskedName.ifBlank {
                            patientDisplayName.ifBlank { stringResource(R.string.report_option_name_censor) }
                        }
                    PatientNameOption.EXCLUDE ->
                        stringResource(R.string.report_option_name_excluded)
                }

                ReportOptionsSection(
                    includeImages = sheet.config.includeSampleImages,
                    onIncludeImagesChange = { onConfigChange(sheet.config.copy(includeSampleImages = it)) },
                    patientNameOption = sheet.config.patientNameOption,
                    onPatientNameOptionChange = { onConfigChange(sheet.config.copy(patientNameOption = it)) },
                    patientNamePreview = patientNamePreview,
                    isPatientCodename = sheet.isPatientCodename,
                )

                if (sheet.requiresWarningAck) {
                    Spacer(Modifier.height(12.dp))
                    SampleCountWarningBanner(sampleCount = sheet.includedSampleCount)
                }

                sheet.error?.let { error ->
                    Spacer(Modifier.height(8.dp))
                    Text(text = error, color = colors.danger, fontSize = 12.sp)
                }

                Spacer(Modifier.height(16.dp))
                PatientReportSummaryText(sheet = sheet)

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onPreview,
                    enabled = sheet.includedSessionCount > 0 && !sheet.isLoadingPreview && !sheet.isGenerating,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.brandFill,
                        contentColor = colors.onBrandFill,
                        disabledContainerColor = colors.brandFill.copy(alpha = 0.4f),
                        disabledContentColor = colors.onBrandFill.copy(alpha = 0.7f),
                    ),
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(49.dp)
                        .testTag(ReportSheetTestTags.PREVIEW_BUTTON),
                ) {
                    if (sheet.isLoadingPreview) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = colors.onBrandFill,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.report_action_preview_report),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                PatientReportGenerateButton(sheet = sheet, onGenerate = onGenerate)
            }
            ReportSheetStep.PREVIEW -> {
                PatientReportPreviewHeader(
                    currentPage = previewCurrentPage,
                    totalPages = previewTotalPages,
                    onDismiss = onBackToConfig,
                )

                Spacer(Modifier.height(16.dp))
                PdfPreviewViewport(
                    pdfBytes = sheet.previewPdfBytes,
                    onPageInfoChanged = { cur, tot ->
                        previewCurrentPage = cur
                        previewTotalPages = tot
                    },
                )

                Spacer(Modifier.height(12.dp))
                ReportPreviewSummaryCard(
                    includeImages = sheet.config.includeSampleImages,
                    patientNameOption = sheet.config.patientNameOption,
                )

                if (sheet.requiresWarningAck) {
                    Spacer(Modifier.height(12.dp))
                    SampleCountAcknowledgmentCard(
                        sampleCount = sheet.includedSampleCount,
                        acknowledged = sheet.isWarningAcknowledged,
                        onAcknowledgedChange = onAcknowledgeWarning,
                    )
                }

                sheet.error?.let { error ->
                    Spacer(Modifier.height(8.dp))
                    Text(text = error, color = colors.danger, fontSize = 12.sp)
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onGenerate,
                    enabled = sheet.canGeneratePdf,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.brandFill,
                        contentColor = colors.onBrandFill,
                        disabledContainerColor = colors.brandFill.copy(alpha = 0.4f),
                        disabledContentColor = colors.onBrandFill.copy(alpha = 0.7f),
                    ),
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(49.dp)
                        .testTag(ReportSheetTestTags.GENERATE_PDF_BUTTON),
                ) {
                    if (sheet.isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = colors.onBrandFill,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.report_action_generate_pdf),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onBackToConfig,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = colors.textSecondary,
                    ),
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag(ReportSheetTestTags.EDIT_OPTIONS_BUTTON),
                ) {
                    Text(
                        text = stringResource(R.string.report_action_edit_options),
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }

    if (sheet.showDiscardConfirm || externalShowDiscardConfirm || internalShowDiscardConfirm) {
        ReportDiscardConfirmDialog(
            onConfirm = {
                internalShowDiscardConfirm = false
                onExternalShowDiscardConfirmChange(false)
                onDismiss()
            },
            onDismiss = {
                internalShowDiscardConfirm = false
                onExternalShowDiscardConfirmChange(false)
                onDismissDiscard()
            },
        )
    }
}

@Composable
private fun PatientReportPreviewHeader(
    currentPage: Int,
    totalPages: Int,
    onDismiss: () -> Unit,
) {
    val colors = AgarthaTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.report_preview_title),
                color = colors.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = stringResource(
                    R.string.report_preview_subtitle,
                    currentPage,
                    totalPages,
                ),
                color = colors.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
            )
        }
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(colors.surfaceMuted)
                .testTag(ReportSheetTestTags.HEADER_CLOSE_BUTTON),
        ) {
            Icon(
                imageVector = AgarthaIcons.Close,
                contentDescription = stringResource(R.string.patient_report_sheet_close),
                tint = colors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun PatientReportSheetHeader(
    candidateCount: Int,
    patientName: String?,
    onDismiss: () -> Unit,
) {
    val colors = AgarthaTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.patient_report_sheet_title),
                color = colors.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            val subtitle = if (patientName != null) {
                pluralStringResource(
                    R.plurals.patient_report_sheet_subtitle,
                    candidateCount,
                    patientName,
                    candidateCount,
                )
            } else {
                pluralStringResource(
                    R.plurals.patient_report_sheet_summary_sessions,
                    candidateCount,
                    candidateCount,
                )
            }
            Text(
                text = subtitle,
                color = colors.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
            )
        }
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(colors.surfaceMuted)
                .testTag(ReportSheetTestTags.HEADER_CLOSE_BUTTON),
        ) {
            Icon(
                imageVector = AgarthaIcons.Close,
                contentDescription = stringResource(R.string.patient_report_sheet_close),
                tint = colors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private enum class RangeField { START, END }

@Composable
private fun PatientReportDateCards(
    startDate: LocalDate?,
    endDate: LocalDate?,
    onRangeSelected: (LocalDate?, LocalDate?) -> Unit,
) {
    var editing by remember { mutableStateOf<RangeField?>(null) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DateCard(
            prefix = stringResource(R.string.date_range_from),
            date = startDate,
            onClick = { editing = RangeField.START },
            modifier = Modifier.weight(1f),
        )
        DateCard(
            prefix = stringResource(R.string.date_range_to),
            date = endDate,
            onClick = { editing = RangeField.END },
            modifier = Modifier.weight(1f),
        )
    }

    editing?.let { field ->
        val initial = if (field == RangeField.START) startDate ?: endDate else endDate ?: startDate
        val dialogTitle = stringResource(
            if (field == RangeField.START) R.string.date_range_select_start else R.string.date_range_select_end,
        )
        SingleDatePickerDialog(
            title = dialogTitle,
            initialDate = initial,
            onDismiss = { editing = null },
            onConfirm = { picked ->
                when (field) {
                    RangeField.START -> {
                        val newEnd = endDate?.takeIf { it >= picked } ?: picked
                        onRangeSelected(picked, newEnd)
                        editing = RangeField.END
                    }
                    RangeField.END -> {
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
private fun DateCard(
    prefix: String,
    date: LocalDate?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    val shape = RoundedCornerShape(16.dp)
    val formattedDate = date?.format(ReportDateCardFormat) ?: stringResource(R.string.date_range_select_date)

    Row(
        modifier = modifier
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = AgarthaIcons.DateRange,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = prefix,
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 13.sp,
            )
            Text(
                text = formattedDate,
                color = colors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 20.sp,
            )
        }
    }
}

private val ReportDateCardFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

@Composable
private fun PatientReportQuickRanges(
    startDate: LocalDate?,
    endDate: LocalDate?,
    onRangeSelected: (LocalDate?, LocalDate?) -> Unit,
) {
    val colors = AgarthaTheme.colors
    val today = remember { LocalDate.now() }
    val last30Start = remember(today) { today.minusDays(29) }
    val last7Start = remember(today) { today.minusDays(6) }

    val isAllTime = startDate == null && endDate == null
    val isLast30 = startDate == last30Start && endDate == today
    val isLast7 = startDate == last7Start && endDate == today

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuickRangeChip(
            label = stringResource(R.string.patient_report_range_all_time),
            isActive = isAllTime,
            onClick = { onRangeSelected(null, null) },
            modifier = Modifier.weight(1f),
        )
        QuickRangeChip(
            label = stringResource(R.string.patient_report_range_last_30),
            isActive = isLast30,
            onClick = { onRangeSelected(last30Start, today) },
            modifier = Modifier.weight(1f),
        )
        QuickRangeChip(
            label = stringResource(R.string.patient_report_range_last_7),
            isActive = isLast7,
            onClick = { onRangeSelected(last7Start, today) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun QuickRangeChip(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    val bg = if (isActive) colors.accentTint else colors.surfaceMuted
    val border = if (isActive) colors.accent else colors.border
    val textColor = if (isActive) colors.accent else colors.textSecondary

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
        )
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
            color = colors.textSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        else -> Column(
            modifier = Modifier
                .heightIn(max = 340.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            sheet.candidates.forEach { candidate ->
                PatientReportSessionRow(
                    candidate = candidate,
                    sheet = sheet,
                    isSelected = candidate.session.id in sheet.effectiveSelectedIds,
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
    candidate: PatientReportCandidate,
    sheet: PatientReportSheetState,
    isSelected: Boolean,
    onToggle: () -> Unit,
) {
    val colors = AgarthaTheme.colors
    val hasVerifiedSamples = candidate.verifiedSampleCount > 0
    val inRange = isWithinReportRange(candidate.session.startedAt, sheet.startDate, sheet.endDate)
    val enabled = hasVerifiedSamples && inRange

    val shape = RoundedCornerShape(16.dp)
    val rowBg = when {
        isSelected -> colors.accentTint2
        else -> colors.surface
    }
    val rowBorder = when {
        isSelected -> colors.accent
        else -> colors.border
    }
    val alphaModifier = if (enabled) Modifier else Modifier.alpha(0.55f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(alphaModifier)
            .clip(shape)
            .background(rowBg)
            .border(1.dp, rowBorder, shape)
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        // Custom 24dp checkbox (rounded 7dp)
        CustomReportCheckbox(
            isChecked = isSelected,
            enabled = enabled,
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = candidate.session.label ?: candidate.session.id.take(SESSION_ID_TAKE),
                color = if (enabled) colors.textPrimary else colors.textSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = formatDate(candidate.session.startedAt),
                color = colors.textSecondary,
                fontSize = 12.sp,
            )
        }

        Spacer(Modifier.width(8.dp))

        // Status or sample count badge
        when {
            !hasVerifiedSamples -> {
                ReasonTag(
                    text = stringResource(R.string.patient_report_sheet_session_no_verified),
                    isAmber = true,
                )
            }
            !inRange -> {
                ReasonTag(
                    text = stringResource(R.string.patient_report_sheet_session_out_of_range),
                    isAmber = true,
                )
            }
            else -> {
                ReasonTag(
                    text = pluralStringResource(
                        R.plurals.patient_report_sheet_summary_samples,
                        candidate.verifiedSampleCount,
                        candidate.verifiedSampleCount,
                    ),
                    isAmber = false,
                )
            }
        }
    }
}

@Composable
private fun CustomReportCheckbox(
    isChecked: Boolean,
    enabled: Boolean,
) {
    val colors = AgarthaTheme.colors
    val boxShape = RoundedCornerShape(7.dp)
    val bg = if (isChecked) colors.brandFill else Color.Transparent
    val border = if (isChecked) colors.brandFill else colors.borderStrong

    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(boxShape)
            .background(bg)
            .border(1.5.dp, border, boxShape),
        contentAlignment = Alignment.Center,
    ) {
        if (isChecked) {
            Icon(
                imageVector = AgarthaIcons.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun ReasonTag(
    text: String,
    isAmber: Boolean,
) {
    val colors = AgarthaTheme.colors
    val bg = if (isAmber) colors.warningTint else colors.surfaceMuted
    val textColor = if (isAmber) colors.warningText else colors.textSecondary

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PatientReportSummaryText(sheet: PatientReportSheetState) {
    val colors = AgarthaTheme.colors
    val selectedSessionsCount = sheet.effectiveSelectedIds.size
    val totalSamplesCount = sheet.candidates
        .filter { it.session.id in sheet.effectiveSelectedIds }
        .sumOf { it.verifiedSampleCount }

    val sessionsPart = pluralStringResource(
        R.plurals.patient_report_sheet_summary_sessions,
        selectedSessionsCount,
        selectedSessionsCount,
    )
    val samplesPart = pluralStringResource(
        R.plurals.patient_report_sheet_summary_samples,
        totalSamplesCount,
        totalSamplesCount,
    )
    val suffix = stringResource(R.string.patient_report_sheet_summary_suffix)
    val fullSummary = "$sessionsPart · $samplesPart$suffix"

    Text(
        text = fullSummary,
        color = colors.textSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PatientReportGenerateButton(sheet: PatientReportSheetState, onGenerate: () -> Unit) {
    val colors = AgarthaTheme.colors
    val isEnabled = sheet.effectiveSelectedIds.isNotEmpty() && !sheet.isGenerating
    Button(
        onClick = onGenerate,
        enabled = isEnabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(49.dp)
            .testTag(PatientReportSheetTestTags.GENERATE_BUTTON),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.brandFill,
            contentColor = colors.onBrandFill,
            disabledContainerColor = colors.brandFill.copy(alpha = 0.5f),
            disabledContentColor = colors.onBrandFill.copy(alpha = 0.6f),
        ),
    ) {
        if (sheet.isGenerating) {
            CircularProgressIndicator(
                color = colors.onBrandFill,
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = AgarthaIcons.Description,
                    contentDescription = null,
                    tint = colors.onBrandFill,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.patient_report_sheet_generate_button),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private const val SESSION_ID_TAKE = 8
