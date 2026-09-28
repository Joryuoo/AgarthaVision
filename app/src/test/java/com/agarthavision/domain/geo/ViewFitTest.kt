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

    @Test
    fun `clampedToBounds pulls a fully panned-away map back into view`() {
        val bounds = GeoBounds(minX = 0f, minY = 0f, maxX = 10f, maxY = 10f)
        val fitted = fitBounds(bounds, widthPx = 400f, heightPx = 400f, paddingPx = 0f, minSpan = 0f)

        // A huge pan that would otherwise carry the whole map far off both edges of the screen.
        val panned = fitted.copy(tx = fitted.tx + 5000f, ty = fitted.ty - 5000f)
        val clamped = panned.clampedToBounds(bounds, 400f, 400f)

        val (minScreenX, minScreenY) = clamped.toScreen(bounds.minX, bounds.minY)
        val (maxScreenX, maxScreenY) = clamped.toScreen(bounds.maxX, bounds.maxY)
        assertTrue("map's right edge must still reach onto the screen", maxOf(minScreenX, maxScreenX) >= 0f)
        assertTrue("map's left edge must not clear the screen's right edge", minOf(minScreenX, maxScreenX) <= 400f)
        assertTrue("map's bottom edge must still reach onto the screen", maxOf(minScreenY, maxScreenY) >= 0f)
        assertTrue("map's top edge must not clear the screen's bottom edge", minOf(minScreenY, maxScreenY) <= 400f)
    }

    @Test
    fun `clampedToBounds keeps a zoomed-in map covering the viewport with no gaps`() {
        val bounds = GeoBounds(minX = 0f, minY = 0f, maxX = 10f, maxY = 10f)
        // Zoomed in well past a viewport-covering scale, then dragged so far it would otherwise
        // expose empty space beyond the map's edge (e.g. after zooming out from a corner).
        val zoomedIn = ViewTransform(scale = 200f, tx = 5000f, ty = 5000f)
        val clamped = zoomedIn.clampedToBounds(bounds, 400f, 400f)

        val (minScreenX, minScreenY) = clamped.toScreen(bounds.minX, bounds.minY)
        val (maxScreenX, maxScreenY) = clamped.toScreen(bounds.maxX, bounds.maxY)
        assertTrue("left edge should not expose empty space", minOf(minScreenX, maxScreenX) <= epsilon)
        assertTrue("right edge should not expose empty space", maxOf(minScreenX, maxScreenX) >= 400f - epsilon)
        assertTrue("top edge should not expose empty space", minOf(minScreenY, maxScreenY) <= epsilon)
        assertTrue("bottom edge should not expose empty space", maxOf(minScreenY, maxScreenY) >= 400f - epsilon)
    }

    @Test
    fun `clampedToBounds is a no-op for an already-fitted transform`() {
        val bounds = GeoBounds(minX = -5f, minY = -8f, maxX = 12f, maxY = 20f)
        val fitted = fitBounds(bounds, widthPx = 400f, heightPx = 400f, paddingPx = 0f, minSpan = 0f)

        val clamped = fitted.clampedToBounds(bounds, 400f, 400f)

        assertEquals(fitted.tx, clamped.tx, epsilon)
        assertEquals(fitted.ty, clamped.ty, epsilon)
    }
}
