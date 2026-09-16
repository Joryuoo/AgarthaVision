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
val AgarthaIcons.HomeAppLogo: ImageVector
  get() {
    if (_homeAppLogo != null) {
      return _homeAppLogo!!
    }
    _homeAppLogo =
      ImageVector.Builder(
          name = "home_app_logo",
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
            moveTo(12f, 13.33f)
            close()
            moveTo(6f, 21f)
            quadTo(4.75f, 21f, 3.88f, 20.13f)
            reflectiveQuadTo(3f, 18f)
            verticalLineTo(12f)
            quadTo(3f, 11.4f, 3.23f, 10.85f)
            reflectiveQuadTo(3.88f, 9.88f)
            lineToRelative(6f, -6f)
            quadTo(10.3f, 3.42f, 10.86f, 3.21f)
            reflectiveQuadTo(12f, 3f)
            reflectiveQuadToRelative(1.13f, 0.21f)
            reflectiveQuadToRelative(1f, 0.66f)
            lineToRelative(6f, 6f)
            quadToRelative(0.43f, 0.42f, 0.65f, 0.97f)
            reflectiveQuadTo(21f, 12f)
            verticalLineToRelative(6f)
            quadToRelative(0f, 1.25f, -0.88f, 2.13f)
            reflectiveQuadTo(18f, 21f)
            horizontalLineTo(6f)
            close()
            moveTo(6f, 19f)
            horizontalLineTo(18f)
            quadToRelative(0.43f, 0f, 0.71f, -0.29f)
            quadTo(19f, 18.43f, 19f, 18f)
            verticalLineTo(12f)
            quadToRelative(0f, -0.2f, -0.07f, -0.38f)
            reflectiveQuadTo(18.7f, 11.3f)
            lineTo(14.88f, 7.45f)
            lineTo(13.4f, 8.9f)
            lineTo(17f, 12.5f)
            verticalLineTo(17f)
            horizontalLineTo(7f)
            verticalLineTo(12.5f)
            lineTo(13.45f, 6.05f)
            lineTo(12.7f, 5.3f)
            quadTo(12.5f, 5.1f, 12.31f, 5.05f)
            reflectiveQuadTo(12f, 5f)
            reflectiveQuadTo(11.69f, 5.05f)
            reflectiveQuadTo(11.3f, 5.3f)
            lineToRelative(-6f, 6f)
            quadTo(5.15f, 11.45f, 5.08f, 11.63f)
            reflectiveQuadTo(5f, 12f)
            verticalLineToRelative(6f)
            quadToRelative(0f, 0.43f, 0.29f, 0.71f)
            reflectiveQuadTo(6f, 19f)
            close()
            moveTo(9f, 15f)
            horizontalLineToRelative(6f)
            verticalLineTo(13.33f)
            lineToRelative(-3f, -3f)
            lineToRelative(-3f, 3f)
            verticalLineTo(15f)
            close()
          }
        }
        .build()
    return _homeAppLogo!!
  }

private var _homeAppLogo: ImageVector? = null
