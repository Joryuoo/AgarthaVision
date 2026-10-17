package com.agarthavision.data.inference.ondevice

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import javax.inject.Inject
import javax.inject.Singleton

/** A frame as the model's input tensor, plus the fit needed to map boxes back. */
class PreprocessedFrame(
    /** NHWC RGB, `input_height x input_width x 3`, each channel divided by the manifest scale. */
    val pixels: FloatArray,
    val transform: LetterboxTransform,
)

/**
 * Turns JPEG bytes into the model's input, the way Ultralytics does it in the cloud container:
 * decode, letterbox onto a (114, 114, 114) canvas with bilinear resampling, then write NHWC RGB
 * scaled to 0..1.
 *
 * Every precision takes float input (int8 builds quantise internally), so there is one path.
 */
@Singleton
class FramePreprocessor @Inject constructor() {

    /** Null when [jpegBytes] cannot be decoded. */
    fun preprocess(jpegBytes: ByteArray, manifest: ModelManifest): PreprocessedFrame? {
        val source = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size) ?: return null
        val transform = LetterboxTransform.fit(source.width, source.height, manifest.inputWidth, manifest.inputHeight)
        val input = letterbox(source, transform, manifest)
        if (source !== input) source.recycle()

        val pixels = toTensor(input, manifest)
        input.recycle()
        return PreprocessedFrame(pixels, transform)
    }

    private fun letterbox(source: Bitmap, transform: LetterboxTransform, manifest: ModelManifest): Bitmap {
        // The common case: a 640x640 capture into a 640x640 model. Nothing to scale or pad.
        if (source.width == manifest.inputWidth && source.height == manifest.inputHeight) return source

        val canvasBitmap = Bitmap.createBitmap(manifest.inputWidth, manifest.inputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(canvasBitmap)
        canvas.drawColor(LETTERBOX_FILL)
        val scaled = Bitmap.createScaledBitmap(source, transform.scaledWidth, transform.scaledHeight, true)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(scaled, transform.padLeft.toFloat(), transform.padTop.toFloat(), paint)
        if (scaled !== source) scaled.recycle()
        return canvasBitmap
    }

    private fun toTensor(bitmap: Bitmap, manifest: ModelManifest): FloatArray {
        val pixelCount = manifest.inputWidth * manifest.inputHeight
        val argb = IntArray(pixelCount)
        bitmap.getPixels(argb, 0, manifest.inputWidth, 0, 0, manifest.inputWidth, manifest.inputHeight)

        val scale = manifest.normalizationScale
        val tensor = FloatArray(pixelCount * CHANNELS)
        argb.forEachIndexed { index, pixel ->
            val offset = index * CHANNELS
            tensor[offset] = Color.red(pixel) / scale
            tensor[offset + 1] = Color.green(pixel) / scale
            tensor[offset + 2] = Color.blue(pixel) / scale
        }
        return tensor
    }

    private companion object {
        /** Ultralytics pads with (114, 114, 114). Matching it keeps edge detections comparable. */
        val LETTERBOX_FILL = Color.rgb(114, 114, 114)
        const val CHANNELS = 3
    }
}
