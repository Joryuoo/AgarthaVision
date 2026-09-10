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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.agarthavision.R
import com.agarthavision.domain.model.Report
import com.agarthavision.domain.model.ReportSyncStatus
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.AppColors
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun ReportsSection(
    reports: List<Report>,
    isGenerating: Boolean,
    onGenerate: () -> Unit,
    onShare: (Report) -> Unit,
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.report_section_title),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AgarthaTheme.colors.textPrimary,
                )
                Text(
                    text = if (reports.isEmpty()) {
                        stringResource(R.string.report_empty)
                    } else {
                        "${reports.size} generated"
                    },
                    fontSize = 12.sp,
                    color = AgarthaTheme.colors.textSecondary,
                )
            }
            // Icon rather than a label: the text pill fought the subtitle for width and
            // lost its shape. The app bar's duplicate download button is gone, so this is
            // now the only way to generate from here.
            GenerateReportButton(isGenerating = isGenerating, onClick = onGenerate)
        }

        reports.take(3).forEach { report ->
            ReportRow(report = report, onShare = { onShare(report) })
        }
    }
}

@Composable
private fun ReportRow(report: Report, onShare: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = report.pdfFilePath != null, onClick = onShare)
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
        }
        ReportStatusPill(status = report.supabaseStatus)
    }
}

@Composable
private fun ReportStatusPill(status: ReportSyncStatus) {
    val colors = AgarthaTheme.colors
    val (bg, fg, label) = when (status) {
        ReportSyncStatus.SYNCED -> Triple(colors.successTint, colors.successText, R.string.report_status_synced)
        ReportSyncStatus.SYNC_FAILED -> Triple(colors.dangerTint, colors.dangerText, R.string.report_status_failed)
        ReportSyncStatus.PENDING -> Triple(colors.warningTint, colors.warningText, R.string.report_status_pending)
    }
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = stringResource(label),
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun GenerateReportButton(isGenerating: Boolean, onClick: () -> Unit) {
    val colors = AgarthaTheme.colors
    Box(
        modifier = Modifier
            .size(34.dp)
            .background(
                if (isGenerating) colors.borderStrong else colors.accent,
                RoundedCornerShape(999.dp),
            )
            .clickable(enabled = !isGenerating, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_download),
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
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.MicroscopeBrush)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "Sample ${sample.id}, ${sample.species}" +
                    (sample.confidence?.let { ", $it percent confidence" } ?: ", manual capture")
            },
    ) {
        if (sample.filePath != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(File(sample.filePath))
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        sample.confidence?.let {
            ConfidenceChip(
                text = "$it%",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
            )
        }
        if (sample.isRepeat) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(AgarthaTheme.colors.gold, RoundedCornerShape(999.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            ) {
                Text("R", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = AgarthaTheme.colors.onGold)
            }
        }
        SpeciesBadge(
            text = sample.species,
            isManual = sample.source == SampleSource.Manual,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp),
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
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = AppColors.White)
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
                painter = painterResource(R.drawable.ic_minus_circle),
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

private fun Instant.formatReportDateTime(): String =
    atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
