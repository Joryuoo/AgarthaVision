package com.agarthavision.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
val AgarthaIcons.Close: ImageVector
  get() {
    if (_close != null) {
      return _close!!
    }
    _close =
      ImageVector.Builder(
          name = "close",
          defaultWidth = 24.dp,
          defaultHeight = 24.dp,
          viewportWidth = 24f,
          viewportHeight = 24f,
        )
        .apply {
          path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Bevel,
            strokeLineMiter = 1f,
            pathFillType = PathFillType.Companion.NonZero,
          ) {
            moveTo(6.4f, 19f)
            quadToRelative(-0.425f, 0f, -0.712f, -0.288f)
            reflectiveQuadTo(5.4f, 18f)
            reflectiveQuadToRelative(0.288f, -0.712f)
            lineTo(10.575f, 12f)
            lineTo(5.688f, 7.112f)
            quadToRelative(-0.288f, -0.287f, -0.288f, -0.712f)
            reflectiveQuadToRelative(0.288f, -0.713f)
            quadToRelative(0.287f, -0.287f, 0.712f, -0.287f)
            reflectiveQuadToRelative(0.713f, 0.287f)
            lineTo(12f, 10.575f)
            lineToRelative(4.888f, -4.888f)
            quadToRelative(0.287f, -0.287f, 0.712f, -0.287f)
            reflectiveQuadToRelative(0.713f, 0.287f)
            quadToRelative(0.287f, 0.288f, 0.287f, 0.713f)
            reflectiveQuadToRelative(-0.287f, 0.712f)
            lineTo(13.425f, 12f)
            lineToRelative(4.888f, 4.888f)
            quadToRelative(0.287f, 0.287f, 0.287f, 0.712f)
            reflectiveQuadToRelative(-0.287f, 0.713f)
            quadToRelative(-0.288f, 0.287f, -0.713f, 0.287f)
            reflectiveQuadToRelative(-0.712f, -0.287f)
            lineTo(12f, 13.425f)
            lineToRelative(-4.888f, 4.888f)
            quadTo(6.825f, 19f, 6.4f, 19f)
            close()
          }
        }
        .build()
    return _close!!
  }

private var _close: ImageVector? = null
