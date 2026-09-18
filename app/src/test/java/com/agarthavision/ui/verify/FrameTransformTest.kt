package com.agarthavision.ui.verify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The image-to-canvas transform and its inverse.
 *
 * This is the one piece of the drawing feature whose wrong answer still looks plausible: a box
 * converted with the wrong convention renders somewhere believable on the frame, nothing throws,
 * and the only way to notice is to compare against the specimen under the objective. So the
 * round trip is pinned here rather than found on a device.
 */
class FrameTransformTest {

    /** The one shape the capture pipeline actually emits. */
    private val source = 640f

    private fun transform(canvasW: Float, canvasH: Float) =
        requireNotNull(frameTransform(canvasW, canvasH, source, source))

    /** Every container shape a phone can put this frame in, plus the square it usually is. */
    private val containers = listOf(
        1080f to 1080f,
        1080f to 1920f,
        1920f to 1080f,
        // Far past anything a phone does, because the arithmetic should not care.
        300f to 2400f,
        2400f to 300f,
    )

    @Test
    fun `an image point survives the round trip at every container shape`() {
        val points = listOf(0f to 0f, 1f to 1f, 320f to 320f, 639f to 639f, 100f to 540f)

        containers.forEach { (canvasW, canvasH) ->
            val t = transform(canvasW, canvasH)
            points.forEach { (imageX, imageY) ->
                val backX = t.toImageX(t.toCanvasX(imageX))
                val backY = t.toImageY(t.toCanvasY(imageY))
                assertEquals("x at ${canvasW}x$canvasH", imageX, backX, 1f)
                assertEquals("y at ${canvasW}x$canvasH", imageY, backY, 1f)
            }
        }
    }

    /**
     * The single most likely bug in the feature.
     *
     * A drawn box stored from its top-left corner instead of its centre renders offset by half
     * its own size, on every box, forever, without throwing. The drag below covers the middle
     * quarter of a square frame, so the centre must land dead in the middle and the size must be
     * half the frame — not a quarter-sized box hanging off the top-left.
     */
    @Test
    fun `a drawn box comes back centre-based, not corner-based`() {
        // A square canvas, so scale is 1:1 with no letterbox to reason about.
        val t = transform(640f, 640f)

        val box = t.imageBoxBetween(160f, 160f, 480f, 480f)

        assertEquals(320f, box.x, 0.5f)
        assertEquals(320f, box.y, 0.5f)
        assertEquals(320f, box.width, 0.5f)
        assertEquals(320f, box.height, 0.5f)
    }

    /**
     * Letterbox bands are not part of the image.
     *
     * A 640 square in a 1080x1920 container is scaled to 1080 wide and centred vertically, so the
     * top band is (1920 - 1080) / 2 = 420 canvas pixels of nothing. A drag starting at the very
     * top of the *image* starts 420 pixels down the canvas, and a transform that ignored the band
     * would report every box shifted upward by a third of the frame.
     */
    @Test
    fun `the letterbox band is taken off before converting`() {
        val t = transform(1080f, 1920f)

        assertEquals(0f, t.offsetX, 0.01f)
        assertEquals(420f, t.offsetY, 0.01f)
        assertEquals(0f, t.toImageY(420f), 1f)
        assertEquals(640f, t.toImageY(1500f), 1f)
    }

    @Test
    fun `a box dragged backwards is the same box as one dragged forwards`() {
        val t = transform(640f, 640f)

        val forwards = t.imageBoxBetween(100f, 120f, 300f, 400f)
        val backwards = t.imageBoxBetween(300f, 400f, 100f, 120f)

        assertEquals(forwards, backwards)
        assertTrue("A backwards drag must not produce a negative size.", backwards.width > 0f)
        assertTrue(backwards.height > 0f)
    }

    /**
     * No transform rather than an infinite one.
     *
     * A canvas measured at zero — the frame before its first layout pass — would otherwise divide
     * by zero and hand back coordinates of Infinity, which render as a box that is merely absent
     * rather than as an error.
     */
    @Test
    fun `an unmeasured canvas has no transform at all`() {
        assertNull(frameTransform(0f, 0f, source, source))
        assertNull(frameTransform(1080f, 1920f, 0f, 0f))
        assertNull(frameTransform(-1f, 100f, source, source))
    }
}
