package com.agarthavision.domain.inference

/**
 * A bounding box a human drew, in the coordinate space of the source image.
 *
 * **Centre-based pixels, exactly like [Prediction]** — [x] and [y] are the centre of the box,
 * not its top-left corner. That is not a stylistic choice: `VerificationMapper` copies both
 * straight into `bbox_x/y/w/h`, so a drawn box and a model box have to be interchangeable
 * downstream or the two end up in the same column meaning different things.
 *
 * Deliberately **not** a [Prediction]. A drawn box has no class the model named and no
 * confidence the model assigned, and synthesising either would put invented values into
 * `detections.class_label` and `detections.confidence`, which feed the retraining corpus. What
 * distinguishes a human box there is the row itself: a box on a `BOX_INCORRECT` row is always
 * a redraw, and a box on a row with no `prediction_id` is always an added egg — see
 * `docs/map/objects/Detection.md`, *Box provenance*. Not a fake prediction carried around in
 * the UI.
 */
data class ImageBox(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)
