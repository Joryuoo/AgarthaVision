package com.agarthavision.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.Instant

/**
 * The bucketing rule, which replaced the four-filter version.
 *
 * Two things are being pinned. That a row lands in exactly one bucket by construction, so a chip
 * count can never disagree with the list it labels — the old shape had to route its counts
 * through the same predicate the list used, precisely because that could drift. And that source
 * files nothing, which is what makes the zero-detection case impossible to get wrong.
 */
class QueueBucketTest {

    private fun sample(
        id: String = "sample-1",
        source: FrameSource = FrameSource.MODEL,
        verified: Boolean = false,
        confirmed: Int = 0,
    ) = QueueSample(
        sampleId = id,
        sessionId = "session-1",
        capturedAt = Instant.EPOCH,
        imagePath = "/tmp/$id.jpg",
        source = source,
        isVerified = verified,
        verifiedAt = if (verified) Instant.EPOCH else null,
        confirmedDetections = confirmed,
        userNote = null,
    )

    @Test
    fun `bucketing turns on whether the sample is verified, and nothing else`() {
        assertEquals(QueueBucket.UNVERIFIED, sample(verified = false).bucket)
        assertEquals(QueueBucket.VERIFIED, sample(verified = true).bucket)
    }

    @Test
    fun `an AI capture that found nothing is still just unverified`() {
        // A clean field is a real negative result: the model looked and asserted nothing was
        // there, and a human still has to confirm that. The four-bucket version had to take
        // care to file it on source rather than on prediction count; here there is no way to
        // express the mistake, because source selects no bucket.
        val cleanField = sample(source = FrameSource.MODEL, confirmed = 0)
        assertEquals(QueueBucket.UNVERIFIED, cleanField.bucket)
    }

    @Test
    fun `source does not change the bucket`() {
        assertEquals(
            sample(source = FrameSource.MODEL).bucket,
            sample(source = FrameSource.MANUAL).bucket,
        )
        assertEquals(
            sample(source = FrameSource.MODEL, verified = true).bucket,
            sample(source = FrameSource.MANUAL, verified = true).bucket,
        )
    }

    @Test
    fun `every sample lands in exactly one bucket, so counts sum to the list`() {
        val samples = listOf(
            sample(id = "a"),
            sample(id = "b", source = FrameSource.MANUAL),
            sample(id = "c", verified = true, confirmed = 3),
            sample(id = "d", source = FrameSource.MANUAL, verified = true, confirmed = 1),
        )

        val counts = samples.groupingBy { it.bucket }.eachCount()

        assertEquals(samples.size, counts.values.sum())
        assertEquals(2, counts[QueueBucket.UNVERIFIED])
        assertEquals(2, counts[QueueBucket.VERIFIED])
    }

    @Test
    fun `a sample that became verified is not equal to its unverified self`() {
        // The queue is carried in a StateFlow, which conflates an emission equal to the value
        // it already holds. If isVerified were left out of equals, the emission where a sample
        // crosses into Verified would be swallowed and the bucket would go stale - the same bug
        // that shipped once on FlaggedFrame.
        val before = sample()
        val after = before.copy(isVerified = true)

        assertNotEquals(before, after)
        assertNotEquals(listOf(before), listOf(after))
    }
}
