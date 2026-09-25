package com.agarthavision.data.inference

import com.agarthavision.data.remote.dto.PredictionDto
import com.agarthavision.domain.inference.Prediction
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

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

/**
 * A frame's model output as `samples.predictions_json` stores it, or null for a frame with none.
 *
 * Through the DTO for the reason at the top of this file: the column must stay byte-identical to
 * what capture writes, whether the list came from the container or back down from Supabase.
 */
fun Gson.encodePredictions(predictions: List<Prediction>): String? =
    predictions.takeIf { it.isNotEmpty() }?.let { toJson(it.toPredictionDtos()) }

/** The inverse of [encodePredictions]; null when the column is. */
fun Gson.decodePredictions(json: String?): List<Prediction>? =
    json?.let { fromJson<List<PredictionDto>>(it, PREDICTION_LIST).toDomainPredictions() }

private val PREDICTION_LIST = object : TypeToken<List<PredictionDto>>() {}.type
