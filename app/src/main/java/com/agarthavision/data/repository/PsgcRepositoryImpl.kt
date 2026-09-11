package com.agarthavision.data.repository

import com.agarthavision.data.local.dao.PsgcBarangayDao
import com.agarthavision.data.local.mapper.toDomain
import com.agarthavision.domain.model.PsgcBarangay
import com.agarthavision.domain.repository.PsgcRepository
import javax.inject.Inject

/**
 * Room-backed [PsgcRepository]. No network path exists here by design.
 */
class PsgcRepositoryImpl @Inject constructor(
    private val barangayDao: PsgcBarangayDao,
) : PsgcRepository {
    override suspend fun searchBarangays(query: String, limit: Int): List<PsgcBarangay> {
        val pattern = likePattern(query)
        if (pattern.isEmpty()) return emptyList()
        return barangayDao.search(pattern = pattern, limit = limit).map { it.toDomain() }
    }

    /**
     * Lowercases the query to match the pre-folded `search_text`, and escapes the `LIKE`
     * wildcards so a medtech typing `%` searches for a literal `%` instead of matching
     * every barangay in the country.
     *
     * Folded in Kotlin, not by SQL `lower()`, which handles ASCII only — 439 barangay names
     * contain `ñ`.
     */
    private fun likePattern(query: String): String =
        query
            .trim()
            .lowercase()
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
}
