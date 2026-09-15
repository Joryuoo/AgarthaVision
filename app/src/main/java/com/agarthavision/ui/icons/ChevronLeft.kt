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
val AgarthaIcons.ChevronLeft: ImageVector
  get() {
    if (_chevronLeft != null) {
      return _chevronLeft!!
    }
    _chevronLeft =
      ImageVector.Builder(
          name = "chevron_left",
          defaultWidth = 24.dp,
          defaultHeight = 24.dp,
          viewportWidth = 24f,
          viewportHeight = 24f,
          autoMirror = true,
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
            moveTo(10.8f, 12f)
            lineToRelative(3.9f, 3.9f)
            quadToRelative(0.28f, 0.28f, 0.28f, 0.7f)
            quadToRelative(0f, 0.42f, -0.28f, 0.7f)
            reflectiveQuadTo(14f, 17.58f)
            reflectiveQuadTo(13.3f, 17.3f)
            lineTo(8.7f, 12.7f)
            quadTo(8.55f, 12.55f, 8.49f, 12.38f)
            reflectiveQuadTo(8.43f, 12f)
            reflectiveQuadTo(8.49f, 11.63f)
            reflectiveQuadTo(8.7f, 11.3f)
            lineTo(13.3f, 6.7f)
            quadTo(13.58f, 6.43f, 14f, 6.43f)
            reflectiveQuadTo(14.7f, 6.7f)
            reflectiveQuadToRelative(0.28f, 0.7f)
            reflectiveQuadTo(14.7f, 8.1f)
            lineTo(10.8f, 12f)
            close()
          }
        }
        .build()
    return _chevronLeft!!
  }

private var _chevronLeft: ImageVector? = null
