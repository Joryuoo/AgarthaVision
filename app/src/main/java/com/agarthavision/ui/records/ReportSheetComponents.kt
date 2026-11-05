@file:Suppress("LongParameterList")

package com.agarthavision.ui.records

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agarthavision.R
import com.agarthavision.domain.model.PatientNameOption
import com.agarthavision.ui.icons.AgarthaIcons
import com.agarthavision.ui.icons.Description
import com.agarthavision.ui.icons.Warning
import com.agarthavision.ui.theme.AgarthaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class ReportSheetStep {
    CONFIGURATION,
    PREVIEW,
}

const val RECOMMENDED_MIN_SAMPLES = 10

object ReportSheetTestTags {
    const val SHEET = "report_sheet"
    const val INCLUDE_IMAGES_SWITCH = "report_option_include_images_switch"
    const val PATIENT_NAME_SHOW = "report_option_name_show"
    const val PATIENT_NAME_CENSOR = "report_option_name_censor"
    const val PATIENT_NAME_EXCLUDE = "report_option_name_exclude"
    const val PATIENT_NAME_SELECTED_CAPTION = "report_option_name_selected_caption"
    const val SAMPLE_WARNING_BANNER = "report_sample_warning_banner"
    const val SAMPLE_ACK_CARD = "report_sample_acknowledgment_card"
    const val SAMPLE_ACK_CHECKBOX = "report_sample_acknowledgment_checkbox"
    const val PREVIEW_BUTTON = "report_action_preview_button"
    const val GENERATE_PDF_BUTTON = "report_action_generate_pdf_button"
    const val EDIT_OPTIONS_BUTTON = "report_action_edit_options_button"
    const val CANCEL_BUTTON = "report_action_cancel_button"
    const val PREVIEW_VIEWPORT = "report_preview_viewport"
}

@Composable
fun ReportOptionsSection(
    includeImages: Boolean,
    onIncludeImagesChange: (Boolean) -> Unit,
    patientNameOption: PatientNameOption,
    onPatientNameOptionChange: (PatientNameOption) -> Unit,
    patientNamePreview: String,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(16.dp))
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.report_option_section_title),
            color = colors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = stringResource(R.string.report_option_include_images),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                )
                Text(
                    text = stringResource(R.string.report_option_include_images_subtitle),
                    fontSize = 13.sp,
                    color = colors.textSecondary,
                )
            }
            Switch(
                checked = includeImages,
                onCheckedChange = onIncludeImagesChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.onBrandFill,
                    checkedTrackColor = colors.brandFill,
                    checkedBorderColor = colors.brandFill,
                    uncheckedThumbColor = colors.textSecondary,
                    uncheckedTrackColor = colors.surfaceVariant,
                    uncheckedBorderColor = colors.borderStrong,
                ),
                modifier = Modifier.testTag(ReportSheetTestTags.INCLUDE_IMAGES_SWITCH),
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            thickness = 1.dp,
            color = colors.border,
        )

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.report_option_patient_name),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.report_option_patient_name_subtitle),
                fontSize = 13.sp,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceMuted, RoundedCornerShape(10.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PatientNameOptionPill(
                    label = stringResource(R.string.report_option_name_show),
                    isSelected = patientNameOption == PatientNameOption.SHOW,
                    onClick = { onPatientNameOptionChange(PatientNameOption.SHOW) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag(ReportSheetTestTags.PATIENT_NAME_SHOW),
                )
                PatientNameOptionPill(
                    label = stringResource(R.string.report_option_name_censor),
                    isSelected = patientNameOption == PatientNameOption.CENSOR,
                    onClick = { onPatientNameOptionChange(PatientNameOption.CENSOR) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag(ReportSheetTestTags.PATIENT_NAME_CENSOR),
                )
                PatientNameOptionPill(
                    label = stringResource(R.string.report_option_name_exclude),
                    isSelected = patientNameOption == PatientNameOption.EXCLUDE,
                    onClick = { onPatientNameOptionChange(PatientNameOption.EXCLUDE) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag(ReportSheetTestTags.PATIENT_NAME_EXCLUDE),
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.report_option_name_selected_prefix, patientNamePreview),
                fontSize = 12.sp,
                color = colors.textTertiary,
                modifier = Modifier.testTag(ReportSheetTestTags.PATIENT_NAME_SELECTED_CAPTION),
            )
        }
    }
}

@Composable
private fun PatientNameOptionPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    val backgroundColor = if (isSelected) colors.brandFill else Color.Transparent
    val textColor = if (isSelected) colors.onBrandFill else colors.textPrimary

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = textColor,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun SampleCountWarningBanner(
    sampleCount: Int,
    recommendedCount: Int = RECOMMENDED_MIN_SAMPLES,
    modifier: Modifier = Modifier,
) {
    if (sampleCount !in 1 until recommendedCount) return

    val colors = AgarthaTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.warningTint)
            .border(1.dp, colors.warning.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(14.dp)
            .testTag(ReportSheetTestTags.SAMPLE_WARNING_BANNER),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = AgarthaIcons.Warning,
            contentDescription = null,
            tint = colors.warning,
            modifier = Modifier
                .size(20.dp)
                .padding(top = 2.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pluralStringResource(
                    R.plurals.report_warning_fewer_samples_title,
                    sampleCount,
                    sampleCount,
                    recommendedCount,
                ),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = colors.warningText,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.report_warning_fewer_samples_config_subtitle),
                fontSize = 12.sp,
                color = colors.warningText,
            )
        }
    }
}

@Composable
fun SampleCountAcknowledgmentCard(
    sampleCount: Int,
    acknowledged: Boolean,
    onAcknowledgedChange: (Boolean) -> Unit,
    recommendedCount: Int = RECOMMENDED_MIN_SAMPLES,
    modifier: Modifier = Modifier,
) {
    if (sampleCount !in 1 until recommendedCount) return

    val colors = AgarthaTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.warningTint)
            .border(1.dp, colors.warning.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(14.dp)
            .testTag(ReportSheetTestTags.SAMPLE_ACK_CARD),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = AgarthaIcons.Warning,
                contentDescription = null,
                tint = colors.warning,
                modifier = Modifier
                    .size(20.dp)
                    .padding(top = 2.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pluralStringResource(
                        R.plurals.report_warning_fewer_samples_title,
                        sampleCount,
                        sampleCount,
                        recommendedCount,
                    ),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.warningText,
                )
                Spacer(Modifier.height(2.dp))
                val subtitleRes = if (sampleCount == 1) {
                    R.string.report_warning_fewer_samples_preview_subtitle_single
                } else {
                    R.string.report_warning_fewer_samples_preview_subtitle_multiple
                }
                Text(
                    text = stringResource(subtitleRes),
                    fontSize = 12.sp,
                    color = colors.warningText,
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { onAcknowledgedChange(!acknowledged) }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = acknowledged,
                onCheckedChange = onAcknowledgedChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = colors.brandFill,
                    checkmarkColor = colors.onBrandFill,
                    uncheckedColor = colors.borderStrong,
                ),
                modifier = Modifier.testTag(ReportSheetTestTags.SAMPLE_ACK_CHECKBOX),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.report_warning_acknowledgment_checkbox),
                fontSize = 13.sp,
                color = colors.textPrimary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
fun PdfPreviewViewport(
    pdfBytes: ByteArray?,
    onPageInfoChanged: (currentPage: Int, totalPages: Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = AgarthaTheme.colors
    var renderedBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }

    LaunchedEffect(pdfBytes) {
        if (pdfBytes == null || pdfBytes.isEmpty()) {
            renderedBitmaps = emptyList()
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            val tempFile = File(context.cacheDir, "preview_${UUID.randomUUID()}.pdf")
            try {
                tempFile.writeBytes(pdfBytes)
                val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                val count = renderer.pageCount
                if (count == 0) {
                    renderer.close()
                    pfd.close()
                    renderedBitmaps = emptyList()
                    return@withContext
                }
                val pages = ArrayList<Bitmap>(count)
                for (i in 0 until count) {
                    val page = renderer.openPage(i)
                    val bitmap = Bitmap.createBitmap(
                        page.width,
                        page.height,
                        Bitmap.Config.ARGB_8888,
                    )
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    pages.add(bitmap)
                }
                renderer.close()
                pfd.close()
                renderedBitmaps = pages
            } catch (_: Throwable) {
                renderedBitmaps = emptyList()
            } finally {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 280.dp, max = 400.dp)
            .background(colors.surfaceVariant, RoundedCornerShape(12.dp))
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .testTag(ReportSheetTestTags.PREVIEW_VIEWPORT),
        contentAlignment = Alignment.Center,
    ) {
        if (renderedBitmaps.isNotEmpty()) {
            val pagerState = rememberPagerState(pageCount = { renderedBitmaps.size })
            LaunchedEffect(pagerState.currentPage, renderedBitmaps.size) {
                onPageInfoChanged(pagerState.currentPage + 1, renderedBitmaps.size)
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 280.dp, max = 400.dp),
            ) { pageIndex ->
                val bitmap = renderedBitmaps.getOrNull(pageIndex)
                if (bitmap != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.report_preview_title),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, colors.border, RoundedCornerShape(6.dp)),
                        )
                    }
                }
            }
        } else {
            LaunchedEffect(Unit) {
                onPageInfoChanged(1, 1)
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = AgarthaIcons.Description,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.report_preview_title),
                    fontSize = 14.sp,
                    color = colors.textSecondary,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
fun ReportPreviewSummaryCard(
    includeImages: Boolean,
    patientNameOption: PatientNameOption,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceMuted)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.report_preview_images_label),
                fontSize = 12.sp,
                color = colors.textSecondary,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(
                    if (includeImages) {
                        R.string.report_preview_images_included
                    } else {
                        R.string.report_preview_images_not_included
                    },
                ),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.report_preview_patient_name_label),
                fontSize = 12.sp,
                color = colors.textSecondary,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            val nameOptionText = when (patientNameOption) {
                PatientNameOption.SHOW -> stringResource(R.string.report_option_name_show)
                PatientNameOption.CENSOR -> stringResource(R.string.report_option_name_censor)
                PatientNameOption.EXCLUDE -> stringResource(R.string.report_option_name_exclude)
            }
            Text(
                text = nameOptionText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )
        }
    }
}
