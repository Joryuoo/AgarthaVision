package com.agarthavision.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HitTestTest {

    /** A simple 10x10 square, (0,0) to (10,10). */
    private val square = floatArrayOf(0f, 0f, 10f, 0f, 10f, 10f, 0f, 10f)

    @Test
    fun `point inside a simple convex polygon hits`() {
        assertTrue(pointInRings(5f, 5f, listOf(square)))
    }

    @Test
    fun `point outside a simple convex polygon misses`() {
        assertFalse(pointInRings(15f, 15f, listOf(square)))
    }

    @Test
    fun `point inside a hole does not hit`() {
        // Exterior 0..10 square, hole 4..6 square in the middle.
        val hole = floatArrayOf(4f, 4f, 6f, 4f, 6f, 6f, 4f, 6f)
        val rings = listOf(square, hole)

        assertTrue("point outside the hole but inside the exterior should hit", pointInRings(1f, 1f, rings))
        assertFalse("point inside the hole should not hit", pointInRings(5f, 5f, rings))
    }

    @Test
    fun `multi-part (disjoint) area hits either part`() {
        val partA = square // 0..10, 0..10
        val partB = floatArrayOf(20f, 20f, 30f, 20f, 30f, 30f, 20f, 30f) // 20..30, 20..30
        val rings = listOf(partA, partB)

        assertTrue("point in part A should hit", pointInRings(5f, 5f, rings))
        assertTrue("point in part B should hit", pointInRings(25f, 25f, rings))
        assertFalse("point in neither part should miss", pointInRings(15f, 15f, rings))
    }

    @Test
    fun `hitTest bbox-prefilters and misses fast when clearly outside every area`() {
        val areaA = AreaShape(
            code = "A",
            name = "A",
            parentCode = "P",
            bounds = GeoBounds(0f, 0f, 10f, 10f),
            labelX = 5f,
            labelY = 5f,
            rings = listOf(square),
        )
        val areaB = AreaShape(
            code = "B",
            name = "B",
            parentCode = "P",
            bounds = GeoBounds(20f, 20f, 30f, 30f),
            labelX = 25f,
            labelY = 25f,
            rings = listOf(floatArrayOf(20f, 20f, 30f, 20f, 30f, 30f, 20f, 30f)),
        )
        val set = BoundarySet(listOf(areaA, areaB), GeoBounds(0f, 0f, 30f, 30f))

        assertEquals("A", hitTest(set, 5f, 5f)?.code)
        assertEquals("B", hitTest(set, 25f, 25f)?.code)
        assertNull("far outside every bbox should miss", hitTest(set, 1000f, 1000f))
    }
}
