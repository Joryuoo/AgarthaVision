package com.agarthavision.ui.coverage

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agarthavision.domain.geo.AreaShape
import com.agarthavision.domain.geo.PositiveRateBin
import com.agarthavision.domain.geo.fitBounds
import com.agarthavision.domain.model.AreaCount
import com.agarthavision.domain.model.AreaStat
import com.agarthavision.domain.model.SpeciesFinding
import com.agarthavision.ui.dashboard.coverage.pathFor
import com.agarthavision.ui.theme.AgarthaTheme

private const val TOP_TOWNS_SHOWN = 5
private const val PERCENT_FACTOR = 100
private const val PROVINCE_MAP_PADDING_PX = 6f
private const val POSITIVE_RATE_BIN_COUNT = 4f

@Suppress("LongParameterList") // Every parameter is a distinct, independent sheet slot.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvinceSheet(
    selected: SelectedProvince,
    showAllTowns: Boolean,
    onDismiss: () -> Unit,
    onShowAllTowns: (Boolean) -> Unit,
    sheetState: SheetState,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 8.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(colors.borderStrong, RoundedCornerShape(2.dp)),
            )
        },
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        modifier = modifier,
    ) {
        ProvinceSheetContent(
            selected = selected,
            showAllTowns = showAllTowns,
            onShowAllTowns = onShowAllTowns,
        )
    }
}

/**
 * The sheet's content, split out from [ProvinceSheet] so it's testable directly without a real
 * [ModalBottomSheet] — Robolectric lays out `ModalBottomSheet`'s internal window/dialog content
 * unreliably (zero-height nodes for its children), same idiom `MyCoverageCardTest` established
 * for `hiltViewModel()`-backed composables in Phase 9: keep the piece under test free of the
 * framework machinery that Robolectric can't exercise here.
 */
@Composable
internal fun ProvinceSheetContent(
    selected: SelectedProvince,
    showAllTowns: Boolean,
    onShowAllTowns: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = showAllTowns) { onShowAllTowns(false) }

    val colors = AgarthaTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        ProvinceMiniMap(selected)
        Spacer(Modifier.height(12.dp))
        Text(selected.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
        TownCountLine(selected)
        Spacer(Modifier.height(6.dp))
        RateLine(selected.coverage?.count?.stat)
        if (selected.coverage?.count?.stat is AreaStat.Reported) {
            Spacer(Modifier.height(16.dp))
            SpeciesBreakdown(selected.species)
        }
        if (selected.towns is TownGeometry.Unavailable) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Town outlines aren't available for this province. Figures below are still complete.",
                fontSize = 12.sp,
                color = colors.textSecondary,
            )
        }
        Spacer(Modifier.height(16.dp))
        TownRanking(selected, showAllTowns, onShowAllTowns)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ProvinceMiniMap(selected: SelectedProvince) {
    val colors = AgarthaTheme.colors
    val towns = (selected.towns as? TownGeometry.Available)?.set
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceMuted),
    ) {
        if (towns != null) {
            val townPaths = remember(towns) { mutableMapOf<String, Path>() }
            var cachedSize by remember { mutableStateOf(Pair(0f, 0f)) }
            val coverageByCode = remember(towns, selected.townCounts) {
                towns.areas.associate { area ->
                    val stat = selected.townCounts[area.code]?.stat
                    area.code to stat
                }
            }
            Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                if (size.width != cachedSize.first || size.height != cachedSize.second) {
                    townPaths.clear()
                    cachedSize = size.width to size.height
                }
                val transform = fitBounds(
                    bounds = towns.bounds,
                    widthPx = size.width,
                    heightPx = size.height,
                    paddingPx = PROVINCE_MAP_PADDING_PX,
                )
                towns.areas.forEach { area ->
                    val stat = coverageByCode[area.code]
                    val path = townPaths.getOrPut(area.code) { pathFor(area, transform) }
                    val fill = when (stat) {
                        is AreaStat.Reported -> lerp(
                            colors.accentTint,
                            colors.accent,
                            PositiveRateBin.of(stat.positiveRate) / POSITIVE_RATE_BIN_COUNT,
                        )
                        else -> colors.surfaceVariant
                    }
                    drawPath(path, color = fill)
                }
            }
        }
    }
}

@Composable
private fun TownCountLine(selected: SelectedProvince) {
    val colors = AgarthaTheme.colors
    val towns = selected.towns
    if (towns is TownGeometry.Available) {
        val withData = selected.townCounts.values.count { it.stat != AreaStat.NoData }
        Text(
            text = "$withData towns with data",
            fontSize = 12.sp,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun RateLine(stat: AreaStat?) {
    val colors = AgarthaTheme.colors
    val text = when (stat) {
        is AreaStat.Reported ->
            "${(stat.positiveRate * PERCENT_FACTOR).toInt()}% positive · ${stat.smears} smears"
        AreaStat.TooFew -> "Too few smears to show a rate"
        AreaStat.NoData, null -> "No smears from this province in this period"
    }
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
}

@Composable
private fun SpeciesBreakdown(species: List<SpeciesFinding>) {
    val colors = AgarthaTheme.colors
    val seriesColors = listOf(colors.accent, colors.gold, colors.success)

    Text("Species mix", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(colors.surfaceMuted),
    ) {
        species.forEachIndexed { i, finding ->
            val color = seriesColors.getOrElse(i) { colors.textTertiary }
            Box(
                modifier = Modifier
                    .weight(finding.ratio.coerceAtLeast(0.01f))
                    .height(10.dp)
                    .background(color),
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    species.forEach { finding ->
        Text(
            text = "${finding.name} · ${finding.formattedPercentage}",
            fontSize = 11.sp,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun TownRanking(
    selected: SelectedProvince,
    showAllTowns: Boolean,
    onShowAllTowns: (Boolean) -> Unit,
) {
    val colors = AgarthaTheme.colors
    val towns = selected.towns as? TownGeometry.Available ?: return
    val ranked = towns.set.areas
        .map { area -> area to (selected.townCounts[area.code]?.stat ?: AreaStat.NoData) }
        .sortedWith(
            compareBy<Pair<AreaShape, AreaStat>> { (_, stat) ->
                when (stat) {
                    is AreaStat.Reported -> 0
                    AreaStat.TooFew -> 1
                    AreaStat.NoData -> 2
                }
            }.thenByDescending { (_, stat) -> (stat as? AreaStat.Reported)?.positiveRate ?: 0.0 },
        )

    Text("Towns", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
    Spacer(Modifier.height(8.dp))

    if (showAllTowns) {
        LazyColumn(modifier = Modifier.fillMaxWidth().height(280.dp)) {
            items(ranked) { (area, stat) -> TownRow(area.name, stat) }
        }
    } else {
        ranked.take(TOP_TOWNS_SHOWN).forEach { (area, stat) -> TownRow(area.name, stat) }
        if (ranked.size > TOP_TOWNS_SHOWN) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { onShowAllTowns(true) }) {
                Text("View all ${ranked.size} towns")
            }
        }
    }
}

@Composable
private fun TownRow(name: String, stat: AreaStat) {
    val colors = AgarthaTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(name, fontSize = 13.sp, color = colors.textPrimary)
        Text(
            text = when (stat) {
                is AreaStat.Reported -> "${(stat.positiveRate * PERCENT_FACTOR).toInt()}%"
                AreaStat.TooFew -> "Too few"
                AreaStat.NoData -> "No data"
            },
            fontSize = 13.sp,
            color = colors.textSecondary,
        )
    }
}
