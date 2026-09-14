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
val AgarthaIcons.Science: ImageVector
  get() {
    if (_science != null) {
      return _science!!
    }
    _science =
      ImageVector.Builder(
          name = "science",
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
            moveTo(5f, 21f)
            quadTo(3.73f, 21f, 3.19f, 19.86f)
            reflectiveQuadTo(3.45f, 17.75f)
            lineTo(9f, 11f)
            verticalLineTo(5f)
            horizontalLineTo(8f)
            quadTo(7.58f, 5f, 7.29f, 4.71f)
            reflectiveQuadTo(7f, 4f)
            quadTo(7f, 3.57f, 7.29f, 3.29f)
            reflectiveQuadTo(8f, 3f)
            horizontalLineToRelative(8f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(17f, 4f)
            quadToRelative(0f, 0.42f, -0.29f, 0.71f)
            reflectiveQuadTo(16f, 5f)
            horizontalLineTo(15f)
            verticalLineToRelative(6f)
            lineToRelative(5.55f, 6.75f)
            quadToRelative(0.8f, 0.98f, 0.26f, 2.11f)
            quadTo(20.28f, 21f, 19f, 21f)
            horizontalLineTo(5f)
            close()
            moveTo(5f, 19f)
            horizontalLineTo(19f)
            lineTo(13f, 11.7f)
            verticalLineTo(5f)
            horizontalLineTo(11f)
            verticalLineToRelative(6.7f)
            lineTo(5f, 19f)
            close()
            moveToRelative(7f, -7f)
            close()
          }
        }
        .build()
    return _science!!
  }

private var _science: ImageVector? = null
