package com.agarthavision.domain.usecase.inference

import com.agarthavision.domain.repository.InferenceQueueRepository
import javax.inject.Inject

/**
 * Gives up on a sample's model output, for good, so the medtech can annotate it by hand.
 *
 * **Atomic and final.** One conditional write moves the sample from queued or in inference to
 * manual (`samples.is_manual = 1`, `inference_model_version = 'manual'`). A result that lands
 * afterwards finds the sample manual and is refused, so a cancelled sample never gets a model
 * output. The only way back to one is to discard the sample and capture the field again.
 *
 * Returns `Result<Boolean>` (C4): true when the sample was pending and is now manual. False when
 * there was nothing to cancel, most often because the result landed a moment before the tap.
 * The caller should then show the result rather than a manual sample.
 */
class CancelInferenceUseCase @Inject constructor(
    private val repository: InferenceQueueRepository,
) {
    suspend operator fun invoke(sampleId: String): Result<Boolean> = runCatching {
        require(sampleId.isNotBlank()) { "A sample id is required to cancel inference." }
        repository.cancel(sampleId)
    }
}
