package com.agarthavision.data.repository

import com.agarthavision.data.local.dao.PsgcBarangayDao
import com.agarthavision.data.local.mapper.toDomain
import com.agarthavision.data.local.psgc.PsgcSearchQuery
import com.agarthavision.domain.model.PsgcBarangay
import com.agarthavision.domain.repository.PsgcRepository
import javax.inject.Inject

/**
 * Room-backed [PsgcRepository]. No network path exists here by design — the dataset ships
 * in the APK, so the picker works with the radio off.
 *
 * Results are memoised in an in-memory LRU cache (up to [CACHE_MAX_SIZE] entries) keyed by
 * the normalised, escaped query terms joined with spaces plus the result limit. Equivalent
 * queries that differ only in case or surrounding whitespace collapse to the same entry
 * because [PsgcSearchQuery.terms] already folds case before we build the key.
 *
 * The lock is never held across the suspend DAO call so concurrent searches do not
 * serialise through the cache monitor.
 */
class PsgcRepositoryImpl @Inject constructor(
    private val barangayDao: PsgcBarangayDao,
) : PsgcRepository {

    private val cache = object : LinkedHashMap<String, List<PsgcBarangay>>(
        CACHE_INITIAL_CAPACITY,
        CACHE_LOAD_FACTOR,
        /* accessOrder = */ true,
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, List<PsgcBarangay>>,
        ): Boolean = size > CACHE_MAX_SIZE
    }

    override suspend fun searchBarangays(query: String, limit: Int): List<PsgcBarangay> {
        val terms = PsgcSearchQuery.terms(query)
        val cacheKey = terms.joinToString(" ") + ":" + limit

        synchronized(cache) {
            cache[cacheKey]?.let { return it }
        }

        val result = barangayDao
            .search(terms = terms, limit = limit)
            .map { it.toDomain() }

        synchronized(cache) {
            cache[cacheKey] = result
        }

        return result
    }

    override suspend fun getBarangay(code: String): PsgcBarangay? =
        barangayDao.getByCode(code)?.toDomain()

    companion object {
        private const val CACHE_MAX_SIZE = 64
        private const val CACHE_INITIAL_CAPACITY = 16
        private const val CACHE_LOAD_FACTOR = 0.75f
    }
}
