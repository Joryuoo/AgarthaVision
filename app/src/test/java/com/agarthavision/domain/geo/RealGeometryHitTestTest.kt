package com.agarthavision.domain.geo

import com.agarthavision.data.geo.BoundaryBinaryReader
import com.agarthavision.data.geo.GeoDataset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * [HitTestTest] only exercises synthetic axis-aligned squares, which cannot catch a
 * winding/orientation bug (all-CW vs all-CCW, or an accidentally reversed exterior/hole) that
 * only shows up on real, irregular ring data. This decodes the real committed towns asset
 * directly off disk - no Robolectric needed, since it is a plain file under `assets/` and
 * [BoundaryBinaryReader] is pure JVM - and hit-tests real Cebu City / Mandaue geometry.
 */
class RealGeometryHitTestTest {

    @Test
    fun `a known point inside Cebu City's real geometry hits Cebu City and not neighbouring Mandaue`() {
        val townsBytes = File("src/main/assets/geo/ph-towns-q2_2026.bin").readBytes()
        val index = BoundaryBinaryReader.readTownsIndex(townsBytes, GeoDataset.VINTAGE)
        val cebuProvinceKey = "0702200000" // Cebu (see tools/geo/geographic-province-overrides.csv)
        val townSet = BoundaryBinaryReader.readTowns(townsBytes, index.getValue(cebuProvinceKey))

        val cebuCity = townSet.byCode.getValue("0730600000")
        val mandaue = townSet.byCode.getValue("0731300000")

        // Centroid of Cebu City's largest ring in real lon/lat (123.8664 E, 10.3585 N),
        // independently confirmed (offline) to fall inside Cebu City's polygon and outside
        // Mandaue's - see the point-in-polygon spot check this test encodes in Kotlin form.
        val x = GeoProjection.x(123.86641002760999)
        val y = GeoProjection.y(10.358504259666649)

        assertEquals("expected the point to hit Cebu City", "0730600000", hitTest(townSet, x, y)?.code)
        assertEquals(
            "point-in-polygon against Cebu City's real rings should be true",
            true,
            pointInRings(x, y, cebuCity.rings),
        )
        assertEquals(
            "the same point must not also register inside neighbouring Mandaue - a" +
                " winding/orientation bug would show up here",
            false,
            pointInRings(x, y, mandaue.rings),
        )
    }

    @Test
    fun `a point far outside any real town in Cebu misses entirely`() {
        val townsBytes = File("src/main/assets/geo/ph-towns-q2_2026.bin").readBytes()
        val index = BoundaryBinaryReader.readTownsIndex(townsBytes, GeoDataset.VINTAGE)
        val townSet = BoundaryBinaryReader.readTowns(townsBytes, index.getValue("0702200000"))

        // Deep in the Pacific, nowhere near any town in the province.
        val x = GeoProjection.x(130.0)
        val y = GeoProjection.y(10.0)

        assertNull(hitTest(townSet, x, y))
    }
}
