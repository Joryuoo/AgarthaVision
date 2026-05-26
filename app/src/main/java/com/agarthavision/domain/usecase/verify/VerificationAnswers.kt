package com.agarthavision.domain.usecase.verify

import com.agarthavision.domain.model.EggSpecies

data class VerificationAnswers(
    val isEgg: Boolean? = null,
    val isBoxCorrect: Boolean? = null,
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
