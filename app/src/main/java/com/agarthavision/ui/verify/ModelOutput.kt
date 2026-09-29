package com.agarthavision.ui.verify

import com.agarthavision.domain.inference.InferenceState
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.model.FrameSource

/**
 * What the inference container had to say about a frame — exactly three states, never collapsed.
 *
 * **The distinction between [Read] with nothing in it and [Unavailable] is load-bearing.** A
 * clean field is a real clinical result and the most common one in surveillance: the model looked
 * and asserted nothing was there. "No model output" means the server never answered. Collapsing
 * them would make a negative smear indistinguishable from a broken container, on the screen where
 * the medtech decides what to record. 86d4a6prb deliberately persists a zero-detection frame with
 * an empty predictions list precisely so the clean field is recorded at all.
 *
 * Derived from the frame on every recomposition rather than stored beside it. There is no
 * `hasModelOutput` flag anywhere, and there must not be: it would be a second source of truth for
 * a fact the frame already carries, and it would drift.
 */
internal sealed interface ModelOutput {

    /**
     * Inference has not come back yet. A spinner, nothing else.
     *
     * Produced by a frame whose [InferenceState] is still pending: capture saves the frame at
     * once and the background queue gives it a model output later (14zcqntj6ny). Such a frame
     * has no predictions yet, and reading it as [Read] with nothing in it would show a clean
     * field no model has looked at. PB-15b is the other caller: a sample synced from another
     * device, whose frame and detections are still being resolved from Storage.
     */
    data object InProgress : ModelOutput

    /**
     * The container was unreachable, so nothing was ever asked of the model.
     *
     * Not a failure and not an empty result — the field was still captured, and the medtech can
     * still verify it in full by adding the eggs they see. See [FrameSource.MANUAL].
     */
    data object Unavailable : ModelOutput

    /**
     * The model answered. [species] may be empty, which reads as "no eggs detected".
     *
     * @property species one entry per distinct class the model named, with how many boxes it
     *   drew for that class, ordered by class name so the list does not reshuffle between frames.
     * @property total every box on the frame — the sum of [species] counts, carried explicitly
     *   so the renderer never re-derives a number the medtech reads as clinical.
     */
    data class Read(
        val species: List<ModelSpeciesCount>,
        val total: Int,
    ) : ModelOutput
}

/** One class the model named on this frame, and how many boxes it drew for it. */
internal data class ModelSpeciesCount(
    val label: String,
    val count: Int,
)

/**
 * Reads a frame's model output.
 *
 * This is the model's own claim, before any human answer — deliberately not the same number as
 * what submitting would write. The two can and should differ: the gap between them is exactly
 * what the medtech is being asked to create.
 *
 * A frame awaiting inference is [ModelOutput.InProgress], read off the frame's own
 * [InferenceState] rather than passed in, so there is still one source of truth for "has a model
 * output". [isResolving] is kept for its own, different case below.
 *
 * @param isResolving true while the frame is still being fetched and its detections are not yet
 *   known. Nothing sets it today; see [ModelOutput.InProgress].
 */
internal fun FlaggedFrame.modelOutput(isResolving: Boolean = false): ModelOutput = when {
    isResolving -> ModelOutput.InProgress
    inferenceState.isPending -> ModelOutput.InProgress
    source == FrameSource.MANUAL -> ModelOutput.Unavailable
    else -> ModelOutput.Read(
        species = predictions
            .groupingBy { it.classLabel }
            .eachCount()
            .map { (label, count) -> ModelSpeciesCount(label, count) }
            .sortedBy { it.label },
        total = predictions.size,
    )
}
