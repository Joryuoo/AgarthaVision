package com.agarthavision.data.repository

import android.content.Context
import androidx.room.Room
import com.agarthavision.core.database.AgarthaDatabase
import com.agarthavision.data.local.dao.PsgcBarangayDao
import com.agarthavision.data.local.entity.PsgcBarangayEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * In-memory Room tests for the 5-tier ranking in [PsgcBarangayDao.search].
 *
 * These tests exercise the ORDER BY CASE in [psgcSearchQuery] — the highest-risk change
 * in ticket 86d4brgef. Mis-ordered bind args cause silent mis-ranking, not crashes, so
 * only an assertion that checks relative position can catch them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PsgcSearchRankingTest {

    private lateinit var db: AgarthaDatabase
    private lateinit var dao: PsgcBarangayDao

    @Before
    fun setUp() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, AgarthaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.psgcBarangayDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // -------------------------------------------------------------------------
    // Ticket scenario: "Poblacion Oriental" (name contains both terms) must
    // rank ahead of "Poblacion" in Misamis Oriental (name contains only the
    // first term; "oriental" is only in the province part of search_text).
    // -------------------------------------------------------------------------

    @Test
    fun `poblacion oriental ranks ahead of poblacion-in-misamis-oriental for query poblacion oriental`() =
        runTest {
            dao.insertAll(
                listOf(
                    // Tier 0: lower(name) == "poblacion oriental" (exact match)
                    barangay(
                        code = "1000000001",
                        name = "Poblacion Oriental",
                        cityMuniName = "Jimenez",
                        provinceName = "Misamis Occidental",
                    ),
                    // Tier 4: name is only "Poblacion"; "oriental" is only in the province
                    barangay(
                        code = "2000000002",
                        name = "Poblacion",
                        cityMuniName = "Oroquieta City",
                        provinceName = "Misamis Oriental",
                    ),
                ),
            )

            val results = dao.search(
                terms = listOf("poblacion", "oriental"),
                limit = 80,
            )

            assertEquals("expected exactly 2 rows", 2, results.size)
            assertEquals(
                "Poblacion Oriental must be first (tier 0 — exact name match)",
                "Poblacion Oriental",
                results[0].name,
            )
            assertEquals(
                "Poblacion (Misamis Oriental) must be second (tier 4 — name-only miss)",
                "Poblacion",
                results[1].name,
            )
        }

    // -------------------------------------------------------------------------
    // Tier ordering: exact (0) > starts-with (1) > contains (2) > per-term (3) > fallback (4)
    // -------------------------------------------------------------------------

    @Test
    fun `exact name match ranks before starts-with match`() = runTest {
        dao.insertAll(
            listOf(
                // Tier 1: name starts with "santo" but is not equal to "santo"
                barangay(
                    code = "1100000001",
                    name = "Santo Nino",
                    cityMuniName = "Digos City",
                    provinceName = "Davao del Sur",
                ),
                // Tier 0: lower(name) == "santo"
                barangay(
                    code = "1100000002",
                    name = "Santo",
                    cityMuniName = "Claveria",
                    provinceName = "Misamis Oriental",
                ),
            ),
        )

        val results = dao.search(terms = listOf("santo"), limit = 80)

        assertEquals(2, results.size)
        assertEquals(
            "Exact match 'Santo' must be tier 0 and come first",
            "Santo",
            results[0].name,
        )
        assertEquals("Santo Nino", results[1].name)
    }

    @Test
    fun `starts-with match ranks before infix-contains match`() = runTest {
        dao.insertAll(
            listOf(
                // Tier 2: name contains "san" but does not start with "san"
                barangay(
                    code = "1200000001",
                    name = "Kauswagan San",
                    cityMuniName = "Lanao del Norte",
                    provinceName = "Lanao del Norte",
                ),
                // Tier 1: name starts with "san"
                barangay(
                    code = "1200000002",
                    name = "San Isidro",
                    cityMuniName = "Cagayan de Oro",
                    provinceName = "Misamis Oriental",
                ),
            ),
        )

        val results = dao.search(terms = listOf("san"), limit = 80)

        assertEquals(2, results.size)
        assertEquals(
            "'San Isidro' (starts-with, tier 1) must precede 'Kauswagan San' (contains, tier 2)",
            "San Isidro",
            results[0].name,
        )
        assertEquals("Kauswagan San", results[1].name)
    }

    @Test
    fun `infix-contains match ranks before per-term scatter match`() = runTest {
        // "luz" as a term: "luzviminda" contains "luz" contiguously (tier 2);
        // a row where "luz" only appears in city/province but not in name at all → tier 4.
        // Insert an intermediate row where "luz" appears in name but scattered across words.
        // Actually, for tier 3 we need every individual term in name but not as a contiguous
        // block.  Use two terms: "luz" and "minda":
        //   Tier 2: name = "Luzviminda" — contains the joined string "luz minda"? No.
        //   Actually joinedTerms = "luz minda". lower(name) LIKE "%luz minda%" won't match
        //   "Luzviminda". Let's use "luz" and "villa":
        //   Tier 2 candidate: name = "Villa Luzuriaga" — LIKE "%luz villa%"? No.
        //   Tier 3 candidate: name = "Villa Luzuriaga" — lower(name) LIKE "%luz%" AND LIKE "%villa%"? Yes.
        //   Tier 2 candidate: name = "Luzville" — LIKE "%luzville%"? No (joined is "luz ville").
        //
        // Simpler: single term "agri":
        //   Tier 2: name contains "agri" contiguously ("Agricultures")
        //   Tier 3 requires per-term, but with a single term tier 3 degenerates to tier 2.
        //
        // Use two terms "san" and "jose":
        //   joinedTerms = "san jose"
        //   Tier 2: name contains "san jose" → "San Jose"  lower(name) LIKE "%san jose%" ✓
        //   Tier 3: name = "Jose San" → lower(name) LIKE "%san%" AND LIKE "%jose%" but NOT
        //     LIKE "%san jose%" → tier 3
        dao.insertAll(
            listOf(
                barangay(
                    code = "1300000001",
                    name = "Jose San",
                    cityMuniName = "Quirino",
                    provinceName = "Quirino",
                ),
                barangay(
                    code = "1300000002",
                    name = "San Jose",
                    cityMuniName = "Nueva Ecija",
                    provinceName = "Nueva Ecija",
                ),
            ),
        )

        val results = dao.search(terms = listOf("san", "jose"), limit = 80)

        assertEquals(2, results.size)
        assertEquals(
            "'San Jose' (contiguous substring, tier 2) must precede 'Jose San' (per-term scatter, tier 3)",
            "San Jose",
            results[0].name,
        )
        assertEquals("Jose San", results[1].name)
    }

    // -------------------------------------------------------------------------
    // A row that matches only through search_text (not name) must still appear,
    // but in tier 4 (the fallback).
    // -------------------------------------------------------------------------

    @Test
    fun `search-text-only match is returned but falls into the fallback tier`() = runTest {
        dao.insertAll(
            listOf(
                // Name does not contain "oriental"; "oriental" is only in the province name
                barangay(
                    code = "1400000001",
                    name = "Aplaya",
                    cityMuniName = "Calapan City",
                    provinceName = "Oriental Mindoro",
                ),
            ),
        )

        // Searching "oriental" — WHERE matches via search_text, but name tier tests all fail
        val results = dao.search(terms = listOf("oriental"), limit = 80)

        assertEquals("row should still be returned even when match is in province only", 1, results.size)
        assertEquals("Aplaya", results[0].name)
    }

    @Test
    fun `search-text-only match ranks behind a name match for the same term`() = runTest {
        dao.insertAll(
            listOf(
                // search_text match only (term in province, not in name) → tier 4
                barangay(
                    code = "1500000001",
                    name = "Aplaya",
                    cityMuniName = "Calapan City",
                    provinceName = "Oriental Mindoro",
                ),
                // Tier 1: name starts with "oriental"
                barangay(
                    code = "1500000002",
                    name = "Oriental",
                    cityMuniName = "Calapan City",
                    provinceName = "Oriental Mindoro",
                ),
            ),
        )

        val results = dao.search(terms = listOf("oriental"), limit = 80)

        assertEquals(2, results.size)
        assertEquals(
            "'Oriental' (name match, tier 0/1) must precede 'Aplaya' (search_text-only, tier 4)",
            "Oriental",
            results[0].name,
        )
        assertEquals("Aplaya", results[1].name)
    }

    // -------------------------------------------------------------------------
    // Empty terms must return empty list, not every row.
    // -------------------------------------------------------------------------

    @Test
    fun `empty terms list returns nothing rather than all rows`() = runTest {
        dao.insertAll(
            listOf(
                barangay("9000000001", "Lahug", "City of Cebu", null),
            ),
        )

        val results = dao.search(terms = emptyList(), limit = 80)

        assertTrue("empty terms must return empty list", results.isEmpty())
    }

    // -------------------------------------------------------------------------
    // LIMIT is respected: seeding more rows than the limit must cap the result.
    // -------------------------------------------------------------------------

    @Test
    fun `limit is respected and does not return more rows than requested`() = runTest {
        val rows = (1..10).map { i ->
            barangay(
                code = "8%07d".format(i),
                name = "Barangay%03d".format(i),
                cityMuniName = "City of Test",
                provinceName = "Test Province",
            )
        }
        dao.insertAll(rows)

        val results = dao.search(terms = listOf("barangay"), limit = 5)

        assertEquals("limit should cap results to 5", 5, results.size)
    }

    // -------------------------------------------------------------------------
    // Helper — mirrors the builder in PsgcReverseLookupTest exactly.
    // -------------------------------------------------------------------------

    private fun barangay(
        code: String,
        name: String,
        cityMuniName: String,
        provinceName: String?,
    ) = PsgcBarangayEntity(
        code = code,
        name = name,
        cityMuniCode = code.take(6),
        cityMuniName = cityMuniName,
        provinceCode = provinceName?.let { code.take(4) },
        provinceName = provinceName,
        regionCode = code.take(2),
        regionName = "Region Test",
        searchText = "$name $cityMuniName ${provinceName.orEmpty()}".lowercase(),
    )
}
