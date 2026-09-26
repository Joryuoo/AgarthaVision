package com.agarthavision.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

@Composable
fun Sparkline(
    points: List<Double?>,
    lineColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier.clearAndSetSemantics {},
    ) {
        val nonNullPoints = points.filterNotNull()
        if (nonNullPoints.size < 2) return@Canvas

        val min = nonNullPoints.minOrNull() ?: 0.0
        val max = nonNullPoints.maxOrNull() ?: 0.0
        val range = (max - min).coerceAtLeast(0.0001)

        val w = size.width
        val h = size.height
        val stepX = if (points.size > 1) w / (points.size - 1) else w

        val path = Path()
        var isFirst = true

        for (i in points.indices) {
            val pt = points[i]
            if (pt == null) {
                isFirst = true
                continue
            }
            val x = i * stepX
            val normalizedY = if (max == min) 0.5f else ((pt - min) / range).toFloat()
            val y = h - (normalizedY * (h - 4.dp.toPx()) + 2.dp.toPx())

            if (isFirst) {
                path.moveTo(x, y)
                isFirst = false
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(
                width = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}
