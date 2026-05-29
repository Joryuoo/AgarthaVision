package com.agarthavision.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.material3.LocalContentColor

@Composable
fun SvgIcon(
    pathData: String,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    strokeWidth: Float = 1.6f,
    drawExtras: (DrawScope.() -> Unit)? = null,
) {
    val path = remember(pathData) {
        PathParser().parsePathString(pathData).toPath()
    }
    Canvas(modifier = modifier) {
        val iconScale = size.width / 24f
        scale(iconScale, iconScale, pivot = Offset.Zero) {
            if (pathData.isNotEmpty()) {
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
            drawExtras?.invoke(this)
        }
    }
}
