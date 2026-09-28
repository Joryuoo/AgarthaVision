package com.agarthavision.ui.verify

import com.agarthavision.domain.inference.Prediction
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.model.FrameSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.Instant

/**
 * The three model-output states, pinned as a pure derivation.
 *
 * The one that matters is the boundary between a clean field and an unreachable container.
 * Both leave the medtech with no boxes to answer questions about, and they mean opposite
 * things: a clean field is a real clinical result and the most common one in surveillance,
 * while no model output means the server never answered. A change that collapsed them would
 * make a negative smear indistinguishable from a broken container, and nothing would throw.
 */
class ModelOutputTest {

    private fun frame(
        source: FrameSource = FrameSource.MODEL,
        classes: List<String> = emptyList(),
    ) = FlaggedFrame(
        sampleId = "sample-1",
        sessionId = "session-1",
        capturedAt = Instant.EPOCH,
        jpegBytes = ByteArray(0),
        predictions = classes.map { Prediction(it, 0.9f, 10f, 10f, 5f, 5f) },
        source = source,
    )

    @Test
    fun `a model frame with no detections is a read, not an absence`() {
        val output = frame(source = FrameSource.MODEL, classes = emptyList()).modelOutput()

        assertEquals(ModelOutput.Read(species = emptyList(), total = 0), output)
    }

    @Test
    fun `a manual frame is an unreachable container, not an empty read`() {
        val output = frame(source = FrameSource.MANUAL).modelOutput()

        assertEquals(ModelOutput.Unavailable, output)
    }

    /** Restated as an inequality, because collapsing the two is the failure mode. */
    @Test
    fun `a clean field and an unreachable container are never the same state`() {
        val cleanField = frame(source = FrameSource.MODEL, classes = emptyList()).modelOutput()
        val unreachable = frame(source = FrameSource.MANUAL).modelOutput()

        assertNotEquals(cleanField, unreachable)
    }

    @Test
    fun `boxes are grouped per class and counted`() {
        val output = frame(classes = listOf("Trichuris", "Ascaris", "Ascaris")).modelOutput()

        assertEquals(
            ModelOutput.Read(
                // Ordered by class name so the list does not reshuffle between frames.
                species = listOf(
                    ModelSpeciesCount("Ascaris", 2),
                    ModelSpeciesCount("Trichuris", 1),
                ),
                total = 3,
            ),
            output,
        )
    }

    /**
     * The total is every box, not every class — the number the medtech reads as the model's
     * claim about this field.
     */
    @Test
    fun `the total counts boxes, not species`() {
        val output = frame(classes = List(5) { "Ascaris" }).modelOutput()

        assertEquals(1, (output as ModelOutput.Read).species.size)
        assertEquals(5, output.total)
    }

    @Test
    fun `a frame still resolving is in progress, whatever it currently holds`() {
        assertEquals(
            ModelOutput.InProgress,
            frame(classes = listOf("Ascaris")).modelOutput(isResolving = true),
        )
        assertEquals(
            ModelOutput.InProgress,
            frame(source = FrameSource.MANUAL).modelOutput(isResolving = true),
        )
    }
}
