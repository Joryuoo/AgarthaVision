package com.agarthavision.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.agarthavision.ui.theme.AgarthaVisionTheme
import kotlin.math.min

/**
 * Marks the region of the camera preview that actually becomes a captured frame.
 *
 * `ImageProxy.toJpegBytes` centre-crops each analysis frame to a square before
 * posting it, so everything outside this square is discarded. Drawing it lets the
 * medtech frame the specimen inside what the model will actually see.
 *
 * The geometry only lines up because two things hold:
 * - preview and analysis share one field of view (`CameraManager` pins both to the
 *   same aspect-ratio strategy), and
 * - `PreviewView` uses `FIT_CENTER`, so the whole stream is on screen rather than
 *   centre-cropped to fill. Under `FILL_CENTER` the analysed region is wider than
 *   the display and no on-screen box could mark it honestly.
 *
 * @param previewAspectRatio width / height of the displayed preview stream. Null
 *   until the camera binds, in which case nothing is drawn — better no boundary than
 *   one in the wrong place.
 */
@Composable
fun CaptureFrameBoundary(
    previewAspectRatio: Float?,
    modifier: Modifier = Modifier,
) {
    if (previewAspectRatio == null || previewAspectRatio <= 0f) return

    Canvas(modifier = modifier.fillMaxSize()) {
        // Where FIT_CENTER actually lands the stream inside this view. Same fit-and-
        // centre math as FrameWithBoxes uses for the verification image.
        val scale = min(size.width / previewAspectRatio, size.height)
        val drawnWidth = scale * previewAspectRatio
        val drawnHeight = scale
        val left = (size.width - drawnWidth) / 2f
        val top = (size.height - drawnHeight) / 2f

        // The captured frame is the centred square of that.
        val side = min(drawnWidth, drawnHeight)
        val squareLeft = left + (drawnWidth - side) / 2f
        val squareTop = top + (drawnHeight - side) / 2f

        drawExcludedScrim(squareLeft, squareTop, side)
        drawCornerBrackets(squareLeft, squareTop, side)
    }
}

/** Dims everything the crop throws away, so the live area reads as the subject. */
private fun DrawScope.drawExcludedScrim(left: Float, top: Float, side: Float) {
    val scrim = Color.Black.copy(alpha = 0.35f)
    val right = left + side
    val bottom = top + side

    drawRect(scrim, Offset.Zero, Size(size.width, top))
    drawRect(scrim, Offset(0f, bottom), Size(size.width, size.height - bottom))
    drawRect(scrim, Offset(0f, top), Size(left, side))
    drawRect(scrim, Offset(right, top), Size(size.width - right, side))
}

/**
 * Corner brackets rather than a full outline: they mark the boundary without laying
 * a continuous line over the specimen.
 */
private fun DrawScope.drawCornerBrackets(left: Float, top: Float, side: Float) {
    val stroke = Stroke(width = 2.dp.toPx())
    val arm = side * CORNER_ARM_FRACTION
    val right = left + side
    val bottom = top + side
    val color = Color.White.copy(alpha = 0.9f)

    fun bracket(x: Float, y: Float, dx: Float, dy: Float) {
        drawLine(color, Offset(x, y), Offset(x + dx, y), stroke.width)
        drawLine(color, Offset(x, y), Offset(x, y + dy), stroke.width)
    }

    bracket(left, top, arm, arm)
    bracket(right, top, -arm, arm)
    bracket(left, bottom, arm, -arm)
    bracket(right, bottom, -arm, -arm)
}

private const val CORNER_ARM_FRACTION = 0.08f

@Preview(showBackground = true, backgroundColor = 0xFF1D1D1E)
@Composable
private fun CaptureFrameBoundaryPreview() {
    AgarthaVisionTheme {
        CaptureFrameBoundary(previewAspectRatio = 3f / 4f)
    }
}
