package com.agarthavision.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ViewFitTest {

    private val epsilon = 1e-3f

    @Test
    fun `fit is aspect-correct - a wide bounds does not stretch`() {
        // 2:1 aspect bounds in a square viewport - width is the limiting axis.
        val bounds = GeoBounds(minX = 0f, minY = 0f, maxX = 20f, maxY = 10f)
        val transform = fitBounds(bounds, widthPx = 1000f, heightPx = 1000f, paddingPx = 0f, minSpan = 0f)

        // The x-span (20) must map to <= viewport width, same scale factor as y - if either
        // axis used a different scale, the shape would stretch.
        val (screenMinX, screenMinY) = transform.toScreen(bounds.minX, bounds.minY)
        val (screenMaxX, screenMaxY) = transform.toScreen(bounds.maxX, bounds.maxY)
        val screenWidth = abs(screenMaxX - screenMinX)
        val screenHeight = abs(screenMaxY - screenMinY)

        // width:height on screen should match width:height in map units (20:10 = 2:1)
        assertEquals(2f, screenWidth / screenHeight, 0.01f)
    }

    @Test
    fun `padding is respected - content never touches the viewport edge`() {
        val bounds = GeoBounds(minX = 0f, minY = 0f, maxX = 10f, maxY = 10f)
        val padding = 50f
        val transform = fitBounds(bounds, widthPx = 400f, heightPx = 400f, paddingPx = padding, minSpan = 0f)

        val (minScreenX, minScreenY) = transform.toScreen(bounds.minX, bounds.minY)
        val (maxScreenX, maxScreenY) = transform.toScreen(bounds.maxX, bounds.maxY)

        assertTrue("left edge should be at or beyond padding", minOf(minScreenX, maxScreenX) >= padding - epsilon)
        assertTrue("top edge should be at or beyond padding", minOf(minScreenY, maxScreenY) >= padding - epsilon)
        assertTrue(
            "right edge should be within viewport minus padding",
            maxOf(minScreenX, maxScreenX) <= 400f - padding + epsilon,
        )
    }

    @Test
    fun `minSpan prevents over-zoom on a tiny bounds`() {
        // A near-zero-size bounds (a single small island) should not blow up the scale.
        val tinyBounds = GeoBounds(minX = 5f, minY = 5f, maxX = 5.001f, maxY = 5.001f)
        val transform = fitBounds(tinyBounds, widthPx = 1000f, heightPx = 1000f, paddingPx = 0f, minSpan = 1f)

        // With minSpan = 1, the effective span is 1 map unit over a 1000px viewport, so scale
        // should be roughly 1000, not the enormous value a 0.001-unit span would produce.
        assertEquals(1000f, transform.scale, 5f)
    }

    @Test
    fun `toScreen and toMap are exact inverses`() {
        val bounds = GeoBounds(minX = -20f, minY = -30f, maxX = 40f, maxY = 15f)
        val transform = fitBounds(bounds, widthPx = 800f, heightPx = 600f, paddingPx = 24f)

        val samplePoints = listOf(
            0f to 0f,
            -20f to -30f,
            40f to 15f,
            10.5f to -4.25f,
            -5f to 5f,
        )

        samplePoints.forEach { (x, y) ->
            val (px, py) = transform.toScreen(x, y)
            val (backX, backY) = transform.toMap(px, py)
            assertEquals("x round-trip for ($x, $y)", x, backX, epsilon)
            assertEquals("y round-trip for ($x, $y)", y, backY, epsilon)
        }
    }
}
