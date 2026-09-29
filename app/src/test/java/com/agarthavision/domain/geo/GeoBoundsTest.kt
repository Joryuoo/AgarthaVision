package com.agarthavision.domain.geo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoBoundsTest {

    @Test
    fun `intersects is true for overlapping boxes`() {
        val a = GeoBounds(minX = 0f, minY = 0f, maxX = 10f, maxY = 10f)
        val b = GeoBounds(minX = 5f, minY = 5f, maxX = 15f, maxY = 15f)

        assertTrue(a.intersects(b))
        assertTrue(b.intersects(a))
    }

    @Test
    fun `intersects is true for touching edges`() {
        val a = GeoBounds(minX = 0f, minY = 0f, maxX = 10f, maxY = 10f)
        val b = GeoBounds(minX = 10f, minY = 0f, maxX = 20f, maxY = 10f)

        assertTrue(a.intersects(b))
    }

    @Test
    fun `intersects is true when one box contains the other`() {
        val outer = GeoBounds(minX = 0f, minY = 0f, maxX = 100f, maxY = 100f)
        val inner = GeoBounds(minX = 40f, minY = 40f, maxX = 60f, maxY = 60f)

        assertTrue(outer.intersects(inner))
        assertTrue(inner.intersects(outer))
    }

    @Test
    fun `intersects is false for boxes separated on the x axis`() {
        val a = GeoBounds(minX = 0f, minY = 0f, maxX = 10f, maxY = 10f)
        val b = GeoBounds(minX = 20f, minY = 0f, maxX = 30f, maxY = 10f)

        assertFalse(a.intersects(b))
        assertFalse(b.intersects(a))
    }

    @Test
    fun `intersects is false for boxes separated on the y axis`() {
        val a = GeoBounds(minX = 0f, minY = 0f, maxX = 10f, maxY = 10f)
        val b = GeoBounds(minX = 0f, minY = 20f, maxX = 10f, maxY = 30f)

        assertFalse(a.intersects(b))
    }
}
