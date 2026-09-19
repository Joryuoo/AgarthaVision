package com.agarthavision.domain.usecase.verify

import com.agarthavision.domain.inference.ImageBox
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
     * (`supabase/migrations/0012_polyparasitism_findings.sql`). It exists to keep **"a human did
     * not object"** distinguishable from **"a human confirmed this"** in a table that doubles as
     * the retraining corpus, where a species with no human behind it must never be
     * indistinguishable from one with.
     *
     * That distinction is load-bearing again. 86d4auj84 had removed the silent pre-fill and made
     * the sheet ask, at which point every submitted species was a deliberate assertion and this
     * was true on every row — a flag recording nothing. The screen now pre-fills every answer
     * from model output, so that a medtech whose model was right submits without tapping
     * anything, and the flag carries real information once more: false on a row nobody touched,
     * true the moment they confirm or change it.
     *
     * **Invariant:** every path by which the *medtech* sets [species] sets this true. Seeding a
     * row from the model's own class does not, and must not — a seeded species is the model's
     * answer sitting in the slot a human answer is read from, which is precisely the thing this
     * flag is here to tell apart.
     */
    val speciesTouched: Boolean = false,
    /**
     * A box the medtech drew by hand, in the model's own coordinate space.
     *
     * On a prediction-backed row this **replaces** the model's box: the model boxed a real egg
     * badly, and this is where it actually is. On an added row it is simply where the egg is,
     * and it stays null when the medtech did not bother — an added egg with no box is a complete
     * finding, which is what `detections.bbox_*` is nullable for.
     *
     * Deliberately not a synthesised [com.agarthavision.domain.inference.Prediction]: that would
     * need a class label and a confidence the model never assigned, and both feed the retraining
     * corpus.
     */
    val drawnBox: ImageBox? = null,
    /**
     * The model put this box in the wrong place, and that does not stop being true because a
     * human fixed it.
     *
     * Set when a redraw is committed, and **it locks Q2 to "No"**. This is the training signal
     * the whole drawing feature exists to capture: an implementation that lets Q2 flip back to
     * "Yes" after a redraw destroys the label, silently, leaving a frame that claims the model
     * localised correctly while carrying the human's geometry.
     *
     * A frame reopened for editing reconstructs this by comparing the stored `bbox_*` against
     * the prediction it belongs to. That is the only durable record: the alternative is a new
     * column, which means a Room version bump, and this project has already been burned once by
     * a version collision (`core/database/AgarthaDatabase.kt` records it).
     */
    val boxReplaced: Boolean = false,
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
