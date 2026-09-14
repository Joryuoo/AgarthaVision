package com.agarthavision.domain.repository

import com.agarthavision.domain.model.RecordsTotals
import com.agarthavision.domain.model.Session
import com.agarthavision.domain.model.SessionsCounts
import com.agarthavision.domain.model.SessionWithStats
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for locally persisted recording sessions.
 */
@Suppress("TooManyFunctions")
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

    /**
     * Observes a paginated, filtered window of sessions for the Records screen.
     * Filtering (species, date range, free-text search) and aggregation are performed
     * in SQL; [limit] controls the page size for load-more pagination.
     *
     * Mirrors the underlying Room query's bind parameters one-to-one.
     */
    @Suppress("LongParameterList")
    fun observeSessionRecordsPage(
        userId: String,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
        species: String?,
        limit: Int,
    ): Flow<List<SessionWithStats>>

    /**
     * Observes whole-filtered-set totals for the Records stat row. Applies the same
     * filter predicate as [observeSessionRecordsPage] (no LIMIT) so the stat row and
     * the paginated list can never disagree on the universe of sessions counted.
     *
     * Mirrors the underlying Room query's bind parameters one-to-one.
     */
    @Suppress("LongParameterList")
    fun observeSessionRecordsTotals(
        userId: String,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
        species: String?,
    ): Flow<RecordsTotals>

    /**
     * Observes a paginated, filtered window of sessions for the Sessions screen.
     * When [userId] is null (never-signed-in device), observes all local sessions
     * without a date cap; otherwise applies the recent-window / date-range filter.
     * Active sessions (`ended_at IS NULL`) are always included. Per ADR-007.
     */
    @Suppress("LongParameterList")
    fun observeVisibleSessionsPage(
        userId: String?,
        sinceMillis: Long,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
        limit: Int,
    ): Flow<List<SessionWithStats>>

    /**
     * Live counts (total and active) for the Sessions screen header. Applies the same
     * filter predicate as [observeVisibleSessionsPage] so the header and the list can
     * never disagree. Per ADR-007.
     */
    @Suppress("LongParameterList")
    fun observeVisibleSessionsCounts(
        userId: String?,
        sinceMillis: Long,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
    ): Flow<SessionsCounts>
}
