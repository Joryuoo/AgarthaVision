package com.agarthavision.domain.model

import com.agarthavision.domain.inference.Prediction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.Instant

/**
 * The equality contract, which is load-bearing and has broken twice in opposite directions.
 *
 * `FlaggedFrame` is carried in a `StateFlow`, and `StateFlow` conflates an emission that
 * compares equal to the value it already holds. So equality decides what the verification queue
 * is allowed to notice, and both halves of it are a real bug when they go wrong:
 *
 * - Compare **too little** and a re-emission carrying a changed frame is swallowed, leaving the
 *   queue rendering stale data. That happened: equality once compared `sampleId` alone.
 * - Compare **too much** — specifically, include `jpegBytes` — and a frame stops comparing equal
 *   to itself, because the store re-reads the JPEG from disk on every emission and array
 *   equality is by reference.
 */
class FlaggedFrameTest {

    private fun frame(
        sampleId: String = "sample-1",
        predictions: List<Prediction> = emptyList(),
        source: FrameSource = FrameSource.MODEL,
        bytes: ByteArray = ByteArray(4),
    ) = FlaggedFrame(
        sampleId = sampleId,
        sessionId = "session-1",
        capturedAt = Instant.EPOCH,
        jpegBytes = bytes,
        predictions = predictions,
        source = source,
    )

    @Test
    fun `frames differing only in their bytes are equal`() {
        // The store hands back a fresh array on every emission, and a failed read hands back
        // an empty one. Including the bytes would make a frame unequal to itself across
        // emissions, and would make two unrelated broken frames equal to each other.
        val a = frame(bytes = ByteArray(4) { 1 })
        val b = frame(bytes = ByteArray(8) { 2 })

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertEquals(listOf(a), listOf(b))
    }

    @Test
    fun `frames differing in a rendered property are not equal`() {
        val plain = frame()
        val withPrediction = frame(
            predictions = listOf(Prediction("Ascaris", 0.9f, 1f, 2f, 3f, 4f)),
        )

        assertNotEquals(plain, withPrediction)
        // The list comparison is the one that actually matters: the store emits lists, and it
        // is the list that StateFlow conflates.
        assertNotEquals(listOf(plain), listOf(withPrediction))
    }

    @Test
    fun `frames differing in source are not equal`() {
        // Source decides what the verification screen renders, so a change to it has to reach
        // the UI.
        assertNotEquals(frame(source = FrameSource.MODEL), frame(source = FrameSource.MANUAL))
    }

    @Test
    fun `frames with different ids are not equal`() {
        assertNotEquals(frame(sampleId = "a"), frame(sampleId = "b"))
    }
}
