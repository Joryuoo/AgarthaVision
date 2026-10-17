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
 * the normalised, escaped query terms plus the result limit — see [CacheKey]. Equivalent
 * queries that differ only in case, surrounding whitespace or comma placement collapse to
 * the same entry because [PsgcSearchQuery.parse] already folds case and splits commas
 * before we build the key.
 *
 * The lock is never held across the suspend DAO call so concurrent searches do not
 * serialise through the cache monitor.
 */
class PsgcRepositoryImpl @Inject constructor(
    private val barangayDao: PsgcBarangayDao,
) : PsgcRepository {

    /** Cache key: the parsed WHERE terms and name-ranking terms, plus the result limit. */
    private data class CacheKey(val terms: List<String>, val nameTerms: List<String>, val limit: Int)

    private val cache = object : LinkedHashMap<CacheKey, List<PsgcBarangay>>(
        CACHE_INITIAL_CAPACITY,
        CACHE_LOAD_FACTOR,
        /* accessOrder = */ true,
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<CacheKey, List<PsgcBarangay>>,
        ): Boolean = size > CACHE_MAX_SIZE
    }

    override suspend fun searchBarangays(query: String, limit: Int): List<PsgcBarangay> {
        val parsed = PsgcSearchQuery.parse(query)
        val cacheKey = CacheKey(terms = parsed.terms, nameTerms = parsed.nameTerms, limit = limit)

        synchronized(cache) {
            cache[cacheKey]?.let { return it }
        }

        val result = barangayDao
            .search(terms = parsed.terms, limit = limit, nameTerms = parsed.nameTerms)
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
