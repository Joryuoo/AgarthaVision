package com.agarthavision.ui.coverage

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agarthavision.R
import com.agarthavision.domain.geo.AreaShape
import com.agarthavision.domain.model.AreaStat
import com.agarthavision.domain.model.SpeciesFinding
import com.agarthavision.ui.dashboard.coverage.CoverageStatColumn
import com.agarthavision.ui.dashboard.coverage.RateSwatch
import com.agarthavision.ui.dashboard.coverage.foldSpecies
import com.agarthavision.ui.dashboard.coverage.speciesColor
import com.agarthavision.ui.dashboard.coverage.speciesShortName
import com.agarthavision.ui.icons.AgarthaIcons
import com.agarthavision.ui.icons.ChevronRight
import com.agarthavision.ui.theme.AgarthaTheme
import kotlinx.coroutines.launch

private const val TOP_TOWNS_SHOWN = 5
private const val PERCENT_FACTOR = 100

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
    val scope = rememberCoroutineScope()
    val onClose = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
        Unit
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceHigh,
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
            onClose = onClose,
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
    onClose: () -> Unit = {},
) {
    BackHandler(enabled = showAllTowns) { onShowAllTowns(false) }

    val colors = AgarthaTheme.colors
    val hasData = (selected.coverage?.count?.smears ?: 0) > 0
    val stat = selected.coverage?.count?.stat

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        SheetHeader(selected = selected, hasData = hasData, onClose = onClose)
        Spacer(Modifier.height(12.dp))
        StatsRow(stat)
        if (hasData) {
            if (stat is AreaStat.Reported && selected.species.isNotEmpty()) {
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
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SheetHeader(
    selected: SelectedProvince,
    hasData: Boolean,
    onClose: () -> Unit,
) {
    val colors = AgarthaTheme.colors
    val townsWithData = selected.townCounts.values.count { it.stat != AreaStat.NoData }
    val townsWithDataText = if (hasData && selected.towns is TownGeometry.Available && townsWithData > 0) {
        pluralStringResource(R.plurals.coverage_towns_with_data, townsWithData, townsWithData)
    } else {
        null
    }
    val subtitle = listOfNotNull(selected.regionName, townsWithDataText).joinToString(" · ")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(selected.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.coverage_close_sheet),
                tint = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun StatsRow(stat: AreaStat?) {
    val colors = AgarthaTheme.colors
    when (stat) {
        is AreaStat.Reported -> {
            Row(modifier = Modifier.fillMaxWidth()) {
                CoverageStatColumn(
                    value = "${(stat.positiveRate * PERCENT_FACTOR).toInt()}%",
                    label = stringResource(R.string.coverage_positive_rate_label),
                    valueColor = colors.accent,
                    modifier = Modifier.weight(1f),
                )
                CoverageStatColumn(
                    value = "${stat.positives}",
                    label = stringResource(R.string.coverage_positive_smears_label),
                    modifier = Modifier.weight(1f),
                )
                CoverageStatColumn(
                    value = "${stat.smears}",
                    label = stringResource(R.string.coverage_smears_examined_label),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        AreaStat.TooFew -> {
            Text(
                text = "Too few smears to show a rate",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        AreaStat.NoData, null -> {
            Text(
                text = "No smears from this province in this period",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun SpeciesBreakdown(species: List<SpeciesFinding>) {
    val colors = AgarthaTheme.colors
    val slices = foldSpecies(species)

    Text(
        text = stringResource(R.string.coverage_species_header),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = colors.textSecondary,
        letterSpacing = 0.8.sp,
    )
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(colors.surfaceMuted),
    ) {
        slices.forEach { finding ->
            Box(
                modifier = Modifier
                    .weight(finding.ratio.coerceAtLeast(0.01f))
                    .height(8.dp)
                    .background(speciesColor(finding.name, colors)),
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        slices.forEach { finding ->
            val shortName = speciesShortName(finding.name)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(speciesColor(finding.name, colors)),
                )
                Text(
                    text = shortName,
                    fontSize = 12.sp,
                    fontStyle = if (shortName == "Other") FontStyle.Normal else FontStyle.Italic,
                    color = colors.textPrimary,
                )
                Text(
                    text = finding.formattedPercentage,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                )
            }
        }
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
    val ranked = remember(towns.set, selected.townCounts) {
        towns.set.areas
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
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.coverage_towns_header),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textSecondary,
            letterSpacing = 0.8.sp,
        )
        if (!showAllTowns && ranked.size > TOP_TOWNS_SHOWN) {
            Row(
                modifier = Modifier.clickable { onShowAllTowns(true) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.coverage_all_towns, ranked.size),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.accent,
                )
                Icon(
                    imageVector = AgarthaIcons.ChevronRight,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))

    if (showAllTowns) {
        LazyColumn(modifier = Modifier.fillMaxWidth().height(280.dp)) {
            items(ranked) { (area, stat) -> TownRow(area.name, stat, selected.townCounts[area.code]?.smears ?: 0) }
        }
    } else {
        ranked.take(TOP_TOWNS_SHOWN).forEach { (area, stat) ->
            TownRow(area.name, stat, selected.townCounts[area.code]?.smears ?: 0)
        }
    }
}

@Composable
private fun TownRow(name: String, stat: AreaStat, smears: Int) {
    val colors = AgarthaTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RateSwatch(stat = stat, size = 12.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(name, fontSize = 14.sp, color = colors.textPrimary)
                if (smears > 0) {
                    Text(
                        text = "$smears smears",
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                    )
                }
            }
        }
        Text(
            text = when (stat) {
                is AreaStat.Reported -> "${(stat.positiveRate * PERCENT_FACTOR).toInt()}%"
                AreaStat.TooFew -> "Too few"
                AreaStat.NoData -> "No data"
            },
            fontSize = 13.sp,
            fontWeight = if (stat is AreaStat.Reported) FontWeight.Bold else FontWeight.Normal,
            color = if (stat is AreaStat.Reported) colors.textPrimary else colors.textTertiary,
        )
    }
}
