package com.agarthavision.domain.usecase.capture

import com.agarthavision.data.inference.RemoteInferenceEngine
import com.agarthavision.data.repository.FlaggedFrameStore
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.usecase.inference.InferenceConnectionException
import java.time.Instant
import javax.inject.Inject

/**
 * Orchestrates a single inference call for a sampled camera frame.
 *
 * Runs against the cloud container through [RemoteInferenceEngine]. The engine is reached
 * directly rather than through a selector: on-device inference was measured and deferred, so
 * there is exactly one backend to choose from and a chooser would be ceremony.
 *
 * If detections are found, the frame is added to the [FlaggedFrameStore].
 * See `docs/map/processes/infer.md`.
 */
class InferFrameUseCase @Inject constructor(
    private val remoteEngine: RemoteInferenceEngine,
    private val flaggedFrameStore: FlaggedFrameStore,
) {
    /**
     * Runs inference over [jpegBytes] and flags the frame when anything is detected.
     *
     * @param sessionId the active recording session ID.
     * @param jpegBytes the raw image bytes to analyze.
     * @throws InferenceConnectionException on network or server-side failure.
     */
    suspend operator fun invoke(sessionId: String, jpegBytes: ByteArray) {
        val result = remoteEngine.infer(jpegBytes)

        // Most frames end here: an empty result is not a failure, it is a clean field.
        if (result.predictions.isEmpty()) return

        flaggedFrameStore.add(
            FlaggedFrame(
                sessionId = sessionId,
                capturedAt = Instant.now(),
                jpegBytes = jpegBytes,
                predictions = result.predictions,
                inferenceModelVersion = result.modelVersion,
                imageWidth = result.imageWidth,
                imageHeight = result.imageHeight,
            )
        )
    }
}
