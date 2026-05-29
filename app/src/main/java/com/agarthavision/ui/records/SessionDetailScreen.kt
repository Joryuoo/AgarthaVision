@file:Suppress("FunctionNaming", "LongMethod")

package com.agarthavision.ui.records

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.agarthavision.R
import com.agarthavision.domain.model.Report
import com.agarthavision.domain.model.ReportSyncStatus
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private data class SessionDetailUi(
    val id: String,
    val label: String?,
    val dateLabel: String,
    val timeLabel: String,
    val patientIdOrNote: String?,
    val epg: Int,
    val confirmedEggs: Int,
    val speciesCount: Int,
    val samplesTotal: Int,
    val verifiedSamples: List<SampleUi>,
)

private data class SampleUi(
    val id: String,
    val source: SampleSource,
    val species: String,
    val confidence: Int?,
    val filePath: String?,
)

private enum class SampleSource { Ai, Manual }

@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    onSampleClick: (String) -> Unit,
    viewModel: SessionDetailViewModel = hiltViewModel(),
) {
    AgarthaTheme {
        val state by viewModel.state.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val snackbarHostState = remember { SnackbarHostState() }
        val generatedMessage = stringResource(R.string.report_generated_toast)
        val generationFailedTemplate = stringResource(R.string.report_generation_failed)
        val sessionDetail = mapToUiModel(state)

        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                when (event) {
                    is SessionDetailEvent.ReportGenerated -> snackbarHostState.showSnackbar(generatedMessage)
                }
            }
        }

        LaunchedEffect(state.generationError) {
            state.generationError?.let { error ->
                snackbarHostState.showSnackbar(generationFailedTemplate.format(error))
            }
        }

        if (sessionDetail == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.Blue)
            }
            return@AgarthaTheme
        }

        Scaffold(
            topBar = {
                SessionDetailAppBar(
                    title = sessionDetail.label ?: "Session ${sessionDetail.id}",
                    subtitle = if (sessionDetail.patientIdOrNote.isNullOrBlank()) {
                        "${sessionDetail.dateLabel} · ${sessionDetail.timeLabel}"
                    } else {
                        "${sessionDetail.dateLabel} · ${sessionDetail.timeLabel} · ${sessionDetail.patientIdOrNote}"
                    },
                    isGenerating = state.isGenerating,
                    onBack = onBack,
                    onGenerateReport = viewModel::generateReport,
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = AppColors.White,
        ) { inner ->
            if (sessionDetail.verifiedSamples.isEmpty()) {
                SessionDetailEmpty(
                    session = sessionDetail,
                    reports = state.reports,
                    isGenerating = state.isGenerating,
                    onGenerate = viewModel::generateReport,
                    onShare = { report -> shareReportCsv(context, report) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inner),
                )
            } else {
                SessionDetailPopulated(
                    session = sessionDetail,
                    reports = state.reports,
                    isGenerating = state.isGenerating,
                    onGenerate = viewModel::generateReport,
                    onShare = { report -> shareReportCsv(context, report) },
                    onSampleClick = { sample -> onSampleClick(sample.id) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inner),
                )
            }
        }
    }
}

private fun mapToUiModel(state: SessionDetailState): SessionDetailUi? {
    val sessionData = state.session ?: return null
    val sessionRecord = sessionData.session
    val startedAt = Instant.ofEpochMilli(sessionRecord.startedAt)
        .atZone(ZoneId.systemDefault())

    val samples = sessionData.samples.map { item ->
        val primary = item.primaryDetection
        val hasAi = item.detections.isNotEmpty() && !item.sample.isManual
        SampleUi(
            id = item.sample.id,
            source = if (hasAi) SampleSource.Ai else SampleSource.Manual,
            species = primary?.expertClass ?: primary?.classLabel ?: "Manual",
            confidence = primary?.confidence?.let { (it * 100).toInt() },
            filePath = item.sample.filePath,
        )
    }

    return SessionDetailUi(
        id = sessionRecord.id.takeLast(4),
        label = sessionRecord.label,
        dateLabel = startedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
        timeLabel = startedAt.format(DateTimeFormatter.ofPattern("HH:mm")),
        patientIdOrNote = sessionRecord.notes,
        epg = state.epg,
        confirmedEggs = state.totalEggCount,
        speciesCount = state.eggCounts.size,
        samplesTotal = sessionData.samples.size,
        verifiedSamples = samples,
    )
}

@Composable
private fun SessionDetailAppBar(
    title: String,
    subtitle: String,
    isGenerating: Boolean,
    onBack: () -> Unit,
    onGenerateReport: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.White)
            .padding(start = Spacing.xs, end = Spacing.sm, top = 14.dp, bottom = 12.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_left),
                contentDescription = stringResource(R.string.session_detail_back),
                tint = AppColors.Gray700,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(Modifier.weight(1f).padding(start = Spacing.xs)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = AppColors.Gray900)
            Text(
                subtitle,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = AppColors.Gray500,
                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        IconButton(onClick = onGenerateReport, enabled = !isGenerating) {
            Icon(
                painter = painterResource(R.drawable.ic_download),
                contentDescription = stringResource(R.string.report_generate),
                tint = if (isGenerating) AppColors.Gray300 else AppColors.Gray700,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun EpgHeroCard(
    epg: Int,
    confirmedEggs: Int,
    speciesCount: Int,
    samplesTotal: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(AppColors.Gray50, RoundedCornerShape(12.dp))
            .border(1.dp, AppColors.Gray100, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp)),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(AppColors.BlueTint.copy(alpha = 0.6f), Color.Transparent),
                            center = Offset(size.width, 0f),
                            radius = 220.dp.toPx(),
                        ),
                    )
                },
        )
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "EGGS PER GRAM",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.Gray500,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                epg.toString(),
                fontSize = 56.sp,
                lineHeight = 56.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.Gray900,
                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum, cv11, ss01, ss03"),
            )
            Spacer(Modifier.height(12.dp))
            EpgMeta(confirmedEggs, speciesCount, samplesTotal)
        }
    }
}

@Composable
private fun EpgMeta(confirmedEggs: Int, speciesCount: Int, samplesTotal: Int) {
    if (confirmedEggs == 0) {
        Text("No confirmed eggs yet", fontSize = 13.sp, color = AppColors.Gray500)
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MetaItem(confirmedEggs.toString(), "confirmed")
            DotSeparator()
            MetaItem(speciesCount.toString(), "species")
            DotSeparator()
            MetaItem(samplesTotal.toString(), "samples")
        }
    }
}

@Composable
private fun MetaItem(value: String, label: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.Gray900,
            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
        )
        Spacer(Modifier.width(3.dp))
        Text(label, fontSize = 13.sp, color = AppColors.Gray500)
    }
}

@Composable
private fun DotSeparator() {
    Text(
        "·",
        fontSize = 13.sp,
        color = AppColors.Gray300,
        modifier = Modifier.padding(horizontal = 7.dp),
    )
}

@Composable
private fun ReportsSection(
    reports: List<Report>,
    isGenerating: Boolean,
    onGenerate: () -> Unit,
    onShare: (Report) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AppColors.Gray50, RoundedCornerShape(12.dp))
            .border(1.dp, AppColors.Gray100, RoundedCornerShape(12.dp))
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
                    color = AppColors.Gray900,
                )
                Text(
                    text = if (reports.isEmpty()) {
                        stringResource(R.string.report_empty)
                    } else {
                        "${reports.size} generated"
                    },
                    fontSize = 12.sp,
                    color = AppColors.Gray500,
                )
            }
            SmallActionPill(
                label = stringResource(if (isGenerating) R.string.report_generating else R.string.report_generate),
                enabled = !isGenerating,
                onClick = onGenerate,
            )
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
            .clickable(enabled = report.csvFilePath != null, onClick = onShare)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = report.generatedAt.formatReportDateTime(),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = AppColors.Gray900,
            )
            Text(
                text = report.positiveSpecies.joinToString(", ").ifBlank {
                    stringResource(R.string.report_no_positive_species)
                },
                fontSize = 12.sp,
                color = AppColors.Gray500,
            )
        }
        ReportStatusPill(status = report.supabaseStatus)
    }
}

@Composable
private fun ReportStatusPill(status: ReportSyncStatus) {
    val (bg, fg, label) = when (status) {
        ReportSyncStatus.SYNCED -> Triple(AppColors.GreenTint, AppColors.GreenText, R.string.report_status_synced)
        ReportSyncStatus.SYNC_FAILED -> Triple(AppColors.RedTint, AppColors.Red, R.string.report_status_failed)
        ReportSyncStatus.PENDING -> Triple(AppColors.AmberTint, AppColors.AmberText, R.string.report_status_pending)
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
private fun SmallActionPill(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .background(if (enabled) AppColors.Blue else AppColors.Gray300, RoundedCornerShape(999.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(label, color = AppColors.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Gray900)
        Text(
            count,
            fontSize = 12.sp,
            color = AppColors.Gray500,
            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
        )
    }
}

@Composable
private fun SampleTile(
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
    val bg = if (isManual) AppColors.Amber else AppColors.Blue
    Box(
        modifier = modifier
            .background(bg, RoundedCornerShape(999.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = AppColors.White)
    }
}

@Composable
private fun SessionDetailPopulated(
    session: SessionDetailUi,
    reports: List<Report>,
    isGenerating: Boolean,
    onGenerate: () -> Unit,
    onShare: (Report) -> Unit,
    onSampleClick: (SampleUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier,
        contentPadding = PaddingValues(
            start = Spacing.xl,
            end = Spacing.xl,
            top = Spacing.xs,
            bottom = Spacing.xxl,
        ),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                EpgHeroCard(
                    epg = session.epg,
                    confirmedEggs = session.confirmedEggs,
                    speciesCount = session.speciesCount,
                    samplesTotal = session.samplesTotal,
                    modifier = Modifier.semantics(mergeDescendants = true) {
                        contentDescription = "Eggs per gram: ${session.epg}, ${session.confirmedEggs} confirmed, ${session.speciesCount} species, ${session.samplesTotal} samples"
                    },
                )
                Spacer(Modifier.height(Spacing.md))
                ReportsSection(
                    reports = reports,
                    isGenerating = isGenerating,
                    onGenerate = onGenerate,
                    onShare = onShare,
                )
                Spacer(Modifier.height(Spacing.lg))
                SectionHeader(
                    title = "Verified samples",
                    count = "${session.verifiedSamples.size} of ${session.samplesTotal}",
                )
                Spacer(Modifier.height(Spacing.sm))
            }
        }
        items(session.verifiedSamples, key = { it.id }) { sample ->
            SampleTile(sample = sample, onClick = { onSampleClick(sample) })
        }
    }
}

@Composable
private fun SessionDetailEmpty(
    session: SessionDetailUi,
    reports: List<Report>,
    isGenerating: Boolean,
    onGenerate: () -> Unit,
    onShare: (Report) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(horizontal = Spacing.xl)
            .padding(top = Spacing.xs)
            .verticalScroll(rememberScrollState()),
    ) {
        EpgHeroCard(
            epg = session.epg,
            confirmedEggs = session.confirmedEggs,
            speciesCount = session.speciesCount,
            samplesTotal = session.samplesTotal,
        )
        Spacer(Modifier.height(Spacing.md))
        ReportsSection(
            reports = reports,
            isGenerating = isGenerating,
            onGenerate = onGenerate,
            onShare = onShare,
        )
        Spacer(Modifier.height(60.dp))
        EmptyStateGraphic()
    }
}

@Composable
private fun EmptyStateGraphic() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(AppColors.Gray50, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_minus_circle),
                contentDescription = null,
                tint = AppColors.Gray300,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text("No verified samples yet", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Gray700)
        Spacer(Modifier.height(4.dp))
        Text(
            "Captured frames will appear here once verified.",
            fontSize = 13.sp,
            color = AppColors.Gray500,
            textAlign = TextAlign.Center,
        )
    }
}

private fun shareReportCsv(context: Context, report: Report) {
    val path = report.csvFilePath ?: return
    val file = File(path)
    if (!file.exists()) return
    val uri = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrElse { return }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, file.name)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(intent, file.name).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(chooser) }
}

private fun Instant.formatReportDateTime(): String =
    atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
