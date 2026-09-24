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
            answers.speciesIsComplete && (answers.fieldTotal ?: 0) > 0
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
     * True when this row is one confirmed egg with a model box behind it.
     *
     * This replaced an `eggContribution: Int` that tried to answer "how many eggs is this row
     * worth?" for both kinds of row at once. It cannot be answered on one row any more: an
     * added row now carries [VerificationAnswers.fieldTotal], a total for its whole species
     * that already counts the boxes its siblings hold, so turning it into a number needs the
     * siblings. That arithmetic moved to the list-level helpers below, which is where it can
     * see them.
     */
    val countsAsEgg: Boolean
        get() = prediction != null && answers.isEgg == true
}

/**
 * Boxes the medtech kept as eggs of [species] — the part of the count the model supplied.
 *
 * Zero for a null [species], which is what a card holds before it is named and what a box holds
 * before its species question is answered. "How many boxes of no species" has no answer, and
 * counting the unnamed ones together would put a floor under a freshly added card drawn from
 * boxes that have nothing to do with it.
 */
private fun Finding.matchesStage(stage: EggStage?, otherStageText: String): Boolean = when {
    stage == null -> true
    answers.stage != stage -> false
    else -> stage != EggStage.OTHER || answers.otherStageText == otherStageText
}

fun List<Finding>.boxedCountOf(
    species: String?,
    stage: EggStage? = null,
    otherStageText: String = "",
): Int =
    if (species == null) 0 else count {
        it.countsAsEgg && it.answers.speciesLabel == species && it.matchesStage(stage, otherStageText)
    }

/**
 * What the medtech says is in this field for [species]: their own total when they gave one,
 * and otherwise the boxes they kept.
 *
 * The two are not added. A total already includes the boxes — that is what makes it a total —
 * and summing them is the double-count the old per-row contribution walked into.
 */
fun List<Finding>.fieldTotalOf(
    species: String?,
    stage: EggStage? = null,
    otherStageText: String = "",
): Int =
    firstOrNull {
        it.prediction == null && it.answers.speciesLabel == species &&
            it.matchesStage(stage, otherStageText)
    }?.answers?.fieldTotal ?: boxedCountOf(species, stage, otherStageText)

/**
 * Eggs of [species] with no box behind them.
 *
 * These are the slots the Add Species reveal list enumerates, and the detection rows a submit
 * writes for the eggs the model missed. Floored at zero: a total below the boxed count is a
 * contradiction [totalsAreConsistent] holds submit on rather than clamping away, so it does
 * reach here, and it must produce no slots instead of a negative count.
 */
fun List<Finding>.unboxedCountOf(species: String?): Int =
    (fieldTotalOf(species) - boxedCountOf(species)).coerceAtLeast(0)

/**
 * The lowest total the medtech can claim for [species] without contradicting what is already on
 * the frame: the boxes kept, plus the boxes they drew themselves on added eggs.
 *
 * The drawn half is the part that matters. Geometry a human placed by hand is the most expensive
 * data this screen produces, and a total below this floor would leave the mapper writing fewer
 * slots than there are boxes — a stray digit silently deleting work. Submit refuses instead; see
 * [totalsAreConsistent].
 */
fun List<Finding>.floorFor(species: String?): Int {
    val added = firstOrNull { it.prediction == null && it.answers.speciesLabel == species }
    return boxedCountOf(species) + (added?.answers?.drawnBoxes?.size ?: 0)
}

/**
 * True when no added row claims fewer eggs than the frame already accounts for.
 *
 * Checked at list level rather than clamped as the medtech types: a field that fights the
 * keyboard cannot be typed through — heading for 23 with a floor of 11 would snap "2" to 11
 * before the 3 arrives. The value is taken as given and submit explains why it is held.
 */
fun List<Finding>.totalsAreConsistent(): Boolean =
    none { finding ->
        finding.prediction == null &&
            (finding.answers.fieldTotal ?: 0) < floorFor(finding.answers.speciesLabel)
    }

/** Every species this frame has something to say about, in a stable order. */
fun List<Finding>.speciesPresent(): List<String> =
    mapNotNull { finding ->
        finding.answers.speciesLabel?.takeIf { finding.countsAsEgg || finding.prediction == null }
    }.distinct().sorted()

data class SpeciesStageKey(
    val species: String,
    val stage: EggStage? = null,
    val otherStageText: String = "",
)

fun List<Finding>.speciesStageKeysPresent(): List<SpeciesStageKey> =
    mapNotNull { finding ->
        val species = finding.answers.speciesLabel ?: return@mapNotNull null
        if (finding.countsAsEgg || finding.prediction == null) {
            SpeciesStageKey(species, finding.answers.stage, finding.answers.otherStageText)
        } else null
    }.distinct()

/**
 * One species, stage, and its egg count for this field — the shape that reaches
 * `sample_species_findings`.
 */
data class FindingRow(
    val species: String,
    val stage: EggStage? = null,
    val otherStageText: String = "",
    val eggCount: Int,
) {
    val stageDisplayName: String?
        get() = when (stage) {
            null -> null
            EggStage.OTHER -> otherStageText.trim().ifBlank { EggStage.OTHER.displayName }
            else -> stage.displayName
        }
}

/**
 * Collapses a frame's findings into the rows that get persisted.
 */
fun List<Finding>.toFindingRows(): List<FindingRow> =
    speciesStageKeysPresent()
        .mapNotNull { key ->
            val count = fieldTotalOf(key.species, key.stage, key.otherStageText)
            if (count > 0) FindingRow(key.species, key.stage, key.otherStageText, count) else null
        }
        .sortedWith(compareBy({ it.species }, { it.stage?.name }, { it.otherStageText }))
