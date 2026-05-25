package com.agarthavision.core.util

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

/**
 * Converts a CameraX [ImageProxy] to JPEG bytes.
 */
fun ImageProxy.toJpegBytes(quality: Int = 80): ByteArray {
    val bitmap = toBitmap()
    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
    return stream.toByteArray()
}
