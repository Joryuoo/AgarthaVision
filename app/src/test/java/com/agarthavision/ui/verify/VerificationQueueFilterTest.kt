package com.agarthavision.ui.verify

import com.agarthavision.domain.inference.Prediction
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.model.FrameSource
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class VerificationQueueFilterTest {

    @Test
    fun `filterQueueFrames respects selected filter`() {
        val modelFrame = frame(source = FrameSource.MODEL)
        val manualFrame = frame(source = FrameSource.MANUAL)
        val frames = listOf(modelFrame, manualFrame)

        assertEquals(frames, filterQueueFrames(frames, QueueFilter.ALL))
        assertEquals(listOf(modelFrame), filterQueueFrames(frames, QueueFilter.FLAGGED))
        assertEquals(listOf(manualFrame), filterQueueFrames(frames, QueueFilter.MANUAL))
    }

    @Test
    fun `an AI capture that found nothing still files under AI`() {
        // Filed on source, never on prediction count. A clean field is a recorded MODEL frame
        // with an empty prediction list, and it is a real negative result - dropping it from
        // the AI chip would hide it from the medtech who has to confirm it.
        val cleanField = frame(source = FrameSource.MODEL, predictions = emptyList())
        val withDetection = frame(
            source = FrameSource.MODEL,
            id = 2,
            predictions = listOf(Prediction("Ascaris", 0.9f, 1f, 2f, 3f, 4f)),
        )
        val frames = listOf(cleanField, withDetection)

        assertEquals(frames, filterQueueFrames(frames, QueueFilter.FLAGGED))
    }

    private fun frame(
        source: FrameSource,
        id: Int = 1,
        predictions: List<Prediction> = emptyList(),
    ): FlaggedFrame = FlaggedFrame(
        // Real ids: without them every frame compares equal and the assertions above
        // check only list length, not which frames came back.
        sampleId = "sample-$id-${source.name}",
        sessionId = "session-1",
        capturedAt = Instant.ofEpochMilli(id.toLong()),
        jpegBytes = ByteArray(4),
        predictions = predictions,
        source = source,
    )
}
