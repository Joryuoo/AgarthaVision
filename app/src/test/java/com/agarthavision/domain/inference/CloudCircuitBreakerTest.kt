package com.agarthavision.domain.inference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class CloudCircuitBreakerTest {

    private val time = TestTimeSource()
    private val breaker = CloudCircuitBreaker(
        timeSource = time,
        failureThreshold = 3,
        baseCooldown = 60.seconds,
        maxCooldown = 10.minutes,
    )

    @Test
    fun `stays closed below the failure threshold`() {
        repeat(2) { breaker.recordFailure() }

        assertEquals(CloudCircuitBreaker.State.CLOSED, breaker.state)
        assertTrue(breaker.allowsRequest())
    }

    @Test
    fun `opens after the threshold and skips the cloud for the cool-down`() {
        repeat(3) { breaker.recordFailure() }

        assertEquals(CloudCircuitBreaker.State.OPEN, breaker.state)
        assertFalse(breaker.allowsRequest())

        time += 59.seconds
        assertFalse(breaker.allowsRequest())
    }

    @Test
    fun `lets one probe through once the cool-down has passed`() {
        repeat(3) { breaker.recordFailure() }

        time += 60.seconds

        assertEquals(CloudCircuitBreaker.State.HALF_OPEN, breaker.state)
        assertTrue(breaker.allowsRequest())
    }

    @Test
    fun `a successful probe closes the breaker and forgets the failures`() {
        repeat(3) { breaker.recordFailure() }
        time += 60.seconds

        breaker.recordSuccess()

        assertEquals(CloudCircuitBreaker.State.CLOSED, breaker.state)
        // Forgotten: two more failures do not reopen it, it takes three again.
        repeat(2) { breaker.recordFailure() }
        assertTrue(breaker.allowsRequest())
    }

    @Test
    fun `a failed probe reopens for twice as long`() {
        repeat(3) { breaker.recordFailure() }
        time += 60.seconds

        breaker.recordFailure()

        assertEquals(CloudCircuitBreaker.State.OPEN, breaker.state)
        time += 119.seconds
        assertFalse(breaker.allowsRequest())
        time += 1.seconds
        assertTrue(breaker.allowsRequest())
    }

    @Test
    fun `the cool-down stops growing at the cap`() {
        repeat(3) { breaker.recordFailure() }
        // 60 s, then 120, 240, 480, and every probe after that waits the 10 minute cap.
        repeat(6) {
            time += 10.minutes
            breaker.recordFailure()
        }

        time += 10.minutes - 1.seconds
        assertFalse(breaker.allowsRequest())
        time += 1.seconds
        assertTrue(breaker.allowsRequest())
    }

    @Test
    fun `a success between failures resets the count`() {
        repeat(2) { breaker.recordFailure() }
        breaker.recordSuccess()
        repeat(2) { breaker.recordFailure() }

        assertEquals(CloudCircuitBreaker.State.CLOSED, breaker.state)
    }
}
