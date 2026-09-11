package com.agarthavision.core.camera

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.agarthavision.core.util.toJpegBytes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Caches the most recent camera frame as JPEG bytes.
 *
 * Per ADR-005, capture is medtech-triggered: there is no auto-dispatch to inference
 * here any more. `analyze()` does exactly one thing per frame — JPEG-encode it and
 * publish it to [latestFrameBytes] — so the Manual Capture flow (Track 2.13) can
 * snapshot the live frame without spinning up a separate `ImageCapture` use case.
 * See CONTEXT.md.
 */
@Singleton
@Suppress("TooGenericExceptionCaught")
class FrameSampler @Inject constructor() : ImageAnalysis.Analyzer {

    private val _latestFrameBytes = MutableStateFlow<ByteArray?>(null)

    /**
     * Most recent JPEG bytes from the camera stream. Updated on every analyzed
     * frame. Null before the first frame arrives.
     */
    val latestFrameBytes: StateFlow<ByteArray?> = _latestFrameBytes.asStateFlow()

    override fun analyze(image: ImageProxy) {
        try {
            val jpegBytes = image.toJpegBytes()
            _latestFrameBytes.value = jpegBytes
        } catch (throwable: Throwable) {
            Log.w(TAG, "Frame sampling failed; continuing capture.", throwable)
        } finally {
            image.close()
        }
    }

    private companion object {
        private const val TAG = "FrameSampler"
    }
}
