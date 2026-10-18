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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.agarthavision.R
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.Report
import com.agarthavision.domain.model.ReportSyncStatus
import com.agarthavision.ui.components.ReadOnlyAuthorNote
import com.agarthavision.ui.components.SkeletonBox
import com.agarthavision.ui.icons.AgarthaIcons
import com.agarthavision.ui.icons.DescriptionFilled
import com.agarthavision.ui.icons.Download
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
    val colors = AgarthaTheme.colors
    // A report covers verified samples only, so with none there is nothing to report on
    // (86d4bzm9k). A sample verified as negative still counts: that is a result.
    val canGenerate = state.session.verifiedSamples.isNotEmpty()
    val readOnlyAuthor = state.readOnlyAuthor

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(16.dp))
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        if (readOnlyAuthor != null) {
            ReadOnlyAuthorNote(authorName = readOnlyAuthor.name)
            Spacer(Modifier.height(8.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            // The subtitle can now be a full sentence; weight lets it wrap instead of pushing
            // the button out, and the gap keeps the two apart when it does.
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = stringResource(R.string.report_section_title),
                    fontSize = 18.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
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
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    color = colors.textSecondary,
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

        if (state.reports.isNotEmpty()) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                thickness = 1.dp,
                color = colors.border,
            )
            state.reports.forEachIndexed { index, report ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        thickness = 1.dp,
                        color = colors.border,
                    )
                }
                ReportRow(report = report, onOpen = { state.onOpenReport(report) })
            }
        }

        // Only paginate once reports spill past a single page.
        if (state.totalReports > REPORTS_PER_PAGE) {
            Spacer(Modifier.height(8.dp))
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
    val colors = AgarthaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                enabled = report.pdfFilePath != null,
                onClick = onOpen,
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 46.dp, height = 50.dp)
                .background(colors.surfaceMuted, RoundedCornerShape(10.dp))
                .border(1.dp, colors.border, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = AgarthaIcons.DescriptionFilled,
                    contentDescription = null,
                    tint = colors.brandFill,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.report_format_pdf),
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textSecondary,
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = report.generatedAt.formatReportDateTime(),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(2.dp))
            val andOthersLabel = stringResource(R.string.report_and_others)
            val positiveSpeciesText = remember(report.positiveSpecies, andOthersLabel) {
                formatReportPositiveSpecies(report.positiveSpecies, andOthersLabel)
            }
            Text(
                text = positiveSpeciesText.ifBlank {
                    stringResource(R.string.report_no_positive_species)
                },
                fontSize = 13.sp,
                color = colors.textSecondary,
                lineHeight = 17.sp,
            )
        }

        Spacer(Modifier.width(8.dp))

        ReportStatusPill(status = report.supabaseStatus)
    }
}

internal fun formatReportPositiveSpecies(
    species: List<String>,
    andOthersLabel: String = "and others",
): String {
    val mainPresent = mutableListOf<String>()
    var hasOther = false

    for (name in species) {
        val eggSpecies = EggSpecies.fromClassLabel(name)
        if (eggSpecies != null && eggSpecies != EggSpecies.OTHER) {
            val canonical = eggSpecies.canonicalClass ?: name
            if (canonical !in mainPresent) {
                mainPresent.add(canonical)
            }
        } else if (name.isNotBlank()) {
            hasOther = true
        }
    }

    val displayItems = if (hasOther) {
        mainPresent + andOthersLabel
    } else {
        mainPresent
    }
    return displayItems.joinToString(", ")
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
            .size(40.dp)
            .background(
                if (clickable) colors.brandFill else colors.brandFill.copy(alpha = 0.4f),
                CircleShape,
            )
            .clip(CircleShape)
            .clickable(enabled = clickable, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = AgarthaIcons.Download,
            contentDescription = stringResource(
                if (isGenerating) R.string.report_generating else R.string.report_generate,
            ),
            tint = Color.White,
            modifier = Modifier.size(20.dp),
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
    val descriptionRes = if (sample.isManual) {
        R.string.sample_tile_content_description_manual
    } else {
        R.string.sample_tile_content_description
    }
    val description = stringResource(descriptionRes, eggCountText)

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
