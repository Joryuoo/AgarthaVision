package com.agarthavision.data.geo

import com.agarthavision.data.local.psgc.readBundledBarangays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Independently recomputes the town -> province-key join `build-town-keys.py` performed, from
 * the same two sources it reads (the bundled PSGC asset and
 * `tools/geo/geographic-province-overrides.csv`), and proves the *shipped binary asset* agrees
 * with that recomputation for every one of the 1,642 towns - not just the 34 override rows.
 *
 * This is the check [GeoDatasetIntegrityTest] does not do: that test proves the town-code set
 * matches and every town resolves to *some* province, but never proves the `provinceKey` value
 * itself is the one PSGC (or the overrides CSV) actually says it should be. A join bug that
 * swapped which override row applied to which town, or a stray digit typo in a real
 * `province_code`, would pass every existing test and still silently mis-shade the choropleth.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GeoJoinCorrectnessTest {

    @Test
    fun `every town's provinceKey in the shipped asset matches the independently recomputed join`() {
        val mismatches = mutableListOf<String>()
        expectedProvinceKeyByTown.forEach { (townCode, expectedKey) ->
            val actual = directory.towns[townCode]
            if (actual == null) {
                mismatches += "$townCode: missing from asset directory entirely"
            } else if (actual.provinceKey != expectedKey) {
                mismatches += "$townCode (${actual.name}): expected province key $expectedKey, " +
                    "got ${actual.provinceKey}"
            }
        }
        assertTrue(
            "province-key join mismatches found:\n" + mismatches.joinToString("\n"),
            mismatches.isEmpty(),
        )
        assertEquals(1_642, expectedProvinceKeyByTown.size)
        assertEquals(1_642, directory.towns.size)
    }

    @Test
    fun `overrides CSV has no rows for towns that actually have a real province_code`() {
        val realProvinceCodes = psgcBarangays
            .filter { it.provinceCode != null }
            .map { it.cityMuniCode }
            .toSet()

        overridesCsv.keys.forEach { townCode ->
            assertTrue(
                "$townCode appears in overrides CSV but the bundled PSGC data gives it a real province_code",
                townCode !in realProvinceCodes,
            )
        }
    }

    @Test
    fun `exactly 85 distinct province keys across the whole town directory, each resolvable`() {
        val distinctKeys = directory.towns.values.map { it.provinceKey }.toSet()
        assertEquals(GeoDataset.PROVINCE_UNIT_COUNT, distinctKeys.size)
        distinctKeys.forEach { key ->
            assertNotNull("province key $key has no entry in directory.provinces", directory.provinces[key])
        }
    }

    @Test
    fun `known towns decode to geographically plausible bounding boxes`() {
        // lon = x / cos(12.5deg), lat = -y (see GeoProjection). Rough real-world sanity check,
        // not a precision check - this exists purely to catch a join against the WRONG town's
        // geometry (e.g. Davao decoding to a bbox somewhere in the Sulu Sea).
        assertPlausible("Davao City", "1130700000", "1102400000", lonRange = 125.0..126.2, latRange = 6.8..7.7)
        assertPlausible("Cebu City", "0730600000", "0702200000", lonRange = 123.7..124.1, latRange = 10.2..10.5)
        assertPlausible("Manila", "1380600000", "1300000000", lonRange = 120.9..121.1, latRange = 14.5..14.7)
        // A small, unambiguous non-NCR, non-highly-urbanised Luzon town for contrast.
        assertPlausible("Basco (Batanes)", "0200901000", "0200900000", lonRange = 121.8..122.2, latRange = 20.3..20.6)
    }

    private fun assertPlausible(
        label: String,
        townCode: String,
        expectedProvinceKey: String,
        lonRange: ClosedRange<Double>,
        latRange: ClosedRange<Double>,
    ) {
        val ref = directory.towns[townCode]
        assertNotNull("$label ($townCode) missing from directory", ref)
        assertEquals("$label ($townCode) unexpected province key", expectedProvinceKey, ref!!.provinceKey)

        val byteRange = townsIndex.getValue(expectedProvinceKey)
        val townSet = BoundaryBinaryReader.readTowns(townsBytes, byteRange)
        val shape = townSet.byCode[townCode]
        assertNotNull("$label ($townCode) has no decoded geometry in province $expectedProvinceKey", shape)

        val cosRefLat = Math.cos(Math.toRadians(12.5))
        val minLon = shape!!.bounds.minX / cosRefLat
        val maxLon = shape.bounds.maxX / cosRefLat
        // y = -lat, and minY(screen) corresponds to maxLat.
        val maxLat = -shape.bounds.minY
        val minLat = -shape.bounds.maxY

        assertTrue(
            "$label lon bbox [$minLon, $maxLon] not within expected $lonRange",
            minLon >= lonRange.start - 0.3 && maxLon <= lonRange.endInclusive + 0.3,
        )
        assertTrue(
            "$label lat bbox [$minLat, $maxLat] not within expected $latRange",
            minLat >= latRange.start - 0.3 && maxLat <= latRange.endInclusive + 0.3,
        )
    }

    private companion object {
        private lateinit var psgcBarangays: List<com.agarthavision.data.local.entity.PsgcBarangayEntity>
        private lateinit var directory: com.agarthavision.domain.geo.AreaDirectory
        private lateinit var townsBytes: ByteArray
        private lateinit var townsIndex: Map<String, IntRange>
        private lateinit var overridesCsv: Map<String, String>
        private lateinit var expectedProvinceKeyByTown: Map<String, String>

        @BeforeClass
        @JvmStatic
        fun readOnce() {
            val context = RuntimeEnvironment.getApplication()
            psgcBarangays = readBundledBarangays(context)

            val provincesBytes = context.assets.open(GeoDataset.PROVINCES_ASSET_PATH).use { it.readBytes() }
            val (_, loadedDirectory) = BoundaryBinaryReader.readProvinces(provincesBytes, GeoDataset.VINTAGE)
            directory = loadedDirectory

            townsBytes = context.assets.open(GeoDataset.TOWNS_ASSET_PATH).use { it.readBytes() }
            townsIndex = BoundaryBinaryReader.readTownsIndex(townsBytes, GeoDataset.VINTAGE)

            overridesCsv = loadOverridesCsv()

            expectedProvinceKeyByTown = psgcBarangays
                .distinctBy { it.cityMuniCode }
                .associate { row ->
                    val key = row.provinceCode ?: overridesCsv[row.cityMuniCode]
                    ?: error("town ${row.cityMuniCode} has null province_code and no overrides CSV row")
                    row.cityMuniCode to key
                }
        }

        /** Repo-root-relative; Gradle's unit-test working directory is the `app/` module dir. */
        private fun loadOverridesCsv(): Map<String, String> {
            val candidates = listOf(
                File("../tools/geo/geographic-province-overrides.csv"),
                File("tools/geo/geographic-province-overrides.csv"),
            )
            val file = candidates.firstOrNull { it.exists() }
                ?: error(
                    "could not locate geographic-province-overrides.csv from working dir " +
                        File(".").absolutePath,
                )
            return file.readLines()
                .drop(1) // header
                .filter { it.isNotBlank() }
                .associate { line ->
                    val parts = line.split(",", limit = 3)
                    parts[0] to parts[1]
                }
        }
    }
}
