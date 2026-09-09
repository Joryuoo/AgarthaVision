package com.agarthavision.domain.inference

/**
 * The outcome of one frame passing through one [InferenceEngine].
 *
 * [imageWidth] and [imageHeight] describe the space [predictions] are expressed in, and are
 * carried through to the sample row so the verification overlay can scale boxes correctly
 * regardless of which engine produced them.
 */
data class InferenceResult(
    val predictions: List<Prediction>,
    val imageWidth: Int?,
    val imageHeight: Int?,
    val modelVersion: String?,
    val engine: InferenceEngineId,
    val timings: InferenceTimings = InferenceTimings(),
)
