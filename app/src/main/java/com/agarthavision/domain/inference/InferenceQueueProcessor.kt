package com.agarthavision.domain.inference

import com.agarthavision.domain.repository.InferenceQueueRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The inference queue's single consumer: one frame at a time, oldest first, cloud first with
 * the on-device model as the fallback.
 *
 * Capture saves a sample as [InferenceState.QUEUED] and returns at once. This is what gives
 * that sample its model output afterwards. For each frame:
 * 1. **Claim** it, moving it to [InferenceState.IN_INFERENCE]. A frame cancelled or deleted
 *    since it was listed fails the claim and is skipped.
 * 2. **Cloud**, unless the [circuitBreaker] is open. Any failure at all, a `503` included, is
 *    recorded against the breaker, and the same frame moves on to step 3.
 * 3. **On-device**, when the cloud was skipped or failed.
 * 4. **Write** the result, only if the frame is still in inference. A frame cancelled while it
 *    ran refuses the write, so the result is discarded and the sample stays manual.
 *
 * **When both engines fail** the frame goes back to the queue and waits before it is tried
 * again, [firstRetryDelay] and then twice as long each time, up to [maxRetryDelay]. At
 * [maxAttempts] failed attempts it becomes a manual sample, so a frame that can never be read
 * does not sit in the queue forever. The count lives on the row, so it survives a restart.
 * Other frames are not held up: a frame waiting out its retry delay is skipped until it is due.
 *
 * **Exactly one consumer, and everything here relies on it.** `requeueInterrupted` at the start
 * of every pass is only safe because nothing else is in flight. `WorkManagerInferenceQueue`
 * chains passes so they run one after another, and [drain] also holds a lock, so two passes can
 * never overlap even if two workers were started at once. The instance is a singleton, so
 * [circuitBreaker] and the retry schedule carry over from one pass to the next.
 *
 * Pure Kotlin (C2). The engines are the two [InferenceEngine]s; which one is which is decided
 * by the caller, in `InferenceModule`.
 */
@Suppress("LongParameterList")
class InferenceQueueProcessor(
    private val repository: InferenceQueueRepository,
    private val cloudEngine: InferenceEngine,
    private val deviceEngine: InferenceEngine,
    private val circuitBreaker: CloudCircuitBreaker = CloudCircuitBreaker(),
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    private val firstRetryDelay: Duration = DEFAULT_FIRST_RETRY_DELAY,
    private val maxRetryDelay: Duration = DEFAULT_MAX_RETRY_DELAY,
) {

    /** A frame that failed on both engines, and when it may be tried again. In memory only. */
    private data class Retry(val failures: Int, val notBefore: ComparableTimeMark)

    private val retries = mutableMapOf<String, Retry>()

    /** Held for a whole pass, and by anything reading [retries]. */
    private val passLock = Mutex()

    /**
     * One pass: every queued frame that is due, oldest first, one at a time.
     *
     * Re-reads the queue after every frame, so a frame captured mid-pass is picked up in the same
     * pass once the older ones are done.
     *
     * Throws only when the queue itself cannot be read or reset, on a database error say. A
     * single frame that fails is handled here and never ends the pass.
     */
    suspend fun drain(): DrainSummary = passLock.withLock { drainLocked() }

    private suspend fun drainLocked(): DrainSummary {
        repository.requeueInterrupted()

        var summary = DrainSummary()
        val triedThisPass = mutableSetOf<String>()
        while (true) {
            val next = repository.queuedSampleIds()
                .firstOrNull { it !in triedThisPass && isDue(it) }
                ?: break
            triedThisPass += next

            val outcome = attempt { process(next) }.getOrElse {
                // The frame's row could not be read or written. The next pass starts by putting
                // it back in the queue, so it only has to wait its turn.
                scheduleRetry(next)
                FrameOutcome.RETRYING
            }
            summary = summary.plus(outcome)
        }

        // Frames cancelled, deleted or finished elsewhere need no retry schedule any more.
        retries.keys.retainAll(repository.queuedSampleIds().toSet())
        return summary
    }

    /**
     * How long until the earliest failed frame is due to be tried again, or null when no frame is
     * waiting on a retry. Zero or negative when one is already due. The caller schedules the next
     * pass by it.
     */
    suspend fun nextRetryIn(): Duration? = passLock.withLock {
        retries.values.minOfOrNull { -it.notBefore.elapsedNow() }
    }

    private suspend fun process(sampleId: String): FrameOutcome {
        if (!repository.claim(sampleId)) return FrameOutcome.SKIPPED

        val result = attempt { repository.readFrame(sampleId) }.getOrNull()?.let { infer(it) }
        return if (result != null) complete(sampleId, result) else fail(sampleId)
    }

    private suspend fun complete(sampleId: String, result: InferenceResult): FrameOutcome {
        retries.remove(sampleId)
        return if (repository.complete(sampleId, result)) FrameOutcome.COMPLETED else FrameOutcome.DISCARDED
    }

    private suspend fun fail(sampleId: String): FrameOutcome =
        when (repository.recordFailure(sampleId, maxAttempts)) {
            InferenceState.QUEUED -> {
                scheduleRetry(sampleId)
                FrameOutcome.RETRYING
            }
            InferenceState.MANUAL -> {
                retries.remove(sampleId)
                FrameOutcome.GAVE_UP
            }
            // Cancelled or deleted while it ran: nothing was written, nothing to retry.
            else -> {
                retries.remove(sampleId)
                FrameOutcome.DISCARDED
            }
        }

    /** Cloud first unless the breaker says otherwise, then this phone. Null when both fail. */
    private suspend fun infer(jpegBytes: ByteArray): InferenceResult? {
        if (circuitBreaker.allowsRequest()) {
            attempt { cloudEngine.infer(jpegBytes) }
                .onSuccess { result ->
                    circuitBreaker.recordSuccess()
                    return result
                }
                .onFailure { circuitBreaker.recordFailure() }
        }
        return attempt { deviceEngine.infer(jpegBytes) }.getOrNull()
    }

    private fun isDue(sampleId: String): Boolean =
        retries[sampleId]?.notBefore?.hasPassedNow() ?: true

    private fun scheduleRetry(sampleId: String) {
        val failures = (retries[sampleId]?.failures ?: 0) + 1
        val delay = (firstRetryDelay * (1 shl (failures - 1).coerceAtMost(MAX_DOUBLINGS)))
            .coerceAtMost(maxRetryDelay)
        retries[sampleId] = Retry(failures, timeSource.markNow() + delay)
    }

    /**
     * [runCatching] that lets cancellation through. Swallowing a [CancellationException] would
     * leave a cancelled queue running.
     */
    private inline fun <T> attempt(block: () -> T): Result<T> =
        runCatching(block).onFailure { if (it is CancellationException) throw it }

    /** What happened to one frame. */
    enum class FrameOutcome {
        /** A model answered and the result was written. */
        COMPLETED,

        /** The frame was cancelled or deleted while it ran; any result was thrown away. */
        DISCARDED,

        /** Both engines failed; the frame is back in the queue, waiting out its retry delay. */
        RETRYING,

        /** Both engines failed for the last allowed time; the frame is now manual. */
        GAVE_UP,

        /** The frame was no longer queued when its turn came. */
        SKIPPED,
    }

    /** How many frames a pass left in each outcome. */
    data class DrainSummary(
        val completed: Int = 0,
        val discarded: Int = 0,
        val retrying: Int = 0,
        val gaveUp: Int = 0,
        val skipped: Int = 0,
    ) {
        fun plus(outcome: FrameOutcome): DrainSummary = when (outcome) {
            FrameOutcome.COMPLETED -> copy(completed = completed + 1)
            FrameOutcome.DISCARDED -> copy(discarded = discarded + 1)
            FrameOutcome.RETRYING -> copy(retrying = retrying + 1)
            FrameOutcome.GAVE_UP -> copy(gaveUp = gaveUp + 1)
            FrameOutcome.SKIPPED -> copy(skipped = skipped + 1)
        }
    }

    companion object {
        /**
         * Both engines failing on five separate occasions, spread over about eight minutes,
         * means the frame is not going to get a model output. The medtech annotates it by hand.
         */
        const val DEFAULT_MAX_ATTEMPTS = 5
        val DEFAULT_FIRST_RETRY_DELAY = 30.seconds
        val DEFAULT_MAX_RETRY_DELAY = 5.minutes

        /** Keeps the shift from overflowing; the delay hits its cap long before. */
        private const val MAX_DOUBLINGS = 16
    }
}
