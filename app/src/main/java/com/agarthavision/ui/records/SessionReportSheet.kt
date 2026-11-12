@file:Suppress("LongParameterList", "CyclomaticComplexMethod")

package com.agarthavision.ui.records

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agarthavision.R
import com.agarthavision.domain.model.PatientNameOption
import com.agarthavision.domain.model.ReportConfig
import com.agarthavision.ui.components.LocalAppLocked
import com.agarthavision.ui.icons.AgarthaIcons
import com.agarthavision.ui.icons.Close
import com.agarthavision.ui.theme.AgarthaTheme

data class SessionReportSheetState(
    val step: ReportSheetStep = ReportSheetStep.CONFIGURATION,
    val config: ReportConfig = ReportConfig(),
    val verifiedSampleCount: Int = 0,
    val patientDisplayName: String = "",
    val patientMaskedName: String = "",
    val previewPdfBytes: ByteArray? = null,
    val isLoadingPreview: Boolean = false,
    val isGenerating: Boolean = false,
    val isWarningAcknowledged: Boolean = false,
    val error: String? = null,
    val isPatientCodename: Boolean = false,
) {
    val requiresWarningAck: Boolean get() = verifiedSampleCount < RECOMMENDED_MIN_SAMPLES
    val canGeneratePdf: Boolean get() = !isGenerating && (!requiresWarningAck || isWarningAcknowledged)
    val estimatedPages: Int get() = if (config.includeSampleImages && verifiedSampleCount > 0) {
        1 + ((verifiedSampleCount + 3) / 4)
    } else {
        1
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SessionReportSheetState) return false
        if (step != other.step) return false
        if (config != other.config) return false
        if (verifiedSampleCount != other.verifiedSampleCount) return false
        if (patientDisplayName != other.patientDisplayName) return false
        if (patientMaskedName != other.patientMaskedName) return false
        if (!previewPdfBytes.contentEquals(other.previewPdfBytes)) return false
        if (isLoadingPreview != other.isLoadingPreview) return false
        if (isGenerating != other.isGenerating) return false
        if (isWarningAcknowledged != other.isWarningAcknowledged) return false
        if (error != other.error) return false
        if (isPatientCodename != other.isPatientCodename) return false
        return true
    }

    override fun hashCode(): Int {
        var result = step.hashCode()
        result = 31 * result + config.hashCode()
        result = 31 * result + verifiedSampleCount
        result = 31 * result + patientDisplayName.hashCode()
        result = 31 * result + patientMaskedName.hashCode()
        result = 31 * result + (previewPdfBytes?.contentHashCode() ?: 0)
        result = 31 * result + isLoadingPreview.hashCode()
        result = 31 * result + isGenerating.hashCode()
        result = 31 * result + isWarningAcknowledged.hashCode()
        result = 31 * result + (error?.hashCode() ?: 0)
        result = 31 * result + isPatientCodename.hashCode()
        return result
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionReportSheet(
    state: SessionReportSheetState,
    sessionLabel: String,
    sessionDate: String,
    onDismiss: () -> Unit,
    onConfigChange: (ReportConfig) -> Unit,
    onAcknowledgeWarning: (Boolean) -> Unit,
    onPreview: () -> Unit,
    onBackToConfig: () -> Unit,
    onGeneratePdf: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    if (LocalAppLocked.current) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceHigh,
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
        SessionReportSheetContent(
            state = state,
            sessionLabel = sessionLabel,
            sessionDate = sessionDate,
            onDismiss = onDismiss,
            onConfigChange = onConfigChange,
            onAcknowledgeWarning = onAcknowledgeWarning,
            onPreview = onPreview,
            onBackToConfig = onBackToConfig,
            onGeneratePdf = onGeneratePdf,
        )
    }
}

@Composable
internal fun SessionReportSheetContent(
    state: SessionReportSheetState,
    sessionLabel: String,
    sessionDate: String,
    onDismiss: () -> Unit,
    onConfigChange: (ReportConfig) -> Unit,
    onAcknowledgeWarning: (Boolean) -> Unit,
    onPreview: () -> Unit,
    onBackToConfig: () -> Unit,
    onGeneratePdf: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    var previewCurrentPage by rememberSaveable { mutableIntStateOf(1) }
    var previewTotalPages by rememberSaveable { mutableIntStateOf(state.estimatedPages) }
    val scrollState = rememberScrollState()
    if (LocalAppLocked.current) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(ReportSheetTestTags.SHEET)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        when (state.step) {
            ReportSheetStep.CONFIGURATION -> {
                SessionReportSheetHeader(
                    title = stringResource(R.string.report_sheet_generate_title),
                    subtitle = "$sessionLabel · $sessionDate",
                    onDismiss = onDismiss,
                )

                Spacer(Modifier.height(16.dp))

                val patientNamePreview = when (state.config.patientNameOption) {
                    PatientNameOption.SHOW ->
                        state.patientDisplayName.ifBlank { stringResource(R.string.report_option_name_show) }
                    PatientNameOption.CENSOR ->
                        state.patientMaskedName.ifBlank { stringResource(R.string.report_option_name_censor) }
                    PatientNameOption.EXCLUDE ->
                        stringResource(R.string.report_option_name_excluded)
                }

                ReportOptionsSection(
                    includeImages = state.config.includeSampleImages,
                    onIncludeImagesChange = { onConfigChange(state.config.copy(includeSampleImages = it)) },
                    patientNameOption = state.config.patientNameOption,
                    onPatientNameOptionChange = { onConfigChange(state.config.copy(patientNameOption = it)) },
                    patientNamePreview = patientNamePreview,
                    isPatientCodename = state.isPatientCodename,
                )

                if (state.requiresWarningAck) {
                    Spacer(Modifier.height(12.dp))
                    SampleCountWarningBanner(sampleCount = state.verifiedSampleCount)
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceMuted)
                        .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.report_sheet_pdf_report_label),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.report_sheet_pdf_page_summary,
                            state.estimatedPages,
                            state.estimatedPages,
                        ),
                        fontSize = 13.sp,
                        color = colors.textSecondary,
                    )
                }

                state.error?.let { error ->
                    Spacer(Modifier.height(8.dp))
                    Text(text = error, color = colors.danger, fontSize = 12.sp)
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = onPreview,
                    enabled = !state.isLoadingPreview && state.verifiedSampleCount > 0,
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
                    if (state.isLoadingPreview) {
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

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = colors.textSecondary,
                    ),
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag(ReportSheetTestTags.CANCEL_BUTTON),
                ) {
                    Text(
                        text = stringResource(R.string.report_action_cancel),
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                    )
                }
            }
            ReportSheetStep.PREVIEW -> {
                SessionReportSheetHeader(
                    title = stringResource(R.string.report_preview_title),
                    subtitle = stringResource(
                        R.string.report_preview_subtitle,
                        previewCurrentPage,
                        previewTotalPages,
                    ),
                    onDismiss = onDismiss,
                )

                Spacer(Modifier.height(16.dp))

                PdfPreviewViewport(
                    pdfBytes = state.previewPdfBytes,
                    onPageInfoChanged = { cur, tot ->
                        previewCurrentPage = cur
                        previewTotalPages = tot
                    },
                )

                Spacer(Modifier.height(12.dp))

                ReportPreviewSummaryCard(
                    includeImages = state.config.includeSampleImages,
                    patientNameOption = state.config.patientNameOption,
                )

                if (state.requiresWarningAck) {
                    Spacer(Modifier.height(12.dp))
                    SampleCountAcknowledgmentCard(
                        sampleCount = state.verifiedSampleCount,
                        acknowledged = state.isWarningAcknowledged,
                        onAcknowledgedChange = onAcknowledgeWarning,
                    )
                }

                state.error?.let { error ->
                    Spacer(Modifier.height(8.dp))
                    Text(text = error, color = colors.danger, fontSize = 12.sp)
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = onGeneratePdf,
                    enabled = state.canGeneratePdf,
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
                    if (state.isGenerating) {
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
}

@Composable
private fun SessionReportSheetHeader(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
) {
    val colors = AgarthaTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
            )
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
                .background(colors.surfaceMuted),
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
