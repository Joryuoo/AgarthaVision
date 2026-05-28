package com.agarthavision.data.repository

import com.agarthavision.data.local.dao.DetectionDao
import com.agarthavision.data.local.mapper.toDomain
import com.agarthavision.domain.model.Detection
import com.agarthavision.domain.model.EggCount
import com.agarthavision.domain.repository.DetectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DetectionRepositoryImpl @Inject constructor(
    private val detectionDao: DetectionDao,
) : DetectionRepository {
    override suspend fun getDetectionsForSample(sampleId: String): List<Detection> =
        detectionDao.getDetectionsForSample(sampleId).map { it.toDomain() }

    override fun observeDetectionsForSample(sampleId: String): Flow<List<Detection>> =
        detectionDao.observeDetectionsForSample(sampleId).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getConfirmedEggCountsForSession(sessionId: String, userId: String): List<EggCount> =
        detectionDao.getConfirmedEggCountsForSession(sessionId, userId).map { row ->
            EggCount(species = row.species, count = row.eggCount)
        }

    override fun observeConfirmedEggCountsSince(userId: String, sinceTimestamp: Long): Flow<List<EggCount>> =
        detectionDao.observeConfirmedEggCountsSince(userId, sinceTimestamp).map { rows ->
            rows.map { EggCount(species = it.species, count = it.eggCount) }
        }

    override fun observeDailyEggCountsSince(userId: String, sinceTimestamp: Long): Flow<List<com.agarthavision.domain.repository.DailyEggCount>> =
        detectionDao.observeDailyEggCountsSince(userId, sinceTimestamp).map { rows ->
            rows.map { com.agarthavision.domain.repository.DailyEggCount(timestamp = it.timestamp, count = it.eggCount) }
        }
}
