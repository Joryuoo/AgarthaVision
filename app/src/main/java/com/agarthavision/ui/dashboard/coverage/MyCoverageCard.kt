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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.agarthavision.R
import com.agarthavision.domain.geo.IslandGroup
import com.agarthavision.domain.model.AreaStat
import com.agarthavision.domain.model.CoverageFraming
import com.agarthavision.domain.model.HomePeriod
import com.agarthavision.domain.model.MyCoverage
import com.agarthavision.domain.model.ProvinceCoverage
import com.agarthavision.domain.model.SpeciesFinding
import com.agarthavision.ui.components.EmptyState
import com.agarthavision.ui.components.SkeletonBox
import com.agarthavision.ui.theme.AgarthaColors
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.AppColors

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
    val cardBg = if (theme.isDark) AppColors.Gray700 else theme.surface

    Box(
        modifier = modifier
            .testTag("myCoverageCard")
            .clip(shape)
            .background(cardBg, shape)
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
                    color = if (theme.isDark) AppColors.White else theme.textSecondary,
                    letterSpacing = 1.2.sp,
                )
                Text(
                    text = periodLabel(period),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (theme.isDark) AppColors.White.copy(alpha = 0.8f) else theme.textSecondary,
                )
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

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
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
                        val shortName = item.name.split(" ").firstOrNull() ?: item.name
                        val dotColor = when (shortName.lowercase()) {
                            "ascaris" -> theme.accent
                            "trichuris" -> theme.gold
                            "hookworm" -> theme.success
                            else -> theme.textSecondary
                        }
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
        Column {
            CoverageCardLegend(colors = theme)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Tap to explore →",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = theme.accent,
            )
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
            CoverageCardLegend(colors = theme)
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

    val luzonCount = coverage.islandGroupCounts[IslandGroup.LUZON] ?: 0
    val visayasCount = coverage.islandGroupCounts[IslandGroup.VISAYAS] ?: 0
    val mindanaoCount = coverage.islandGroupCounts[IslandGroup.MINDANAO] ?: 0
    val summaryText = "Luzon $luzonCount · Visayas $visayasCount · Mindanao $mindanaoCount"

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
                    text = "+ $remaining more",
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
            Text(
                text = summaryText,
                fontSize = 10.sp,
                color = theme.textSecondary,
            )
        }
    }
}

@Composable
private fun ProvinceRow(
    province: ProvinceCoverage,
    colors: AgarthaColors,
    modifier: Modifier = Modifier,
) {
    val stat = province.count.stat
    val ratePercent = (stat as? AreaStat.Reported)?.let { (it.positiveRate * PERCENT_FACTOR).toInt() }
    val rateFloat = (stat as? AreaStat.Reported)?.positiveRate?.toFloat()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 1.5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = province.name,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = if (ratePercent != null) "$ratePercent%" else "Too few",
                fontSize = 11.sp,
                fontWeight = if (ratePercent != null) FontWeight.Bold else FontWeight.Normal,
                color = if (ratePercent != null) colors.accent else colors.textSecondary,
            )
        }
        Spacer(Modifier.height(2.dp))
        ProvinceRateBar(rate = rateFloat, colors = colors)
        if (stat is AreaStat.Reported) {
            Spacer(Modifier.height(1.dp))
            Text(
                text = "${stat.smears} smears",
                fontSize = 9.sp,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun ProvinceRateBar(
    rate: Float?,
    colors: AgarthaColors,
    modifier: Modifier = Modifier,
) {
    val barShape = RoundedCornerShape(2.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(barShape)
            .background(colors.surfaceMuted),
    ) {
        if (rate != null) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(rate.coerceIn(0.03f, 1f))
                    .clip(barShape)
                    .background(colors.accent),
            )
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidthPx = 1f
                val spacingPx = 4f
                var x = -size.height
                while (x < size.width) {
                    drawLine(
                        color = colors.border,
                        start = Offset(x, size.height),
                        end = Offset(x + size.height, 0f),
                        strokeWidth = strokeWidthPx,
                    )
                    x += spacingPx
                }
            }
        }
    }
}

@Composable
private fun CoverageCardLegend(
    colors: AgarthaColors,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = "0",
            fontSize = 9.sp,
            color = colors.textTertiary,
        )
        for (bin in 0..4) {
            val color = colors.coverageBinColor(bin)
            Box(
                modifier = Modifier
                    .size(width = 6.dp, height = 6.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color),
            )
        }
        Text(
            text = "40%+",
            fontSize = 9.sp,
            color = colors.textTertiary,
        )
        Spacer(Modifier.width(2.dp))
        Canvas(
            modifier = Modifier
                .size(width = 6.dp, height = 6.dp)
                .clip(RoundedCornerShape(1.dp))
                .border(0.5.dp, colors.border, RoundedCornerShape(1.dp)),
        ) {
            drawRect(colors.surfaceMuted)
            val strokeWidthPx = 1f
            val spacingPx = 3f
            var x = -size.height
            while (x < size.width) {
                drawLine(
                    color = colors.borderStrong,
                    start = Offset(x, size.height),
                    end = Offset(x + size.height, 0f),
                    strokeWidth = strokeWidthPx,
                )
                x += spacingPx
            }
        }
        Text(
            text = "few",
            fontSize = 9.sp,
            color = colors.textTertiary,
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
