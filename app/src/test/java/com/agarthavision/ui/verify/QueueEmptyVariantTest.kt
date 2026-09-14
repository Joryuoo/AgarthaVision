package com.agarthavision.ui.verify

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the pure [queueEmptyVariant] function.
 */
class QueueEmptyVariantTest {

    @Test
    fun `returns NEVER_HAD when no frames verified and filter is off`() {
        assertEquals(
            QueueEmptyVariant.NEVER_HAD,
            queueEmptyVariant(verified = 0, filterActive = false),
        )
    }

    @Test
    fun `returns ALL_DONE when verified greater than zero and filter is off`() {
        assertEquals(
            QueueEmptyVariant.ALL_DONE,
            queueEmptyVariant(verified = 3, filterActive = false),
        )
    }

    @Test
    fun `returns FILTERED when filter is active and no frames were verified`() {
        assertEquals(
            QueueEmptyVariant.FILTERED,
            queueEmptyVariant(verified = 0, filterActive = true),
        )
    }

    @Test
    fun `filter wins over verified count — returns FILTERED even with verified frames`() {
        assertEquals(
            QueueEmptyVariant.FILTERED,
            queueEmptyVariant(verified = 3, filterActive = true),
        )
    }

    @Test
    fun `returns ALL_DONE for exactly one verified frame and filter off`() {
        assertEquals(
            QueueEmptyVariant.ALL_DONE,
            queueEmptyVariant(verified = 1, filterActive = false),
        )
    }

    @Test
    fun `large verified count with filter off still returns ALL_DONE`() {
        assertEquals(
            QueueEmptyVariant.ALL_DONE,
            queueEmptyVariant(verified = Int.MAX_VALUE, filterActive = false),
        )
    }
}
