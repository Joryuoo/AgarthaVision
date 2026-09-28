@file:Suppress("TooManyFunctions", "LongParameterList", "CyclomaticComplexMethod")

package com.agarthavision.ui.records

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agarthavision.R
import com.agarthavision.domain.model.CLINICAL_ZONE
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.Report
import com.agarthavision.domain.model.ReportSyncStatus
import com.agarthavision.domain.model.SessionLinkState
import com.agarthavision.ui.components.DateRangeFilterBar
import com.agarthavision.ui.components.EmptyState
import com.agarthavision.ui.components.SkeletonBox
import com.agarthavision.ui.icons.AgarthaIcons
import com.agarthavision.ui.icons.FileOpen
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.AppColors
import com.agarthavision.ui.theme.DialogShape
import com.agarthavision.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    val colors = AgarthaTheme.colors
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
    var isFiltersVisible by rememberSaveable { mutableStateOf(true) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta < -8f) {
                    isFiltersVisible = false
                } else if (delta > 8f) {
                    isFiltersVisible = true
                }
                return Offset.Zero
            }
        }
    }

    val isFiltersRetracted by remember {
        derivedStateOf {
            !isFiltersVisible && (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0)
        }
    }

    LaunchedEffect(state.selectedSpecies, state.startDate, state.endDate, state.searchQuery) {
        isFiltersVisible = true
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= listState.layoutInfo.totalItemsCount - 1 && state.canLoadMore
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.onLoadMore()
    }

    val nowMillis = remember { System.currentTimeMillis() }
    val todayStartMillis = remember(nowMillis) {
        Instant.ofEpochMilli(nowMillis).atZone(CLINICAL_ZONE).toLocalDate()
            .atStartOfDay(CLINICAL_ZONE).toInstant().toEpochMilli()
    }
    val sevenDaysAgoMillis = remember(nowMillis) {
        nowMillis - 7 * 24 * 60 * 60 * 1000L
    }

    val groupedReports = remember(state.reports, state.startDate, state.endDate, todayStartMillis, sevenDaysAgoMillis) {
        val hasDateFilter = state.startDate != null || state.endDate != null
        val today = mutableListOf<Report>()
        val thisWeek = mutableListOf<Report>()
        val earlier = mutableListOf<Report>()
        for (report in state.reports) {
            val t = report.generatedAt.toEpochMilli()
            when {
                t >= todayStartMillis -> today.add(report)
                t >= sevenDaysAgoMillis -> thisWeek.add(report)
                hasDateFilter -> earlier.add(report)
            }
        }
        buildList {
            if (today.isNotEmpty()) add("TODAY" to today)
            if (thisWeek.isNotEmpty()) add("THIS WEEK" to thisWeek)
            if (earlier.isNotEmpty()) add("EARLIER" to earlier)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .background(colors.background),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 480.dp)
                    .align(Alignment.TopCenter),
            ) {
                // 1. Header (tight padding)
                ReportsScreenHeader(
                    total = state.totalReports,
                    unsynced = state.unsyncedReports,
                )

                // 2. Search Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surfaceVariant)
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        BasicTextField(
                            value = state.searchQuery,
                            onValueChange = viewModel::onSearchChanged,
                            modifier = Modifier.weight(1f),
                            textStyle = TextStyle(
                                fontSize = 15.sp,
                                color = colors.textPrimary,
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(colors.accent),
                            decorationBox = { innerTextField ->
                                if (state.searchQuery.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.reports_search_placeholder),
                                        fontSize = 15.sp,
                                        color = colors.textTertiary,
                                    )
                                }
                                innerTextField()
                            },
                        )
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.onSearchChanged("") },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = null,
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }

                // 3. StatsRow & DateRangeFilterBar (retractable when scrolling down)
                AnimatedVisibility(
                    visible = !isFiltersRetracted,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 4.dp),
                    ) {
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
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        DateRangeFilterBar(
                            startDate = state.startDate,
                            endDate = state.endDate,
                            onRangeSelected = viewModel::onDateRangeSelected,
                        )
                    }
                }

                // 4. LazyColumn list of reports
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(nestedScrollConnection),
                    contentPadding = PaddingValues(bottom = Spacing.md),
                ) {
                    when {
                        state.isLoading && state.reports.isEmpty() -> items(REPORTS_SKELETON_COUNT) {
                            ReportCardSkeleton(modifier = Modifier.padding(horizontal = Spacing.xl, vertical = 4.dp))
                        }
                        groupedReports.isEmpty() -> {
                            item {
                                val isDefaultDate = state.startDate == null && state.endDate == null
                                val hasOlderRecords = isDefaultDate && state.reports.isNotEmpty()
                                if (hasOlderRecords) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = Spacing.xl, vertical = Spacing.xxxl),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        EmptyState(
                                            icon = Icons.Outlined.Inbox,
                                            title = "No recent reports",
                                            body = "No reports found for today or this week. " +
                                                "Select a date range to view earlier reports.",
                                        )
                                    }
                                } else {
                                    ReportsEmptyState(narrowed = state.isNarrowed)
                                }
                            }
                        }
                        else -> {
                            groupedReports.forEach { (sectionTitle, items) ->
                                item(key = "section_$sectionTitle") {
                                    Text(
                                        text = sectionTitle,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp,
                                        color = colors.textSecondary,
                                        modifier = Modifier.padding(
                                            start = 20.dp,
                                            end = 20.dp,
                                            top = 10.dp,
                                            bottom = 4.dp,
                                        ),
                                    )
                                }
                                items(items, key = { it.id }) { report ->
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
                            }
                            if (state.canLoadMore) {
                                item(key = "load_more_indicator") {
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
    }
}

@Composable
private fun ReportsScreenHeader(
    total: Int,
    unsynced: Int,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 2.dp),
    ) {
        Text(
            text = stringResource(R.string.reports_title),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = if (colors.isDark) Color.White else Color.Black,
            letterSpacing = (-0.5).sp,
            lineHeight = 34.sp,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 1.dp),
        ) {
            val reportText = if (total == 1) "1 report" else "$total reports"
            Text(
                text = reportText,
                fontSize = 14.sp,
                color = colors.textSecondary,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = " · ",
                fontSize = 14.sp,
                color = colors.textSecondary,
                fontWeight = FontWeight.Medium,
            )
            if (unsynced > 0) {
                Text(
                    text = "$unsynced not synced",
                    fontSize = 14.sp,
                    color = colors.goldText,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                Text(
                    text = stringResource(R.string.settings_sync_all_synced),
                    fontSize = 14.sp,
                    color = colors.textSecondary,
                    fontWeight = FontWeight.Medium,
                )
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
                bgColor = colors.brandFill,
                contentColor = colors.onBrandFill,
                labelColor = colors.onBrandFill.copy(alpha = 0.8f),
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
        containerColor = AgarthaTheme.colors.surfaceHigh,
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

private val REPORT_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
private val REPORT_DATE_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.getDefault())

internal fun formatReportSubtitle(generatedAt: Instant, patientName: String?): String {
    val localDateTime = generatedAt.atZone(ZoneId.systemDefault())
    val isToday = localDateTime.toLocalDate() == LocalDate.now(ZoneId.systemDefault())
    val formatter = if (isToday) REPORT_TIME_FORMATTER else REPORT_DATE_TIME_FORMATTER
    val timeStr = formatter.format(localDateTime)
    return if (patientName.isNullOrBlank()) {
        "Generated $timeStr"
    } else {
        "Generated $timeStr · $patientName"
    }
}

private data class StatusBadgeStyle(
    val bg: Color,
    val fg: Color,
    val icon: ImageVector,
    val textRes: Int,
)

@Composable
private fun ReportStatusBadge(status: ReportSyncStatus) {
    val colors = AgarthaTheme.colors
    val style = when (status) {
        ReportSyncStatus.SYNCED -> StatusBadgeStyle(
            bg = colors.successTint,
            fg = colors.successText,
            icon = Icons.Outlined.CloudDone,
            textRes = R.string.report_status_synced,
        )
        ReportSyncStatus.SYNC_FAILED -> StatusBadgeStyle(
            bg = colors.dangerTint,
            fg = colors.dangerText,
            icon = Icons.Outlined.CloudOff,
            textRes = R.string.report_status_failed,
        )
        ReportSyncStatus.PENDING -> StatusBadgeStyle(
            bg = colors.warningTint,
            fg = colors.warningText,
            icon = Icons.Outlined.CloudQueue,
            textRes = R.string.records_status_pending_sync,
        )
    }
    Row(
        modifier = Modifier
            .background(style.bg, RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = style.icon,
            contentDescription = null,
            tint = style.fg,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = stringResource(style.textRes),
            color = style.fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            style = TextStyle(fontFeatureSettings = "tnum"),
        )
    }
}

@Composable
private fun SpeciesBulletItem(
    speciesName: String,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    val bulletColor = when {
        speciesName.contains("ascaris", ignoreCase = true) -> colors.accent
        speciesName.contains("trichuris", ignoreCase = true) -> AppColors.Gold
        speciesName.contains("hookworm", ignoreCase = true) -> AppColors.Amber
        else -> colors.accent
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(bulletColor, CircleShape),
        )
        Text(
            text = speciesName,
            fontSize = 12.sp,
            fontStyle = FontStyle.Italic,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun ReportStatCount(count: String, label: String) {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = count,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = AgarthaTheme.colors.textPrimary,
        )
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            color = AgarthaTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun ReportActionButton(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    isPrimary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    val bg = if (isPrimary) colors.accentTint else colors.surface
    val contentColor = if (isPrimary) colors.accent else colors.textPrimary
    val borderModifier = if (!isPrimary) {
        Modifier.border(1.dp, colors.border, RoundedCornerShape(8.dp))
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .then(borderModifier)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
            .semantics { this.contentDescription = contentDescription },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
        )
    }
}

@Composable
internal fun ReportCard(
    report: Report,
    onSessionClick: () -> Unit,
    onOpenPdf: () -> Unit,
    onOpenCsv: () -> Unit,
    onSharePdf: () -> Unit,
    onShareCsv: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(12.dp))
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onSessionClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = report.sessionLabel ?: report.sessionId.take(8),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )
            ReportStatusBadge(status = report.supabaseStatus)
        }

        Spacer(Modifier.height(2.dp))

        Text(
            text = formatReportSubtitle(report.generatedAt, report.patientName),
            fontSize = 12.sp,
            color = colors.textSecondary,
        )

        Spacer(Modifier.height(4.dp))

        if (report.positiveSpecies.isEmpty()) {
            Text(
                text = stringResource(R.string.report_no_positive_species),
                fontSize = 12.sp,
                color = colors.textSecondary,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                report.positiveSpecies.forEach { speciesName ->
                    SpeciesBulletItem(speciesName = speciesName)
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReportStatCount(
                count = report.totalEggsConfirmed.toString(),
                label = "eggs",
            )
            ReportStatCount(
                count = if (report.positiveSpecies.isEmpty()) "0" else report.positiveSpecies.size.toString(),
                label = "species",
            )
            ReportStatCount(
                count = report.totalSamples.toString(),
                label = "samples",
            )
        }

        val hasPdf = report.pdfFilePath != null
        val hasCsv = report.csvFilePath != null
        if (hasPdf || hasCsv) {
            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = colors.border, thickness = 1.dp)
            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (hasPdf) {
                        ReportActionButton(
                            icon = Icons.Outlined.PictureAsPdf,
                            label = stringResource(R.string.reports_format_pdf),
                            contentDescription = stringResource(R.string.reports_open_pdf),
                            isPrimary = true,
                            onClick = onOpenPdf,
                        )
                    }

                    if (hasCsv) {
                        ReportActionButton(
                            icon = Icons.Outlined.TableChart,
                            label = stringResource(R.string.reports_format_csv),
                            contentDescription = stringResource(R.string.reports_open_csv),
                            isPrimary = false,
                            onClick = onOpenCsv,
                        )
                    }
                }

                var shareMenuExpanded by remember { mutableStateOf(false) }

                Box {
                    IconButton(
                        onClick = {
                            when {
                                hasPdf && hasCsv -> shareMenuExpanded = true
                                hasPdf -> onSharePdf()
                                hasCsv -> onShareCsv()
                                else -> {}
                            }
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = stringResource(R.string.report_share_action),
                            tint = colors.textSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    if (hasPdf && hasCsv) {
                        DropdownMenu(
                            expanded = shareMenuExpanded,
                            onDismissRequest = { shareMenuExpanded = false },
                            modifier = Modifier.background(colors.surfaceHigh),
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.reports_share_pdf),
                                        color = colors.textPrimary,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.PictureAsPdf,
                                        contentDescription = null,
                                        tint = colors.accent,
                                    )
                                },
                                onClick = {
                                    shareMenuExpanded = false
                                    onSharePdf()
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.reports_share_csv),
                                        color = colors.textPrimary,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.TableChart,
                                        contentDescription = null,
                                        tint = colors.textPrimary,
                                    )
                                },
                                onClick = {
                                    shareMenuExpanded = false
                                    onShareCsv()
                                },
                            )
                        }
                    }
                }
            }
        }
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

