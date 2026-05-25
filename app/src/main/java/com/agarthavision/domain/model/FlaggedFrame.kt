package com.agarthavision.domain.model

import com.agarthavision.data.remote.dto.PredictionDto
import java.time.Instant

/**
 * In-memory representation of a frame flagged by the inference engine.
 *
 * This exists between being detected (FLAGGED) and being expert-verified (VERIFIED).
 * See docs/03_MOBILE_APP_PLAN.md §1.4.
 */
data class FlaggedFrame(
    val sessionId: String,
    val capturedAt: Instant,
    val jpegBytes: ByteArray,
    val predictions: List<PredictionDto>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FlaggedFrame
        if (sessionId != other.sessionId) return false
        if (capturedAt != other.capturedAt) return false
        if (!jpegBytes.contentEquals(other.jpegBytes)) return false
        if (predictions != other.predictions) return false
        return true
    }

    override fun hashCode(): Int {
        var result = sessionId.hashCode()
        result = 31 * result + capturedAt.hashCode()
        result = 31 * result + jpegBytes.contentHashCode()
        result = 31 * result + predictions.hashCode()
        return result
    }
}
