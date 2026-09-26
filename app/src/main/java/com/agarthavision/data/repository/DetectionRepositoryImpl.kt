package com.agarthavision.data.repository

import com.agarthavision.data.local.dao.DetectionDao
import com.agarthavision.data.local.mapper.toDomain
import com.agarthavision.domain.model.Detection
import com.agarthavision.domain.model.EggCount
import com.agarthavision.domain.repository.DetectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

import com.agarthavision.data.local.mapper.isPredictionBacked
import com.agarthavision.domain.model.DetectionVerdict
import com.agarthavision.domain.model.ModelRuling

class DetectionRepositoryImpl @Inject constructor(
    private val detectionDao: DetectionDao,
) : DetectionRepository {
    override suspend fun getDetectionsForSample(sampleId: String): List<Detection> =
        detectionDao.getDetectionsForSample(sampleId).map { it.toDomain() }

    override fun observeDetectionsForSample(sampleId: String): Flow<List<Detection>> =
        detectionDao.observeDetectionsForSample(sampleId).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getConfirmedEggCountsForSession(sessionId: String, userId: String?): List<EggCount> =
        detectionDao.getConfirmedEggCountsForSession(sessionId, userId).map { row ->
            EggCount(species = row.species, count = row.eggCount)
        }

    override fun observeConfirmedEggCountsSince(userId: String, sinceTimestamp: Long): Flow<List<EggCount>> =
        detectionDao.observeConfirmedEggCountsSince(userId, sinceTimestamp).map { rows ->
            rows.map { EggCount(species = it.species, count = it.eggCount) }
        }

    override suspend fun getSpeciesLabelsForSessions(
        sessionIds: List<String>,
    ): Map<String, List<String>> =
        detectionDao.getSpeciesLabelsForSessions(sessionIds)
            .groupBy({ it.sessionId }, { it.species })

    override fun observeModelRulingsBetween(
        userId: String,
        fromMillis: Long,
        toMillis: Long,
    ): Flow<List<ModelRuling>> =
        detectionDao.observeRulingsBetween(userId, fromMillis, toMillis).map { rows ->
            rows.filter { row ->
                isPredictionBacked(row.detectionId, row.sampleId, row.detectionsInSample)
            }.map { row ->
                ModelRuling(
                    verifiedAt = row.verifiedAt,
                    verdict = DetectionVerdict.fromValue(row.verdict),
                )
            }
        }

    override fun observeSessionFindingsBetween(
        userId: String,
        startMillis: Long,
        endMillis: Long,
    ): Flow<List<com.agarthavision.domain.model.SessionFinding>> =
        detectionDao.observeSessionFindingsBetween(userId, startMillis, endMillis).map { rows ->
            rows.map { row ->
                com.agarthavision.domain.model.SessionFinding(
                    sessionId = row.sessionId,
                    rawSpecies = row.rawSpecies,
                    townCode = row.townCode,
                )
            }
        }
}
