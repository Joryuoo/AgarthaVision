package com.agarthavision.domain.model

import com.agarthavision.domain.inference.Prediction
import java.time.Instant

data class FlaggedFrame(
    val sampleId: String = "",
    val sessionId: String,
    val capturedAt: Instant,
    val jpegBytes: ByteArray,
    val predictions: List<Prediction>,
    val source: FrameSource = FrameSource.MODEL,
    val inferenceModelVersion: String? = null,
    val imageWidth: Int? = null,
    val imageHeight: Int? = null,
) {
    /**
     * Compares every property **except [jpegBytes]** — do not replace this with the
     * generated `data class` implementation.
     *
     * Two frames are the same frame when they describe the same row. `sampleId` is a
     * unique primary key, so leaving the bytes out cannot merge distinct frames.
     *
     * Including them would break the app in two ways:
     * - `FlaggedFrameStore.toFlaggedFrame()` re-reads the JPEG from disk on every Room
     *   emission, so the array is a fresh instance each time. Array equality is by
     *   reference, so a frame would compare unequal to itself across emissions and the
     *   verification queue would lose its place.
     * - A failed read yields `ByteArray(0)`, so two unrelated broken frames would then
     *   compare equal.
     *
     * **Every other property is compared, deliberately — including the mutable ones.** This
     * once compared `sampleId` alone, and a Room re-emission that had only flipped a flag
     * therefore compared equal to the list already held; `StateFlow` conflated it away and the
     * queue silently went stale. Anything the queue renders from has to be in here.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FlaggedFrame) return false
        return sampleId == other.sampleId &&
            sessionId == other.sessionId &&
            capturedAt == other.capturedAt &&
            predictions == other.predictions &&
            source == other.source &&
            inferenceModelVersion == other.inferenceModelVersion &&
            imageWidth == other.imageWidth &&
            imageHeight == other.imageHeight
    }

    override fun hashCode(): Int {
        var result = sampleId.hashCode()
        result = 31 * result + sessionId.hashCode()
        result = 31 * result + capturedAt.hashCode()
        result = 31 * result + predictions.hashCode()
        result = 31 * result + source.hashCode()
        result = 31 * result + (inferenceModelVersion?.hashCode() ?: 0)
        result = 31 * result + (imageWidth ?: 0)
        result = 31 * result + (imageHeight ?: 0)
        return result
    }
}
