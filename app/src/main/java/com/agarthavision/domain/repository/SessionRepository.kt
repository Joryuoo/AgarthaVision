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
     * Observes sessions visible to the caller, newest first.
     * null owner = everything on the device; concrete owner = own rows plus unowned rows.
     */
    fun observeAllSessions(userId: String?): Flow<List<Session>>

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
     * Observes a paginated, filtered window of sessions for the Records screen.
     * Filtering (species, date range, free-text search) and aggregation are performed
     * in SQL; [limit] controls the page size for load-more pagination.
     *
     * Mirrors the underlying Room query's bind parameters one-to-one.
     */
    @Suppress("LongParameterList")
    fun observeSessionRecordsPage(
        userId: String?,
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
        userId: String?,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
        species: String?,
    ): Flow<RecordsTotals>

    /**
     * Observes a paginated, filtered window of one patient's sessions for the Sessions
     * screen. When [userId] is null (never-signed-in device), observes that patient's local
     * sessions without a date cap; otherwise applies the recent-window / date-range filter.
     *
     * [patientId] is a hard scope, not a filter: the screen is reached from a patient row and
     * lists that patient's smears only.
     *
     * [activeSessionId] is exempt from the date filter so the smear currently being worked in
     * is never hidden by a date range — but not from [patientId], because an open smear under
     * another patient does not belong in this list. Null when there is no active session. The
     * exemption used to be `ended_at IS NULL`, which stopped distinguishing anything when
     * sessions stopped ending. Per ADR-007.
     */
    @Suppress("LongParameterList")
    fun observeVisibleSessionsPage(
        userId: String?,
        patientId: String,
        activeSessionId: String?,
        sinceMillis: Long,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
        limit: Int,
    ): Flow<List<SessionWithStats>>

    /**
     * Live counts (sessions, and frames awaiting review) for the Sessions screen header.
     * Applies the same filter predicate as [observeVisibleSessionsPage] so the header and
     * the list can never disagree. Per ADR-007.
     */
    @Suppress("LongParameterList")
    fun observeVisibleSessionsCounts(
        userId: String?,
        patientId: String,
        activeSessionId: String?,
        sinceMillis: Long,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
    ): Flow<SessionsCounts>
}
