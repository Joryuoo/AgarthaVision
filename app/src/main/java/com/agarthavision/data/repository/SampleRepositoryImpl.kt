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

    override fun observeLatestSample(userId: String): Flow<Sample?> =
        sampleDao.observeLatestSample(userId).map { entity ->
            entity?.toDomain()
        }

    override fun observeAllSamples(userId: String): Flow<List<Sample>> =
        sampleDao.observeAllSamples(userId).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getSampleById(sampleId: String): Sample? =
        sampleDao.getSampleById(sampleId)?.toDomain()

    override fun observeSamplesForSession(sessionId: String, userId: String): Flow<List<Sample>> =
        sampleDao.observeSamplesForSession(sessionId, userId).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getSamplesForSession(sessionId: String, userId: String): List<Sample> =
        sampleDao.getSamplesForSession(sessionId, userId).map { it.toDomain() }

    override suspend fun getSamplesPendingSync(userId: String): List<Sample> =
        sampleDao.getSamplesPendingSync(userId).map { it.toDomain() }
}
