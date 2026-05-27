package com.agarthavision.domain.usecase.capture

import com.agarthavision.data.remote.InferenceApi
import com.agarthavision.data.repository.FlaggedFrameStore
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.usecase.inference.InferenceConnectionException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import javax.inject.Inject

/**
 * Orchestrates a single inference call for a sampled camera frame.
 *
 * If detections are found, the frame is added to the [FlaggedFrameStore].
 * See docs/03_MOBILE_APP_PLAN.md §1.4.
 */
class InferFrameUseCase @Inject constructor(
    private val inferenceApi: InferenceApi,
    private val flaggedFrameStore: FlaggedFrameStore,
) {
    /**
     * Sends [jpegBytes] to the inference container.
     *
     * @param sessionId the active recording session ID.
     * @param jpegBytes the raw image bytes to analyze.
     * @throws InferenceConnectionException on network or server-side failure.
     */
    suspend operator fun invoke(sessionId: String, jpegBytes: ByteArray) {
        val response = runCatching {
            inferenceApi.infer(
                image = jpegBytes.toRequestBody("image/jpeg".toMediaType())
            )
        }.getOrElse {
            throw InferenceConnectionException(it)
        }

        if (!response.isSuccessful) {
            throw InferenceConnectionException(Exception("Inference failed: ${response.code()}"))
        }

        val body = response.body()
        val predictions = body?.predictions.orEmpty()
        if (predictions.isEmpty()) return

        flaggedFrameStore.add(
            FlaggedFrame(
                sessionId = sessionId,
                capturedAt = Instant.now(),
                jpegBytes = jpegBytes,
                predictions = predictions,
                inferenceModelVersion = body?.modelVersion,
                imageWidth = body?.image?.width,
                imageHeight = body?.image?.height,
            )
        )
    }
}
