package com.agarthavision.ui.coverage

import androidx.compose.ui.geometry.Offset
import com.agarthavision.domain.geo.ViewTransform
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [nextCameraTransform] is the pure math behind `CoverageMap`'s pinch/pan gesture handling in
 * [MyCoverageScreen] — extracted so the anchored-zoom behavior is checkable without Compose
 * gesture-simulation infrastructure.
 */
class MyCoverageCameraTest {

    private val identity = ViewTransform(scale = 1f, tx = 0f, ty = 0f)

    @Test
    fun `zooming in at the origin leaves the origin's map point fixed on screen`() {
        // The map point under screen (0, 0) is (0, 0) at identity transform.
        val next = nextCameraTransform(
            current = identity,
            centroid = Offset(0f, 0f),
            pan = Offset.Zero,
            zoom = 2f,
            minScale = 0.1f,
            maxScale = 100f,
        )

        assertEquals(2f, next.scale, 0.0001f)
        // Re-projecting map (0,0) through the new transform must still land on screen (0,0).
        val (screenX, screenY) = next.toScreen(0f, 0f)
        assertEquals(0f, screenX, 0.0001f)
        assertEquals(0f, screenY, 0.0001f)
    }

    @Test
    fun `zooming toward a non-origin centroid keeps the map point under it fixed`() {
        val current = ViewTransform(scale = 2f, tx = 10f, ty = -5f)
        val centroid = Offset(150f, 80f)
        val mapUnderCentroidBefore = current.toMap(centroid.x, centroid.y)

        val next = nextCameraTransform(
            current = current,
            centroid = centroid,
            pan = Offset.Zero,
            zoom = 3f,
            minScale = 0.1f,
            maxScale = 100f,
        )

        val screenAfter = next.toScreen(mapUnderCentroidBefore.first, mapUnderCentroidBefore.second)
        assertEquals(centroid.x, screenAfter.first, 0.001f)
        assertEquals(centroid.y, screenAfter.second, 0.001f)
    }

    @Test
    fun `pan is added on top of the zoom-anchored translation`() {
        val next = nextCameraTransform(
            current = identity,
            centroid = Offset(50f, 50f),
            pan = Offset(20f, -30f),
            zoom = 1f,
            minScale = 0.1f,
            maxScale = 100f,
        )

        // zoom == 1 means no scale change, so translation should shift by exactly `pan`.
        assertEquals(20f, next.tx, 0.0001f)
        assertEquals(-30f, next.ty, 0.0001f)
    }

    @Test
    fun `scale is clamped to the lower bound`() {
        val next = nextCameraTransform(
            current = identity,
            centroid = Offset.Zero,
            pan = Offset.Zero,
            zoom = 0.01f,
            minScale = 0.8f,
            maxScale = 40f,
        )

        assertEquals(0.8f, next.scale, 0.0001f)
    }

    @Test
    fun `scale is clamped to the upper bound`() {
        val next = nextCameraTransform(
            current = identity,
            centroid = Offset.Zero,
            pan = Offset.Zero,
            zoom = 1000f,
            minScale = 0.8f,
            maxScale = 40f,
        )

        assertEquals(40f, next.scale, 0.0001f)
    }

    @Test
    fun `when the requested zoom is clamped away, the anchor still holds using the applied ratio`() {
        // Requesting a huge zoom that gets clamped down must not drag translation as if the
        // full raw `zoom` had been applied — using the raw ratio here would drift the map every
        // frame the pinch is held past the limit.
        val current = ViewTransform(scale = 39f, tx = 5f, ty = 5f)
        val centroid = Offset(100f, 100f)
        val mapUnderCentroidBefore = current.toMap(centroid.x, centroid.y)

        val next = nextCameraTransform(
            current = current,
            centroid = centroid,
            pan = Offset.Zero,
            zoom = 5f, // would ask for scale 195, clamped to 40
            minScale = 0.8f,
            maxScale = 40f,
        )

        assertEquals(40f, next.scale, 0.0001f)
        val screenAfter = next.toScreen(mapUnderCentroidBefore.first, mapUnderCentroidBefore.second)
        assertEquals(centroid.x, screenAfter.first, 0.001f)
        assertEquals(centroid.y, screenAfter.second, 0.001f)
    }
}
