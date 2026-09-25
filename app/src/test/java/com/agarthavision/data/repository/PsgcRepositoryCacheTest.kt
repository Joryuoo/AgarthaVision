package com.agarthavision.data.repository

import androidx.sqlite.db.SupportSQLiteQuery
import com.agarthavision.data.local.dao.PsgcBarangayDao
import com.agarthavision.data.local.entity.PsgcBarangayEntity
import com.agarthavision.domain.model.PsgcBarangay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the in-memory LRU cache inside [PsgcRepositoryImpl].
 *
 * Uses a hand-written fake DAO so there is no Room / Robolectric dependency here — the
 * cache is pure Kotlin logic and does not need a database to be tested.
 */
class PsgcRepositoryCacheTest {

    // -------------------------------------------------------------------------
    // Cache hit: equivalent queries differing only in case / whitespace
    // -------------------------------------------------------------------------

    @Test
    fun `equivalent queries differing in case hit the cache and call the DAO only once`() =
        runTest {
            val fakeDao = FakePsgcBarangayDao(
                listOf(
                    psgcEntity("0723017001", "Lahug", "City of Cebu", null),
                ),
            )
            val repository = PsgcRepositoryImpl(fakeDao)

            // "cebu" lowercased → cache key "cebu:80"
            val first = repository.searchBarangays("cebu", limit = 80)
            // " Cebu " trimmed + lowercased → same cache key "cebu:80"
            val second = repository.searchBarangays(" Cebu ", limit = 80)

            assertEquals("both calls should return the same list", first, second)
            assertEquals(
                "DAO search should have been invoked exactly once (second call is a cache hit)",
                1,
                fakeDao.searchCallCount,
            )
        }

    @Test
    fun `equivalent query with extra internal whitespace hits the cache`() = runTest {
        val fakeDao = FakePsgcBarangayDao(emptyList())
        val repository = PsgcRepositoryImpl(fakeDao)

        repository.searchBarangays("san jose", limit = 80)
        // PsgcSearchQuery.terms splits on whitespace and re-joins, so extra spaces collapse
        repository.searchBarangays("san  jose", limit = 80)

        assertEquals(
            "tab/multi-space variants collapse to the same cache key",
            1,
            fakeDao.searchCallCount,
        )
    }

    // -------------------------------------------------------------------------
    // Cache miss: a genuinely different query must go to the DAO
    // -------------------------------------------------------------------------

    @Test
    fun `a different query bypasses the cache and calls the DAO again`() = runTest {
        val fakeDao = FakePsgcBarangayDao(emptyList())
        val repository = PsgcRepositoryImpl(fakeDao)

        repository.searchBarangays("lahug", limit = 80)
        repository.searchBarangays("cebu", limit = 80)

        assertEquals(
            "two distinct queries should each call the DAO once",
            2,
            fakeDao.searchCallCount,
        )
    }

    @Test
    fun `a different limit for the same query bypasses the cache`() = runTest {
        val fakeDao = FakePsgcBarangayDao(emptyList())
        val repository = PsgcRepositoryImpl(fakeDao)

        repository.searchBarangays("cebu", limit = 10)
        repository.searchBarangays("cebu", limit = 20)

        assertEquals(
            "same query but different limit → different cache key → two DAO calls",
            2,
            fakeDao.searchCallCount,
        )
    }

    // -------------------------------------------------------------------------
    // Cache returns correct data — not just a hit/miss boolean
    // -------------------------------------------------------------------------

    @Test
    fun `cached result matches the original DAO result on subsequent calls`() = runTest {
        val entity = psgcEntity("0723017001", "Lahug", "City of Cebu", null)
        val fakeDao = FakePsgcBarangayDao(listOf(entity))
        val repository = PsgcRepositoryImpl(fakeDao)

        val firstResult = repository.searchBarangays("lahug", limit = 80)
        val cachedResult = repository.searchBarangays("lahug", limit = 80)

        assertEquals(1, firstResult.size)
        assertEquals("Lahug", firstResult[0].name)
        assertEquals(firstResult, cachedResult)
    }

    // -------------------------------------------------------------------------
    // Fake DAO implementation — tracks search call count without Room / Robolectric
    // -------------------------------------------------------------------------

    private class FakePsgcBarangayDao(
        private val results: List<PsgcBarangayEntity>,
    ) : PsgcBarangayDao {

        var searchCallCount = 0

        override suspend fun count(): Int = results.size

        override suspend fun insertAll(barangays: List<PsgcBarangayEntity>) = Unit

        override suspend fun deleteAll() = Unit

        override suspend fun getByCode(code: String): PsgcBarangayEntity? = null

        /**
         * Override the default [search] directly so we can count calls. The real impl
         * delegates to [searchRaw]; here we short-circuit to avoid needing a real Room DB.
         */
        override suspend fun search(
            terms: List<String>,
            limit: Int,
        ): List<PsgcBarangayEntity> {
            searchCallCount++
            return results.take(limit)
        }

        /** Never called by [PsgcRepositoryImpl] directly, only via [search]. */
        override suspend fun searchRaw(query: SupportSQLiteQuery): List<PsgcBarangayEntity> =
            results
    }

    private fun psgcEntity(
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
        regionName = "Region VII (Central Visayas)",
        searchText = "$name $cityMuniName ${provinceName.orEmpty()}".lowercase(),
    )
}
