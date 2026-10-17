package com.agarthavision.domain.inference

import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Decides whether the next queued frame is worth sending to the cloud at all.
 *
 * A dead or flapping container costs every frame its full timeout before the on-device fallback
 * runs. After [failureThreshold] cloud failures in a row the breaker **opens**: frames skip the
 * cloud and go straight to the phone for a cool-down. When the cool-down has passed, the next
 * frame is a **probe**. If the probe gets through, the breaker closes. If it fails, the breaker
 * opens again for twice as long, up to [maxCooldown], so a container that stays down is asked
 * less and less often.
 *
 * A `503` from a full server queue is a failure like any other: it is the server saying "not
 * now", and the phone can answer the frame itself.
 *
 * **Not thread-safe, and it does not need to be.** The inference queue has exactly one consumer,
 * which is the only caller. Held in memory only: after a restart the cloud gets a fresh chance,
 * which is the right default for a server that may have come back while the app was closed.
 *
 * Pure Kotlin (C2). [timeSource] is injectable so tests can move time by hand.
 */
class CloudCircuitBreaker(
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
    private val failureThreshold: Int = DEFAULT_FAILURE_THRESHOLD,
    private val baseCooldown: Duration = DEFAULT_BASE_COOLDOWN,
    private val maxCooldown: Duration = DEFAULT_MAX_COOLDOWN,
) {
    /** The breaker's position, derived rather than stored so it cannot drift from the clock. */
    enum class State {
        /** Frames go to the cloud first. */
        CLOSED,

        /** The cloud is being skipped until the cool-down ends. */
        OPEN,

        /** The cool-down is over: the next frame probes the cloud. */
        HALF_OPEN,
    }

    private var consecutiveFailures = 0
    private var openUntil: ComparableTimeMark? = null

    /** How many times in a row the breaker has opened, which sets the next cool-down. */
    private var consecutiveTrips = 0

    val state: State
        get() {
            val until = openUntil ?: return State.CLOSED
            return if (until.hasPassedNow()) State.HALF_OPEN else State.OPEN
        }

    /** True when the next frame should try the cloud first. */
    fun allowsRequest(): Boolean = state != State.OPEN

    /** The cloud answered. Closes the breaker and forgets every earlier failure. */
    fun recordSuccess() {
        consecutiveFailures = 0
        consecutiveTrips = 0
        openUntil = null
    }

    /** The cloud failed, for any reason. May open the breaker. */
    fun recordFailure() {
        consecutiveFailures++
        val probeFailed = state == State.HALF_OPEN
        if (probeFailed || consecutiveFailures >= failureThreshold) {
            val cooldown = (baseCooldown * (1 shl consecutiveTrips.coerceAtMost(MAX_DOUBLINGS)))
                .coerceAtMost(maxCooldown)
            consecutiveTrips++
            openUntil = timeSource.markNow() + cooldown
        }
    }

    companion object {
        /** Three in a row rules out one dropped packet, and caps the waste at three timeouts. */
        const val DEFAULT_FAILURE_THRESHOLD = 3
        val DEFAULT_BASE_COOLDOWN = 60.seconds
        val DEFAULT_MAX_COOLDOWN = 10.minutes

        /** Keeps the shift from overflowing; the cool-down hits [DEFAULT_MAX_COOLDOWN] long before. */
        private const val MAX_DOUBLINGS = 16
    }
}
