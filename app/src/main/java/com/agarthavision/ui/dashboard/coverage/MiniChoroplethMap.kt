package com.agarthavision.ui.dashboard.coverage

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.agarthavision.domain.geo.AreaShape
import com.agarthavision.domain.geo.BoundarySet
import com.agarthavision.domain.geo.GeoBounds
import com.agarthavision.domain.geo.PositiveRateBin
import com.agarthavision.domain.geo.ViewTransform
import com.agarthavision.domain.geo.fitBounds
import com.agarthavision.domain.model.AreaCount
import com.agarthavision.domain.model.AreaStat
import com.agarthavision.domain.model.ProvinceCoverage
import com.agarthavision.ui.theme.AgarthaColors

private const val MAP_PADDING_PX = 8f
private const val POSITIVE_RATE_BIN_COUNT = 4f
private const val HATCH_LINE_SPACING_PX = 6f
private const val STROKE_WIDTH_PX = 1.2f
private const val HIGHLIGHT_STROKE_WIDTH_PX = 2.5f

/**
 * A minimal, static (no gestures) province choropleth for the My coverage card. Gestures and
 * richer rendering live only in the later full-screen coverage phase — this is the pager card's
 * small preview.
 */
@Suppress("LongParameterList")
@Composable
internal fun MiniChoroplethMap(
    provinces: BoundarySet,
    coverageByCode: Map<String, ProvinceCoverage>,
    fitBounds: GeoBounds,
    colors: AgarthaColors,
    modifier: Modifier = Modifier,
    towns: BoundarySet? = null,
    townCounts: Map<String, AreaCount> = emptyMap(),
    showCentroidDots: Boolean = false,
) {
    val pathCache = remember(provinces) { mutableMapOf<String, Path>() }
    val townPathCache = remember(towns) { mutableMapOf<String, Path>() }
    var lastTransform by remember { mutableStateOf<ViewTransform?>(null) }

    Canvas(modifier = modifier.clipToBounds()) {
        val transform = fitBounds(
            bounds = fitBounds,
            widthPx = size.width,
            heightPx = size.height,
            paddingPx = MAP_PADDING_PX,
        )
        if (lastTransform != transform) {
            pathCache.clear()
            townPathCache.clear()
            lastTransform = transform
        }
        if (showCentroidDots) {
            provinces.areas.forEach { area ->
                val path = pathCache.getOrPut(area.code) { pathFor(area, transform) }
                drawArea(path, null, colors)
            }
            val maxSmears = coverageByCode.values.maxOfOrNull { it.count.smears }?.coerceAtLeast(1) ?: 1
            val minRadiusPx = 3.5f.dp.toPx()
            val maxRadiusPx = 7.5f.dp.toPx()

            coverageByCode.values.forEach { province ->
                val shape = provinces.byCode[province.code] ?: return@forEach
                val (sx, sy) = transform.toScreen(shape.labelX, shape.labelY)
                val fraction = (province.count.smears.toFloat() / maxSmears).coerceIn(0f, 1f)
                val radius = minRadiusPx + (maxRadiusPx - minRadiusPx) * fraction

                val stat = province.count.stat
                if (stat is AreaStat.TooFew) {
                    drawCircle(
                        color = Color.White,
                        radius = radius + 1.2f.dp.toPx(),
                        center = Offset(sx, sy),
                    )
                    drawCircle(
                        color = colors.accent,
                        radius = radius,
                        center = Offset(sx, sy),
                    )
                    drawCircle(
                        color = colors.gold,
                        radius = radius,
                        center = Offset(sx, sy),
                        style = Stroke(
                            width = 1.2f.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(4f.dp.toPx(), 3f.dp.toPx()),
                                0f,
                            ),
                        ),
                    )
                } else {
                    val dotColor = if (stat is AreaStat.Reported) {
                        val bin = PositiveRateBin.of(stat.positiveRate)
                        lerp(colors.goldTint, colors.gold, bin / POSITIVE_RATE_BIN_COUNT)
                    } else {
                        colors.border
                    }
                    drawCircle(
                        color = Color.White,
                        radius = radius + 1.2f.dp.toPx(),
                        center = Offset(sx, sy),
                    )
                    drawCircle(
                        color = dotColor,
                        radius = radius,
                        center = Offset(sx, sy),
                    )
                }
            }
        } else if (towns != null) {
            provinces.areas.forEach { area ->
                val path = pathCache.getOrPut(area.code) { pathFor(area, transform) }
                drawArea(path, null, colors)
            }
            towns.areas.forEach { town ->
                val path = townPathCache.getOrPut(town.code) { pathFor(town, transform) }
                val stat = townCounts[town.code]?.stat
                drawArea(path, stat, colors)
            }
        } else {
            drawProvinces(provinces, coverageByCode, transform, colors, pathCache = pathCache)
        }
    }
}

/**
 * Draws every [AreaShape] in [provinces], shaded by [coverageByCode]'s stat, under an
 * already-computed [transform].
 *
 * Extracted so the full-screen coverage map (live pan/zoom, an externally-driven
 * [ViewTransform]) and this static card preview (a [fitBounds]-computed one) share exactly the
 * same path-building/shading logic without sharing a top-level composable signature — the two
 * screens need different transform-sourcing but must render identically.
 */
@Suppress("LongParameterList") // Every parameter is a distinct, independent rendering input.
internal fun DrawScope.drawProvinces(
    provinces: BoundarySet,
    coverageByCode: Map<String, ProvinceCoverage>,
    transform: ViewTransform,
    colors: AgarthaColors,
    highlightCode: String? = null,
    highlightColor: Color? = null,
    pathCache: MutableMap<String, Path>? = null,
) {
    provinces.areas.forEach { area ->
        val stat = coverageByCode[area.code]?.count?.stat
        val path = pathCache?.getOrPut(area.code) { pathFor(area, transform) } ?: pathFor(area, transform)
        drawArea(path, stat, colors)
    }
    if (highlightCode != null && highlightColor != null) {
        provinces.byCode[highlightCode]?.let { area ->
            val path = pathCache?.getOrPut(area.code) { pathFor(area, transform) } ?: pathFor(area, transform)
            drawPath(path, color = highlightColor, style = Stroke(HIGHLIGHT_STROKE_WIDTH_PX))
        }
    }
}

private fun DrawScope.drawArea(
    path: Path,
    stat: AreaStat?,
    colors: AgarthaColors,
) {
    when (stat) {
        is AreaStat.Reported -> {
            val bin = PositiveRateBin.of(stat.positiveRate)
            val fill = lerp(colors.goldTint, colors.gold, bin / POSITIVE_RATE_BIN_COUNT)
            drawPath(path, color = fill)
            drawPath(path, color = Color.White, style = Stroke(STROKE_WIDTH_PX))
        }
        AreaStat.TooFew -> {
            drawPath(path, color = colors.accent)
            drawHatching(path, colors.gold)
            drawPath(path, color = Color.White, style = Stroke(STROKE_WIDTH_PX))
        }
        AreaStat.NoData, null -> {
            drawPath(path, color = colors.accent)
            drawPath(path, color = Color.White, style = Stroke(STROKE_WIDTH_PX))
        }
    }
}

internal fun pathFor(area: AreaShape, transform: ViewTransform): Path {
    val path = Path().apply { fillType = PathFillType.EvenOdd }
    area.rings.forEach { ring ->
        var i = 0
        var started = false
        while (i + 1 < ring.size) {
            val (sx, sy) = transform.toScreen(ring[i], ring[i + 1])
            if (!started) {
                path.moveTo(sx, sy)
                started = true
            } else {
                path.lineTo(sx, sy)
            }
            i += 2
        }
        path.close()
    }
    return path
}

/** A simple diagonal-line hatch clipped to [path], for the "too few smears" tier. */
private fun DrawScope.drawHatching(path: Path, color: Color) {
    clipPath(path) {
        val bounds = path.getBounds()
        var x = bounds.left - bounds.height
        while (x < bounds.right) {
            drawLine(
                color = color,
                start = Offset(x, bounds.bottom),
                end = Offset(x + bounds.height, bounds.top),
                strokeWidth = STROKE_WIDTH_PX,
            )
            x += HATCH_LINE_SPACING_PX
        }
    }
}
