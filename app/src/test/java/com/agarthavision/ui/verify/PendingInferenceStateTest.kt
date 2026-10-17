package com.agarthavision.ui.verify

import com.agarthavision.domain.inference.InferenceState
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.model.FrameSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * A sample awaiting inference is never treated as a clean field (14zcqntj6ny).
 *
 * Capture saves the frame before any model has seen it, so a queued frame has zero predictions,
 * exactly like a clean field. These pin the two derived facts the Verification Screen reads off
 * that: whether the frame is a clean field, and whether the missed-egg question applies at all.
 */
class PendingInferenceStateTest {

    private fun frame(state: InferenceState) = FlaggedFrame(
        sampleId = "sample-1",
        sessionId = "session-1",
        capturedAt = Instant.EPOCH,
        jpegBytes = ByteArray(0),
        predictions = emptyList(),
        source = FrameSource.MODEL,
        inferenceState = state,
    )

    @Test
    fun `a queued or running frame is not a clean field`() {
        listOf(InferenceState.QUEUED, InferenceState.IN_INFERENCE).forEach { state ->
            assertFalse(VerificationUiState(frame = frame(state)).isCleanField)
        }
    }

    @Test
    fun `a ready frame with no detections still is one`() {
        assertTrue(VerificationUiState(frame = frame(InferenceState.READY)).isCleanField)
    }

    @Test
    fun `the missed-egg question does not apply until the model has answered`() {
        assertNull(VerificationUiState(frame = frame(InferenceState.QUEUED)).missedEgg)
    }
}
