package com.agarthavision.ui.dashboard.coverage

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import com.agarthavision.domain.geo.AreaShape
import com.agarthavision.domain.geo.BoundarySet
import com.agarthavision.domain.geo.GeoBounds
import com.agarthavision.domain.geo.PositiveRateBin
import com.agarthavision.domain.geo.ViewTransform
import com.agarthavision.domain.geo.fitBounds
import com.agarthavision.domain.model.AreaStat
import com.agarthavision.domain.model.ProvinceCoverage
import com.agarthavision.ui.theme.AgarthaColors

private const val MAP_PADDING_PX = 8f
private const val POSITIVE_RATE_BIN_COUNT = 4f
private const val HATCH_LINE_SPACING_PX = 6f
private const val STROKE_WIDTH_PX = 1.2f

/**
 * A minimal, static (no gestures) province choropleth for the My coverage card. Gestures and
 * richer rendering live only in the later full-screen coverage phase — this is the pager card's
 * small preview.
 */
@Composable
internal fun MiniChoroplethMap(
    provinces: BoundarySet,
    coverageByCode: Map<String, ProvinceCoverage>,
    fitBounds: GeoBounds,
    colors: AgarthaColors,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val transform = fitBounds(
            bounds = fitBounds,
            widthPx = size.width,
            heightPx = size.height,
            paddingPx = MAP_PADDING_PX,
        )
        provinces.areas.forEach { area ->
            val stat = coverageByCode[area.code]?.count?.stat
            drawArea(area, transform, stat, colors)
        }
    }
}

private fun DrawScope.drawArea(
    area: AreaShape,
    transform: ViewTransform,
    stat: AreaStat?,
    colors: AgarthaColors,
) {
    val path = pathFor(area, transform)
    when (stat) {
        is AreaStat.Reported -> {
            val bin = PositiveRateBin.of(stat.positiveRate)
            val fill = lerp(colors.accentTint, colors.accent, bin / POSITIVE_RATE_BIN_COUNT)
            drawPath(path, color = fill)
        }
        AreaStat.TooFew -> {
            drawPath(path, color = colors.surfaceMuted)
            drawHatching(path, colors.border)
        }
        AreaStat.NoData, null -> {
            drawPath(path, color = colors.surfaceMuted)
            drawPath(path, color = colors.border, style = Stroke(STROKE_WIDTH_PX))
        }
    }
}

private fun pathFor(area: AreaShape, transform: ViewTransform): Path {
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
