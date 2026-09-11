@file:Suppress("FunctionNaming", "LongMethod")

package com.agarthavision.ui.records

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agarthavision.R
import com.agarthavision.domain.model.Report
import com.agarthavision.ui.components.BackArrow
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal data class SessionDetailUi(
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

internal data class SampleUi(
    val id: String,
    val source: SampleSource,
    val species: String,
    val confidence: Int?,
    val filePath: String?,
    val isRepeat: Boolean = false,
)

internal enum class SampleSource { Ai, Manual }

/** Multiplier to convert a [0,1] confidence ratio into a whole-number percentage. */
private const val CONFIDENCE_PERCENT_MULTIPLIER = 100

/** Number of trailing session-id characters kept for the compact detail-screen label. */
private const val SESSION_ID_SHORT_LENGTH = 4

@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    onSampleClick: (String) -> Unit,
    viewModel: SessionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val generatedMessage = stringResource(R.string.report_generated_toast)
    val shareActionLabel = stringResource(R.string.report_share_action)
    var shareError by remember { mutableStateOf<Int?>(null) }
    val generationFailedTemplate = stringResource(R.string.report_generation_failed)
    val sessionDetail = mapToUiModel(state)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SessionDetailEvent.ReportGenerated -> {
                    // Offer the share sheet right here: hunting the file down in a file
                    // manager was the whole complaint.
                    val result = snackbarHostState.showSnackbar(
                        message = generatedMessage,
                        actionLabel = shareActionLabel,
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        shareError = when (event.format) {
                            ExportFormat.PDF -> shareReportPdf(context, event.pdfPath)
                            ExportFormat.CSV -> shareReportCsv(context, event.csvPath)
                        }
                    }
                }
            }
        }
    }

    // Surface a failed share instead of dropping it — a stale path used to be a dead tap.
    LaunchedEffect(shareError) {
        shareError?.let { messageRes ->
            snackbarHostState.showSnackbar(context.getString(messageRes))
            shareError = null
        }
    }

    LaunchedEffect(state.generationError) {
        state.generationError?.let { error ->
            snackbarHostState.showSnackbar(generationFailedTemplate.format(error))
        }
    }

    if (sessionDetail == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AgarthaTheme.colors.accent)
        }
        return
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
                onBack = onBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = AgarthaTheme.colors.background,
        // AgarthaNavGraph zeroes contentWindowInsets app-wide, so each screen applies
        // its own. Without this the app bar draws under the status bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { inner ->
        val contentState = SessionDetailContentState(
            session = sessionDetail,
            reports = state.reports,
            totalReports = state.totalReports,
            currentPage = state.currentPage,
            isGenerating = state.isGenerating,
            onGenerate = viewModel::generateReport,
            onOpenReport = { report ->
                shareError = when {
                    report.pdfFilePath != null -> viewReportPdf(context, report.pdfFilePath)
                    report.csvFilePath != null -> viewReportCsv(context, report.csvFilePath)
                    else -> R.string.report_share_missing_path
                }
            },
            onPrevPage = viewModel::goToPreviousReportPage,
            onNextPage = viewModel::goToNextReportPage,
        )
        if (sessionDetail.verifiedSamples.isEmpty()) {
            SessionDetailEmpty(
                state = contentState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner),
            )
        } else {
            SessionDetailPopulated(
                state = contentState,
                onSampleClick = { sample -> onSampleClick(sample.id) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner),
            )
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
            confidence = primary?.confidence?.let { (it * CONFIDENCE_PERCENT_MULTIPLIER).toInt() },
            filePath = item.sample.filePath,
            isRepeat = item.sample.isRepeat,
        )
    }

    return SessionDetailUi(
        id = sessionRecord.id.takeLast(SESSION_ID_SHORT_LENGTH),
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
    onBack: () -> Unit,
) {
    val colors = AgarthaTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .statusBarsPadding()
            .padding(start = Spacing.xs, end = Spacing.sm, top = 14.dp, bottom = 12.dp),
    ) {
        BackArrow(
            onBack = onBack,
            contentDescription = stringResource(R.string.session_detail_back),
        )
        Column(Modifier.weight(1f).padding(start = Spacing.xs)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary)
            Text(
                subtitle,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary,
                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * Snapshot shared by [SessionDetailPopulated] and [SessionDetailEmpty] — both render the same
 * hero + reports section, differing only in what follows.
 */
internal data class SessionDetailContentState(
    val session: SessionDetailUi,
    val reports: List<Report>,
    val totalReports: Int,
    val currentPage: Int,
    val isGenerating: Boolean,
    val onGenerate: (ExportFormat) -> Unit,
    val onOpenReport: (Report) -> Unit,
    val onPrevPage: () -> Unit,
    val onNextPage: () -> Unit,
)

@Composable
private fun SessionDetailPopulated(
    state: SessionDetailContentState,
    onSampleClick: (SampleUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = state.session
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
                        contentDescription = "Eggs per gram: ${session.epg}, " +
                            "${session.confirmedEggs} confirmed, " +
                            "${session.speciesCount} species, " +
                            "${session.samplesTotal} samples"
                    },
                )
                Spacer(Modifier.height(Spacing.md))
                ReportsSection(state = state)
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
    state: SessionDetailContentState,
    modifier: Modifier = Modifier,
) {
    val session = state.session
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
        ReportsSection(state = state)
        Spacer(Modifier.height(60.dp))
        EmptyStateGraphic()
    }
}

@Composable
internal fun EpgHeroCard(
    epg: Int,
    confirmedEggs: Int,
    speciesCount: Int,
    samplesTotal: Int,
    modifier: Modifier = Modifier,
) {
    val themeColors = AgarthaTheme.colors
    val heroGlow = themeColors.accentTint
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(themeColors.surfaceVariant, RoundedCornerShape(12.dp))
            .border(1.dp, themeColors.border, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp)),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(heroGlow.copy(alpha = 0.6f), Color.Transparent),
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
                color = AgarthaTheme.colors.textSecondary,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                epg.toString(),
                fontSize = 56.sp,
                lineHeight = 56.sp,
                fontWeight = FontWeight.Bold,
                color = AgarthaTheme.colors.textPrimary,
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
        Text("No confirmed eggs yet", fontSize = 13.sp, color = AgarthaTheme.colors.textSecondary)
    } else {
        StatRun(
            listOf(
                Stat(confirmedEggs.toString(), "confirmed"),
                Stat(speciesCount.toString(), "species"),
                Stat(samplesTotal.toString(), "samples"),
            )
        )
    }
}

