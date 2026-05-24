package com.agarthavision.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Response shape from the self-hosted inference container's `POST /infer`.
 *
 * Designed to be compatible with the Roboflow Hosted Inference response so the
 * mobile code can stay agnostic to the hosting backend. Per ADR-003.
 */
data class InferenceResponseDto(
    @SerializedName("predictions") val predictions: List<PredictionDto> = emptyList(),
    @SerializedName("image") val image: ImageMetaDto? = null,
)

data class PredictionDto(
    @SerializedName("class") val classLabel: String,
    @SerializedName("confidence") val confidence: Float,
    @SerializedName("x") val x: Float,
    @SerializedName("y") val y: Float,
    @SerializedName("width") val width: Float,
    @SerializedName("height") val height: Float,
)

data class ImageMetaDto(
    @SerializedName("width") val width: Int,
    @SerializedName("height") val height: Int,
)
