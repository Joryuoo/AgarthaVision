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
 */
class PsgcRepositoryImpl @Inject constructor(
    private val barangayDao: PsgcBarangayDao,
) : PsgcRepository {
    override suspend fun searchBarangays(query: String, limit: Int): List<PsgcBarangay> =
        barangayDao
            .search(terms = PsgcSearchQuery.terms(query), limit = limit)
            .map { it.toDomain() }
}
