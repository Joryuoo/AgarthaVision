package com.agarthavision.domain.repository

import com.agarthavision.domain.model.Session
import com.agarthavision.domain.model.SessionWithStats
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

    /**
     * Observes sessions with sample and EPG counts.
     */
    fun observeSessionsWithStats(userId: String, sinceMillis: Long): Flow<List<SessionWithStats>>

    /**
     * Updates the label for a session.
     */
    suspend fun updateSessionLabel(sessionId: String, label: String)

    /**
     * Observes sessions owned by [userId] plus any unclaimed local sessions. When
     * [userId] is null (never-signed-in device), observes all local sessions. Per ADR-007.
     */
    fun observeVisibleSessions(userId: String?): Flow<List<Session>>

    /**
     * Opts a session out of (or back into) being claimed at the next login. Per ADR-007.
     */
    suspend fun setClaimExempt(sessionId: String, exempt: Boolean)

    /**
     * Claims a single unowned session for [userId] (the manual "Link to account" action).
     * Per ADR-007.
     */
    suspend fun claimSession(sessionId: String, userId: String)
}
