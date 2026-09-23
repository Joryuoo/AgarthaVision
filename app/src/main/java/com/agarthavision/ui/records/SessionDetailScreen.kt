@file:Suppress("FunctionNaming", "LongMethod", "CyclomaticComplexMethod", "ReturnCount")

package com.agarthavision.ui.records

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agarthavision.R
import com.agarthavision.domain.model.LpfDensity
import com.agarthavision.domain.model.Report
import com.agarthavision.ui.components.BackArrow
import com.agarthavision.ui.components.SkeletonBox
import androidx.compose.ui.text.style.TextOverflow
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.HeroDensityStyle
import com.agarthavision.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

internal data class SessionDetailUi(
    val id: String,
    val label: String?,
    val dateLabel: String,
    val timeLabel: String,
    val confirmedEggs: Int,
    val speciesCount: Int,
    val samplesTotal: Int,
    val lpfPerSpecies: Map<String, LpfDensity>,
    /** Every distinct species confirmed this session, custom "Other" names included. */
    val detectedSpecies: List<String>,
    val verifiedSamples: List<SampleUi>,
)

internal data class SampleUi(
    val id: String,
    val source: SampleSource,
    val species: String,
    val confidence: Int?,
    val filePath: String?,
    val storagePath: String?,
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
    onOpenVerifyQueue: () -> Unit = {},
    viewModel: SessionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val generatedMessage = stringResource(R.string.report_generated_toast)
    val shareActionLabel = stringResource(R.string.report_share_action)
    var shareError by remember { mutableStateOf<Int?>(null) }
    val generationFailedTemplate = stringResource(R.string.report_generation_failed)
    val restoringMessage = stringResource(R.string.report_restoring)
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

                // Launched rather than awaited: showSnackbar suspends until the snackbar
                // goes away, and collecting the next event behind it would hold the opened
                // file back by the length of a snackbar.
                SessionDetailEvent.ReportRestoreStarted -> {
                    launch { snackbarHostState.showSnackbar(restoringMessage) }
                }

                is SessionDetailEvent.ReportRestored -> {
                    shareError = when {
                        event.pdfPath != null -> viewReportPdf(context, event.pdfPath)
                        event.csvPath != null -> viewReportCsv(context, event.csvPath)
                        else -> R.string.report_share_file_gone
                    }
                }

                SessionDetailEvent.ReportRestoreFailed ->
                    shareError = R.string.report_restore_failed
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

    if (!state.sessionResolved) {
        SessionDetailSkeleton(onBack = onBack)
        return
    }
    val unavailable = state.unavailable
    if (unavailable != null) {
        SessionDetailUnavailableScreen(unavailable = unavailable, onBack = onBack)
        return
    }
    if (sessionDetail == null) {
        SessionDetailSkeleton(onBack = onBack)
        return
    }

    Scaffold(
        topBar = {
            SessionDetailAppBar(
                title = sessionDetail.label ?: "Session ${sessionDetail.id}",
                subtitle = "${sessionDetail.dateLabel} · ${sessionDetail.timeLabel}",
                onBack = onBack,
                showVerify = state.canOpenVerifyQueue,
                onOpenVerifyQueue = onOpenVerifyQueue,
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
                val result = when {
                    report.pdfFilePath != null -> viewReportPdf(context, report.pdfFilePath)
                    report.csvFilePath != null -> viewReportCsv(context, report.csvFilePath)
                    else -> R.string.report_share_missing_path
                }
                // A file this device has never had is the synced-from-elsewhere case, not a
                // mistake to scold the medtech for. Fetch it instead of reporting it.
                if (result == R.string.report_share_file_gone ||
                    result == R.string.report_share_missing_path
                ) {
                    viewModel.restoreReportFiles(report.id)
                } else {
                    shareError = result
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
            storagePath = item.sample.storagePath,
        )
    }

    return SessionDetailUi(
        id = sessionRecord.id.takeLast(SESSION_ID_SHORT_LENGTH),
        label = sessionRecord.label,
        dateLabel = startedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
        timeLabel = startedAt.format(DateTimeFormatter.ofPattern("HH:mm")),
        confirmedEggs = state.totalEggCount,
        speciesCount = state.eggCounts.size,
        samplesTotal = sessionData.samples.size,
        lpfPerSpecies = state.lpfPerSpecies,
        detectedSpecies = state.eggCounts.map { it.species },
        verifiedSamples = samples,
    )
}

@Composable
private fun SessionDetailSkeleton(onBack: () -> Unit) {
    val colors = AgarthaTheme.colors
    Scaffold(
        topBar = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                BackArrow(
                    onBack = onBack,
                    contentDescription = stringResource(R.string.session_detail_back),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = Spacing.sm),
                ) {
                    SkeletonBox(modifier = Modifier.width(160.dp).height(22.dp))
                    Spacer(Modifier.height(4.dp))
                    SkeletonBox(modifier = Modifier.width(200.dp).height(14.dp))
                }
            }
        },
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = Spacing.xl)
                .padding(top = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // LPF hero placeholder
            SkeletonBox(
                modifier = Modifier.fillMaxWidth().height(150.dp),
                shape = RoundedCornerShape(12.dp),
            )
            // Reports row placeholder
            SkeletonBox(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(8.dp),
            )
            // 3-column grid of 6 sample-tile placeholders (2 rows × 3)
            repeat(2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SkeletonBox(modifier = Modifier.weight(1f).aspectRatio(1f), shape = RoundedCornerShape(8.dp))
                    SkeletonBox(modifier = Modifier.weight(1f).aspectRatio(1f), shape = RoundedCornerShape(8.dp))
                    SkeletonBox(modifier = Modifier.weight(1f).aspectRatio(1f), shape = RoundedCornerShape(8.dp))
                }
            }
        }
    }
}

@Composable
private fun SessionDetailAppBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    showVerify: Boolean = false,
    onOpenVerifyQueue: () -> Unit = {},
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
        Column(Modifier.weight(1f).padding(start = Spacing.sm)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary)
            Text(
                subtitle,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary,
                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                modifier = Modifier.padding(top = 2.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showVerify) {
            IconButton(onClick = onOpenVerifyQueue) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.FactCheck,
                    contentDescription = stringResource(R.string.session_detail_open_verify),
                    tint = colors.textPrimary,
                )
            }
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
                LpfHeroCard(
                    session = session,
                    modifier = Modifier.semantics(mergeDescendants = true) {
                        contentDescription = "Total confirmed: ${session.confirmedEggs}, " +
                            "${session.speciesCount} species, " +
                            "${session.samplesTotal} fields"
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
        LpfHeroCard(session = session)
        Spacer(Modifier.height(Spacing.md))
        ReportsSection(state = state)
        Spacer(Modifier.height(60.dp))
        EmptyStateGraphic()
    }
}

@Composable
internal fun LpfHeroCard(
    session: SessionDetailUi,
    modifier: Modifier = Modifier,
) {
    val confirmedEggs = session.confirmedEggs
    val speciesCount = session.speciesCount
    val samplesTotal = session.samplesTotal
    val themeColors = AgarthaTheme.colors
    val onCard = themeColors.onAccent
    val onCardMuted = onCard.copy(alpha = 0.72f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(themeColors.accent, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp)),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(onCard.copy(alpha = 0.10f), Color.Transparent),
                            center = Offset(size.width, 0f),
                            radius = 220.dp.toPx(),
                        ),
                    )
                },
        )
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "TOTAL EGGS CONFIRMED",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = onCardMuted,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                confirmedEggs.toString(),
                color = onCard,
                style = HeroDensityStyle,
            )
            Spacer(Modifier.height(12.dp))
            LpfMeta(confirmedEggs, speciesCount, samplesTotal, onCard)
            
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.session_detail_lpf_title).uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = onCardMuted,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(8.dp))

            // A wholly negative session is a real result - and in surveillance it is the most
            // common one, and the one a report is most often needed for. It gets a sentence
            // saying so, not an absent section the medtech has to interpret. Stated once here
            // rather than as a row of `0-0 LPF` per species, and the PDF says the same thing
            // the same way: PB-18 asks for one of the two, consistently.
            if (session.lpfPerSpecies.isEmpty()) {
                Text(
                    stringResource(R.string.session_detail_no_parasites),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = onCard,
                    modifier = Modifier.testTag(SessionDetailTestTags.NO_PARASITES),
                )
            }
            session.lpfPerSpecies.forEach { (species, density) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(SessionDetailTestTags.lpfRow(species)),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        species,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        // Binomials are italic per the design system; "Hookworm" is a common
                        // name covering two genera, so it is not.
                        fontStyle = if (species.isBinomial()) FontStyle.Italic else FontStyle.Normal,
                        color = onCard,
                        modifier = Modifier.weight(1f),
                    )
                    density.descriptor?.let { descriptor ->
                        Text(
                            stringResource(descriptor.labelRes),
                            fontSize = 12.sp,
                            color = onCardMuted,
                            modifier = Modifier.padding(end = 10.dp),
                        )
                    }
                    Text(
                        stringResource(R.string.lpf_range_value, density.min, density.max),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = onCard,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun LpfMeta(
    confirmedEggs: Int,
    speciesCount: Int,
    samplesTotal: Int,
    contentColor: Color,
) {
    val labelColor = contentColor.copy(alpha = 0.72f)
    if (confirmedEggs == 0) {
        Text("No confirmed eggs in $samplesTotal fields", fontSize = 13.sp, color = labelColor)
    } else {
        StatRun(
            listOf(
                Stat(speciesCount.toString(), "species"),
                Stat(samplesTotal.toString(), "fields"),
            ),
            valueColor = contentColor,
            labelColor = labelColor,
            separatorColor = contentColor.copy(alpha = 0.6f),
        )
    }
}

