package com.agarthavision.data.repository

import com.agarthavision.data.local.dao.DetectionDao
import com.agarthavision.data.local.mapper.toDomain
import com.agarthavision.domain.model.Detection
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
}
