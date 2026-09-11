package com.agarthavision.domain.usecase.verify

import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.EggStage

data class VerificationAnswers(
    val isEgg: Boolean? = null,
    val isBoxCorrect: Boolean? = null,
    val species: EggSpecies? = null,
    val otherSpeciesText: String = "",
    val stage: EggStage? = null,
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
