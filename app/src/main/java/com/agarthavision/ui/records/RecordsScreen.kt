package com.agarthavision.ui.records

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agarthavision.R
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.usecase.records.SessionRecordItem
import com.agarthavision.ui.components.DateRangeFilterBar
import com.agarthavision.ui.components.SearchInput
import com.agarthavision.ui.components.SkeletonBox
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.AppColors
import com.agarthavision.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val RECORDS_SKELETON_COUNT = 6

enum class SyncStatus { Synced, PendingSync }

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
            RecordsAppBar(
                subtitle = if (state.startDate == null && state.endDate == null) {
                    "All sessions"
                } else {
                    "Filtered date range"
                },
            )
        },
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
                StatsRow(
                    sessionsCount = if (state.isLoading) "—" else state.totals.sessionCount.toString(),
                    eggsCount = if (state.isLoading) "—" else state.totals.totalEpg.toString(),
                    samplesCount = if (state.isLoading) "—" else state.totals.totalSamples.toString(),
                    modifier = Modifier.padding(horizontal = Spacing.xl),
                )
            }
            item {
                Spacer(Modifier.height(Spacing.md))
                SpeciesFilterChips(
                    selected = state.selectedSpecies,
                    onSelect = viewModel::onSpeciesSelected,
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
                state.isLoading -> items(RECORDS_SKELETON_COUNT) {
                    RecordCardSkeleton(modifier = Modifier.padding(horizontal = Spacing.xl, vertical = 4.dp))
                }
                state.sessions.isEmpty() -> item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .padding(horizontal = Spacing.xl),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.records_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AgarthaTheme.colors.textSecondary,
                        )
                    }
                }
                else -> {
                    items(state.sessions, key = { it.session.id }) { record ->
                        RecordCard(
                            record = record,
                            onClick = { onSessionClick(record.session.id) },
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

@Composable
private fun RecordsAppBar(subtitle: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(AgarthaTheme.colors.background)
            .statusBarsPadding()
            .padding(horizontal = Spacing.xl, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Records",
                style = MaterialTheme.typography.headlineSmall,
                color = AgarthaTheme.colors.textPrimary,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = AgarthaTheme.colors.textSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}


@Composable
private fun StatsRow(
    sessionsCount: String,
    eggsCount: String,
    samplesCount: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        val colors = AgarthaTheme.colors
        StatTile(
            "Sessions", sessionsCount, Modifier.weight(1f),
            colors = StatTileColors(
                bgColor = colors.accent,
                contentColor = colors.onAccent,
                labelColor = colors.onAccent.copy(alpha = 0.8f),
            ),
        )
        StatTile(
            "Eggs found", eggsCount, Modifier.weight(1f),
            colors = StatTileColors(
                bgColor = colors.gold,
                contentColor = colors.onGold,
                labelColor = colors.onGold.copy(alpha = 0.75f),
            ),
        )
        StatTile(
            "Samples", samplesCount, Modifier.weight(1f),
            colors = StatTileColors(
                bgColor = AppColors.Gray700,
                contentColor = AppColors.White,
                labelColor = AppColors.White.copy(alpha = 0.8f),
            ),
        )
    }
}

/**
 * Color triad for [StatTile] — bundled since bg/content/label always travel together.
 * No defaults: every call site supplies its own triad (theme-token defaults would require a
 * @Composable context, which a plain data class constructor doesn't have).
 */
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
    colors: StatTileColors = StatTileColors(
        bgColor = AgarthaTheme.colors.surfaceVariant,
        contentColor = AgarthaTheme.colors.textPrimary,
        labelColor = AgarthaTheme.colors.textSecondary,
    ),
) {
    val neutralBg = colors.bgColor == AgarthaTheme.colors.surfaceVariant ||
        colors.bgColor == AgarthaTheme.colors.surface
    Column(
        modifier = modifier
            .background(colors.bgColor, RoundedCornerShape(12.dp))
            .border(
                1.dp,
                if (neutralBg) AgarthaTheme.colors.border else androidx.compose.ui.graphics.Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
    ) {
        Text(
            label.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.labelColor,
            letterSpacing = 0.6.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = colors.contentColor,
            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum, cv11, ss01, ss03"),
            lineHeight = 22.sp,
        )
    }
}

@Composable
private fun SpeciesFilterChips(
    selected: EggSpecies?,
    onSelect: (EggSpecies?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.xl),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { SpeciesChip("All species", selected == null) { onSelect(null) } }
        items(EggSpecies.entries.filterNot { it == EggSpecies.OTHER }) { species ->
            SpeciesChip(species.displayName, selected == species) { onSelect(species) }
        }
    }
}

@Composable
private fun SpeciesChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = AgarthaTheme.colors
    val bg = if (selected) colors.textPrimary else colors.surface
    val border = if (selected) colors.textPrimary else colors.borderStrong
    val text = if (selected) colors.background else colors.textSecondary

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg, RoundedCornerShape(999.dp))
            .border(1.dp, border, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = text,
            fontStyle = if (label == EggSpecies.HOOKWORM.displayName) FontStyle.Normal else FontStyle.Italic,
        )
    }
}

@Composable
private fun RecordCard(
    record: SessionRecordItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateLabel = Instant.ofEpochMilli(record.session.startedAt)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    val timeLabel = Instant.ofEpochMilli(record.session.startedAt)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))
    val metaText = if (record.session.notes.isNullOrBlank()) {
        "$dateLabel · $timeLabel"
    } else {
        "$dateLabel · $timeLabel · ${record.session.notes}"
    }
    val speciesCount = if (record.speciesLabels.isEmpty()) "-" else record.speciesLabels.size.toString()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AgarthaTheme.colors.surface, RoundedCornerShape(12.dp))
            .border(1.dp, AgarthaTheme.colors.border, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(
                    record.session.label ?: "Session ${record.session.id.take(4)}",
                    style = MaterialTheme.typography.titleLarge,
                    color = AgarthaTheme.colors.textPrimary,
                )
                Text(
                    metaText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = AgarthaTheme.colors.textSecondary,
                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            StatusPill(SyncStatus.Synced)
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = AgarthaTheme.colors.border, thickness = 1.dp)
        Spacer(Modifier.height(10.dp))

        StatRun(
            listOf(
                Stat(record.totalEpg.toString(), "eggs"),
                Stat(speciesCount, "species"),
                Stat(record.sampleCount.toString(), "samples"),
            )
        )
    }
}

@Composable
private fun RecordCardSkeleton(modifier: Modifier = Modifier) {
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
private fun StatusPill(status: SyncStatus) {
    val colors = AgarthaTheme.colors
    val (bg, fg, text) = when (status) {
        SyncStatus.Synced -> Triple(colors.successTint, colors.successText, "Synced")
        SyncStatus.PendingSync -> Triple(colors.warningTint, colors.warningText, "Pending sync")
    }
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(999.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(
            text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
        )
    }
}
