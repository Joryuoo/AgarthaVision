package com.agarthavision.domain.usecase.capture

import com.agarthavision.data.repository.FlaggedFrameStore
import com.agarthavision.domain.inference.InferenceEngine
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
 * Runs against the cloud container through the [InferenceEngine] interface: on-device
 * inference was measured and deferred, so there is one backend and a selector would be
 * ceremony. See `docs/map/processes/infer.md`.
 *
 * Returns `Result<CaptureOutcome>` (C4): success carries the persisted row's id and source, so
 * the caller can confirm the tap and offer a way into that exact sample without re-reading the
 * store. Only an *unexpected* failure propagates as [Result.failure]: a lost connection is not
 * one (it becomes a Manual Capture), but a persistence error or a non-connectivity HTTP error is.
 */
class CaptureFieldUseCase @Inject constructor(
    private val inferenceEngine: InferenceEngine,
    private val flaggedFrameStore: FlaggedFrameStore,
) {
    /**
     * @param sessionId the active recording session ID.
     * @param jpegBytes the snapshot of [FrameSampler.latestFrame] to analyze. The caller is
     *   responsible for having checked it is fresh — see `CaptureViewModel.onCapture`.
     * @return the persisted frame's [CaptureOutcome] on success; [Result.failure] on an
     *   unexpected error.
     */
    @Suppress("SwallowedException")
    suspend operator fun invoke(sessionId: String, jpegBytes: ByteArray): Result<CaptureOutcome> =
        runCatching {
            val result = try {
                inferenceEngine.infer(jpegBytes)
            } catch (connectionFailure: InferenceConnectionException) {
                // Expected outcome, not an error to propagate: an unreachable inference
                // container falls back to a MANUAL frame instead of failing the tap. The cause
                // is deliberately not rethrown or logged here — domain/ has no Android logging
                // API (C2), and RemoteInferenceEngine already surfaces the underlying network
                // failure to Retrofit's own logging interceptor.
                val sampleId = flaggedFrameStore.add(
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
                return@runCatching CaptureOutcome(sampleId, FrameSource.MANUAL)
            }

            // Every server response is recorded, zero detections included: a clean field is a
            // normal negative result, not a reason to skip persisting.
            val sampleId = flaggedFrameStore.add(
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
            CaptureOutcome(sampleId, FrameSource.MODEL)
        }
}

/**
 * What one shutter tap produced.
 *
 * [sampleId] is carried out deliberately rather than left for the caller to pick off the head of
 * `FlaggedFrameStore.state`. The head is whatever row is newest *now*, and it moves on its own:
 * verifying or deleting a sample takes a row out of the flagged set and promotes an older one.
 * A confirmation that reads the head is therefore a confirmation about an arbitrary frame, which
 * is exactly the bug this return value exists to make impossible.
 *
 * @property sampleId primary key of the row this tap wrote.
 * @property source whether the inference container answered ([FrameSource.MODEL], zero detections
 *   included) or was unreachable ([FrameSource.MANUAL]).
 */
data class CaptureOutcome(
    val sampleId: String,
    val source: FrameSource,
)
