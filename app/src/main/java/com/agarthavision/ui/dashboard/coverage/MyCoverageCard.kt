@file:Suppress("TooManyFunctions")

package com.agarthavision.ui.dashboard.coverage

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agarthavision.R
import com.agarthavision.domain.geo.PositiveRateBin
import com.agarthavision.domain.model.AreaStat
import com.agarthavision.domain.model.CoverageFraming
import com.agarthavision.domain.model.HomePeriod
import com.agarthavision.domain.model.MyCoverage
import com.agarthavision.domain.model.ProvinceCoverage
import com.agarthavision.domain.model.SpeciesFinding
import com.agarthavision.ui.components.AgarthaBadge
import com.agarthavision.ui.components.AgarthaBadgeVariant
import com.agarthavision.ui.components.EmptyState
import com.agarthavision.ui.components.SkeletonBox
import com.agarthavision.ui.icons.AgarthaIcons
import com.agarthavision.ui.icons.ChevronRight
import com.agarthavision.ui.theme.AgarthaColors
import com.agarthavision.ui.theme.AgarthaTheme

private const val TOP_PROVINCES_SHOWN = 3
private const val PERCENT_FACTOR = 100

/**
 * Renders the My Coverage summary card. [modifier] must provide a bounded height — the card
 * body uses `weight(1f)` internally and collapses to 0px inside an unbounded parent.
 */
@Composable
fun MyCoverageCard(
    period: HomePeriod,
    onOpen: (HomePeriod) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MyCoverageCardViewModel = hiltViewModel(),
) {
    LaunchedEffect(period) { viewModel.setPeriod(period) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val theme = AgarthaTheme.colors
    val shape = RoundedCornerShape(16.dp)

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.GridView,
                        contentDescription = null,
                        tint = theme.accent,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.coverage_title),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = theme.textPrimary,
                    )
                }
                AgarthaBadge(variant = AgarthaBadgeVariant.Secondary) {
                    Text(
                        text = periodLabel(period),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
            if (state is MyCoverageCardUiState.Ready) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoverageLegend()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.coverage_explore),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = theme.accent,
                        )
                        Icon(
                            imageVector = AgarthaIcons.ChevronRight,
                            contentDescription = null,
                            tint = theme.accent,
                            modifier = Modifier.size(14.dp),
                        )
                    }
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
    val framing = coverage.framing
    val isCountry = framing is CoverageFraming.Country
    val singleProvinceTowns = (framing as? CoverageFraming.SingleProvince)?.let { state.singleProvinceTowns }

    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiniChoroplethMap(
            provinces = state.provinces,
            coverageByCode = coverageByCode,
            fitBounds = state.fitBounds,
            colors = theme,
            modifier = Modifier
                .weight(0.42f)
                .fillMaxHeight(),
            towns = singleProvinceTowns,
            townCounts = coverage.townCounts,
            showCentroidDots = isCountry,
        )
        Spacer(Modifier.width(18.dp))
        Box(
            modifier = Modifier
                .weight(0.58f)
                .fillMaxHeight()
                .padding(start = 6.dp),
        ) {
            when (framing) {
                is CoverageFraming.SingleProvince -> SingleProvinceContent(
                    province = coverageByCode[framing.code],
                    species = state.species,
                )
                is CoverageFraming.IslandGroupFrame -> IslandGroupContent(
                    title = framing.title,
                    coverage = coverage,
                )
                is CoverageFraming.Country -> CountryContent(
                    coverage = coverage,
                )
                CoverageFraming.Empty -> Unit
            }
        }
    }
}

@Composable
private fun SingleProvinceContent(
    province: ProvinceCoverage?,
    species: List<SpeciesFinding>,
) {
    val theme = AgarthaTheme.colors
    val stat = province?.count?.stat

    Column(modifier = Modifier.fillMaxSize()) {
        Column {
            Text(
                text = province?.name ?: "Unknown province",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = theme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            if (stat is AreaStat.Reported) {
                val annotatedRate = buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.accent,
                        ),
                    ) {
                        append("${(stat.positiveRate * PERCENT_FACTOR).toInt()}%")
                    }
                    withStyle(
                        SpanStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = theme.textSecondary,
                        ),
                    ) {
                        append(" positive")
                    }
                }
                Text(text = annotatedRate)
            } else {
                Text(
                    text = rateHeadline(stat),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.textPrimary,
                )
            }
            Text(
                text = smearsSubtitle(stat),
                fontSize = 11.sp,
                color = theme.textSecondary,
            )
            if (species.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    species.take(TOP_PROVINCES_SHOWN).forEach { item ->
                        val shortName = speciesShortName(item.name)
                        val dotColor = speciesColor(item.name, theme)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(dotColor),
                            )
                            val speciesText = buildAnnotatedString {
                                withStyle(
                                    SpanStyle(
                                        fontStyle = FontStyle.Italic,
                                        fontSize = 11.sp,
                                        color = theme.textPrimary,
                                    ),
                                ) {
                                    append(shortName)
                                }
                                append(" ")
                                withStyle(
                                    SpanStyle(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = theme.textPrimary,
                                    ),
                                ) {
                                    append(item.formattedPercentage)
                                }
                            }
                            Text(text = speciesText)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IslandGroupContent(
    title: String,
    coverage: MyCoverage,
) {
    val theme = AgarthaTheme.colors
    val provWord = if (coverage.provinces.size == 1) "province" else "provinces"
    val subtitle = "${coverage.provinces.size} $provWord · ${coverage.totals.smears} smears"

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = theme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = theme.textSecondary,
            )
            Spacer(Modifier.height(4.dp))
            coverage.provinces.take(TOP_PROVINCES_SHOWN).forEach { province ->
                ProvinceRow(province = province, colors = theme)
            }
        }
        Column {
            if (coverage.unlocatedSmears > 0) {
                Text(
                    text = pluralStringResource(
                        R.plurals.coverage_unlocated_smears,
                        coverage.unlocatedSmears,
                        coverage.unlocatedSmears,
                    ),
                    fontSize = 9.sp,
                    color = theme.textTertiary,
                )
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

@Composable
private fun CountryContent(
    coverage: MyCoverage,
) {
    val theme = AgarthaTheme.colors
    val groupCount = coverage.islandGroupCounts.keys.size
    val provWord = if (coverage.provinces.size == 1) "province" else "provinces"
    val groupWord = if (groupCount == 1) "island group" else "island groups"
    val subtitle = "${coverage.provinces.size} $provWord · $groupCount $groupWord"

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = "Philippines",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = theme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = theme.textSecondary,
            )
            Spacer(Modifier.height(4.dp))
            coverage.provinces.take(TOP_PROVINCES_SHOWN).forEach { province ->
                ProvinceRow(province = province, colors = theme)
            }
            if (coverage.provinces.size > TOP_PROVINCES_SHOWN) {
                val remaining = coverage.provinces.size - TOP_PROVINCES_SHOWN
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.coverage_more_provinces, remaining),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = theme.textSecondary,
                )
            }
        }
        Column {
            if (coverage.unlocatedSmears > 0) {
                Text(
                    text = pluralStringResource(
                        R.plurals.coverage_unlocated_smears,
                        coverage.unlocatedSmears,
                        coverage.unlocatedSmears,
                    ),
                    fontSize = 9.sp,
                    color = theme.textTertiary,
                )
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

private const val PROVINCE_ROW_HATCH_SPACING_PX = 3f
private const val PROVINCE_ROW_HATCH_STROKE_PX = 1f
private const val PROVINCE_ROW_MIN_RATE_WIDTH = 0.03f

@Composable
private fun ProvinceRow(
    province: ProvinceCoverage,
    colors: AgarthaColors,
    modifier: Modifier = Modifier,
) {
    val stat = province.count.stat
    val bin = (stat as? AreaStat.Reported)?.let { PositiveRateBin.of(it.positiveRate) }
    val ratePercent = (stat as? AreaStat.Reported)?.let { (it.positiveRate * PERCENT_FACTOR).toInt() }
    val rateFloat = (stat as? AreaStat.Reported)?.positiveRate?.toFloat()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = province.name,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.surfaceMuted),
        ) {
            if (rateFloat != null && bin != null) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(rateFloat.coerceIn(PROVINCE_ROW_MIN_RATE_WIDTH, 1f))
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.coverageBinColor(bin)),
                )
            } else {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawHatchedRect(
                        colors.coverageTooFew,
                        colors.accent,
                        PROVINCE_ROW_HATCH_SPACING_PX,
                        PROVINCE_ROW_HATCH_STROKE_PX,
                    )
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (ratePercent != null) "$ratePercent%" else "Too few",
            fontSize = 11.sp,
            fontWeight = if (ratePercent != null) FontWeight.Bold else FontWeight.Normal,
            color = if (ratePercent != null) colors.textPrimary else colors.textTertiary,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 30.dp),
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
                val framingTitle = if (framing is CoverageFraming.IslandGroupFrame) {
                    framing.title
                } else {
                    "Philippines"
                }
                "My coverage, $framingTitle, $topText. Opens full coverage map."
            }
            CoverageFraming.Empty -> "My coverage, no smears in this period. Opens full coverage map."
        }
    }
}
