package com.agarthavision.data.inference.ondevice

import com.agarthavision.domain.inference.Prediction
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns the detection head's raw output into [Prediction]s in source-image pixels.
 *
 * The output is `[1, 4 + classes, anchors]`, row-major, read here as one flat array: for a
 * 640x640 input that is 8400 candidate boxes, geometry first (centre-x, centre-y, width,
 * height), then one sigmoid score per class. There is no objectness row.
 *
 * The model is not end-to-end (no one-to-one head), so this is where NMS happens. Mirrors
 * Ultralytics' `non_max_suppression` as the cloud container runs it:
 *  1. **Gate.** Keep boxes whose best class score clears the threshold, one label per box.
 *     Decode mechanics, not a clinical filter: see C7.
 *  2. **Suppress.** Greedy class-agnostic NMS (`agnostic_nms=True` on the cloud): a box
 *     suppresses any weaker box it overlaps past the IoU threshold, whatever its class. An
 *     egg the model can't place gets one box with its best guess, not one per species.
 *  3. **Cap** at the manifest's `max_detections`, highest score first.
 *  4. **Rescale and clip.** Undo the letterbox, clip to the frame, and return centre-based
 *     boxes, exactly the geometry `inference/server.py` sends.
 */
@Singleton
class YoloOutputDecoder @Inject constructor() {

    fun decode(output: FloatArray, transform: LetterboxTransform, manifest: ModelManifest): List<Prediction> {
        val anchors = manifest.anchors
        val classCount = manifest.rows - ModelManifest.BOX_GEOMETRY_ROWS
        require(output.size == manifest.rows * anchors) {
            "Output has ${output.size} values, manifest expects ${manifest.rows} x $anchors"
        }

        val widthScale = if (manifest.normalizedBoxes) manifest.inputWidth.toFloat() else 1f
        val heightScale = if (manifest.normalizedBoxes) manifest.inputHeight.toFloat() else 1f

        val candidates = ArrayList<Candidate>()
        for (anchor in 0 until anchors) {
            var bestClass = 0
            var bestScore = output[scoreIndex(0, anchor, anchors)]
            for (classIndex in 1 until classCount) {
                val score = output[scoreIndex(classIndex, anchor, anchors)]
                if (score > bestScore) {
                    bestScore = score
                    bestClass = classIndex
                }
            }
            // Ultralytics keeps score > conf, strictly.
            if (bestScore <= manifest.confThreshold) continue

            val centreX = output[ROW_CENTRE_X * anchors + anchor] * widthScale
            val centreY = output[ROW_CENTRE_Y * anchors + anchor] * heightScale
            val width = output[ROW_WIDTH * anchors + anchor] * widthScale
            val height = output[ROW_HEIGHT * anchors + anchor] * heightScale
            candidates += Candidate(
                classIndex = bestClass,
                score = bestScore,
                left = centreX - width / 2,
                top = centreY - height / 2,
                right = centreX + width / 2,
                bottom = centreY + height / 2,
            )
        }

        return suppress(candidates, manifest.iouThreshold)
            .take(manifest.maxDetections)
            .map { it.toPrediction(transform, manifest) }
    }

    private fun scoreIndex(classIndex: Int, anchor: Int, anchors: Int): Int =
        (ModelManifest.BOX_GEOMETRY_ROWS + classIndex) * anchors + anchor

    /** Greedy class-agnostic NMS. Returns survivors highest score first. */
    private fun suppress(candidates: List<Candidate>, iouThreshold: Float): List<Candidate> {
        val kept = ArrayList<Candidate>()
        val remaining = candidates.sortedByDescending { it.score }.toMutableList()
        while (remaining.isNotEmpty()) {
            val best = remaining.removeAt(0)
            kept += best
            remaining.removeAll { iou(best, it) > iouThreshold }
        }
        return kept
    }

    private fun iou(a: Candidate, b: Candidate): Float {
        val overlapWidth = minOf(a.right, b.right) - maxOf(a.left, b.left)
        val overlapHeight = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        if (overlapWidth <= 0f || overlapHeight <= 0f) return 0f
        val intersection = overlapWidth * overlapHeight
        val union = a.area + b.area - intersection
        return if (union <= 0f) 0f else intersection / union
    }

    private fun Candidate.toPrediction(transform: LetterboxTransform, manifest: ModelManifest): Prediction {
        val sourceWidth = transform.sourceWidth.toFloat()
        val sourceHeight = transform.sourceHeight.toFloat()
        val x1 = transform.sourceX(left).coerceIn(0f, sourceWidth)
        val y1 = transform.sourceY(top).coerceIn(0f, sourceHeight)
        val x2 = transform.sourceX(right).coerceIn(0f, sourceWidth)
        val y2 = transform.sourceY(bottom).coerceIn(0f, sourceHeight)
        return Prediction(
            classLabel = manifest.classNames[classIndex],
            confidence = score,
            x = (x1 + x2) / 2,
            y = (y1 + y2) / 2,
            width = x2 - x1,
            height = y2 - y1,
        )
    }

    /** A box in model-input pixels, corner-based for the IoU arithmetic. */
    private data class Candidate(
        val classIndex: Int,
        val score: Float,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) {
        val area: Float get() = (right - left) * (bottom - top)
    }

    private companion object {
        const val ROW_CENTRE_X = 0
        const val ROW_CENTRE_Y = 1
        const val ROW_WIDTH = 2
        const val ROW_HEIGHT = 3
    }
}
