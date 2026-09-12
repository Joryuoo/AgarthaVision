package com.agarthavision.domain.usecase.verify

import com.agarthavision.domain.inference.Prediction
import com.agarthavision.domain.model.EggStage

/**
 * One thing the medtech is asserting about the frame in front of them.
 *
 * [prediction] is null when there is no model output behind the row — a manual capture, or a
 * species the medtech added to an AI frame because the model never boxed it. Those two are the
 * same fact, which is why they are the same type: a human assertion with no box. The isEgg and
 * isBoxCorrect questions are questions *about a box*, so they are not rendered and are never
 * answered on the medtech's behalf when [prediction] is null.
 *
 * **Invariant.** Within one frame the first `frame.predictions.size` findings are the
 * prediction-backed ones, in prediction order; added rows only ever append. Removal is refused
 * on a prediction-backed row — you cannot delete a box the model produced, you mark it
 * `FALSE_POSITIVE` (constraint C8). The list index is therefore the prediction ordinal, which
 * the deterministic detection id derivation in `VerificationMapper` depends on.
 *
 * A frame carrying an empty list is a **clean field**: an AI capture where the model asserted
 * nothing was there and the medtech agreed. That is valid and submittable.
 */
data class Finding(
    val prediction: Prediction? = null,
    val answers: VerificationAnswers = VerificationAnswers(),
) {
    /** True when this row has everything it needs to be persisted. */
    val isComplete: Boolean
        get() = if (prediction == null) {
            answers.speciesIsComplete && (answers.eggCount ?: 0) > 0
        } else {
            when {
                answers.isEgg == null -> false
                !answers.isEgg -> true
                answers.isBoxCorrect == null -> false
                // A misplaced box still contains an egg, and an egg still has to be counted,
                // so the species question is asked either way. Short-circuiting here — which
                // is what this did before counting existed — silently dropped a real egg from
                // the low-power-field count. The verdict still records that the box was wrong;
                // see computeVerdict.
                else -> answers.speciesIsComplete
            }
        }

    /**
     * Eggs this finding contributes to its (species, stage) count.
     *
     * A prediction-backed row is worth exactly one egg and the medtech cannot edit that — the
     * number of boxes they said "yes" to *is* the count. Only an added row carries a typed
     * number.
     */
    val eggContribution: Int
        get() = when {
            prediction == null -> answers.eggCount ?: 0
            answers.isEgg == true -> 1
            else -> 0
        }
}

/**
 * One `(species, stage)` pair and its egg count for this field — the shape that reaches
 * `sample_species_findings`.
 */
data class FindingRow(
    val species: String,
    val stage: EggStage?,
    val eggCount: Int,
)

/**
 * Collapses a frame's findings into the rows that get persisted.
 *
 * Several findings can land on the same `(species, stage)` — five confirmed Ascaris boxes plus
 * a typed count for Ascaris the model missed — and they add up into one row, because the table
 * holds one row per pair per frame, not one per assertion.
 *
 * Rows contributing zero eggs drop out, which is how a rejected box (`isEgg = false`) leaves no
 * trace in the count while still persisting as a labelled `FALSE_POSITIVE` detection.
 */
fun List<Finding>.toFindingRows(): List<FindingRow> =
    filter { it.eggContribution > 0 }
        .mapNotNull { finding ->
            finding.answers.speciesLabel?.let { label ->
                Triple(label, finding.answers.stage, finding.eggContribution)
            }
        }
        .groupBy { (label, stage, _) -> label to stage }
        .map { (key, group) -> FindingRow(key.first, key.second, group.sumOf { it.third }) }
        .sortedWith(compareBy({ it.species }, { it.stage?.value ?: "" }))
