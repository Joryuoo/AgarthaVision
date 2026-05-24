package com.agarthavision.data.repository

import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.data.local.mapper.toDomain
import com.agarthavision.data.local.mapper.toEntity
import com.agarthavision.domain.model.Sample
import com.agarthavision.domain.repository.SampleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Room-backed implementation of [SampleRepository].
 */
class SampleRepositoryImpl @Inject constructor(
    private val sampleDao: SampleDao,
) : SampleRepository {
    override suspend fun saveSample(sample: Sample) {
        sampleDao.insertSample(sample.toEntity())
    }

    override fun observeLatestSample(): Flow<Sample?> =
        sampleDao.observeLatestSample().map { entity ->
            entity?.toDomain()
        }

    override suspend fun getSampleById(sampleId: String): Sample? =
        sampleDao.getSampleById(sampleId)?.toDomain()
}
