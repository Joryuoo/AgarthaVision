package com.agarthavision.domain.model

import com.agarthavision.data.remote.dto.PredictionDto
import java.time.Instant

data class FlaggedFrame(
    val sessionId: String,
    val capturedAt: Instant,
    val jpegBytes: ByteArray,
    val predictions: List<PredictionDto>,
    val inferenceModelVersion: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FlaggedFrame
        if (sessionId != other.sessionId) return false
        if (capturedAt != other.capturedAt) return false
        if (!jpegBytes.contentEquals(other.jpegBytes)) return false
        if (predictions != other.predictions) return false
        if (inferenceModelVersion != other.inferenceModelVersion) return false
        return true
    }

    override fun hashCode(): Int {
        var result = sessionId.hashCode()
        result = 31 * result + capturedAt.hashCode()
        result = 31 * result + jpegBytes.contentHashCode()
        result = 31 * result + predictions.hashCode()
        result = 31 * result + (inferenceModelVersion?.hashCode() ?: 0)
        return result
    }
}
