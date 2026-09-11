package com.agarthavision.data.local.psgc

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

/**
 * Structural sweep of every row in the bundled dataset.
 *
 * [PsgcSeederTest] proves the seeding path works and spot-checks three known barangays. That
 * would not notice a regenerated asset that silently truncated a region, duplicated a code or
 * shifted a column on the other 41,998 rows — and a wrong `city_muni_code` is invisible in the
 * picker but breaks the Admin Website's rollup. These assertions cover the whole file.
 *
 * Parsed once for the class: 42,001 rows through the real parser is not free.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PsgcDatasetIntegrityTest {

    @Test
    fun `every barangay in the vintage is present`() {
        assertEquals(PsgcDataset.BARANGAY_COUNT, rows.size)
    }

    @Test
    fun `every code is a unique zero-padded 10-digit PSGC`() {
        // The Postgres CHECK on sessions.psgc_barangay_code is `^[0-9]{10}$`
        // (0010_session_psgc_barangay.sql). A code that fails here is one the app could write
        // and the database would then reject on sync, which surfaces far from the cause.
        val malformed = rows.filterNot { it.code.length == 10 && it.code.all(Char::isDigit) }
        assertTrue("Codes that are not 10 digits: ${malformed.take(5).map { it.code }}", malformed.isEmpty())

        val duplicates = rows.groupBy { it.code }.filterValues { it.size > 1 }.keys
        assertTrue("Duplicate PSGC codes: ${duplicates.take(5)}", duplicates.isEmpty())
    }

    @Test
    fun `every barangay code sits under its own city's code`() {
        // PSGC is hierarchical and the map keys on the barangay code alone, resolving upward
        // through it — so a row whose code does not sit beneath its recorded city would
        // aggregate into the wrong unit on the choropleth while looking perfectly fine in the
        // picker.
        //
        // Compared on the first five digits (region + province) rather than seven, because
        // Manila's 897 barangays sit under a sub-municipality: their city_muni_code is the
        // chartered city while digits 6-7 of the barangay code are the sub-municipality, so a
        // seven-digit comparison would fail for all of them by design. See tools/psgc/README.md.
        val detached = rows.filterNot { it.code.take(5) == it.cityMuniCode.take(5) }
        assertTrue(
            "Barangays whose code does not sit under their city/municipality: " +
                detached.take(5).map { "${it.name} ${it.code} vs ${it.cityMuniCode}" },
            detached.isEmpty(),
        )
    }

    @Test
    fun `Manila's sub-municipality rows keep the shape the generator documents`() {
        // Guards the exception above rather than leaving it as prose: these rows are the
        // reason the check is five digits wide, so a regeneration that flattened them would
        // otherwise silently loosen that test.
        val manila = rows.filter { it.cityMuniName.contains("Manila", ignoreCase = true) }
        assertEquals(897, manila.size)
        assertTrue(
            "Manila barangays should sit under a sub-municipality, not directly under the city.",
            manila.all { it.code.take(7) != it.cityMuniCode.take(7) },
        )
    }

    @Test
    fun `all seventeen regions of the vintage are represented`() {
        // 4Q 2023, so seventeen — the Negros Island Region was created in 2Q 2024 and is
        // deliberately absent. See docs/map/objects/PsgcBarangay.md on the vintage pin.
        assertEquals(17, rows.map { it.regionName }.distinct().size)
    }

    @Test
    fun `no row is missing the names the picker renders`() {
        // Province is legitimately null for highly urbanised cities; name and city never are.
        val nameless = rows.filter { it.name.isBlank() || it.cityMuniName.isBlank() }
        assertTrue("Rows missing a name or city: ${nameless.take(5).map { it.code }}", nameless.isEmpty())
    }

    @Test
    fun `search text is lowercased so the SQL LIKE can match it`() {
        // PsgcSearchQuery lowercases the query in Kotlin rather than using SQL lower(), which
        // is ASCII-only and would not fold the 'ñ' in 439 barangay names. That only works if
        // the stored haystack is lowercased the same way.
        val notFolded = rows.filter { it.searchText != it.searchText.lowercase() }
        assertTrue("Rows with non-lowercased search text: ${notFolded.take(5).map { it.code }}", notFolded.isEmpty())
    }

    private companion object {
        private lateinit var rows: List<com.agarthavision.data.local.entity.PsgcBarangayEntity>

        @BeforeClass
        @JvmStatic
        fun parseOnce() {
            val context: Context = RuntimeEnvironment.getApplication()
            rows = context.assets.open(PsgcDataset.ASSET_PATH).use { asset ->
                InputStreamReader(GZIPInputStream(asset), Charsets.UTF_8).buffered().useLines { lines ->
                    PsgcCsvParser.parseLines(lines).toList()
                }
            }
        }
    }
}
