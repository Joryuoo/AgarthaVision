package com.agarthavision.domain.usecase.verify

import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.EggStage

/**
 * The medtech's answers about a single [Finding].
 *
 * Deliberately holds **one** `(species, stage, count)`, not a list. A bounding box contains one
 * egg, so a list here would be meaningless; polyparasitism is expressed as several [Finding]s
 * on the frame, not several species on one finding.
 */
data class VerificationAnswers(
    val isEgg: Boolean? = null,
    val isBoxCorrect: Boolean? = null,
    val species: EggSpecies? = null,
    val otherSpeciesText: String = "",
    val stage: EggStage? = null,
    /**
     * Eggs of this species and stage in this field. Only ever set on a finding with no
     * prediction — a box is worth exactly one egg, and that is not the medtech's to edit.
     */
    val eggCount: Int? = null,
    /**
     * True once the medtech deliberately selects a species, **including re-picking the value
     * that was pre-filled from the model output**. Seeded false by auto-fill.
     *
     * Persisted to `detections.species_touched`. Without it, a pre-filled answer submitted
     * untouched produces `CONFIRMED` and "a human did not object" becomes indistinguishable
     * from "a human confirmed this" — in a table that doubles as the retraining corpus. See
     * `supabase/migrations/0012_polyparasitism_findings.sql`.
     *
     * **Invariant:** any path that sets [species] from a human action must set this true.
     */
    val speciesTouched: Boolean = false,
) {
    /**
     * True when the species question is answered.
     *
     * **[stage] deliberately does not gate this.** It is offered wherever the species defines
     * one and it persists when given, but a medtech who does not record it is not blocked from
     * submitting — the same rule ticket 86d4a6jwy set when the dropdown was introduced. The
     * developmental stage is not the reading the surveillance output turns on; the infectivity
     * level is, and that is tracked separately (86d3fzd28).
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
