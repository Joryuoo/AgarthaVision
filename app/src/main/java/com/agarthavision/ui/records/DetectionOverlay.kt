package com.agarthavision.ui.records

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import com.agarthavision.core.util.CAPTURE_FRAME_SIZE_PX
import com.agarthavision.domain.model.Detection
import com.agarthavision.ui.theme.detectionBoxColor
import com.agarthavision.ui.verify.frameTransform
import com.agarthavision.ui.verify.toCanvasX
import com.agarthavision.ui.verify.toCanvasY

/**
 * The detection boxes, each in its own colour, over the frame.
 *
 * ## This was drawing every box in the wrong place
 *
 * The overlay this replaces treated `bbox_*` as **normalised 0–1 with a top-left origin**:
 *
 * ```
 * val left = bx.coerceIn(0f, 1f) * size.width
 * ```
 *
 * What is stored is **centre-based pixels in the source image's space**.
 * `VerificationMapper` copies `Prediction.x/y/width/height` straight through, and
 * `Prediction`'s own KDoc says so. For a real detection at `x = 320f`, `coerceIn(0f, 1f)`
 * clamped it to `1.0` and the box landed in the bottom-right corner of the frame. Every box.
 *
 * **The `coerceIn` is what hid it** — it turned an out-of-range number into a plausible-looking
 * rectangle instead of anything visible as wrong. It is gone, not widened: a box outside the
 * frame means the data is wrong and should look wrong.
 *
 * The arithmetic below is the same `frameTransform` the Verification Screen draws with, imported
 * rather than copied. Two screens computing the same letterbox two ways is how the boxes drift
 * apart again.
 *
 * ## Dimensions
 *
 * The domain `Sample` carries no image dimensions at all, so this defaults to
 * [CAPTURE_FRAME_SIZE_PX]. That is safe by construction rather than a guess: `toJpegBytes()`
 * centre-crops and downscales every frame to a 640 square before it is ever posted, so 640 is
 * the only value a stored dimension holds in practice.
 *
 * **One exception, and it is the reason to read this twice:** a device whose camera cannot supply
 * a 640 stream encodes at its own smaller native square rather than upscaling
 * (`ImageExtensions.kt`). A sample from such a device renders its boxes slightly off here.
 * Carrying the real dimensions down would mean adding them to the domain `Sample` and to
 * `SampleRemoteDataSource.toEntity()`, which sets `imageWidth = null` on **every** sample pulled
 * from Supabase. That is the fix; this comment is the placeholder for it. Silently assuming 640
 * for every sample is how the bug above happened the first time.
 */
@Composable
internal fun DetectionOverlay(
    detections: List<Detection>,
    hiddenIndices: Set<Int>,
    modifier: Modifier = Modifier,
    strokeWidth: Float = 3f,
) {
    // Capture colours at composition time — DrawScope inside Canvas is not @Composable.
    val colors = remember(detections.size) { List(detections.size) { detectionBoxColor(it) } }
    Canvas(modifier = modifier) {
        val transform = frameTransform(
            canvasWidth = size.width,
            canvasHeight = size.height,
            sourceWidth = CAPTURE_FRAME_SIZE_PX.toFloat(),
            sourceHeight = CAPTURE_FRAME_SIZE_PX.toFloat(),
        ) ?: return@Canvas

        detections.forEachIndexed { index, detection ->
            if (index in hiddenIndices) return@forEachIndexed
            // A detection with no box is a valid finding - an egg the medtech added and did not
            // draw - and simply has nothing to render.
            val cx = detection.bboxX ?: return@forEachIndexed
            val cy = detection.bboxY ?: return@forEachIndexed
            val bw = detection.bboxW ?: return@forEachIndexed
            val bh = detection.bboxH ?: return@forEachIndexed
            drawRect(
                color = colors[index],
                topLeft = Offset(
                    transform.toCanvasX(cx - bw / 2f),
                    transform.toCanvasY(cy - bh / 2f),
                ),
                size = Size(bw * transform.scale, bh * transform.scale),
                style = Stroke(width = strokeWidth),
            )
        }
    }
}

@Composable
internal fun TileBoxesOverlay(
    boxes: List<TileBox>,
    modifier: Modifier = Modifier,
) {
    if (boxes.isEmpty()) return
    Canvas(modifier = modifier) {
        val transform = frameTransform(
            canvasWidth = size.width,
            canvasHeight = size.height,
            sourceWidth = CAPTURE_FRAME_SIZE_PX.toFloat(),
            sourceHeight = CAPTURE_FRAME_SIZE_PX.toFloat(),
        ) ?: return@Canvas

        val stroke = Stroke(width = 1.5f * density)
        boxes.forEach { box ->
            drawRect(
                color = box.color,
                topLeft = Offset(
                    transform.toCanvasX(box.cx - box.w / 2f),
                    transform.toCanvasY(box.cy - box.h / 2f),
                ),
                size = Size(box.w * transform.scale, box.h * transform.scale),
                style = stroke,
            )
        }
    }
}

