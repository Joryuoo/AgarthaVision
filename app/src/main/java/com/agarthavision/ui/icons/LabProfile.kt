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
val AgarthaIcons.LabProfile: ImageVector
  get() {
    if (_labProfile != null) {
      return _labProfile!!
    }
    _labProfile =
      ImageVector.Builder(
          name = "lab_profile",
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
            moveTo(9f, 12f)
            quadTo(8.58f, 12f, 8.29f, 11.71f)
            quadTo(8f, 11.43f, 8f, 11f)
            reflectiveQuadTo(8.29f, 10.29f)
            quadTo(8.58f, 10f, 9f, 10f)
            horizontalLineToRelative(6f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(16f, 11f)
            reflectiveQuadToRelative(-0.29f, 0.71f)
            reflectiveQuadTo(15f, 12f)
            horizontalLineTo(9f)
            close()
            moveTo(9f, 8f)
            quadTo(8.58f, 8f, 8.29f, 7.71f)
            quadTo(8f, 7.43f, 8f, 7f)
            reflectiveQuadTo(8.29f, 6.29f)
            quadTo(8.58f, 6f, 9f, 6f)
            horizontalLineToRelative(6f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(16f, 7f)
            reflectiveQuadTo(15.71f, 7.71f)
            reflectiveQuadTo(15f, 8f)
            horizontalLineTo(9f)
            close()
            moveTo(6f, 14f)
            horizontalLineToRelative(7.5f)
            quadToRelative(0.73f, 0f, 1.35f, 0.31f)
            reflectiveQuadTo(15.9f, 15.2f)
            lineTo(18f, 17.95f)
            verticalLineTo(4f)
            horizontalLineTo(6f)
            verticalLineTo(14f)
            close()
            moveToRelative(0f, 6f)
            horizontalLineTo(17.05f)
            lineTo(14.33f, 16.43f)
            quadToRelative(-0.15f, -0.2f, -0.36f, -0.31f)
            reflectiveQuadTo(13.5f, 16f)
            horizontalLineTo(6f)
            verticalLineToRelative(4f)
            close()
            moveToRelative(12f, 2f)
            horizontalLineTo(6f)
            quadTo(5.18f, 22f, 4.59f, 21.41f)
            reflectiveQuadTo(4f, 20f)
            verticalLineTo(4f)
            quadTo(4f, 3.17f, 4.59f, 2.59f)
            reflectiveQuadTo(6f, 2f)
            horizontalLineTo(18f)
            quadToRelative(0.82f, 0f, 1.41f, 0.59f)
            reflectiveQuadTo(20f, 4f)
            verticalLineTo(20f)
            quadToRelative(0f, 0.82f, -0.59f, 1.41f)
            reflectiveQuadTo(18f, 22f)
            close()
            moveTo(6f, 20f)
            verticalLineTo(4f)
            verticalLineTo(20f)
            close()
            moveTo(6f, 16f)
            verticalLineTo(14f)
            verticalLineToRelative(2f)
            close()
          }
        }
        .build()
    return _labProfile!!
  }

private var _labProfile: ImageVector? = null
