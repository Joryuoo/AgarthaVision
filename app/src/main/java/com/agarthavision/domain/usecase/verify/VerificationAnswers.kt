package com.agarthavision.domain.usecase.verify

import com.agarthavision.domain.model.EggSpecies

/**
 * The medtech's answers about a single [Finding], in the order the sheet asks them.
 *
 * Deliberately holds **one** species and count, not a list. A bounding box contains one egg, so
 * a list here would be meaningless; polyparasitism is expressed as several [Finding]s on the
 * frame, not several species on one finding.
 *
 * [speciesConfirmed] is the answer to "is this a <model's species> egg?" (86d4auj84). Yes copies
 * the model's species into [species] in the same step, so the medtech never re-picks what they
 * just agreed with; no clears it and the sheet offers the species picker instead. It stays null
 * when the model's class maps to no [EggSpecies] — there is nothing to confirm, so the picker is
 * offered straight away — and on a finding with no prediction, which is a human assertion with
 * nothing to agree or disagree with.
 */
data class VerificationAnswers(
    val isEgg: Boolean? = null,
    val isBoxCorrect: Boolean? = null,
    val speciesConfirmed: Boolean? = null,
    val species: EggSpecies? = null,
    val otherSpeciesText: String = "",
    /**
     * Eggs of this species in this field. Only ever set on a finding with no prediction — a
     * box is worth exactly one egg, and that is not the medtech's to edit.
     */
    val eggCount: Int? = null,
    /**
     * True once the medtech deliberately asserts a species — by confirming the model's
     * suggestion, or by picking one themselves.
     *
     * Persisted to `detections.species_touched`
     * (`supabase/migrations/0012_polyparasitism_findings.sql`). It was added when the sheet
     * pre-filled the model's species silently, to keep "a human did not object" distinguishable
     * from "a human confirmed this" in a table that doubles as the retraining corpus.
     *
     * 86d4auj84 then removed the silent pre-fill: the sheet now *asks*, so under the current
     * flow every submitted species is a deliberate assertion and this is true on every row. It
     * is kept, and asserted in `SubmitVerificationUseCaseTest`, precisely because that is an
     * invariant worth catching the loss of — a future path that writes a species without
     * asking would show up here as a false, rather than silently entering the corpus as a
     * human judgement.
     *
     * **Invariant:** any path that sets [species] must set this true.
     */
    val speciesTouched: Boolean = false,
) {
    /**
     * True when the species question is answered.
     *
     * No developmental-stage gate. The dropdown 86d4a6jwy added was reverted on staging
     * (`9dcfd5d`) — the four stages it shipped were never checked against literature — and the
     * ticket is deprioritised. The stage is not the reading the surveillance output turns on;
     * the infectivity level is, and that is tracked separately (86d3fzd28).
     */
    val speciesIsComplete: Boolean
        get() = when (species) {
            null -> false
            EggSpecies.OTHER -> otherSpeciesText.isNotBlank()
            else -> true
        }

    /**
     * The canonical class name, or the free text for a species the dropdown does not cover.
     * Null when no species has been chosen. Same convention as `detections.class_label`, so
     * findings and detections group together.
     */
    val speciesLabel: String?
        get() = when (species) {
            null -> null
            // OTHER carries no canonical class; the typed text is the label.
            EggSpecies.OTHER -> otherSpeciesText.trim().takeIf { it.isNotBlank() }
            else -> species.canonicalClass
        }
}
