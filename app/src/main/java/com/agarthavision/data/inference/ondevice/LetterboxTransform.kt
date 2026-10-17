package com.agarthavision.data.inference.ondevice

import kotlin.math.roundToInt

/**
 * How a source frame was fitted into the model's input, so detections can be mapped back to
 * source-image pixels afterwards.
 *
 * Ultralytics letterboxes: scale by the smaller ratio to keep the aspect, round the scaled
 * size, then centre it on a grey canvas. The arithmetic below mirrors its `LetterBox` and
 * `scale_boxes` step for step, rounding included, because the cloud container runs exactly
 * that code and the two engines' boxes have to land on the same pixels.
 *
 * Captured frames are already 640x640, so for the shipped model this is the identity. It
 * matters on cameras whose best stream is smaller (`core/util/ImageExtensions.kt`).
 */
data class LetterboxTransform(
    val scale: Float,
    val padLeft: Int,
    val padTop: Int,
    val scaledWidth: Int,
    val scaledHeight: Int,
    val sourceWidth: Int,
    val sourceHeight: Int,
) {
    /** Maps an x coordinate from model-input pixels back to source pixels. */
    fun sourceX(inputX: Float): Float = (inputX - padLeft) / scale

    /** Maps a y coordinate from model-input pixels back to source pixels. */
    fun sourceY(inputY: Float): Float = (inputY - padTop) / scale

    /** Maps a length from model-input pixels back to source pixels. */
    fun sourceLength(inputLength: Float): Float = inputLength / scale

    companion object {
        fun fit(sourceWidth: Int, sourceHeight: Int, inputWidth: Int, inputHeight: Int): LetterboxTransform {
            require(sourceWidth > 0 && sourceHeight > 0) { "Empty source frame" }
            val scale = minOf(inputWidth.toFloat() / sourceWidth, inputHeight.toFloat() / sourceHeight)
            val scaledWidth = (sourceWidth * scale).roundToInt()
            val scaledHeight = (sourceHeight * scale).roundToInt()
            return LetterboxTransform(
                scale = scale,
                // Ultralytics' round(pad - 0.1): an odd leftover pixel goes to the right/bottom.
                padLeft = ((inputWidth - scaledWidth) / 2f - PAD_BIAS).roundToInt(),
                padTop = ((inputHeight - scaledHeight) / 2f - PAD_BIAS).roundToInt(),
                scaledWidth = scaledWidth,
                scaledHeight = scaledHeight,
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
            )
        }

        private const val PAD_BIAS = 0.1f
    }
}
