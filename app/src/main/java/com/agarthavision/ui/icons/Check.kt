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
val AgarthaIcons.Check: ImageVector
  get() {
    if (_check != null) {
      return _check!!
    }
    _check =
      ImageVector.Builder(
          name = "check",
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
            moveTo(9.55f, 18.2f)
            quadToRelative(-0.275f, 0f, -0.5f, -0.1f)
            reflectiveQuadToRelative(-0.4f, -0.3f)
            lineToRelative(-4.35f, -4.35f)
            quadToRelative(-0.3f, -0.3f, -0.3f, -0.712f)
            reflectiveQuadToRelative(0.3f, -0.713f)
            quadToRelative(0.3f, -0.3f, 0.713f, -0.3f)
            reflectiveQuadToRelative(0.712f, 0.3f)
            lineTo(9.55f, 15.85f)
            lineToRelative(9.15f, -9.15f)
            quadToRelative(0.3f, -0.3f, 0.713f, -0.3f)
            reflectiveQuadToRelative(0.712f, 0.3f)
            quadToRelative(0.3f, 0.3f, 0.3f, 0.713f)
            reflectiveQuadToRelative(-0.3f, 0.712f)
            lineToRelative(-9.85f, 9.85f)
            quadToRelative(-0.175f, 0.2f, -0.4f, 0.3f)
            reflectiveQuadToRelative(-0.5f, 0.1f)
            close()
          }
        }
        .build()
    return _check!!
  }

private var _check: ImageVector? = null
