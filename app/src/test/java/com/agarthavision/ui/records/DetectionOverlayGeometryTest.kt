package com.agarthavision.ui.records

import com.agarthavision.core.util.CAPTURE_FRAME_SIZE_PX
import com.agarthavision.ui.theme.DetectionBoxPalette
import com.agarthavision.ui.theme.detectionBoxColor
import com.agarthavision.ui.verify.frameTransform
import com.agarthavision.ui.verify.toCanvasX
import com.agarthavision.ui.verify.toCanvasY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The conversion the Sample Data Screen's overlay was getting wrong.
 *
 * **The bug survived this long because nothing pinned the convention.** `bbox_*` was read as
 * normalised 0–1 with a top-left origin; what is stored is centre-based pixels in the source
 * image's space. A real detection at `x = 320f` was clamped by `coerceIn(0f, 1f)` to `1.0` and
 * landed in the bottom-right corner of the frame — every box, with nothing thrown. The clamp is
 * what turned an out-of-range number into a plausible rectangle.
 *
 * A passing test is what stops it coming back the next time someone reads the KDoc and believes
 * it. Note there is no `coerceIn` anywhere below: a box outside the frame means the data is
 * wrong and should look wrong.
 */
class DetectionOverlayGeometryTest {

    private val source = CAPTURE_FRAME_SIZE_PX.toFloat()

    /** The corner the overlay draws from, for a centre-based box. */
    private fun topLeftOf(
        canvasW: Float,
        canvasH: Float,
        cx: Float,
        cy: Float,
        w: Float,
        h: Float,
    ): Pair<Float, Float> {
        val t = requireNotNull(frameTransform(canvasW, canvasH, source, source))
        return t.toCanvasX(cx - w / 2f) to t.toCanvasY(cy - h / 2f)
    }

    /**
     * A box dead in the middle of the frame draws from a corner half its size up and left of
     * centre — not from the centre itself, and nowhere near the bottom-right.
     */
    @Test
    fun `a centre-based box draws from its own corner`() {
        val (left, top) = topLeftOf(640f, 640f, cx = 320f, cy = 320f, w = 100f, h = 80f)

        assertEquals(270f, left, 0.5f)
        assertEquals(280f, top, 0.5f)
    }

    /**
     * The specific failure, restated as an inequality.
     *
     * Under the old reading, `x = 320f` clamped to 1.0 and multiplied by the canvas width, so
     * every box started at the right-hand edge. Any conversion that still does that fails here.
     */
    @Test
    fun `a box in the middle does not land in the bottom-right corner`() {
        val canvas = 640f
        val (left, top) = topLeftOf(canvas, canvas, cx = 320f, cy = 320f, w = 100f, h = 80f)

        assertNotEquals(canvas, left)
        assertNotEquals(canvas, top)
    }

    /** Letterbox bands are not part of the image, so a box at the top of a tall canvas is not at 0. */
    @Test
    fun `the letterbox band offsets the whole overlay`() {
        // A 640 square fitted into 1080x1920 is scaled to 1080 and centred: 420px of nothing on top.
        val (left, top) = topLeftOf(1080f, 1920f, cx = 0f, cy = 0f, w = 0f, h = 0f)

        assertEquals(0f, left, 0.5f)
        assertEquals(420f, top, 0.5f)
    }

    // The palette

    @Test
    fun `no two of the first eight detections share a colour`() {
        val first = (0 until DetectionBoxPalette.size).map(::detectionBoxColor)

        assertEquals(DetectionBoxPalette.size, first.toSet().size)
    }

    /**
     * Past the eighth it cycles, which is a decision rather than an oversight: more than eight
     * detections in one low-power field is vanishingly rare, and a cycle is a better failure mode
     * than an unbounded generated palette that eventually emits two near-identical colours.
     */
    @Test
    fun `the ninth detection cycles back to the first colour`() {
        assertEquals(detectionBoxColor(0), detectionBoxColor(DetectionBoxPalette.size))
        assertEquals(detectionBoxColor(1), detectionBoxColor(DetectionBoxPalette.size + 1))
    }

    @Test
    fun `a negative index still lands inside the palette`() {
        assertEquals(detectionBoxColor(DetectionBoxPalette.size - 1), detectionBoxColor(-1))
    }
}
