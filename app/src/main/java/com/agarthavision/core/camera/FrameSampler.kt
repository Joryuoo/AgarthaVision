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
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Throttles camera frames and dispatches them to inference.
 *
 * Enforces a 2-second interval between sampled frames while a session is active.
 * See docs/03_MOBILE_APP_PLAN.md §1.3.
 */
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

    override fun analyze(image: ImageProxy) {
        try {
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
            image.close()

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
