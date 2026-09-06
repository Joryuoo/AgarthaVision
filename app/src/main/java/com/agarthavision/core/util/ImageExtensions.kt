package com.agarthavision.core.util

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import kotlin.math.min

/**
 * Side length, in pixels, of every JPEG this app posts for inference.
 *
 * The model input is 640x640 with server-side letterboxing
 * (`inference/export/out/model_manifest.json`), so sending a square of exactly
 * this size means the server never has to pad or rescale.
 */
const val CAPTURE_FRAME_SIZE_PX = 640

/**
 * Converts a CameraX [ImageProxy] into the app's one canonical frame shape: an
 * upright, centre-cropped square JPEG of [CAPTURE_FRAME_SIZE_PX] on a side.
 *
 * Three things happen here, in order, and the order matters:
 *
 * 1. **Rotate** by `imageInfo.rotationDegrees`. CameraX hands back buffers in
 *    sensor orientation, so without this the model sees portrait frames lying on
 *    their side.
 * 2. **Centre-crop to a square** on the short edge. Cropping before scaling is
 *    what keeps the image from stretching — the aspect ratio is 1:1 going into
 *    the resize and 1:1 coming out, so neither axis is distorted.
 * 3. **Downscale** to [CAPTURE_FRAME_SIZE_PX], filtered. Every device that can
 *    supply a stream at or above that size therefore posts identical geometry,
 *    which is what makes samples comparable across phones.
 *
 * A device whose best stream is smaller than [CAPTURE_FRAME_SIZE_PX] is encoded
 * at its own native square size rather than upscaled: inventing pixels would
 * cost quality and buy nothing, since the server letterboxes to 640 regardless.
 */
fun ImageProxy.toJpegBytes(quality: Int = 80): ByteArray {
    val source = toBitmap()
    val rotation = imageInfo.rotationDegrees

    val upright = if (rotation == 0) {
        source
    } else {
        Bitmap.createBitmap(
            source,
            0,
            0,
            source.width,
            source.height,
            Matrix().apply { postRotate(rotation.toFloat()) },
            true,
        )
    }

    val side = min(upright.width, upright.height)
    val square = Bitmap.createBitmap(
        upright,
        (upright.width - side) / 2,
        (upright.height - side) / 2,
        side,
        side,
    )

    // Never upscale — see the KDoc.
    val target = min(side, CAPTURE_FRAME_SIZE_PX)
    val scaled = if (side == target) {
        square
    } else {
        square.scale(target, target)
    }

    val stream = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, quality, stream)

    // FrameSampler runs this on every analysed frame, so the intermediates are
    // released eagerly rather than left for the collector.
    if (scaled !== square) scaled.recycle()
    if (square !== upright) square.recycle()
    if (upright !== source) upright.recycle()

    return stream.toByteArray()
}

private fun Bitmap.scale(width: Int, height: Int): Bitmap =
    Bitmap.createScaledBitmap(this, width, height, true)
