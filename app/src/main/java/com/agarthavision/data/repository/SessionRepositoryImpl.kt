package com.agarthavision.data.repository

import com.agarthavision.data.local.dao.SessionDao
import com.agarthavision.data.local.mapper.toDomain
import com.agarthavision.domain.model.Session
import com.agarthavision.domain.model.SessionWithStats
import com.agarthavision.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Room-backed implementation of [SessionRepository].
 */
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
                    totalEpg = item.totalEpg
                )
            }
        }

    override suspend fun updateSessionLabel(sessionId: String, label: String) {
        sessionDao.updateSessionLabel(sessionId, label)
    }
}
