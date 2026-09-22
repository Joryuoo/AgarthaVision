@file:Suppress("TooManyFunctions", "LongParameterList", "CyclomaticComplexMethod")

package com.agarthavision.ui.records

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agarthavision.R
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.Report
import com.agarthavision.domain.model.SessionLinkState
import com.agarthavision.ui.components.DateRangeFilterBar
import com.agarthavision.ui.components.EmptyState
import com.agarthavision.ui.components.ScreenHeader
import com.agarthavision.ui.components.SearchInput
import com.agarthavision.ui.components.SkeletonBox
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.DialogShape
import com.agarthavision.ui.theme.Spacing

private const val REPORTS_SKELETON_COUNT = 6
private const val STATS_REPORTS_WEIGHT = 0.25f
private const val STATS_SPECIES_WEIGHT = 0.75f
private val STATS_REPORT_NUMBER_FONT_SIZE = 24.sp
private val STATS_SPECIES_NAME_FONT_SIZE = 18.sp

@Composable
fun RecordsScreen(
    @Suppress("UNUSED_PARAMETER")
    onNavigate: (String) -> Unit = {},
    onSessionClick: (String) -> Unit,
    @Suppress("UNUSED_PARAMETER")
    onBackClick: () -> Unit = {},
    viewModel: RecordsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var shareError by remember { mutableStateOf<Int?>(null) }
    var showSpeciesDialog by remember { mutableStateOf(false) }

    if (showSpeciesDialog) {
        SpeciesFilterDialog(
            selectedSpecies = state.selectedSpecies,
            onSelectSpecies = viewModel::onSpeciesSelected,
            onDismiss = { showSpeciesDialog = false },
        )
    }

    LaunchedEffect(shareError) {
        shareError?.let { messageRes ->
            snackbarHostState.showSnackbar(context.getString(messageRes))
            shareError = null
        }
    }

    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= listState.layoutInfo.totalItemsCount - 1 && state.canLoadMore
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.onLoadMore()
    }

    Scaffold(
        topBar = {
            ScreenHeader(
                title = stringResource(R.string.reports_title),
                purpose = stringResource(R.string.reports_subtitle_purpose),
                status = stringResource(
                    if (!state.isNarrowed) {
                        R.string.reports_scope_all
                    } else {
                        R.string.reports_scope_filtered
                    },
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = AgarthaTheme.colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { inner ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(bottom = Spacing.md),
        ) {
            item {
                Spacer(Modifier.height(Spacing.xs))
                SearchInput(
                    value = state.searchQuery,
                    onValueChange = viewModel::onSearchChanged,
                    modifier = Modifier.padding(horizontal = Spacing.xl),
                )
            }

            item {
                Spacer(Modifier.height(Spacing.md))
                val selected = state.selectedSpecies
                val activeFilter = when (selected) {
                    null -> stringResource(R.string.records_species_all)
                    EggSpecies.OTHER -> stringResource(R.string.records_species_others)
                    else -> selected.displayName
                }
                StatsRow(
                    reportsCount = if (state.isLoading) "—" else state.totalReports.toString(),
                    activeFilter = activeFilter,
                    onSpeciesFilterClick = { showSpeciesDialog = true },
                    modifier = Modifier.padding(horizontal = Spacing.xl),
                )
            }
            item {
                Spacer(Modifier.height(Spacing.xs))
                DateRangeFilterBar(
                    startDate = state.startDate,
                    endDate = state.endDate,
                    onRangeSelected = viewModel::onDateRangeSelected,
                    modifier = Modifier.padding(horizontal = Spacing.xl),
                )
            }
            item { Spacer(Modifier.height(Spacing.xs)) }

            when {
                state.isLoading -> items(REPORTS_SKELETON_COUNT) {
                    ReportCardSkeleton(modifier = Modifier.padding(horizontal = Spacing.xl, vertical = 4.dp))
                }
                state.reports.isEmpty() -> item {
                    ReportsEmptyState(narrowed = state.isNarrowed)
                }
                else -> {
                    items(state.reports, key = { it.id }) { report ->
                        ReportCard(
                            report = report,
                            onSessionClick = { onSessionClick(report.sessionId) },
                            onOpenPdf = {
                                shareError = viewReportPdf(context, report.pdfFilePath)
                            },
                            onOpenCsv = {
                                shareError = viewReportCsv(context, report.csvFilePath)
                            },
                            onSharePdf = {
                                shareError = shareReportPdf(context, report.pdfFilePath)
                            },
                            onShareCsv = {
                                shareError = shareReportCsv(context, report.csvFilePath)
                            },
                            modifier = Modifier.padding(horizontal = Spacing.xl, vertical = 4.dp),
                        )
                    }
                    if (state.canLoadMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Spacing.md),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    color = AgarthaTheme.colors.accent,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val RecordsState.isNarrowed: Boolean
    get() = searchQuery.isNotBlank() || selectedSpecies != null || startDate != null || endDate != null

@Composable
private fun ReportsEmptyState(narrowed: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl, vertical = Spacing.xxxl),
        contentAlignment = Alignment.Center,
    ) {
        if (narrowed) {
            EmptyState(
                icon = Icons.Outlined.SearchOff,
                title = stringResource(R.string.reports_no_match_title),
                body = stringResource(R.string.reports_no_match_body),
            )
        } else {
            EmptyState(
                icon = Icons.Outlined.Inbox,
                title = stringResource(R.string.reports_empty_title),
                body = stringResource(R.string.reports_empty_body),
            )
        }
    }
}

@Composable
private fun StatsRow(
    reportsCount: String,
    activeFilter: String,
    onSpeciesFilterClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        val colors = AgarthaTheme.colors
        StatTile(
            label = "Reports",
            value = reportsCount,
            modifier = Modifier
                .weight(STATS_REPORTS_WEIGHT)
                .fillMaxHeight(),
            colors = StatTileColors(
                bgColor = colors.accent,
                contentColor = colors.onAccent,
                labelColor = colors.onAccent.copy(alpha = 0.8f),
            ),
            valueFontSize = STATS_REPORT_NUMBER_FONT_SIZE,
        )
        StatTile(
            label = "Species filter",
            value = activeFilter,
            modifier = Modifier
                .weight(STATS_SPECIES_WEIGHT)
                .fillMaxHeight(),
            colors = StatTileColors(
                bgColor = colors.gold,
                contentColor = colors.onGold,
                labelColor = colors.onGold.copy(alpha = 0.75f),
            ),
            valueFontSize = STATS_SPECIES_NAME_FONT_SIZE,
            onClick = onSpeciesFilterClick,
            showDropdown = true,
        )
    }
}

private data class StatTileColors(
    val bgColor: androidx.compose.ui.graphics.Color,
    val contentColor: androidx.compose.ui.graphics.Color,
    val labelColor: androidx.compose.ui.graphics.Color,
)

@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    colors: StatTileColors,
    valueFontSize: TextUnit = 20.sp,
    onClick: (() -> Unit)? = null,
    showDropdown: Boolean = false,
) {
    val neutralBg = colors.bgColor == AgarthaTheme.colors.surfaceVariant ||
        colors.bgColor == AgarthaTheme.colors.surface
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(colors.bgColor, shape)
            .border(
                1.dp,
                if (neutralBg) AgarthaTheme.colors.border else androidx.compose.ui.graphics.Color.Transparent,
                shape,
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 10.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label.uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.labelColor,
                letterSpacing = 0.6.sp,
                lineHeight = 12.sp,
            )
            if (showDropdown) {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = colors.labelColor,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = valueFontSize,
            fontWeight = FontWeight.Bold,
            color = colors.contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum, cv11, ss01, ss03"),
            lineHeight = valueFontSize,
        )
    }
}

@Composable
private fun SpeciesFilterDialog(
    selectedSpecies: EggSpecies?,
    onSelectSpecies: (EggSpecies?) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(
        null to stringResource(R.string.records_species_all),
        EggSpecies.ASCARIS to EggSpecies.ASCARIS.displayName,
        EggSpecies.TRICHURIS to EggSpecies.TRICHURIS.displayName,
        EggSpecies.HOOKWORM to EggSpecies.HOOKWORM.displayName,
        EggSpecies.OTHER to stringResource(R.string.records_species_others),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = DialogShape,
        containerColor = AgarthaTheme.colors.surface,
        titleContentColor = AgarthaTheme.colors.textPrimary,
        title = {
            Text(
                text = stringResource(R.string.records_species_modal_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                options.forEach { (species, label) ->
                    val isSelected = species == selectedSpecies
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .then(
                                if (isSelected) {
                                    Modifier.background(AgarthaTheme.colors.accentTint)
                                } else {
                                    Modifier
                                },
                            )
                            .clickable {
                                onSelectSpecies(species)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) AgarthaTheme.colors.accent else AgarthaTheme.colors.textPrimary,
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = AgarthaTheme.colors.accent,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(android.R.string.cancel),
                    color = AgarthaTheme.colors.textSecondary,
                )
            }
        },
    )
}

@Composable
private fun ReportCard(
    report: Report,
    onSessionClick: () -> Unit,
    onOpenPdf: () -> Unit,
    onOpenCsv: () -> Unit,
    onSharePdf: () -> Unit,
    onShareCsv: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AgarthaTheme.colors.surface, RoundedCornerShape(12.dp))
            .border(1.dp, AgarthaTheme.colors.border, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onSessionClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = report.generatedAt.formatReportDateTime(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AgarthaTheme.colors.textPrimary,
                )
                Text(
                    text = stringResource(
                        R.string.reports_session_link,
                        report.sessionLabel ?: report.sessionId.take(8),
                    ),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = AgarthaTheme.colors.accent,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            ReportStatusPill(status = report.supabaseStatus)
        }

        Spacer(Modifier.height(8.dp))
        val noSpeciesText = stringResource(R.string.report_no_positive_species)
        val moreFormat = stringResource(R.string.reports_species_more)
        Text(
            text = formatPositiveSpeciesSummary(
                species = report.positiveSpecies,
                emptyFallback = noSpeciesText,
                moreFormat = moreFormat,
            ),
            fontSize = 12.sp,
            color = AgarthaTheme.colors.textSecondary,
        )

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = AgarthaTheme.colors.border, thickness = 1.dp)
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatRun(
                listOf(
                    Stat(report.totalEggsConfirmed.toString(), "eggs"),
                    Stat(
                        if (report.positiveSpecies.isEmpty()) "0" else report.positiveSpecies.size.toString(),
                        "species",
                    ),
                    Stat(report.totalSamples.toString(), "samples"),
                ),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (report.pdfFilePath != null) {
                    ActionChip(
                        label = stringResource(R.string.reports_open_pdf),
                        onClick = onOpenPdf,
                    )
                    ActionChip(
                        label = stringResource(R.string.reports_share_pdf),
                        onClick = onSharePdf,
                    )
                }
                if (report.csvFilePath != null) {
                    ActionChip(
                        label = stringResource(R.string.reports_open_csv),
                        onClick = onOpenCsv,
                    )
                    ActionChip(
                        label = stringResource(R.string.reports_share_csv),
                        onClick = onShareCsv,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionChip(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(AgarthaTheme.colors.surfaceVariant)
            .border(1.dp, AgarthaTheme.colors.border, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = AgarthaTheme.colors.textPrimary,
        )
    }
}

@Composable
private fun ReportCardSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AgarthaTheme.colors.surface, RoundedCornerShape(12.dp))
            .border(1.dp, AgarthaTheme.colors.border, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        SkeletonBox(modifier = Modifier.width(140.dp).height(20.dp))
        Spacer(Modifier.height(4.dp))
        SkeletonBox(modifier = Modifier.width(180.dp).height(12.dp))
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = AgarthaTheme.colors.border, thickness = 1.dp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SkeletonBox(modifier = Modifier.width(48.dp).height(28.dp))
            SkeletonBox(modifier = Modifier.width(48.dp).height(28.dp))
            SkeletonBox(modifier = Modifier.width(48.dp).height(28.dp))
        }
    }
}

@Composable
internal fun StatusPill(linkState: SessionLinkState) {
    val colors = AgarthaTheme.colors
    val (bg, fg, text) = when (linkState) {
        SessionLinkState.SYNCED -> Triple(
            colors.successTint,
            colors.successText,
            stringResource(R.string.report_status_synced),
        )
        SessionLinkState.PENDING -> Triple(
            colors.warningTint,
            colors.warningText,
            stringResource(R.string.records_status_pending_sync),
        )
    }
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(999.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
        )
    }
}

internal const val MAX_DISPLAYED_SPECIES = 3
internal const val TRUNCATED_SPECIES_PREFIX_COUNT = 2

internal fun formatPositiveSpeciesSummary(
    species: List<String>,
    emptyFallback: String,
    moreFormat: String,
): String {
    return when {
        species.isEmpty() -> emptyFallback
        species.size <= MAX_DISPLAYED_SPECIES -> species.joinToString(", ")
        else -> {
            val visible = species.take(TRUNCATED_SPECIES_PREFIX_COUNT).joinToString(", ")
            val remaining = species.size - TRUNCATED_SPECIES_PREFIX_COUNT
            "$visible, ${String.format(moreFormat, remaining)}"
        }
    }
}

