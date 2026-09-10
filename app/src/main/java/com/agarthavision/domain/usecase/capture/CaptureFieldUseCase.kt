package com.agarthavision.domain.usecase.capture

import com.agarthavision.data.inference.RemoteInferenceEngine
import com.agarthavision.data.repository.FlaggedFrameStore
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.model.FrameSource
import com.agarthavision.domain.usecase.inference.InferenceConnectionException
import java.time.Instant
import javax.inject.Inject

/**
 * Orchestrates a manual shutter tap: snapshot the cached frame, run inference once, and record
 * a frame for whatever the tap resolved to.
 *
 * Capture source is decided by whether the inference server was *consulted*, not by what it
 * found. Any server response — including a zero-detection result — is an AI Capture
 * ([FrameSource.MODEL]): a clean field is a normal negative result and must be recorded, not
 * discarded. Only an unreachable container falls back to a Manual Capture ([FrameSource.MANUAL]),
 * so a lost connection never looks like a silent failure.
 *
 * Runs against the cloud container through [RemoteInferenceEngine] directly: on-device
 * inference was measured and deferred, so there is one backend and a selector would be
 * ceremony. See `docs/map/processes/infer.md`.
 *
 * Returns `Result<FrameSource>` (C4): success carries the persisted frame's source so the caller
 * can react without re-reading the store. Only an *unexpected* failure propagates as
 * [Result.failure]: a lost connection is not one (it becomes a Manual Capture), but a
 * persistence error or a non-connectivity HTTP error is.
 */
class CaptureFieldUseCase @Inject constructor(
    private val remoteEngine: RemoteInferenceEngine,
    private val flaggedFrameStore: FlaggedFrameStore,
) {
    /**
     * @param sessionId the active recording session ID.
     * @param jpegBytes the snapshot of [FrameSampler.latestFrameBytes] to analyze.
     * @return the persisted frame's [FrameSource] on success; [Result.failure] on an unexpected
     *   error.
     */
    @Suppress("SwallowedException")
    suspend operator fun invoke(sessionId: String, jpegBytes: ByteArray): Result<FrameSource> =
        runCatching {
            val result = try {
                remoteEngine.infer(jpegBytes)
            } catch (connectionFailure: InferenceConnectionException) {
                // Expected outcome, not an error to propagate: an unreachable inference
                // container falls back to a MANUAL frame instead of failing the tap. The cause
                // is deliberately not rethrown or logged here — domain/ has no Android logging
                // API (C2), and RemoteInferenceEngine already surfaces the underlying network
                // failure to Retrofit's own logging interceptor.
                flaggedFrameStore.add(
                    FlaggedFrame(
                        sessionId = sessionId,
                        capturedAt = Instant.now(),
                        jpegBytes = jpegBytes,
                        predictions = emptyList(),
                        source = FrameSource.MANUAL,
                        inferenceModelVersion = null,
                        imageWidth = null,
                        imageHeight = null,
                    ),
                )
                return@runCatching FrameSource.MANUAL
            }

            // Every server response is recorded, zero detections included: a clean field is a
            // normal negative result, not a reason to skip persisting.
            flaggedFrameStore.add(
                FlaggedFrame(
                    sessionId = sessionId,
                    capturedAt = Instant.now(),
                    jpegBytes = jpegBytes,
                    predictions = result.predictions,
                    source = FrameSource.MODEL,
                    inferenceModelVersion = result.modelVersion,
                    imageWidth = result.imageWidth,
                    imageHeight = result.imageHeight,
                ),
            )
            FrameSource.MODEL
        }
}
