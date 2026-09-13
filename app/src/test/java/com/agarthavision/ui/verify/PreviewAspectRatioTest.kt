package com.agarthavision.ui.verify

import com.agarthavision.domain.model.FlaggedFrame
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * The frame preview lays itself out at [previewAspectRatio]; a wrong value here either
 * letterboxes the field or collapses the preview to zero height.
 */
class PreviewAspectRatioTest {

    private fun frame(width: Int?, height: Int?) = FlaggedFrame(
        sampleId = "sample-1",
        sessionId = "session-1",
        capturedAt = Instant.EPOCH,
        jpegBytes = ByteArray(4),
        predictions = emptyList(),
        imageWidth = width,
        imageHeight = height,
    )

    @Test
    fun `uses the frame's own dimensions`() {
        assertEquals(1.5f, frame(960, 640).previewAspectRatio(), 0f)
    }

    @Test
    fun `falls back to square when dimensions are missing`() {
        assertEquals(1f, frame(null, null).previewAspectRatio(), 0f)
        assertEquals(1f, frame(640, null).previewAspectRatio(), 0f)
    }

    @Test
    fun `falls back to square rather than dividing by zero`() {
        assertEquals(1f, frame(640, 0).previewAspectRatio(), 0f)
        assertEquals(1f, frame(0, 640).previewAspectRatio(), 0f)
    }
}
