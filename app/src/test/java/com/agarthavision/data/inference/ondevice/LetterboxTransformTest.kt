package com.agarthavision.data.inference.ondevice

import org.junit.Assert.assertEquals
import org.junit.Test

class LetterboxTransformTest {

    @Test
    fun `a 640 capture into a 640 model is the identity`() {
        val transform = LetterboxTransform.fit(640, 640, 640, 640)

        assertEquals(1f, transform.scale, TOLERANCE)
        assertEquals(0, transform.padLeft)
        assertEquals(0, transform.padTop)
        assertEquals(123.4f, transform.sourceX(123.4f), TOLERANCE)
        assertEquals(56.7f, transform.sourceY(56.7f), TOLERANCE)
        assertEquals(80f, transform.sourceLength(80f), TOLERANCE)
    }

    @Test
    fun `a smaller square stream is scaled up to fill the input, with no padding`() {
        val transform = LetterboxTransform.fit(480, 480, 640, 640)

        assertEquals(640f / 480f, transform.scale, TOLERANCE)
        assertEquals(640, transform.scaledWidth)
        assertEquals(640, transform.scaledHeight)
        assertEquals(0, transform.padLeft)
        assertEquals(0, transform.padTop)
        assertEquals(240f, transform.sourceX(320f), TOLERANCE)
    }

    @Test
    fun `a wide frame is padded top and bottom, and the padding is undone`() {
        val transform = LetterboxTransform.fit(1280, 720, 640, 640)

        assertEquals(0.5f, transform.scale, TOLERANCE)
        assertEquals(360, transform.scaledHeight)
        assertEquals(0, transform.padLeft)
        assertEquals(140, transform.padTop)
        // The input's centre is the source's centre.
        assertEquals(640f, transform.sourceX(320f), TOLERANCE)
        assertEquals(360f, transform.sourceY(320f), TOLERANCE)
        assertEquals(100f, transform.sourceLength(50f), TOLERANCE)
    }

    @Test
    fun `an odd leftover pixel goes to the bottom, as Ultralytics pads`() {
        // 640 * 427/640 rounds to 427 rows, leaving 213 to split: 106 above, 107 below.
        val transform = LetterboxTransform.fit(640, 427, 640, 640)

        assertEquals(427, transform.scaledHeight)
        assertEquals(106, transform.padTop)
    }

    private companion object {
        const val TOLERANCE = 1e-3f
    }
}
