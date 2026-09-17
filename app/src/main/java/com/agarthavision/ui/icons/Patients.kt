package com.agarthavision.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The Patients tab glyph: a head over shoulders.
 *
 * `Science` read as a lab flask and could never read as people, which is what this tab
 * lists now.
 *
 * **Drawn here rather than exported.** Every other glyph in this package is a Material
 * Symbols (Rounded, fill 0) export from 86d4b1gb5, and this one should be too — swap it
 * for the real `person` / `groups` export before merge so it matches its neighbours' stroke
 * weight and optical sizing exactly. The geometry below is deliberately simple so the
 * substitution is a file replacement and nothing else: nothing outside this file knows the
 * shape.
 */
@Suppress("CheckReturnValue")
val AgarthaIcons.Patients: ImageVector
  get() {
    if (_patients != null) {
      return _patients!!
    }
    _patients =
      ImageVector.Builder(
          name = "patients",
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
            strokeLineJoin = StrokeJoin.Round,
            strokeLineMiter = 1f,
            pathFillType = PathFillType.Companion.NonZero,
          ) {
            // Head: a circle of radius 3.5 centred on (12, 7.5), drawn as two semicircles.
            moveTo(12f, 4f)
            arcTo(3.5f, 3.5f, 0f, false, true, 12f, 11f)
            arcTo(3.5f, 3.5f, 0f, false, true, 12f, 4f)
            close()
            // Shoulders: a half-ellipse rising from the baseline at y = 20.
            moveTo(4.5f, 20f)
            arcTo(7.5f, 6.5f, 0f, false, true, 19.5f, 20f)
            close()
          }
        }
        .build()
    return _patients!!
  }

private var _patients: ImageVector? = null
