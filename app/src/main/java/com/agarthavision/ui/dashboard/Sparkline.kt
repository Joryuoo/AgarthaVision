package com.agarthavision.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Sparkline(
    points: List<Double?>,
    lineColor: Color,
    modifier: Modifier = Modifier,
    lineWidth: Dp = 2.dp,
    baselineZero: Boolean = true,
) {
    Canvas(modifier = modifier) {
        if (points.isEmpty()) return@Canvas

        val nonNullPoints = points.filterNotNull()
        val w = size.width
        val h = size.height
        val botPad = 3.dp.toPx()
        val topPad = 3.dp.toPx()

        if (nonNullPoints.isEmpty()) {
            val y = h - botPad
            drawLine(
                color = lineColor.copy(alpha = lineColor.alpha * 0.4f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = lineWidth.toPx(),
                cap = StrokeCap.Round,
            )
            return@Canvas
        }

        val filled = fillGaps(points, nonNullPoints.first())
        val rawMin = nonNullPoints.minOrNull() ?: 0.0
        val rawMax = nonNullPoints.maxOrNull() ?: 0.0

        val min = if (baselineZero) 0.0.coerceAtMost(rawMin) else rawMin
        val max = if (baselineZero) rawMax.coerceAtLeast(3.0) else rawMax
        val range = (max - min).coerceAtLeast(0.0001)

        val stepX = if (points.size > 1) w / (points.size - 1) else w
        val drawH = h - topPad - botPad

        val getY: (Double) -> Float = { value ->
            val norm = if (max == min) 0.5f else ((value - min) / range).toFloat().coerceIn(0f, 1f)
            h - botPad - (norm * drawH)
        }

        if (rawMin == rawMax && rawMin == 0.0) {
            val y = h - botPad
            drawLine(
                color = lineColor.copy(alpha = lineColor.alpha * 0.5f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = lineWidth.toPx(),
                cap = StrokeCap.Round,
            )
            return@Canvas
        }

        val path = buildSparklinePath(filled, stepX, getY)

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(
                width = lineWidth.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

private fun fillGaps(points: List<Double?>, fallback: Double): DoubleArray {
    val filled = DoubleArray(points.size)
    var current = fallback
    for (i in points.indices) {
        val pt = points[i]
        if (pt != null) {
            current = pt
        }
        filled[i] = current
    }
    val firstNonNull = points.indexOfFirst { it != null }
    if (firstNonNull > 0) {
        for (i in 0 until firstNonNull) {
            filled[i] = fallback
        }
    }
    return filled
}

private fun buildSparklinePath(
    filled: DoubleArray,
    stepX: Float,
    getY: (Double) -> Float,
): Path {
    val path = Path()
    path.moveTo(0f, getY(filled[0]))
    for (i in 0 until filled.size - 1) {
        val x0 = i * stepX
        val y0 = getY(filled[i])
        val x1 = (i + 1) * stepX
        val y1 = getY(filled[i + 1])
        val cx = (x0 + x1) / 2f
        path.cubicTo(cx, y0, cx, y1, x1, y1)
    }
    return path
}
