@file:Suppress("TooManyFunctions")

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
private fun Finding.matchesStage(stage: EggStage?, otherStageText: String): Boolean =
    answers.stage == stage &&
        (stage != EggStage.OTHER || answers.otherStageText.trim() == otherStageText.trim())

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
fun List<Finding>.unboxedCountOf(
    species: String?,
    stage: EggStage? = null,
    otherStageText: String = "",
): Int =
    (fieldTotalOf(species, stage, otherStageText) - boxedCountOf(species, stage, otherStageText))
        .coerceAtLeast(0)

/**
 * The lowest total the medtech can claim for [species] without contradicting what is already on
 * the frame: the boxes kept, plus the boxes they drew themselves on added eggs.
 *
 * The drawn half is the part that matters. Geometry a human placed by hand is the most expensive
 * data this screen produces, and a total below this floor would leave the mapper writing fewer
 * slots than there are boxes — a stray digit silently deleting work. Submit refuses instead; see
 * [totalsAreConsistent].
 */
fun List<Finding>.floorFor(
    species: String?,
    stage: EggStage? = null,
    otherStageText: String = "",
): Int {
    val added = firstOrNull {
        it.prediction == null && it.answers.speciesLabel == species && it.matchesStage(stage, otherStageText)
    }
    return boxedCountOf(species, stage, otherStageText) + (added?.answers?.drawnBoxes?.size ?: 0)
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
            (finding.answers.fieldTotal ?: 0) <
            floorFor(finding.answers.speciesLabel, finding.answers.stage, finding.answers.otherStageText)
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

/** Identity an added card is persisted under. Null until a species label exists. */
val VerificationAnswers.speciesStageKey: SpeciesStageKey?
    get() = speciesLabel?.let {
        SpeciesStageKey(it, stage, if (stage == EggStage.OTHER) otherStageText.trim() else "")
    }

/**
 * True once [speciesStageKey] can no longer change by the medtech finishing the card.
 *
 * A card merges with its twin at this point, not before: a stage still waiting to be chosen (or
 * an Other stage still waiting for its text) means the key is not final yet, and merging early
 * would fold a card into the wrong twin the moment a still-blank stage happened to match another
 * still-blank one.
 */
val VerificationAnswers.speciesStageKeyIsSettled: Boolean
    get() = when {
        speciesLabel == null -> false
        EggStage.forSpecies(species).isEmpty() -> true // stage not applicable to this species
        stage == null -> false // stage not chosen yet
        stage == EggStage.OTHER -> otherStageText.isNotBlank()
        else -> true
    }

/** Result of folding added card [removedIndex] into its twin, now at [survivorIndex]. */
data class AddedCardMerge(val findings: List<Finding>, val removedIndex: Int, val survivorIndex: Int)

/**
 * Combines two cards' [VerificationAnswers.isPrimaryAdded] pins when they collapse into one.
 *
 * A `true` pin wins outright — one of the two cards being merged is the row already holding the
 * species' plain id, and that fact must not be lost because it happened to be the one renamed
 * into the collision rather than the one it landed on. Absent a `true`, a `false` from either side
 * still wins over `null`: a pinned-non-primary card merging into an undecided one must not
 * un-pin the species back to "undecided", which is what let it become eligible for primary again.
 */
private fun mergePrimaryPin(a: Boolean?, b: Boolean?): Boolean? = when {
    a == true || b == true -> true
    a == false || b == false -> false
    else -> null
}

/**
 * Folds added card [index] into another added card sharing its [SpeciesStageKey], if one exists.
 *
 * Null when [index] is out of range, unnamed, or has no twin — nothing to fold. The twin's own
 * species/stage/other-text survive; only the totals and drawn boxes are combined, so renaming a
 * card into a collision never loses geometry a human placed.
 */
fun List<Finding>.mergeAddedCardIntoTwin(index: Int): AddedCardMerge? {
    val key = getOrNull(index)?.answers?.speciesStageKey
    val twinIndex = key?.let { k ->
        indices.firstOrNull { i -> i != index && this[i].prediction == null && this[i].answers.speciesStageKey == k }
    }
    return twinIndex?.let { ti ->
        val named = this[index].answers
        val twin = this[ti].answers
        val merged = twin.copy(
            fieldTotal = (twin.fieldTotal ?: 0) + (named.fieldTotal ?: 0),
            drawnBoxes = twin.drawnBoxes + named.drawnBoxes,
            isPrimaryAdded = mergePrimaryPin(twin.isPrimaryAdded, named.isPrimaryAdded),
        )
        val survivorIndex = if (ti < index) ti else ti - 1
        val newFindings = mapIndexed { i, finding -> if (i == ti) finding.copy(answers = merged) else finding }
            .filterIndexed { i, _ -> i != index }
        AddedCardMerge(newFindings, index, survivorIndex)
    }
}

/**
 * Folds every added card into the first added card sharing its [SpeciesStageKey] — the settle-
 * point backstop that guarantees at most one added card per key survives to be persisted.
 */
fun List<Finding>.consolidateAddedTwins(): List<Finding> {
    val result = mutableListOf<Finding>()
    val firstIndexOfKey = mutableMapOf<SpeciesStageKey, Int>()
    forEach { finding ->
        val key = if (finding.prediction == null) finding.answers.speciesStageKey else null
        val firstIndex = key?.let { firstIndexOfKey[it] }
        if (key != null && firstIndex != null) {
            val existing = result[firstIndex].answers
            result[firstIndex] = result[firstIndex].copy(
                answers = existing.copy(
                    fieldTotal = (existing.fieldTotal ?: 0) + (finding.answers.fieldTotal ?: 0),
                    drawnBoxes = existing.drawnBoxes + finding.answers.drawnBoxes,
                    isPrimaryAdded = mergePrimaryPin(existing.isPrimaryAdded, finding.answers.isPrimaryAdded),
                ),
            )
        } else {
            if (key != null) firstIndexOfKey[key] = result.size
            result.add(finding)
        }
    }
    return result
}

/**
 * Which added-card index owns the permanent, stage-less id for each species, keyed by species.
 *
 * A pinned [VerificationAnswers.isPrimaryAdded] wins outright wherever one exists: `true` says
 * this is the card, and `false` — set on every other added card of a species that was reopened
 * with two-or-more rows — says a species has *no* primary to elect here at all, even once every
 * other card of it is removed down to this one lone survivor. That is deliberate, not an
 * oversight: see the flag's own doc for why re-electing a pinned-`false` card would orphan a row.
 *
 * Only a species with no pin anywhere — every added card of it is new this session, with no
 * persisted history yet — falls back to the first such card in list order. That is stable within
 * one editing session as long as that first card is not itself removed; it is the best
 * deterministic rule available before anything has been persisted for the species to pin against.
 */
fun List<Finding>.primaryAddedIndexBySpecies(): Map<String, Int> {
    val pinnedTrue = mutableMapOf<String, Int>()
    val pinnedFalse = mutableSetOf<String>()
    forEachIndexed { index, finding ->
        if (finding.prediction != null) return@forEachIndexed
        val species = finding.answers.speciesStageKey?.species ?: return@forEachIndexed
        when (finding.answers.isPrimaryAdded) {
            true -> pinnedTrue[species] = index
            false -> pinnedFalse += species
            null -> Unit
        }
    }
    val result = pinnedTrue.toMutableMap()
    forEachIndexed { index, finding ->
        if (finding.prediction != null) return@forEachIndexed
        val species = finding.answers.speciesStageKey?.species ?: return@forEachIndexed
        if (species !in result && species !in pinnedFalse) {
            result[species] = index
        }
    }
    return result
}

/**
 * Locks in, for every added card, the primary-vs-stage-aware election [primaryAddedIndexBySpecies]
 * just made — so the decision a submit is about to act on survives as a pin rather than being
 * re-derived from scratch on some later call within the same session.
 *
 * Pins are otherwise only ever assigned reactively, at reopen, from what a previous submit already
 * wrote to disk (see [com.agarthavision.data.local.mapper.toDetectionEntities] and
 * `OpenVerificationTargetUseCase`). A card added and submitted *this* session, with no reopen in
 * between, never went through that path, so it stayed `null` — undecided — even after determining
 * which of it and its siblings got the plain id. Removing its primary sibling later in the same
 * session then let the list-order fallback in [primaryAddedIndexBySpecies] re-elect it, flipping an
 * already-synced stage-aware row's id to plain on the very next submit and orphaning the old row
 * remotely (the upsert-only push never deletes it) — the same double-count class 14zcqnthz6e exists
 * to prevent, just triggered by an in-session removal instead of a later reopen.
 *
 * Called on the list a submit is about to persist, so the pin this writes agrees with the id
 * [com.agarthavision.data.local.mapper.toDetectionEntities] derives from the very same election:
 * both read [primaryAddedIndexBySpecies] over the same list. A species rename resets its card's
 * pin back to `null` first (see `VerificationViewModel.withSpecies`), so a renamed card is still
 * re-elected fresh here rather than carrying a stale election from the species it left.
 */
fun List<Finding>.withResolvedPrimaryPins(): List<Finding> {
    val primaryIndexBySpecies = primaryAddedIndexBySpecies()
    return mapIndexed { index, finding ->
        if (finding.prediction != null) return@mapIndexed finding
        val species = finding.answers.speciesStageKey?.species ?: return@mapIndexed finding
        val isPrimary = primaryIndexBySpecies[species] == index
        if (finding.answers.isPrimaryAdded == isPrimary) {
            finding
        } else {
            finding.copy(answers = finding.answers.copy(isPrimaryAdded = isPrimary))
        }
    }
}

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
