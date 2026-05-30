package com.agarthavision.core.camera

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.agarthavision.core.session.SessionManager
import com.agarthavision.core.session.SessionState
import com.agarthavision.core.util.toJpegBytes
import com.agarthavision.domain.usecase.capture.InferFrameUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Throttles camera frames and dispatches them to inference.
 *
 * Enforces a 2-second interval between sampled frames while a session is active.
 * Per ADR-005, also caches the most recent JPEG ([latestFrameBytes]) so the
 * Manual Capture flow (Track 2.13) can snapshot the live frame without spinning
 * up a separate ImageCapture use case. See docs/03_MOBILE_APP_PLAN.md §1.3, §1.6b.
 */
@Singleton
@Suppress("TooGenericExceptionCaught")
class FrameSampler @Inject constructor(
    private val inferFrameUseCase: InferFrameUseCase,
    private val sessionManager: SessionManager,
) : ImageAnalysis.Analyzer {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val intervalMs = 2_000L

    @Volatile
    private var lastSentAt = 0L

    @Volatile
    private var inFlight = false

    private val _latestFrameBytes = MutableStateFlow<ByteArray?>(null)

    /**
     * Most recent JPEG bytes from the camera stream. Updated on every frame
     * (subject to the [intervalMs] throttle below for inference, but the snapshot
     * cache refreshes on every analyzed frame so manual capture has a fresh
     * image). Null before the first frame arrives.
     */
    val latestFrameBytes: StateFlow<ByteArray?> = _latestFrameBytes.asStateFlow()

    override fun analyze(image: ImageProxy) {
        try {
            val state = sessionManager.state.value
            val jpegBytes = image.toJpegBytes()
            image.close()

            // Always refresh the snapshot cache so Manual Capture (Track 2.13)
            // can grab the latest frame regardless of inference throttling.
            _latestFrameBytes.value = jpegBytes

            if (state !is SessionState.Active || !state.isInferenceRunning) return

            val now = System.currentTimeMillis()
            if (now - lastSentAt < intervalMs || inFlight) return

            lastSentAt = now
            inFlight = true

            scope.launch {
                runCatching {
                    inferFrameUseCase(state.session.sessionId, jpegBytes)
                }.onFailure { throwable ->
                    Log.w(TAG, "Frame inference failed; continuing capture.", throwable)
                }
                inFlight = false
            }
        } catch (throwable: Throwable) {
            image.close()
            inFlight = false
            Log.w(TAG, "Frame sampling failed; continuing capture.", throwable)
        }
    }

    private companion object {
        private const val TAG = "FrameSampler"
    }
}
