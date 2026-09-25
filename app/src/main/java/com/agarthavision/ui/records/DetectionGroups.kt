package com.agarthavision.ui.records

import com.agarthavision.domain.model.Detection
import com.agarthavision.domain.model.DetectionVerdict

/**
 * Where a detection sits on the Sample Data Screen, read straight off its stored verdict and box.
 *
 * Nothing new is stored to make this split. `verdict` already says what the medtech ruled and a
 * null box already says there is nothing to draw; the screen used to read neither, so a box the
 * medtech threw out sat beside the eggs they counted, labelled like one of them.
 */
internal enum class DetectionGroup {
    /** An egg with a box a human stands behind, whether the model's kept box or their redraw. */
    CONFIRMED,

    /**
     * An egg that is counted, whose model box the medtech called misplaced and did not redraw.
     * Since 14zcqnthrx6 no box is stored for it, so there is nothing to draw.
     */
    MISPLACED,

    /** Not an egg. Not counted. The model's box is kept for retraining and hidden by default. */
    REJECTED,
}

internal val Detection.hasBox: Boolean
    get() = bboxX != null && bboxY != null && bboxW != null && bboxH != null

/**
 * A redrawn misplaced box stays [DetectionGroup.CONFIRMED]: the box on the row is then the
 * medtech's own. So does a `WRONG_CLASS` row, which is a real egg under the medtech's species.
 */
internal val Detection.group: DetectionGroup
    get() = when {
        verdict == DetectionVerdict.FALSE_POSITIVE -> DetectionGroup.REJECTED
        verdict == DetectionVerdict.BOX_INCORRECT && !hasBox -> DetectionGroup.MISPLACED
        else -> DetectionGroup.CONFIRMED
    }

/**
 * Indices into this list, split by [group], each group in list order.
 *
 * Indices rather than detections, because the index is what ties a row to its box: it picks the
 * palette colour on both, and it keys which boxes are hidden. Regrouping the rows must not
 * recolour a confirmed egg.
 */
internal fun List<Detection>.indicesByGroup(): Map<DetectionGroup, List<Int>> =
    indices.groupBy { this[it].group }

/** The boxes the frame starts with hidden: every rejected one. */
internal fun List<Detection>.hiddenByDefault(): Set<Int> =
    indices.filterTo(mutableSetOf()) { this[it].group == DetectionGroup.REJECTED }
