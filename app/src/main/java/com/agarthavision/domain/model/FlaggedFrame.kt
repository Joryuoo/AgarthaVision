package com.agarthavision.domain.model

import com.agarthavision.data.remote.dto.PredictionDto
import java.time.Instant

data class FlaggedFrame(
    val sampleId: String = "",
    val sessionId: String,
    val capturedAt: Instant,
    val jpegBytes: ByteArray,
    val predictions: List<PredictionDto>,
    val source: FrameSource = FrameSource.MODEL,
    val markedAsRepeat: Boolean = false,
    val inferenceModelVersion: String? = null,
    val imageWidth: Int? = null,
    val imageHeight: Int? = null,
) {
    override fun equals(other: Any?) = other is FlaggedFrame && sampleId == other.sampleId

    override fun hashCode(): Int = sampleId.hashCode()
}
