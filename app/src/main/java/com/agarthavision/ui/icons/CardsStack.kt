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
val AgarthaIcons.CardsStack: ImageVector
  get() {
    if (_cardsStack != null) {
      return _cardsStack!!
    }
    _cardsStack =
      ImageVector.Builder(
          name = "cards_stack",
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
            moveTo(8f, 16.1f)
            quadToRelative(-0.82f, 0f, -1.41f, -0.59f)
            quadTo(6f, 14.93f, 6f, 14.1f)
            verticalLineTo(5f)
            quadTo(6f, 4.17f, 6.59f, 3.59f)
            reflectiveQuadTo(8f, 3f)
            horizontalLineTo(20f)
            quadToRelative(0.83f, 0f, 1.41f, 0.59f)
            reflectiveQuadTo(22f, 5f)
            verticalLineToRelative(9.1f)
            quadToRelative(0f, 0.82f, -0.59f, 1.41f)
            reflectiveQuadTo(20f, 16.1f)
            horizontalLineTo(8f)
            close()
            moveToRelative(0f, -2f)
            horizontalLineTo(20f)
            verticalLineTo(5f)
            horizontalLineTo(8f)
            verticalLineToRelative(9.1f)
            close()
            moveToRelative(0f, 0f)
            verticalLineTo(5f)
            verticalLineToRelative(9.1f)
            close()
            moveTo(5.25f, 19f)
            close()
            moveTo(17f, 9f)
            quadToRelative(0.43f, 0f, 0.71f, -0.29f)
            reflectiveQuadTo(18f, 8f)
            quadTo(18f, 7.57f, 17.71f, 7.29f)
            reflectiveQuadTo(17f, 7f)
            horizontalLineTo(10.98f)
            quadToRelative(-0.43f, 0f, -0.7f, 0.29f)
            reflectiveQuadTo(10f, 8f)
            quadToRelative(0f, 0.42f, 0.29f, 0.71f)
            reflectiveQuadTo(11f, 9f)
            horizontalLineToRelative(6f)
            close()
            moveToRelative(-3f, 3f)
            quadToRelative(0.43f, 0f, 0.71f, -0.29f)
            quadTo(15f, 11.43f, 15f, 11f)
            reflectiveQuadTo(14.71f, 10.29f)
            reflectiveQuadTo(14f, 10f)
            horizontalLineTo(10.98f)
            quadToRelative(-0.43f, 0f, -0.7f, 0.29f)
            reflectiveQuadTo(10f, 11f)
            reflectiveQuadToRelative(0.29f, 0.71f)
            reflectiveQuadTo(11f, 12f)
            horizontalLineToRelative(3f)
            close()
            moveTo(5.45f, 20.98f)
            quadTo(4.63f, 21.1f, 3.98f, 20.6f)
            reflectiveQuadTo(3.2f, 19.27f)
            lineTo(1.88f, 9.38f)
            quadTo(1.83f, 8.95f, 2.08f, 8.63f)
            reflectiveQuadTo(2.75f, 8.25f)
            quadTo(3.15f, 8.2f, 3.48f, 8.44f)
            quadTo(3.8f, 8.67f, 3.85f, 9.1f)
            lineTo(5.25f, 19f)
            lineTo(12.1f, 18.05f)
            lineToRelative(4.1f, -0.57f)
            quadTo(16.65f, 17.4f, 17f, 17.7f)
            reflectiveQuadToRelative(0.35f, 0.78f)
            quadToRelative(0f, 0.38f, -0.25f, 0.66f)
            quadToRelative(-0.25f, 0.29f, -0.63f, 0.34f)
            lineTo(5.45f, 20.98f)
            close()
          }
        }
        .build()
    return _cardsStack!!
  }

private var _cardsStack: ImageVector? = null
