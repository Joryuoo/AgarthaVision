package com.agarthavision.ui.dashboard.coverage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.agarthavision.R
import com.agarthavision.domain.model.AreaStat
import com.agarthavision.domain.model.CoverageFraming
import com.agarthavision.domain.model.HomePeriod
import com.agarthavision.domain.model.MyCoverage
import com.agarthavision.ui.components.EmptyState
import com.agarthavision.ui.components.SkeletonBox
import com.agarthavision.ui.theme.AgarthaTheme

private const val TOP_PROVINCES_SHOWN = 3
private const val PERCENT_FACTOR = 100

@Composable
fun MyCoverageCard(
    period: HomePeriod,
    onOpen: (HomePeriod) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MyCoverageCardViewModel = hiltViewModel(),
) {
    LaunchedEffect(period) { viewModel.setPeriod(period) }
    val state by viewModel.uiState.collectAsState()

    val theme = AgarthaTheme.colors
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .testTag("myCoverageCard")
            .clip(shape)
            .background(theme.surface, shape)
            .border(1.dp, theme.border, shape)
            .clickable { onOpen(period) }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = contentDescriptionFor(state)
            }
            .padding(16.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "MY COVERAGE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.textSecondary,
                    letterSpacing = 1.2.sp,
                )
                Text(
                    text = periodLabel(period),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = theme.textSecondary,
                )
            }
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxSize()) {
                when (val s = state) {
                    is MyCoverageCardUiState.Loading -> SkeletonBox(modifier = Modifier.fillMaxSize())
                    is MyCoverageCardUiState.Empty -> EmptyState(
                        icon = Icons.Outlined.Map,
                        title = "No smears in this period",
                        body = "Smears you examine will appear here by province.",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    is MyCoverageCardUiState.Error -> Text(
                        text = s.message,
                        fontSize = 12.sp,
                        color = theme.textSecondary,
                    )
                    is MyCoverageCardUiState.Ready -> ReadyContent(s)
                }
            }
        }
    }
}

@Composable
private fun ReadyContent(state: MyCoverageCardUiState.Ready) {
    val theme = AgarthaTheme.colors
    val coverage = state.coverage
    val coverageByCode = coverage.provinces.associateBy { it.code }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            MiniChoroplethMap(
                provinces = state.provinces,
                coverageByCode = coverageByCode,
                fitBounds = state.fitBounds,
                colors = theme,
                modifier = Modifier
                    .weight(0.55f)
                    .fillMaxHeight(),
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier
                    .weight(0.45f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
            ) {
                when (val framing = coverage.framing) {
                    is CoverageFraming.SingleProvince -> {
                        val province = coverageByCode[framing.code]
                        Text(
                            text = province?.name ?: "Unknown province",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = theme.textPrimary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = rateHeadline(province?.count?.stat),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.textPrimary,
                        )
                        Text(
                            text = smearsSubtitle(province?.count?.stat),
                            fontSize = 12.sp,
                            color = theme.textSecondary,
                        )
                    }
                    is CoverageFraming.IslandGroupFrame -> TopProvincesSummary(framing.title, coverage)
                    is CoverageFraming.Country -> TopProvincesSummary("Philippines", coverage)
                    CoverageFraming.Empty -> Unit
                }
                if (coverage.unlocatedSmears > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = pluralStringResource(
                            R.plurals.coverage_unlocated_smears,
                            coverage.unlocatedSmears,
                            coverage.unlocatedSmears,
                        ),
                        fontSize = 10.sp,
                        color = theme.textTertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun TopProvincesSummary(title: String, coverage: MyCoverage) {
    val theme = AgarthaTheme.colors
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = theme.textPrimary,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = "${coverage.totals.smears} smears",
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = theme.textPrimary,
    )
    Spacer(Modifier.height(6.dp))
    coverage.provinces.take(TOP_PROVINCES_SHOWN).forEach { province ->
        Text(
            text = "${province.name} · ${rateHeadline(province.count.stat)}",
            fontSize = 11.sp,
            color = theme.textSecondary,
        )
    }
}

private fun rateHeadline(stat: AreaStat?): String = when (stat) {
    is AreaStat.Reported -> "${(stat.positiveRate * PERCENT_FACTOR).toInt()}% positive"
    AreaStat.TooFew -> "Too few"
    AreaStat.NoData, null -> "No smears"
}

private fun smearsSubtitle(stat: AreaStat?): String = when (stat) {
    is AreaStat.Reported -> "${stat.positives} of ${stat.smears} smears"
    AreaStat.TooFew -> "Not enough smears yet"
    AreaStat.NoData, null -> "No smears examined"
}

private fun periodLabel(period: HomePeriod): String = when (period) {
    HomePeriod.TODAY -> "Today"
    HomePeriod.LAST_7_DAYS -> "7 days"
    HomePeriod.LAST_30_DAYS -> "30 days"
}

private fun contentDescriptionFor(state: MyCoverageCardUiState): String = when (state) {
    is MyCoverageCardUiState.Loading -> "My coverage, loading. Opens full coverage map."
    is MyCoverageCardUiState.Empty -> "My coverage, no smears in this period. Opens full coverage map."
    is MyCoverageCardUiState.Error -> "My coverage, couldn't load. Opens full coverage map."
    is MyCoverageCardUiState.Ready -> {
        val coverage = state.coverage
        when (val framing = coverage.framing) {
            is CoverageFraming.SingleProvince -> {
                val province = coverage.provinces.firstOrNull { it.code == framing.code }
                val stat = province?.count?.stat
                val statText = when (stat) {
                    is AreaStat.Reported ->
                        "${(stat.positiveRate * PERCENT_FACTOR).toInt()} percent positive, " +
                            "${stat.positives} of ${stat.smears} smears"
                    AreaStat.TooFew -> "too few smears to report"
                    AreaStat.NoData, null -> "no smears"
                }
                "My coverage, ${province?.name ?: "unknown province"}, $statText. Opens full coverage map."
            }
            is CoverageFraming.IslandGroupFrame, is CoverageFraming.Country -> {
                val top = coverage.provinces.firstOrNull()
                val topText = if (top != null) {
                    "top province ${top.name} ${rateHeadline(top.count.stat)}"
                } else {
                    "no provinces reported"
                }
                "My coverage, Philippines, $topText. Opens full coverage map."
            }
            CoverageFraming.Empty -> "My coverage, no smears in this period. Opens full coverage map."
        }
    }
}
