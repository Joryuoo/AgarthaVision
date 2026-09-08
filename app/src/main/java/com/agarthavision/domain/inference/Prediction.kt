package com.agarthavision.domain.inference

/**
 * One detected bounding box, in the coordinate space of the source image.
 *
 * The geometry is **centre-based pixels**: [x] and [y] are the centre of the box, not its
 * top-left corner. This matches the container's `POST /infer` response exactly
 * (`inference/server.py`), and the on-device engine is required to reproduce the same
 * convention so both backends are interchangeable downstream.
 *
 * Two consumers depend on that convention directly: `VerificationMapper` copies these values
 * straight into `bbox_x/y/w/h`, and `FrameWithBoxes` renders them as centre-based.
 */
data class Prediction(
    val classLabel: String,
    val confidence: Float,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)
