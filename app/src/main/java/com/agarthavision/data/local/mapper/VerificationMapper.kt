package com.agarthavision.data.local.mapper

import com.agarthavision.data.local.entity.DetectionEntity
import com.agarthavision.data.remote.dto.PredictionDto
import com.agarthavision.domain.model.DetectionVerdict
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.usecase.verify.VerificationAnswers
import java.util.UUID

fun computeVerdict(answers: VerificationAnswers, modelClass: String): DetectionVerdict = when {
    answers.isEgg != true -> DetectionVerdict.FALSE_POSITIVE
    answers.isBoxCorrect != true -> DetectionVerdict.BOX_INCORRECT
    answers.species == null -> DetectionVerdict.FALSE_POSITIVE
    answers.species == EggSpecies.OTHER -> DetectionVerdict.WRONG_CLASS
    answers.species.canonicalClass != modelClass -> DetectionVerdict.WRONG_CLASS
    else -> DetectionVerdict.CONFIRMED
}

fun PredictionDto.toDetectionEntity(
    sampleId: String,
    answers: VerificationAnswers,
): DetectionEntity {
    val verdict = computeVerdict(answers, classLabel)
    val expertClass: String? = when {
        verdict == DetectionVerdict.WRONG_CLASS && answers.species == EggSpecies.OTHER ->
            answers.otherSpeciesText.ifBlank { null }
        verdict == DetectionVerdict.WRONG_CLASS ->
            answers.species?.canonicalClass
        else -> null
    }
    return DetectionEntity(
        detectionId = UUID.randomUUID().toString(),
        sampleId = sampleId,
        classLabel = classLabel,
        confidence = confidence,
        bboxX = x,
        bboxY = y,
        bboxW = width,
        bboxH = height,
        verdict = verdict.value,
        expertClass = expertClass,
        verifiedByUser = true,
    )
}
