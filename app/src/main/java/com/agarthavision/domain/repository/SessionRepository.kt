package com.agarthavision.domain.repository

import com.agarthavision.domain.model.Session
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for locally persisted recording sessions.
 */
interface SessionRepository {
    /**
     * Observes all sessions owned by [userId], newest first.
     */
    fun observeAllSessions(userId: String): Flow<List<Session>>

    /**
     * Loads a session by identifier.
     */
    suspend fun getSessionById(sessionId: String): Session?
}
