package com.agarthavision.ui.verify

import com.agarthavision.domain.model.FrameSource
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.ui.capture.QueueFilter
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class VerificationQueueFilterTest {
    @Test
    fun `filterQueueFrames respects selected filter`() {
        val modelFrame = frame(source = FrameSource.MODEL)
        val manualFrame = frame(source = FrameSource.MANUAL)
        val repeatFrame = frame(source = FrameSource.MODEL, repeat = true, id = 2)
        val frames = listOf(modelFrame, manualFrame, repeatFrame)

        assertEquals(frames, filterQueueFrames(frames, QueueFilter.ALL))
        assertEquals(listOf(modelFrame, repeatFrame), filterQueueFrames(frames, QueueFilter.FLAGGED))
        assertEquals(listOf(manualFrame), filterQueueFrames(frames, QueueFilter.MANUAL))
        assertEquals(listOf(repeatFrame), filterQueueFrames(frames, QueueFilter.REPEAT))
    }

    private fun frame(
        source: FrameSource,
        repeat: Boolean = false,
        id: Int = 1,
    ): FlaggedFrame = FlaggedFrame(
        sessionId = "session-1",
        capturedAt = Instant.ofEpochMilli(id.toLong()),
        jpegBytes = ByteArray(4),
        predictions = emptyList(),
        source = source,
        markedAsRepeat = repeat,
    )
}
