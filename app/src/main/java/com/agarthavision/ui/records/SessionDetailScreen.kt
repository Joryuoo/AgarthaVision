@file:Suppress("FunctionNaming")

package com.agarthavision.ui.records

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agarthavision.R
import com.agarthavision.domain.model.Report
import com.agarthavision.domain.model.ReportSyncStatus
import com.agarthavision.domain.model.SampleStatus
import com.agarthavision.domain.usecase.records.SampleRecordItem
import com.komoui.components.Badge as KomoBadge
import com.komoui.components.BadgeVariant
import com.komoui.components.Button as KomoButton
import com.komoui.components.ButtonSize
import com.komoui.components.ButtonVariant
import com.komoui.themes.styles
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Shows verified samples in a selected session and exposes Generate Report.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    onSampleClick: (String) -> Unit,
    viewModel: SessionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val session = state.session
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val generatedMessage = stringResource(R.string.report_generated_toast)
    val generationFailedTemplate = stringResource(R.string.report_generation_failed)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SessionDetailEvent.ReportGenerated -> {
                    snackbarHostState.showSnackbar(generatedMessage)
                }
            }
        }
    }

    LaunchedEffect(state.generationError) {
        state.generationError?.let { error ->
            snackbarHostState.showSnackbar(generationFailedTemplate.format(error))
        }
    }

    Scaffold(
        containerColor = MaterialTheme.styles.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        session?.session?.label?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.session_detail_title),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.session_detail_back),
                            tint = MaterialTheme.styles.foreground,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.styles.background,
                    titleContentColor = MaterialTheme.styles.foreground,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
        ) {
            ReportsSection(
                reports = state.reports,
                isGenerating = state.isGenerating,
                onGenerate = viewModel::generateReport,
                onShare = { report -> shareReportCsv(context, report) },
                modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
            )
            if (session != null) {
                EpgSummaryCard(
                    totalEggCount = state.totalEggCount,
                    epg = state.epg,
                    counts = state.eggCounts,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            if (session == null || session.samples.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.session_detail_empty),
                        color = MaterialTheme.styles.mutedForeground,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(start = 0.dp, top = 12.dp, end = 0.dp, bottom = 24.dp),
                ) {
                    items(session.samples, key = { it.sample.id }) { item ->
                        SampleRow(item = item, onClick = { onSampleClick(item.sample.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportsSection(
    reports: List<Report>,
    isGenerating: Boolean,
    onGenerate: () -> Unit,
    onShare: (Report) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.styles.card,
            contentColor = MaterialTheme.styles.cardForeground,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.report_section_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.styles.foreground,
                )
                KomoButton(
                    onClick = onGenerate,
                    enabled = !isGenerating,
                    size = ButtonSize.Sm,
                ) {
                    Text(
                        stringResource(
                            if (isGenerating) R.string.report_generating else R.string.report_generate,
                        ),
                    )
                }
            }

            if (reports.isEmpty()) {
                Text(
                    text = stringResource(R.string.report_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.styles.mutedForeground,
                )
            } else {
                reports.forEach { report ->
                    ReportRow(report = report, onShare = { onShare(report) })
                }
            }
        }
    }
}

@Composable
private fun ReportRow(report: Report, onShare: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = report.csvFilePath != null, onClick = onShare)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = report.generatedAt.toEpochMilli().formatDateTime(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.styles.foreground,
            )
            Text(
                text = report.positiveSpecies.joinToString(", ").ifBlank {
                    stringResource(R.string.report_no_positive_species)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.styles.mutedForeground,
            )
        }
        ReportSyncBadge(status = report.supabaseStatus)
    }
}

@Composable
private fun ReportSyncBadge(status: ReportSyncStatus) {
    val (variant, label) = when (status) {
        ReportSyncStatus.SYNCED -> BadgeVariant.Default to R.string.report_status_synced
        ReportSyncStatus.SYNC_FAILED -> BadgeVariant.Destructive to R.string.report_status_failed
        ReportSyncStatus.PENDING -> BadgeVariant.Secondary to R.string.report_status_pending
    }
    KomoBadge(variant = variant) {
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall)
    }
}

private fun shareReportCsv(context: Context, report: Report) {
    val path = report.csvFilePath ?: return
    val file = File(path)
    if (!file.exists()) return
    val uri = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrElse {
        // Fall back to ACTION_VIEW on the absolute path if FileProvider isn't configured.
        // The medtech can still find the file under Downloads.
        return
    }
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

@Composable
private fun EpgSummaryCard(
    totalEggCount: Int,
    epg: Int,
    counts: List<EggCountSummary>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.styles.card,
            contentColor = MaterialTheme.styles.cardForeground,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.session_detail_epg_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.styles.mutedForeground,
            )
            Text(
                text = epg.toString(),
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.styles.foreground,
            )
            Text(
                text = stringResource(R.string.session_detail_epg_total_label, totalEggCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.styles.mutedForeground,
            )

            if (counts.isEmpty()) {
                Text(
                    text = stringResource(R.string.session_detail_epg_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.styles.mutedForeground,
                )
            } else {
                counts.forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = entry.species,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.styles.foreground,
                        )
                        Text(
                            text = entry.count.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.styles.mutedForeground,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SampleRow(item: SampleRecordItem, onClick: () -> Unit) {
    val primary = item.primaryDetection
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.styles.secondary,
            contentColor = MaterialTheme.styles.secondaryForeground,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = primary?.expertClass
                            ?: primary?.classLabel
                            ?: stringResource(R.string.session_detail_unknown_detection),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.styles.foreground,
                    )
                    Text(
                        text = stringResource(R.string.session_detail_confidence, primary?.confidence ?: 0f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.styles.mutedForeground,
                    )
                }
                SyncStatusBadge(status = item.sample.status)
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.session_detail_captured, item.sample.timestamp.formatDateTime()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.styles.mutedForeground,
            )
            KomoButton(
                onClick = onClick,
                variant = ButtonVariant.Ghost,
                size = ButtonSize.Default,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.session_detail_open_sample))
            }
        }
    }
}

@Composable
internal fun SyncStatusBadge(status: SampleStatus) {
    val variant = when (status) {
        SampleStatus.SYNCED -> BadgeVariant.Default
        SampleStatus.SYNC_FAILED -> BadgeVariant.Destructive
        else -> BadgeVariant.Secondary
    }

    KomoBadge(variant = variant) {
        Text(status.value.uppercase(), style = MaterialTheme.typography.labelSmall)
    }
}

internal fun Long.formatDateTime(): String =
    Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
