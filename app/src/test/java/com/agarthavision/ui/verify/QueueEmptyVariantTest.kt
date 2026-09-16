package com.agarthavision.ui.verify

import com.agarthavision.domain.model.QueueBucket
import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the pure [queueEmptyVariant] selector. */
class QueueEmptyVariantTest {

    @Test
    fun `empty unverified bucket with nothing verified means nothing was captured`() {
        assertEquals(
            QueueEmptyVariant.NEVER_HAD,
            queueEmptyVariant(bucket = QueueBucket.UNVERIFIED, verifiedCount = 0),
        )
    }

    @Test
    fun `empty unverified bucket with verified samples means the session is done`() {
        assertEquals(
            QueueEmptyVariant.ALL_DONE,
            queueEmptyVariant(bucket = QueueBucket.UNVERIFIED, verifiedCount = 3),
        )
    }

    @Test
    fun `empty verified bucket is simply not yet, whatever the count`() {
        assertEquals(
            QueueEmptyVariant.NONE_VERIFIED,
            queueEmptyVariant(bucket = QueueBucket.VERIFIED, verifiedCount = 0),
        )
        assertEquals(
            QueueEmptyVariant.NONE_VERIFIED,
            queueEmptyVariant(bucket = QueueBucket.VERIFIED, verifiedCount = 2),
        )
    }
}
