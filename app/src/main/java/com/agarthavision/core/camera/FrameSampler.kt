package com.agarthavision.core.camera

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.agarthavision.core.util.ElapsedClock
import com.agarthavision.core.util.toJpegBytes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A camera frame, JPEG-encoded, carrying the moment it was analyzed.
 *
 * [elapsedRealtimeMs] is what makes the cache safe to read. [FrameSampler] is a
 * `@Singleton` whose cache outlives any one camera binding, so a consumer has to be able
 * to tell "the frame on screen right now" from "the last frame of the previous session" —
 * see `CaptureViewModel.onCapture`.
 *
 * **`equals` is deliberately not overridden — do not "fix" this.** Identity comparison on
 * [jpegBytes] is the behaviour we want: `analyze()` allocates a fresh array per frame, so
 * every publish is unequal to the last and [FrameSampler.latestFrame]'s `StateFlow` can
 * never conflate a new frame away. (Contrast `FlaggedFrame`, which excludes its bytes from
 * equality precisely because its store re-reads the same JPEG off disk on each emission.)
 * It is a plain `class`, not a `data class`, so nobody expects value semantics from it.
 */
class CachedFrame(
    val jpegBytes: ByteArray,
    val elapsedRealtimeMs: Long,
)

/**
 * Caches the most recent camera frame as JPEG bytes, stamped with its arrival time.
 *
 * Per ADR-005, capture is medtech-triggered: there is no auto-dispatch to inference
 * here any more. `analyze()` does exactly one thing per frame — JPEG-encode it and
 * publish it to [latestFrame] — so the Manual Capture flow (Track 2.13) can
 * snapshot the live frame without spinning up a separate `ImageCapture` use case.
 *
 * **This class has no knowledge of sessions and must not gain any.** The cache is process-
 * scoped and nothing resets it on a session change, a screen exit, or a camera rebind.
 * Staleness is handled at the read end, by the [CachedFrame.elapsedRealtimeMs] stamp — a
 * consumer rejects anything too old. That closes every stale path, including ones nobody
 * enumerated, rather than depending on a reset call somebody has to remember to make.
 *
 * See CONTEXT.md.
 */
@Singleton
@Suppress("TooGenericExceptionCaught")
class FrameSampler @Inject constructor(
    private val clock: ElapsedClock,
) : ImageAnalysis.Analyzer {

    private val _latestFrame = MutableStateFlow<CachedFrame?>(null)

    /**
     * Most recent frame from the camera stream, with the [ElapsedClock] reading taken as it
     * was encoded. Updated on every analyzed frame. Null before the first frame arrives.
     *
     * A non-null value is **not** a promise the frame is current — it may predate the
     * active camera binding. Check [CachedFrame.elapsedRealtimeMs] before using the bytes.
     */
    val latestFrame: StateFlow<CachedFrame?> = _latestFrame.asStateFlow()

    override fun analyze(image: ImageProxy) {
        try {
            val jpegBytes = image.toJpegBytes()
            _latestFrame.value = CachedFrame(jpegBytes, clock.elapsedRealtimeMs())
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
