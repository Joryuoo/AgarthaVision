package com.agarthavision.data.local.mapper

import com.agarthavision.domain.inference.Prediction

/**
 * One model prediction with its place in the frame — the shape of a `predictions` row.
 *
 * Room holds no such row: the device keeps a frame's model output as `samples.predictions_json`,
 * and a Room table would mean a version bump, which is destructive here and would wipe every
 * unsynced sample on the device. So this exists only at the sync boundary, built from the
 * column on the way out and folded back into it on the way in.
 *
 * [ordinal] is the prediction's index in that list and is the join key for everything: the
 * detection that rules on it is keyed by the same ordinal, so [detectionId] and [id] are two
 * derivations of one position.
 */
data class SamplePrediction(
    val sampleId: String,
    val ordinal: Int,
    val prediction: Prediction,
) {
    val id: String get() = predictionIdFor(sampleId, ordinal)

    /** The detection this prediction's ruling is written to, if the frame was reviewed. */
    val detectionId: String get() = detectionIdFor(sampleId, ordinal)
}

/** The frame's model output, in order, as rows. */
fun List<Prediction>.toSamplePredictions(sampleId: String): List<SamplePrediction> =
    mapIndexed { ordinal, prediction -> SamplePrediction(sampleId, ordinal, prediction) }

/**
 * The frame's model output as the list `predictions_json` holds, or null when these rows do not
 * spell a whole one.
 *
 * **Contiguous from zero or nothing.** The list index *is* the ordinal every detection id is
 * derived from, so a list with a hole in it would not merely be short — every prediction after
 * the hole would sit one index early, reopen against the wrong detection, and a re-submit would
 * write its ruling onto a neighbour's row. No list is better than that: the reopen path already
 * knows how to work without one.
 */
fun List<SamplePrediction>.toFramePredictionsOrNull(): List<Prediction>? {
    if (isEmpty()) return null
    val ordered = sortedBy { it.ordinal }
    val contiguous = ordered.withIndex().all { (index, row) -> row.ordinal == index }
    return if (contiguous) ordered.map { it.prediction } else null
}
