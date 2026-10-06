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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.agarthavision.ui.components.ReadOnlyAuthorNote
import com.agarthavision.ui.icons.AgarthaIcons
import com.agarthavision.ui.icons.Download
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
        val readOnlyAuthor = state.readOnlyAuthor
        if (readOnlyAuthor != null) {
            ReadOnlyAuthorNote(authorName = readOnlyAuthor.name)
        }
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
                        readOnlyAuthor != null && state.totalReports == 0 ->
                            stringResource(R.string.report_empty)
                        readOnlyAuthor != null ->
                            stringResource(R.string.report_generated_count, state.totalReports)
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
            // Not on a colleague's session: its author generates its reports (14zcqntjph6).
            if (readOnlyAuthor == null) {
                GenerateReportButton(
                    isGenerating = state.isGenerating,
                    enabled = canGenerate,
                    onClick = state.onGenerate,
                )
            }
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
                enabled = report.pdfFilePath != null,
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
            if (report.pdfFilePath != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FormatChip(stringResource(R.string.report_format_pdf))
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
    onClick: () -> Unit,
) {
    val colors = AgarthaTheme.colors
    val clickable = enabled && !isGenerating
    Box(
        modifier = Modifier
            .size(34.dp)
            .background(
                if (clickable) colors.accent else colors.borderStrong,
                RoundedCornerShape(999.dp),
            )
            .clickable(enabled = clickable, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = AgarthaIcons.Download,
            contentDescription = stringResource(
                if (isGenerating) R.string.report_generating else R.string.report_generate
            ),
            tint = colors.onAccent,
            modifier = Modifier.size(17.dp),
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
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    val eggCountText = pluralStringResource(
        R.plurals.sessions_eggs_count,
        sample.eggCount,
        sample.eggCount,
    )
    val manualSuffix = if (sample.isManual) ", manual capture" else ""
    val description = "Sample, $eggCountText$manualSuffix"

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
            .background(AppColors.MicroscopeBrush)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = description
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

        TileBoxesOverlay(
            boxes = sample.boxes,
            modifier = Modifier.fillMaxSize(),
        )

        if (sample.isManual) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(colors.gold, RoundedCornerShape(7.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = stringResource(R.string.sample_tile_manual),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onGold,
                )
            }
        }

        if (sample.eggCount > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = eggCountText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
    }
}
