package com.agarthavision.ui.sessions

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionQueueBadgeTest {

    @Test
    fun `zero total samples yields NO_ITEMS`() {
        assertEquals(SessionQueueBadge.NO_ITEMS, sessionQueueBadge(totalSamples = 0, unverified = 0))
    }

    @Test
    fun `non-zero total with zero unverified yields ALL_VERIFIED`() {
        assertEquals(SessionQueueBadge.ALL_VERIFIED, sessionQueueBadge(totalSamples = 3, unverified = 0))
    }

    @Test
    fun `non-zero total with some unverified yields PENDING`() {
        assertEquals(SessionQueueBadge.PENDING, sessionQueueBadge(totalSamples = 3, unverified = 2))
    }

    @Test
    fun `all samples unverified yields PENDING`() {
        assertEquals(SessionQueueBadge.PENDING, sessionQueueBadge(totalSamples = 1, unverified = 1))
    }

    // totalSamples == 0 is checked first, so the impossible case (0 total, n>0 unverified)
    // falls through the same branch and returns NO_ITEMS — pinned here so the implementation
    // cannot silently change without a test failure.
    @Test
    fun `zero total with positive unverified still yields NO_ITEMS (impossible in practice)`() {
        assertEquals(SessionQueueBadge.NO_ITEMS, sessionQueueBadge(totalSamples = 0, unverified = 3))
    }
}
