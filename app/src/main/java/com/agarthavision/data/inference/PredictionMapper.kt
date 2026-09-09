package com.agarthavision.data.inference

import com.agarthavision.data.remote.dto.PredictionDto
import com.agarthavision.domain.inference.Prediction

/**
 * Converts between the wire/persistence shape ([PredictionDto]) and the domain model
 * ([Prediction]).
 *
 * The DTO stays the single serialization shape for both directions on purpose: the container's
 * JSON response and the `samples.predictions_json` column use the same field names, so routing
 * every read and write through the DTO keeps persisted rows byte-identical across this
 * refactor. `PredictionMapperTest` pins that.
 */
fun PredictionDto.toDomain(): Prediction = Prediction(
    classLabel = classLabel,
    confidence = confidence,
    x = x,
    y = y,
    width = width,
    height = height,
)

fun Prediction.toDto(): PredictionDto = PredictionDto(
    classLabel = classLabel,
    confidence = confidence,
    x = x,
    y = y,
    width = width,
    height = height,
)

fun List<PredictionDto>.toDomainPredictions(): List<Prediction> = map { it.toDomain() }

fun List<Prediction>.toPredictionDtos(): List<PredictionDto> = map { it.toDto() }
