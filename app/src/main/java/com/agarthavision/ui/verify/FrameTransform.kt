package com.agarthavision.ui.verify

import com.agarthavision.domain.inference.ImageBox
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The mapping between source-image pixels and canvas pixels for a frame drawn with
 * `ContentScale.Fit`.
 *
 * `Fit` scales uniformly to the longer edge and letterboxes the shorter one, so an overlay that
 * ignored the letterbox bands would land its boxes on empty space beside the image rather than on
 * the image itself. This replicates that arithmetic by hand, in both directions.
 *
 * Pure, and kept out of the composable on purpose: the inverse is the one piece of this feature
 * with a wrong answer that still looks plausible, and a unit test is far cheaper than finding it
 * on a device.
 *
 * @property scale source pixels to canvas pixels, uniform on both axes.
 * @property offsetX left letterbox band, in canvas pixels.
 * @property offsetY top letterbox band, in canvas pixels.
 */
internal data class FrameTransform(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
)

/**
 * The transform for a [sourceWidth] x [sourceHeight] image drawn to fit a
 * [canvasWidth] x [canvasHeight] box.
 *
 * Null when any dimension is zero or negative: there is no meaningful mapping, and returning a
 * transform with an infinite or zero scale would push the failure downstream into geometry that
 * merely looks wrong.
 */
@Suppress("ComplexCondition")
internal fun frameTransform(
    canvasWidth: Float,
    canvasHeight: Float,
    sourceWidth: Float,
    sourceHeight: Float,
): FrameTransform? {
    if (canvasWidth <= 0f || canvasHeight <= 0f || sourceWidth <= 0f || sourceHeight <= 0f) {
        return null
    }
    val scale = min(canvasWidth / sourceWidth, canvasHeight / sourceHeight)
    return FrameTransform(
        scale = scale,
        offsetX = (canvasWidth - sourceWidth * scale) / 2f,
        offsetY = (canvasHeight - sourceHeight * scale) / 2f,
    )
}

/** Image pixel to canvas pixel, horizontally. */
internal fun FrameTransform.toCanvasX(imageX: Float): Float = offsetX + imageX * scale

/** Image pixel to canvas pixel, vertically. */
internal fun FrameTransform.toCanvasY(imageY: Float): Float = offsetY + imageY * scale

/** Canvas pixel back to image pixel, horizontally. The inverse of [toCanvasX]. */
internal fun FrameTransform.toImageX(canvasX: Float): Float = (canvasX - offsetX) / scale

/** Canvas pixel back to image pixel, vertically. The inverse of [toCanvasY]. */
internal fun FrameTransform.toImageY(canvasY: Float): Float = (canvasY - offsetY) / scale

/**
 * Turns two canvas corners — where the drag started and where it ended — into a box in image
 * space.
 *
 * **The result is centre-based**, matching `Prediction`. This is the single most likely bug in
 * the feature: storing the drag's top-left corner as `x`/`y` renders every new box offset by half
 * its own size, and nothing throws. The conversion lives here, once, so there is one place for it
 * to be right.
 *
 * The corners are normalised, so dragging right-to-left or bottom-to-top produces the same box as
 * dragging the other way rather than a negative width the renderer would silently drop.
 */
internal fun FrameTransform.imageBoxBetween(
    startCanvasX: Float,
    startCanvasY: Float,
    endCanvasX: Float,
    endCanvasY: Float,
): ImageBox {
    val left = toImageX(min(startCanvasX, endCanvasX))
    val right = toImageX(max(startCanvasX, endCanvasX))
    val top = toImageY(min(startCanvasY, endCanvasY))
    val bottom = toImageY(max(startCanvasY, endCanvasY))
    return ImageBox(
        x = (left + right) / 2f,
        y = (top + bottom) / 2f,
        width = abs(right - left),
        height = abs(bottom - top),
    )
}
