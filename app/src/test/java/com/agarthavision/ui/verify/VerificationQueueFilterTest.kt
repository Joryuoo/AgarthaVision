package com.agarthavision.ui.verify

import com.agarthavision.domain.model.FrameSource
import com.agarthavision.domain.model.FlaggedFrame
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class VerificationQueueFilterTest {
    @Test
    fun `repeat AI frames are excluded from the AI filter but not ALL or REPEAT`() {
        // A repeat is a duplicate the medtech already accounted for. Leaving it under the
        // AI chip left a route to verifying it by accident.
        val plainAi = frame(source = FrameSource.MODEL, id = 1)
        val repeatAi = frame(source = FrameSource.MODEL, repeat = true, id = 2)
        val frames = listOf(plainAi, repeatAi)

        assertEquals(frames, filterQueueFrames(frames, QueueFilter.ALL))
        assertEquals(listOf(plainAi), filterQueueFrames(frames, QueueFilter.FLAGGED))
        assertEquals(listOf(repeatAi), filterQueueFrames(frames, QueueFilter.REPEAT))
    }

    @Test
    fun `filterQueueFrames respects selected filter`() {
        val modelFrame = frame(source = FrameSource.MODEL)
        val manualFrame = frame(source = FrameSource.MANUAL)
        val repeatFrame = frame(source = FrameSource.MODEL, repeat = true, id = 2)
        val frames = listOf(modelFrame, manualFrame, repeatFrame)

        assertEquals(frames, filterQueueFrames(frames, QueueFilter.ALL))
        // repeatFrame is MODEL but marked repeat, so FLAGGED no longer includes it.
        assertEquals(listOf(modelFrame), filterQueueFrames(frames, QueueFilter.FLAGGED))
        assertEquals(listOf(manualFrame), filterQueueFrames(frames, QueueFilter.MANUAL))
        assertEquals(listOf(repeatFrame), filterQueueFrames(frames, QueueFilter.REPEAT))
    }

    private fun frame(
        source: FrameSource,
        repeat: Boolean = false,
        id: Int = 1,
    ): FlaggedFrame = FlaggedFrame(
        // Real ids: without them every frame compares equal and the assertions below
        // check only list length, not which frames came back.
        sampleId = "sample-$id-${source.name}-$repeat",
        sessionId = "session-1",
        capturedAt = Instant.ofEpochMilli(id.toLong()),
        jpegBytes = ByteArray(4),
        predictions = emptyList(),
        source = source,
        markedAsRepeat = repeat,
    )
}
