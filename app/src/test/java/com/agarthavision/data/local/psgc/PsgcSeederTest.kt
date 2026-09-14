package com.agarthavision.data.local.psgc

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import com.agarthavision.core.database.AgarthaDatabase
import com.agarthavision.data.local.entity.PsgcBarangayEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * End-to-end test of the bundled PSGC dataset: the real asset, through gzip and the parser,
 * into real SQLite, then queried the way the picker queries it.
 *
 * Runs under Robolectric so it lands in `:app:testDebugUnitTest` — no emulator. It reads
 * the asset that actually ships, so a regenerated dataset with a broken shape fails here
 * rather than in a medtech's hands. Nothing in this path touches the network, which is the
 * offline requirement made structural.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PsgcSeederTest {

    private lateinit var database: AgarthaDatabase
    private lateinit var settings: FakeSettingsDataStore

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        database = Room
            .inMemoryDatabaseBuilder(context, AgarthaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settings = FakeSettingsDataStore()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `seeds the bundled dataset and keeps PSGC's real shapes intact`() = runTest {
        val inserted = seeder().seedIfNeeded()

        assertEquals(BUNDLED_BARANGAYS, inserted)
        assertEquals(BUNDLED_BARANGAYS, dao().count())

        // A barangay in a province: every field populated.
        val adams = dao().getByCode("0102801001")
        assertNotNull(adams)
        assertEquals("Adams", adams!!.name)
        assertEquals("Ilocos Norte", adams.provinceName)

        // A chartered city has no province — PSGC's shape, not missing data.
        val cebu = dao().getByCode("0730600001")
        assertNotNull(cebu)
        assertEquals("City of Cebu", cebu!!.cityMuniName)
        assertNull(cebu.provinceName)

        // Manila rolls up to the chartered city, with the sub-municipality kept searchable.
        val manila = dao().getByCode("1380601001")
        assertNotNull(manila)
        assertEquals("City of Manila", manila!!.cityMuniName)
        assertTrue(manila.searchText.contains("tondo"))

        // Codes are stored zero-padded, which is what the map's boundary join needs.
        assertTrue(dao().count() > 0)
        assertNull(dao().getByCode("102801001"))
    }

    @Test
    fun `carries the reorganisations that the 4Q 2023 vintage got wrong`() = runTest {
        seeder().seedIfNeeded()

        // These two are why the vintage moved. Under 4Q 2023 Bacolod sat in Region VI and
        // Sulu in BARMM; PSA has since moved 1,763 barangays between regions, and a
        // surveillance map aggregating by region would have filed every one of them wrong.
        val bacolod = dao().getByCode("1830200001")
        assertNotNull(bacolod)
        assertEquals("City of Bacolod", bacolod!!.cityMuniName)
        assertEquals("Negros Island Region (NIR)", bacolod.regionName)

        val sulu = dao().getByCode("0906601001")
        assertNotNull(sulu)
        assertEquals("Sulu", sulu!!.provinceName)
        assertEquals("Region IX (Zamboanga Peninsula)", sulu.regionName)

        // The codes those two replaced are retired and must not resolve.
        assertNull(dao().getByCode("0630200001"))
    }

    @Test
    fun `finds barangays the way a medtech would type them`() = runTest {
        seeder().seedIfNeeded()

        // Word order does not matter, which matters because PSA spells it "City of Cebu".
        val lahug = search("lahug cebu")
        assertEquals(1, lahug.size)
        assertEquals("Lahug", lahug.single().name)
        assertEquals("City of Cebu", lahug.single().cityMuniName)

        // A contiguous match on how people actually type it would find nothing.
        assertTrue(search("cebu city").isNotEmpty())

        // The sub-municipality is searchable even though it is not displayed.
        assertTrue(search("manila tondo").all { it.cityMuniName == "City of Manila" })

        // Wildcards are literals, not "every barangay in the country".
        assertTrue(search("%").isEmpty())

        // The cap holds for a query that matches thousands.
        assertEquals(RESULT_LIMIT, search("san").size)
    }

    @Test
    fun `does not reseed when the device already holds this vintage`() = runTest {
        settings.state.value = mutablePreferencesOf(SEEDED_VINTAGE to PsgcDataset.VINTAGE)
        dao().insertAll(listOf(placeholder))

        val inserted = seeder().seedIfNeeded()

        assertEquals(0, inserted)
        assertEquals(1, dao().count())
    }

    @Test
    fun `reseeds when a destructive migration emptied the table`() = runTest {
        // The recorded vintage alone would wrongly report this device as seeded.
        settings.state.value = mutablePreferencesOf(SEEDED_VINTAGE to PsgcDataset.VINTAGE)

        val inserted = seeder().seedIfNeeded()

        assertEquals(BUNDLED_BARANGAYS, inserted)
    }

    @Test
    fun `reseeds and replaces the table when the vintage changed`() = runTest {
        settings.state.value = mutablePreferencesOf(SEEDED_VINTAGE to "1q2019")
        dao().insertAll(listOf(placeholder))

        val inserted = seeder().seedIfNeeded()

        assertEquals(BUNDLED_BARANGAYS, inserted)
        assertNull(dao().getByCode(placeholder.code))
        assertEquals(PsgcDataset.VINTAGE, settings.state.value[SEEDED_VINTAGE])
    }

    private fun dao() = database.psgcBarangayDao()

    private fun seeder() = PsgcSeeder(
        context = RuntimeEnvironment.getApplication(),
        database = database,
        barangayDao = dao(),
        settings = settings,
    )

    private suspend fun search(query: String) =
        dao().search(terms = PsgcSearchQuery.terms(query), limit = RESULT_LIMIT)

    private val placeholder = PsgcBarangayEntity(
        code = "9999999999",
        name = "Stale",
        cityMuniCode = "9999999000",
        cityMuniName = "Stale City",
        provinceCode = null,
        provinceName = null,
        regionCode = "9900000000",
        regionName = "Stale Region",
        searchText = "stale stale city",
    )

    private companion object {
        /** The asset that ships today — see `tools/psgc/README.md`. */
        private const val BUNDLED_BARANGAYS = PsgcDataset.BARANGAY_COUNT

        private const val RESULT_LIMIT = 50

        private val SEEDED_VINTAGE = stringPreferencesKey("psgc_seeded_vintage")
    }
}

private class FakeSettingsDataStore : DataStore<Preferences> {
    val state = MutableStateFlow(emptyPreferences())

    override val data: Flow<Preferences> = state.map { it }

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}
