package com.agarthavision.domain.usecase.capture

import com.agarthavision.data.repository.FlaggedFrameStore
import com.agarthavision.domain.inference.InferenceQueue
import com.agarthavision.domain.inference.InferenceState
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.model.FrameSource
import java.time.Instant
import javax.inject.Inject

/**
 * Records a shutter tap: saves the frame at once, queued for a model output that arrives later.
 *
 * **Capture waits on nothing.** It used to run inference first and only then write the row, so
 * every tap waited on the network, and an unreachable container turned the frame into a Manual
 * Capture on the spot, with no way to get a model output later. Now the row is written
 * immediately as [InferenceState.QUEUED], and the background queue (`InferenceQueueProcessor`)
 * gives it a model output afterwards: the cloud first, the on-device model when the cloud cannot
 * be reached.
 *
 * The frame is saved as [FrameSource.MODEL] with no predictions yet. That combination alone used
 * to mean a clean field, so every reader now checks the inference state first: until it is
 * [InferenceState.READY], the empty prediction list is not a result.
 *
 * Returns `Result<CaptureOutcome>` (C4): success carries the saved row's id, so the caller can
 * confirm the tap and offer a way into that exact sample without re-reading the store. Only a
 * failure to save the frame propagates as [Result.failure].
 */
class CaptureFieldUseCase @Inject constructor(
    private val flaggedFrameStore: FlaggedFrameStore,
    private val inferenceQueue: InferenceQueue,
) {
    /**
     * @param sessionId the active recording session ID.
     * @param jpegBytes the snapshot of [FrameSampler.latestFrame] to analyze. The caller is
     *   responsible for having checked it is fresh — see `CaptureViewModel.onCapture`.
     * @return the saved frame's [CaptureOutcome] on success; [Result.failure] when the frame
     *   could not be saved.
     */
    suspend operator fun invoke(sessionId: String, jpegBytes: ByteArray): Result<CaptureOutcome> =
        runCatching {
            val sampleId = flaggedFrameStore.add(
                FlaggedFrame(
                    sessionId = sessionId,
                    capturedAt = Instant.now(),
                    jpegBytes = jpegBytes,
                    predictions = emptyList(),
                    source = FrameSource.MODEL,
                    inferenceModelVersion = null,
                    imageWidth = null,
                    imageHeight = null,
                    inferenceState = InferenceState.QUEUED,
                ),
            )
            // After the write, never before: the queue reads the row, so waking it first could
            // find nothing and go back to sleep with this frame unseen.
            inferenceQueue.notifyQueued()
            CaptureOutcome(sampleId)
        }
}

/**
 * What one shutter tap produced.
 *
 * [sampleId] is carried out deliberately rather than left for the caller to pick off the head of
 * `FlaggedFrameStore.state`. The head is whatever row is newest *now*, and it moves on its own:
 * verifying or deleting a sample takes a row out of the flagged set and promotes an older one.
 * A confirmation that reads the head is therefore a confirmation about an arbitrary frame, which
 * is exactly the bug this return value exists to make impossible.
 *
 * There is no source on it any more. Every tap is queued for a model output, and whether one
 * arrives is decided later by the inference queue, not by the tap (14zcqntj6nz).
 *
 * @property sampleId primary key of the row this tap wrote.
 */
data class CaptureOutcome(
    val sampleId: String,
)
