package com.agarthavision.data.repository

import com.agarthavision.data.local.dao.SessionDao
import com.agarthavision.data.local.mapper.toDomain
import com.agarthavision.domain.model.RecordsTotals
import com.agarthavision.domain.model.Session
import com.agarthavision.domain.model.SessionsCounts
import com.agarthavision.domain.model.SessionWithStats
import com.agarthavision.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Room-backed implementation of [SessionRepository].
 */
@Suppress("TooManyFunctions")
class SessionRepositoryImpl @Inject constructor(
    private val sessionDao: SessionDao,
) : SessionRepository {
    override fun observeAllSessions(userId: String): Flow<List<Session>> =
        sessionDao.observeAllSessions(userId).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getSessionById(sessionId: String): Session? =
        sessionDao.getSessionById(sessionId)?.toDomain()

    override fun observeSessionsWithStats(userId: String, sinceMillis: Long): Flow<List<SessionWithStats>> =
        sessionDao.observeSessionsWithStats(userId, sinceMillis).map { list ->
            list.map { item ->
                SessionWithStats(
                    session = item.session.toDomain(),
                    totalSamples = item.totalSamples,
                    verifiedSamples = item.verifiedSamples,
                    unverifiedSamples = item.unverifiedSamples,
                    totalEpg = item.totalEpg
                )
            }
        }

    override suspend fun updateSessionLabel(sessionId: String, label: String) {
        sessionDao.updateSessionLabel(sessionId, label)
    }

    override fun observeVisibleSessions(userId: String?): Flow<List<Session>> {
        val entities = if (userId == null) {
            sessionDao.observeAllLocal()
        } else {
            sessionDao.observeOwnedOrUnowned(userId)
        }
        return entities.map { list -> list.map { it.toDomain() } }
    }

    override suspend fun setClaimExempt(sessionId: String, exempt: Boolean) {
        sessionDao.setClaimExempt(sessionId, exempt)
    }

    override suspend fun claimSession(sessionId: String, userId: String) {
        sessionDao.claimSession(sessionId, userId)
    }

    override fun observeSessionRecordsPage(
        userId: String,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
        species: String?,
        limit: Int,
    ): Flow<List<SessionWithStats>> =
        sessionDao.observeSessionRecordsPage(userId, startMillis, endMillis, query, species, limit)
            .map { list ->
                list.map { row ->
                    SessionWithStats(
                        session = row.session.toDomain(),
                        totalSamples = row.totalSamples,
                        verifiedSamples = 0,
                        unverifiedSamples = 0,
                        totalEpg = row.totalEpg,
                    )
                }
            }

    override fun observeSessionRecordsTotals(
        userId: String,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
        species: String?,
    ): Flow<RecordsTotals> =
        sessionDao.observeSessionRecordsTotals(userId, startMillis, endMillis, query, species)
            .map { row ->
                RecordsTotals(
                    sessionCount = row.sessionCount,
                    totalSamples = row.totalSamples,
                    totalEpg = row.totalEpg,
                )
            }

    /**
     * Dispatches to [SessionDao.observeSessionsPage] for signed-in users or
     * [SessionDao.observeAllLocalPage] for never-logged-in devices, mirroring the
     * null-userId dispatch in [observeVisibleSessions]. Per ADR-007.
     */
    override fun observeVisibleSessionsPage(
        userId: String?,
        sinceMillis: Long,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
        limit: Int,
    ): Flow<List<SessionWithStats>> = if (userId == null) {
        sessionDao.observeAllLocalPage(startMillis, endMillis, query, limit)
            .map { entities ->
                entities.map { SessionWithStats(it.toDomain(), 0, 0, 0, 0) }
            }
    } else {
        sessionDao.observeSessionsPage(userId, sinceMillis, startMillis, endMillis, query, limit)
            .map { list ->
                list.map { item ->
                    SessionWithStats(
                        session = item.session.toDomain(),
                        totalSamples = item.totalSamples,
                        verifiedSamples = item.verifiedSamples,
                        unverifiedSamples = item.unverifiedSamples,
                        totalEpg = item.totalEpg,
                    )
                }
            }
    }

    /**
     * Dispatches to [SessionDao.observeSessionsCounts] for signed-in users or
     * [SessionDao.observeAllLocalCounts] for never-logged-in devices. Per ADR-007.
     */
    override fun observeVisibleSessionsCounts(
        userId: String?,
        sinceMillis: Long,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
    ): Flow<SessionsCounts> = if (userId == null) {
        sessionDao.observeAllLocalCounts(startMillis, endMillis, query)
            .map { row -> SessionsCounts(totalCount = row.totalCount, activeCount = row.activeCount) }
    } else {
        sessionDao.observeSessionsCounts(userId, sinceMillis, startMillis, endMillis, query)
            .map { row -> SessionsCounts(totalCount = row.totalCount, activeCount = row.activeCount) }
    }
}
