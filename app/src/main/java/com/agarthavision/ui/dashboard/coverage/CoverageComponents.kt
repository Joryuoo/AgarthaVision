package com.agarthavision.ui.dashboard.coverage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agarthavision.R
import com.agarthavision.domain.geo.PositiveRateBin
import com.agarthavision.domain.model.AreaStat
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.SpeciesFinding
import com.agarthavision.ui.theme.AgarthaColors
import com.agarthavision.ui.theme.AgarthaTheme

private const val SWATCH_HATCH_SPACING_PX = 3f
private const val SWATCH_HATCH_STROKE_PX = 1f
private const val PERCENT_FACTOR = 100
private const val DEFAULT_FOLD_KEEP = 3
private const val COVERAGE_BIN_COUNT = 5

/**
 * Fills [DrawScope]'s whole draw area with [fill] then draws a diagonal hatch of [hatch] lines —
 * the shared "too few smears" texture used by [MiniChoroplethMap]'s province shading, the
 * coverage legend swatch, per-town/per-province rate swatches, and provinces rows' rate bar.
 */
internal fun DrawScope.drawHatchedRect(fill: Color, hatch: Color, spacingPx: Float, strokePx: Float) {
    drawRect(color = fill)
    var x = -size.height
    while (x < size.width) {
        drawLine(
            color = hatch,
            start = Offset(x, size.height),
            end = Offset(x + size.height, 0f),
            strokeWidth = strokePx,
        )
        x += spacingPx
    }
}

/** A small swatch summarizing one area's [AreaStat] tier — used by the legend and town rows. */
@Composable
internal fun RateSwatch(
    stat: AreaStat?,
    modifier: Modifier = Modifier,
    size: Dp = 10.dp,
    shape: Shape = RoundedCornerShape(2.dp),
) {
    val colors = AgarthaTheme.colors
    when (stat) {
        is AreaStat.Reported -> {
            val fill = colors.coverageBinColor(PositiveRateBin.of(stat.positiveRate))
            Box(
                modifier = modifier
                    .size(size)
                    .clip(shape)
                    .background(fill, shape),
            )
        }
        AreaStat.TooFew -> {
            Canvas(modifier = modifier.size(size).clip(shape)) {
                drawHatchedRect(colors.coverageTooFew, colors.accent, SWATCH_HATCH_SPACING_PX, SWATCH_HATCH_STROKE_PX)
            }
        }
        AreaStat.NoData, null -> {
            Box(
                modifier = modifier
                    .size(size)
                    .clip(shape)
                    .background(colors.coverageNoData, shape)
                    .border(0.5.dp, colors.border, shape),
            )
        }
    }
}

/**
 * Folds [species] down to the first [keep] entries whose [EggSpecies.fromClassLabel] resolves to
 * a known, non-[EggSpecies.OTHER] species (in incoming order), summing every other entry
 * (unknown labels, [EggSpecies.OTHER], and anything past [keep]) into a single trailing "Other"
 * entry — omitted if nothing was folded into it.
 */
internal fun foldSpecies(species: List<SpeciesFinding>, keep: Int = DEFAULT_FOLD_KEEP): List<SpeciesFinding> {
    val kept = mutableListOf<SpeciesFinding>()
    var otherCount = 0
    var otherRatio = 0f
    for (finding in species) {
        val known = EggSpecies.fromClassLabel(finding.name)
        if (known != null && known != EggSpecies.OTHER && kept.size < keep) {
            kept += finding
        } else {
            otherCount += finding.count
            otherRatio += finding.ratio
        }
    }
    if (otherCount > 0) {
        kept += SpeciesFinding(
            name = "Other",
            count = otherCount,
            ratio = otherRatio,
            formattedPercentage = "${(otherRatio * PERCENT_FACTOR).toInt()}%",
        )
    }
    return kept
}

/** Maps a species [name] to its brand-consistent dot/segment color. */
internal fun speciesColor(name: String, colors: AgarthaColors): Color = when (EggSpecies.fromClassLabel(name)) {
    EggSpecies.ASCARIS -> colors.accent
    EggSpecies.TRICHURIS -> colors.gold
    EggSpecies.HOOKWORM -> colors.success
    else -> colors.textTertiary
}

/** The first word of a species [name] — "Ascaris lumbricoides" -> "Ascaris". */
internal fun speciesShortName(name: String): String = name.trim().split(" ").firstOrNull() ?: name

/**
 * Shared choropleth legend: 5 positive-rate bins plus a "too few" hatch swatch. Used by both the
 * dashboard card footer and the full-screen coverage map.
 */
@Composable
internal fun CoverageLegend(
    modifier: Modifier = Modifier,
    label: String? = null,
    swatchSize: Dp = 8.dp,
    textSize: TextUnit = 9.sp,
) {
    val colors = AgarthaTheme.colors
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (label != null) {
            Text(text = label, fontSize = textSize, fontWeight = FontWeight.SemiBold, color = colors.textSecondary)
            Spacer(Modifier.width(4.dp))
        }
        Text(text = "0", fontSize = textSize, color = colors.textTertiary)
        for (bin in 0 until COVERAGE_BIN_COUNT) {
            Box(
                modifier = Modifier
                    .size(swatchSize)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.coverageBinColor(bin)),
            )
        }
        Text(text = "40%+", fontSize = textSize, color = colors.textTertiary)
        Spacer(Modifier.width(4.dp))
        RateSwatch(stat = AreaStat.TooFew, size = swatchSize)
        Text(text = stringResource(R.string.coverage_too_few), fontSize = textSize, color = colors.textTertiary)
    }
}

/** A single value/label stat column — used by the province sheet's rate/positives/smears row. */
@Composable
internal fun CoverageStatColumn(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = AgarthaTheme.colors.textPrimary,
) {
    val colors = AgarthaTheme.colors
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = valueColor)
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = colors.textSecondary,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
}
