package com.agarthavision.data.geo

import com.agarthavision.data.local.psgc.readBundledBarangays
import com.agarthavision.domain.geo.IslandGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Structural sweep of the real committed boundary assets, mirroring
 * [com.agarthavision.data.local.psgc.PsgcDatasetIntegrityTest]'s role for the PSGC asset:
 * [GeoAssetPackagingTest] proves the files reach the device; this proves what is inside them
 * is internally consistent and matches the bundled PSGC dataset it was joined against.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GeoDatasetIntegrityTest {

    @Test
    fun `the town directory exactly matches the bundled PSGC city_muni_code set`() {
        val psgcTownCodes = psgcBarangays.map { it.cityMuniCode }.toSet()
        val geoTownCodes = directory.towns.keys

        assertEquals(GeoDataset.TOWN_COUNT, psgcTownCodes.size)
        assertEquals(GeoDataset.TOWN_COUNT, geoTownCodes.size)
        assertEquals(
            "the boundary town directory must exactly match the bundled PSGC town set",
            psgcTownCodes,
            geoTownCodes,
        )
    }

    @Test
    fun `exactly 85 province-level units`() {
        assertEquals(GeoDataset.PROVINCE_UNIT_COUNT, provinces.areas.size)
        assertEquals(GeoDataset.PROVINCE_UNIT_COUNT, directory.provinces.size)
    }

    @Test
    fun `every province's region code resolves to an island group`() {
        directory.provinces.values.forEach { province ->
            // Throws IllegalArgumentException on an unknown prefix, which fails the test.
            IslandGroup.fromRegionCode(province.regionCode)
        }
    }

    @Test
    fun `every province has at least one ring with 3 or more points`() {
        provinces.areas.forEach { area ->
            assertTrue(
                "province ${area.code} (${area.name}) has no ring with >= 3 points",
                area.rings.any { it.size / 2 >= 3 },
            )
        }
    }

    @Test
    fun `every town with geometry has at least one ring with 3 or more points`() {
        val townsWithGeometry = directory.towns.values.filter { it.hasGeometry }
        assertTrue(townsWithGeometry.isNotEmpty())

        val checked = HashSet<String>()
        directory.provinces.keys.forEach { provinceKey ->
            val townSet = BoundaryBinaryReader.readTowns(townsBytes, townsIndex.getValue(provinceKey))
            townSet.areas.forEach { town ->
                checked += town.code
                assertTrue(
                    "town ${town.code} (${town.name}) has no ring with >= 3 points",
                    town.rings.any { it.size / 2 >= 3 },
                )
            }
        }
        assertEquals(
            "every town with geometry should have been visited via its province's town set",
            townsWithGeometry.map { it.code }.toSet(),
            checked,
        )
    }

    @Test
    fun `every town's bbox falls within its province's bbox (small epsilon for simplification)`() {
        val epsilon = 5f // projected map units - generous given quantization + simplification drift

        directory.provinces.keys.forEach { provinceKey ->
            val provinceBounds = provinces.byCode.getValue(provinceKey).bounds
            val townSet = BoundaryBinaryReader.readTowns(townsBytes, townsIndex.getValue(provinceKey))

            townSet.areas.forEach { town ->
                assertTrue(
                    "town ${town.code} minX out of province ${provinceKey} bounds",
                    town.bounds.minX >= provinceBounds.minX - epsilon,
                )
                assertTrue(
                    "town ${town.code} maxX out of province ${provinceKey} bounds",
                    town.bounds.maxX <= provinceBounds.maxX + epsilon,
                )
                assertTrue(
                    "town ${town.code} minY out of province ${provinceKey} bounds",
                    town.bounds.minY >= provinceBounds.minY - epsilon,
                )
                assertTrue(
                    "town ${town.code} maxY out of province ${provinceKey} bounds",
                    town.bounds.maxY <= provinceBounds.maxY + epsilon,
                )
            }
        }
    }

    private companion object {
        private lateinit var psgcBarangays: List<com.agarthavision.data.local.entity.PsgcBarangayEntity>
        private lateinit var provinces: com.agarthavision.domain.geo.BoundarySet
        private lateinit var directory: com.agarthavision.domain.geo.AreaDirectory
        private lateinit var townsBytes: ByteArray
        private lateinit var townsIndex: Map<String, IntRange>

        @BeforeClass
        @JvmStatic
        fun readOnce() {
            val context = RuntimeEnvironment.getApplication()
            psgcBarangays = readBundledBarangays(context)

            val provincesBytes = context.assets.open(GeoDataset.PROVINCES_ASSET_PATH).use { it.readBytes() }
            val (loadedProvinces, loadedDirectory) =
                BoundaryBinaryReader.readProvinces(provincesBytes, GeoDataset.VINTAGE)
            provinces = loadedProvinces
            directory = loadedDirectory

            townsBytes = context.assets.open(GeoDataset.TOWNS_ASSET_PATH).use { it.readBytes() }
            townsIndex = BoundaryBinaryReader.readTownsIndex(townsBytes, GeoDataset.VINTAGE)
        }
    }
}
