@file:Suppress("TooManyFunctions")

package com.agarthavision.ui.records

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.agarthavision.ui.icons.AgarthaIcons
import com.agarthavision.ui.icons.ChevronRight
import com.agarthavision.ui.icons.Download
import com.agarthavision.ui.icons.RemoveCircle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.RectangleShape
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.agarthavision.R
import com.agarthavision.ui.components.SkeletonBox
import com.agarthavision.domain.model.Report
import com.agarthavision.domain.model.ReportSyncStatus
import com.agarthavision.ui.image.SampleImageRef
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.AppColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun ReportsSection(
    state: SessionDetailContentState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AgarthaTheme.colors.surfaceVariant, RoundedCornerShape(12.dp))
            .border(1.dp, AgarthaTheme.colors.border, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // A report covers verified samples only, so with none there is nothing to report on
        // (86d4bzm9k). A sample verified as negative still counts: that is a result.
        val canGenerate = state.session.verifiedSamples.isNotEmpty()
        Row(
            modifier = Modifier.fillMaxWidth(),
            // The subtitle can now be a full sentence; weight lets it wrap instead of pushing
            // the button out, and the gap keeps the two apart when it does.
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.report_section_title),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AgarthaTheme.colors.textPrimary,
                )
                Text(
                    text = when {
                        !canGenerate -> stringResource(R.string.report_needs_verified_sample)
                        state.totalReports == 0 -> stringResource(R.string.report_empty)
                        else -> stringResource(R.string.report_generated_count, state.totalReports)
                    },
                    fontSize = 12.sp,
                    color = AgarthaTheme.colors.textSecondary,
                )
            }
            // Icon rather than a label: the text pill fought the subtitle for width and
            // lost its shape. The app bar's duplicate download button is gone, so this is
            // now the only way to generate from here.
            GenerateReportButton(
                isGenerating = state.isGenerating,
                enabled = canGenerate,
                onClick = state.onGenerate,
            )
        }

        state.reports.forEach { report ->
            ReportRow(report = report, onOpen = { state.onOpenReport(report) })
        }

        // Only paginate once reports spill past a single page.
        if (state.totalReports > REPORTS_PER_PAGE) {
            ReportsPager(
                currentPage = state.currentPage,
                totalReports = state.totalReports,
                onPrev = state.onPrevPage,
                onNext = state.onNextPage,
            )
        }
    }
}

@Composable
private fun ReportRow(report: Report, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                enabled = report.pdfFilePath != null || report.csvFilePath != null,
                onClick = onOpen,
            )
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = report.generatedAt.formatReportDateTime(),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = AgarthaTheme.colors.textPrimary,
            )
            Text(
                text = report.positiveSpecies.joinToString(", ").ifBlank {
                    stringResource(R.string.report_no_positive_species)
                },
                fontSize = 12.sp,
                color = AgarthaTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (report.pdfFilePath != null) {
                    FormatChip(stringResource(R.string.report_format_pdf))
                }
                if (report.csvFilePath != null) {
                    FormatChip(stringResource(R.string.report_format_csv))
                }
            }
        }
        ReportStatusPill(status = report.supabaseStatus)
    }
}


@Composable
private fun GenerateReportButton(
    isGenerating: Boolean,
    enabled: Boolean,
    onClick: (ExportFormat) -> Unit,
) {
    val colors = AgarthaTheme.colors
    var menuExpanded by remember { mutableStateOf(false) }
    val clickable = enabled && !isGenerating
    Box {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(
                    if (clickable) colors.accent else colors.borderStrong,
                    RoundedCornerShape(999.dp),
                )
                .clickable(enabled = clickable, onClick = { menuExpanded = true }),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = AgarthaIcons.Download,
                contentDescription = stringResource(
                    if (isGenerating) R.string.report_generating else R.string.report_export
                ),
                tint = colors.onAccent,
                modifier = Modifier.size(17.dp),
            )
        }
        ExportFormatMenu(
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            onSelect = onClick,
        )
    }
}

@Composable
internal fun SectionHeader(
    title: String,
    count: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AgarthaTheme.colors.textPrimary)
        Text(
            count,
            fontSize = 12.sp,
            color = AgarthaTheme.colors.textSecondary,
            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
        )
    }
}

@Composable
internal fun SampleTile(
    sample: SampleUi,
    onClick: () -> Unit,
) {
    val species = sample.speciesLabel()
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.MicroscopeBrush)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                // No confidence and not manual means the medtech rejected every box: no eggs,
                // which the species label already says. It is not a manual capture.
                val provenance = when {
                    sample.confidence != null -> ", ${sample.confidence} percent confidence"
                    sample.source == SampleSource.Manual -> ", manual capture"
                    else -> ""
                }
                contentDescription = "Sample ${sample.id}, $species$provenance"
            },
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(SampleImageRef(sample.filePath, sample.storagePath))
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            loading = { SkeletonBox(modifier = Modifier.fillMaxSize(), shape = RectangleShape) },
            error = {},
        )

        sample.confidence?.let {
            ConfidenceChip(
                text = "$it%",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
            )
        }
        SpeciesBadge(
            text = species,
            isManual = sample.source == SampleSource.Manual,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp),
        )
    }
}

@Composable
internal fun SampleRow(
    sample: SampleUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(10.dp))
            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = sample.timeLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textSecondary,
                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                )
                val sourceLabel = if (sample.source == SampleSource.Ai) {
                    sample.confidence?.let { "$it%" } ?: "AI"
                } else {
                    "Manual"
                }
                Box(
                    modifier = Modifier
                        .background(colors.surfaceMuted, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = sourceLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textSecondary,
                    )
                }
                if (sample.isEdited) {
                    Box(
                        modifier = Modifier
                            .background(colors.warningTint, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.badge_edited),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.warningText,
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            val species = sample.speciesLabel()
            val fontStyle = if (species.isBinomial()) {
                androidx.compose.ui.text.font.FontStyle.Italic
            } else {
                androidx.compose.ui.text.font.FontStyle.Normal
            }
            Text(
                text = species,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontStyle = fontStyle,
                color = colors.textPrimary,
            )
        }
        Icon(
            imageVector = AgarthaIcons.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = colors.textTertiary,
        )
    }
}

@Composable
private fun ConfidenceChip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(999.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = AppColors.White)
    }
}

@Composable
private fun SpeciesBadge(text: String, isManual: Boolean, modifier: Modifier = Modifier) {
    // On-image badges: fixed maroon/amber fills with white text (mode-independent).
    val bg = if (isManual) AppColors.Amber else AppColors.Maroon
    Box(
        modifier = modifier
            .background(bg, RoundedCornerShape(999.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        // A binomial like "Ascaris lumbricoides" is wider than a grid tile; wrapping it
        // turned the pill into a two-line block that hid most of the image. One line,
        // ellipsized - the full name is on the sample detail screen.
        Text(
            text,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.White,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun EmptyStateGraphic() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(AgarthaTheme.colors.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = AgarthaIcons.RemoveCircle,
                contentDescription = null,
                tint = AgarthaTheme.colors.textTertiary,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "No verified samples yet",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = AgarthaTheme.colors.textSecondary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Captured frames will appear here once verified.",
            fontSize = 13.sp,
            color = AgarthaTheme.colors.textSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/**
 * What a sample card calls the sample. A null species means the model boxed something and the
 * medtech rejected all of it, which is a negative result and says so rather than going blank.
 */
@Composable
private fun SampleUi.speciesLabel(): String =
    species ?: stringResource(R.string.session_detail_sample_no_eggs)
