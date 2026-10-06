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
val AgarthaIcons.Description: ImageVector
  get() {
    if (_description != null) {
      return _description!!
    }
    _description =
      ImageVector.Builder(
          name = "description",
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
            moveTo(8f, 18f)
            quadToRelative(-0.425f, 0f, -0.712f, -0.288f)
            reflectiveQuadTo(7f, 17f)
            reflectiveQuadToRelative(0.288f, -0.712f)
            reflectiveQuadTo(8f, 16f)
            horizontalLineToRelative(8f)
            quadToRelative(0.425f, 0f, 0.713f, 0.288f)
            reflectiveQuadTo(17f, 17f)
            reflectiveQuadToRelative(-0.288f, 0.713f)
            reflectiveQuadTo(16f, 18f)
            close()
            moveTo(8f, 14f)
            quadToRelative(-0.425f, 0f, -0.712f, -0.288f)
            reflectiveQuadTo(7f, 13f)
            reflectiveQuadToRelative(0.288f, -0.712f)
            reflectiveQuadTo(8f, 12f)
            horizontalLineToRelative(8f)
            quadToRelative(0.425f, 0f, 0.713f, 0.288f)
            reflectiveQuadTo(17f, 13f)
            reflectiveQuadToRelative(-0.288f, 0.713f)
            reflectiveQuadTo(16f, 14f)
            close()
            moveTo(6f, 22f)
            quadToRelative(-0.825f, 0f, -1.412f, -0.587f)
            reflectiveQuadTo(4f, 20f)
            verticalLineTo(4f)
            quadToRelative(0f, -0.825f, 0.588f, -1.412f)
            reflectiveQuadTo(6f, 2f)
            horizontalLineToRelative(8f)
            lineToRelative(6f, 6f)
            verticalLineToRelative(12f)
            quadToRelative(0f, 0.825f, -0.587f, 1.413f)
            reflectiveQuadTo(18f, 22f)
            close()
            moveTo(13f, 9f)
            verticalLineTo(4f)
            horizontalLineTo(6f)
            verticalLineToRelative(16f)
            horizontalLineToRelative(12f)
            verticalLineTo(9f)
            close()
          }
        }
        .build()
    return _description!!
  }

private var _description: ImageVector? = null
