package com.agarthavision.domain.usecase.capture

import com.agarthavision.data.inference.RemoteInferenceEngine
import com.agarthavision.data.repository.FlaggedFrameStore
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.model.FrameSource
import com.agarthavision.domain.usecase.inference.InferenceConnectionException
import java.time.Instant
import javax.inject.Inject

/**
 * What a single shutter tap resolved to.
 *
 * - [AI_DETECTED] — inference returned at least one prediction; an AI Capture
 *   ([FrameSource.MODEL]) was recorded for verification.
 * - [AI_EMPTY] — inference ran and found nothing. **No frame is recorded**: a clean field has
 *   nothing to verify, and recording it would leave an un-submittable row that blocks End
 *   Session. The caller surfaces a transient "no eggs" hint instead. Aggregating clean fields
 *   as a denominator (LPF density) is deferred to a separate ticket (86d4a6jxw).
 * - [MANUAL] — the inference container was unreachable; a Manual Capture ([FrameSource.MANUAL])
 *   was recorded so a lost connection never looks like a silent failure.
 */
enum class CaptureOutcome { AI_DETECTED, AI_EMPTY, MANUAL }

/**
 * Orchestrates a manual shutter tap: snapshot the cached frame, run inference once, and record
 * a frame **only when there is something to verify**.
 *
 * A detection-bearing result is an AI Capture; an unreachable container is a Manual Capture; a
 * clean result records nothing (see [CaptureOutcome.AI_EMPTY]). This keeps every recorded frame
 * verifiable, so clean fields cannot pile up and block End Session.
 *
 * Runs against the cloud container through [RemoteInferenceEngine] directly: on-device
 * inference was measured and deferred, so there is one backend and a selector would be
 * ceremony. See `docs/map/processes/infer.md`.
 *
 * Returns `Result<CaptureOutcome>` (C4): success carries the resolved outcome so the caller can
 * react without re-reading the store. Only an *unexpected* failure propagates as
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
     * @return the resolved [CaptureOutcome] on success; [Result.failure] on an unexpected error.
     */
    @Suppress("SwallowedException")
    suspend operator fun invoke(sessionId: String, jpegBytes: ByteArray): Result<CaptureOutcome> =
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
                return@runCatching CaptureOutcome.MANUAL
            }

            // A clean field records nothing — see [CaptureOutcome.AI_EMPTY].
            if (result.predictions.isEmpty()) return@runCatching CaptureOutcome.AI_EMPTY

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
            CaptureOutcome.AI_DETECTED
        }
}
