package com.agarthavision.ui.verify

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the pure [queueEmptyVariant] selector.
 *
 * Two variants now, not three: the queue holds unverified rows only, so it can be empty for
 * exactly two reasons, and they are opposites. "Nothing captured yet" wants the medtech to go and
 * capture; "everything captured has been checked" wants to hand them the session's records. The
 * list looks the same in both cases, which is the whole reason the verified count is carried
 * alongside it.
 */
class QueueEmptyVariantTest {

    @Test
    fun `an empty queue with nothing verified means nothing was captured`() {
        assertEquals(QueueEmptyVariant.NEVER_HAD, queueEmptyVariant(verifiedInSession = 0))
    }

    @Test
    fun `an empty queue with verified samples means the session is done`() {
        assertEquals(QueueEmptyVariant.ALL_DONE, queueEmptyVariant(verifiedInSession = 1))
        assertEquals(QueueEmptyVariant.ALL_DONE, queueEmptyVariant(verifiedInSession = 12))
    }

    /**
     * The boundary, pinned on purpose.
     *
     * One verified sample is the moment the copy has to flip: the medtech has finished something,
     * so the empty queue is an achievement rather than an empty start. Off-by-one here would show
     * a first-run message to someone who has just cleared their queue.
     */
    @Test
    fun `the copy flips at the first verified sample`() {
        assertEquals(QueueEmptyVariant.NEVER_HAD, queueEmptyVariant(verifiedInSession = 0))
        assertEquals(QueueEmptyVariant.ALL_DONE, queueEmptyVariant(verifiedInSession = 1))
    }
}
