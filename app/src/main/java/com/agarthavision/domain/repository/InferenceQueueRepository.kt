package com.agarthavision.domain.repository

import com.agarthavision.domain.inference.InferenceResult
import com.agarthavision.domain.inference.InferenceState

/**
 * The inference queue's storage: the sample rows themselves, keyed by `samples.inference_state`.
 *
 * There is no separate queue table. A frame is queued because its row says so, which is what
 * makes the queue survive the app being killed and the phone rebooting with no extra
 * bookkeeping, and why it can never disagree with the sample it describes.
 *
 * **Every transition is one conditional write, and that is the concurrency model.** The queue's
 * consumer, a medtech cancelling from the Verification Screen and a medtech deleting from the
 * queue can all touch the same row at the same moment. Each write names the state it expects to
 * find, so whichever lands second finds the row already moved and changes nothing. There is no
 * read-then-write anywhere that a second writer could slip between.
 *
 * Implemented over `SampleDao` in `data/repository/` (C3).
 */
interface InferenceQueueRepository {

    /**
     * Puts every [InferenceState.IN_INFERENCE] frame back to [InferenceState.QUEUED].
     *
     * Only safe when the caller *is* the single consumer and has nothing in flight: then any row
     * still marked in inference was interrupted, by a killed process or a failed write, and would
     * otherwise stay stuck forever.
     */
    suspend fun requeueInterrupted()

    /** Every [InferenceState.QUEUED] frame, oldest capture first. */
    suspend fun queuedSampleIds(): List<String>

    /**
     * Moves [sampleId] from queued to in inference.
     *
     * @return false when the frame is no longer queued — cancelled, deleted or verified since it
     *   was listed — and must be skipped.
     */
    suspend fun claim(sampleId: String): Boolean

    /** The JPEG captured for [sampleId], or null when the file is gone or unreadable. */
    suspend fun readFrame(sampleId: String): ByteArray?

    /**
     * Writes [result] onto [sampleId] and marks it [InferenceState.READY], **only if it is still
     * in inference**.
     *
     * @return false when the write was refused because the frame was cancelled or deleted while
     *   it ran. The result is then thrown away, which is the guarantee behind "a cancelled sample
     *   never gets a model output".
     */
    suspend fun complete(sampleId: String, result: InferenceResult): Boolean

    /**
     * Records one more failed attempt on an in-inference frame. Below [maxAttempts] it goes back
     * to the queue; at [maxAttempts] it becomes a manual sample.
     *
     * @return the frame's state after the write, or null when the frame was no longer in
     *   inference (cancelled or deleted while it ran) and nothing was written.
     */
    suspend fun recordFailure(sampleId: String, maxAttempts: Int): InferenceState?

    /**
     * Gives up on [sampleId]'s model output for good, if it has not arrived yet.
     *
     * @return true when the frame was pending and is now [InferenceState.MANUAL]. False when
     *   there was nothing to cancel, most often because the result landed first.
     */
    suspend fun cancel(sampleId: String): Boolean
}
