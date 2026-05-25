package com.agarthavision.core.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.agarthavision.core.session.SessionManager
import com.agarthavision.core.session.SessionState
import com.agarthavision.core.util.toJpegBytes
import com.agarthavision.domain.usecase.capture.InferFrameUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Throttles camera frames and dispatches them to inference.
 *
 * Enforces a 2-second interval between sampled frames while a session is active.
 * See docs/03_MOBILE_APP_PLAN.md §1.3.
 */
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

    override fun analyze(image: ImageProxy) {
        val state = sessionManager.state.value
        if (state !is SessionState.Recording) {
            image.close()
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastSentAt < intervalMs || inFlight) {
            image.close()
            return
        }

        lastSentAt = now
        inFlight = true

        val jpegBytes = image.toJpegBytes()
        image.close() // CRITICAL: Backpressure relies on this

        scope.launch {
            try {
                inferFrameUseCase(state.session.sessionId, jpegBytes)
            } finally {
                inFlight = false
            }
        }
    }
}
