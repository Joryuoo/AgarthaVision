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
val AgarthaIcons.FileOpen: ImageVector
  get() {
    if (_fileOpen != null) {
      return _fileOpen!!
    }
    _fileOpen =
      ImageVector.Builder(
          name = "file_open",
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
            moveTo(6f, 22f)
            quadTo(5.18f, 22f, 4.59f, 21.41f)
            reflectiveQuadTo(4f, 20f)
            verticalLineTo(4f)
            quadTo(4f, 3.17f, 4.59f, 2.59f)
            reflectiveQuadTo(6f, 2f)
            horizontalLineToRelative(7.18f)
            quadToRelative(0.4f, 0f, 0.76f, 0.15f)
            reflectiveQuadToRelative(0.64f, 0.43f)
            lineToRelative(4.85f, 4.85f)
            quadTo(19.7f, 7.7f, 19.85f, 8.06f)
            quadTo(20f, 8.42f, 20f, 8.82f)
            verticalLineTo(13f)
            quadToRelative(0f, 0.42f, -0.29f, 0.71f)
            reflectiveQuadTo(19f, 14f)
            quadToRelative(-0.43f, 0f, -0.71f, -0.29f)
            quadTo(18f, 13.43f, 18f, 13f)
            verticalLineTo(9f)
            horizontalLineTo(14f)
            quadTo(13.58f, 9f, 13.29f, 8.71f)
            reflectiveQuadTo(13f, 8f)
            verticalLineTo(4f)
            horizontalLineTo(6f)
            verticalLineTo(20f)
            horizontalLineToRelative(8f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(15f, 21f)
            quadToRelative(0f, 0.43f, -0.29f, 0.71f)
            reflectiveQuadTo(14f, 22f)
            horizontalLineTo(6f)
            close()
            moveTo(19f, 19.43f)
            verticalLineToRelative(1.22f)
            quadToRelative(0f, 0.43f, -0.29f, 0.71f)
            reflectiveQuadTo(18f, 21.65f)
            reflectiveQuadTo(17.29f, 21.36f)
            reflectiveQuadTo(17f, 20.65f)
            verticalLineTo(17f)
            quadToRelative(0f, -0.43f, 0.29f, -0.71f)
            reflectiveQuadTo(18f, 16f)
            horizontalLineToRelative(3.65f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(22.65f, 17f)
            reflectiveQuadToRelative(-0.29f, 0.71f)
            reflectiveQuadTo(21.65f, 18f)
            horizontalLineTo(20.4f)
            lineToRelative(2.25f, 2.25f)
            quadToRelative(0.27f, 0.27f, 0.27f, 0.69f)
            reflectiveQuadToRelative(-0.27f, 0.71f)
            quadToRelative(-0.3f, 0.3f, -0.71f, 0.3f)
            reflectiveQuadToRelative(-0.71f, -0.3f)
            lineTo(19f, 19.43f)
            close()
            moveTo(6f, 20f)
            verticalLineTo(14f)
            verticalLineTo(9f)
            verticalLineTo(4f)
            verticalLineTo(20f)
            close()
          }
        }
        .build()
    return _fileOpen!!
  }

private var _fileOpen: ImageVector? = null
