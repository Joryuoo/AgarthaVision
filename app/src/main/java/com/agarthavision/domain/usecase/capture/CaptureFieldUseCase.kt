package com.agarthavision.domain.usecase.capture

import com.agarthavision.data.inference.RemoteInferenceEngine
import com.agarthavision.data.repository.FlaggedFrameStore
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.model.FrameSource
import com.agarthavision.domain.usecase.inference.InferenceConnectionException
import java.time.Instant
import javax.inject.Inject

/**
 * Orchestrates a manual shutter tap: snapshot the cached frame, run inference once,
 * and always record the result.
 *
 * Per the confirmed manual-trigger flow (Track 2.13), a tap always produces a frame —
 * unlike the old auto-timer path, which dropped clean fields. A
 * successful call records a [FrameSource.MODEL] frame with whatever predictions came
 * back, including an empty list: the medtech tapped a specific field and expects that
 * frame recorded even when the model found nothing. An [InferenceConnectionException]
 * (the inference container is unreachable) instead records a [FrameSource.MANUAL] frame
 * with no predictions, so a lost connection never looks like a silent failure.
 *
 * Runs against the cloud container through [RemoteInferenceEngine] directly: on-device
 * inference was measured and deferred, so there is one backend and a selector would be
 * ceremony. See `docs/map/processes/infer.md`.
 *
 * Returns `Result<FrameSource>` (C4): success carries the resolved source — [FrameSource.MODEL]
 * or [FrameSource.MANUAL] — so the caller can react without re-reading the store. Only an
 * *unexpected* failure propagates as [Result.failure]: a lost connection is not one (it becomes
 * a Manual Capture), but a persistence error or a non-connectivity HTTP error (a 4xx contract
 * error, which [com.agarthavision.domain.usecase.inference.NetworkErrorMapper] re-throws
 * as-is) is.
 */
class CaptureFieldUseCase @Inject constructor(
    private val remoteEngine: RemoteInferenceEngine,
    private val flaggedFrameStore: FlaggedFrameStore,
) {
    /**
     * @param sessionId the active recording session ID.
     * @param jpegBytes the snapshot of [FrameSampler.latestFrameBytes] to analyze.
     * @return the resolved [FrameSource] on success; [Result.failure] on an unexpected error.
     */
    @Suppress("SwallowedException")
    suspend operator fun invoke(sessionId: String, jpegBytes: ByteArray): Result<FrameSource> =
        runCatching {
            val frame = try {
                val result = remoteEngine.infer(jpegBytes)
                FlaggedFrame(
                    sessionId = sessionId,
                    capturedAt = Instant.now(),
                    jpegBytes = jpegBytes,
                    predictions = result.predictions,
                    source = FrameSource.MODEL,
                    inferenceModelVersion = result.modelVersion,
                    imageWidth = result.imageWidth,
                    imageHeight = result.imageHeight,
                )
            } catch (connectionFailure: InferenceConnectionException) {
                // Expected outcome, not an error to propagate: a manual capture always
                // records a frame, so an unreachable inference container falls back to a
                // MANUAL frame instead of failing the tap. See class KDoc. The cause is
                // deliberately not rethrown or logged here — domain/ has no Android
                // logging API (C2), and RemoteInferenceEngine already surfaces the
                // underlying network failure to Retrofit's own logging interceptor.
                FlaggedFrame(
                    sessionId = sessionId,
                    capturedAt = Instant.now(),
                    jpegBytes = jpegBytes,
                    predictions = emptyList(),
                    source = FrameSource.MANUAL,
                    inferenceModelVersion = null,
                    imageWidth = null,
                    imageHeight = null,
                )
            }
            flaggedFrameStore.add(frame)
            frame.source
        }
}
