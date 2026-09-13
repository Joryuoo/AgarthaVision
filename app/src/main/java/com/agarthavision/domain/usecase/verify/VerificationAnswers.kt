package com.agarthavision.domain.usecase.verify

import com.agarthavision.domain.model.EggSpecies

/**
 * One detection's answers, in the order the sheet asks them.
 *
 * [speciesConfirmed] is the answer to "is this a <model's species> egg?". Yes copies the
 * model's species into [species] in the same step, so the medtech never re-picks what they
 * just agreed with; no clears it and the sheet offers the species picker instead. It stays
 * null when the model's class maps to no [EggSpecies] — there is nothing to confirm, so
 * the picker is offered straight away.
 */
data class VerificationAnswers(
    val isEgg: Boolean? = null,
    val isBoxCorrect: Boolean? = null,
    val speciesConfirmed: Boolean? = null,
    val species: EggSpecies? = null,
    val otherSpeciesText: String = "",
) {
    val isComplete: Boolean
        get() = when {
            isEgg == null -> false
            !isEgg -> true
            isBoxCorrect == null -> false
            !isBoxCorrect -> true
            species == null -> false
            species == EggSpecies.OTHER -> otherSpeciesText.isNotBlank()
            else -> true
        }
}
