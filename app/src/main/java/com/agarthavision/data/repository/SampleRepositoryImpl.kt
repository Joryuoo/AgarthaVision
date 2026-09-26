package com.agarthavision.data.repository

import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.data.local.mapper.toDomain
import com.agarthavision.data.local.mapper.toEntity
import com.agarthavision.domain.model.ActivityItem
import com.agarthavision.domain.model.Sample
import com.agarthavision.domain.repository.SampleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Room-backed implementation of [SampleRepository].
 */
@Suppress("TooManyFunctions")
class SampleRepositoryImpl @Inject constructor(
    private val sampleDao: SampleDao,
) : SampleRepository {
    override suspend fun saveSample(sample: Sample) {
        sampleDao.upsertSample(sample.toEntity())
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

    override fun observeSamplesForSession(sessionId: String, userId: String?): Flow<List<Sample>> =
        sampleDao.observeSamplesForSession(sessionId, userId).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getSamplesForSession(sessionId: String, userId: String?): List<Sample> =
        sampleDao.getSamplesForSession(sessionId, userId).map { it.toDomain() }

    override suspend fun getSamplesPendingSyncIncludingDeleted(userId: String): List<Sample> =
        sampleDao.getSamplesPendingSyncIncludingDeleted(userId).map { it.toDomain() }

    override fun observeFlaggedSamplesForSession(
        sessionId: String,
        userId: String?,
    ): Flow<List<Sample>> =
        sampleDao.observeFlaggedSamplesForSession(sessionId, userId).map { entities ->
            entities.map { it.toDomain() }
        }

    override fun observePendingCount(userId: String): Flow<Int> =
        sampleDao.observePendingCount(userId)

    override fun observeFlaggedCount(userId: String): Flow<Int> =
        sampleDao.observeFlaggedCount(userId)

    override fun observeSampleTimesBetween(
        userId: String,
        fromMillis: Long,
        toMillis: Long,
    ): Flow<List<com.agarthavision.domain.model.SampleTime>> =
        sampleDao.observeSampleTimesBetween(userId, fromMillis, toMillis).map { rows ->
            rows.map {
                com.agarthavision.domain.model.SampleTime(
                    sampleId = it.sampleId,
                    status = it.status,
                    capturedAt = it.capturedAt,
                    verifiedAt = it.verifiedAt,
                )
            }
        }

    override fun observeCaptureActivity(
        userId: String,
        limit: Int,
    ): Flow<List<ActivityItem.FramesCaptured>> =
        sampleDao.observeCaptureActivity(userId, limit).map { rows ->
            rows.map {
                ActivityItem.FramesCaptured(
                    sessionId = it.sessionId,
                    sessionLabel = it.sessionLabel,
                    count = it.frameCount,
                    occurredAt = it.occurredAt,
                )
            }
        }

    override fun observeVerifyActivity(
        userId: String,
        limit: Int,
    ): Flow<List<ActivityItem.FramesVerified>> =
        sampleDao.observeVerifyActivity(userId, limit).map { rows ->
            rows.map {
                ActivityItem.FramesVerified(
                    sessionId = it.sessionId,
                    sessionLabel = it.sessionLabel,
                    count = it.frameCount,
                    occurredAt = it.occurredAt,
                )
            }
        }
}
