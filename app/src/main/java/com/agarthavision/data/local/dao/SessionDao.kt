package com.agarthavision.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Embedded
import com.agarthavision.data.local.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for capture sessions.
 *
 * Room binds one DAO interface per entity — this codebase follows that convention
 * throughout, so splitting [SessionDao] across multiple interfaces would be
 * inconsistent with every other `@Dao` in the project for no functional benefit.
 */
@Suppress("TooManyFunctions")
@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity)

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE session_id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE user_id = :userId ORDER BY started_at DESC")
    fun observeAllSessions(userId: String): Flow<List<SessionEntity>>

    @Query("UPDATE sessions SET label = :label WHERE session_id = :sessionId")
    suspend fun updateSessionLabel(sessionId: String, label: String)

    /**
     * Observes sessions visible to a signed-out or offline medtech: those owned by
     * [userId] plus any not-yet-claimed local sessions (`user_id IS NULL`). Newest first.
     * Per ADR-007.
     */
    @Query(
        """
        SELECT * FROM sessions
        WHERE user_id = :userId OR user_id IS NULL
        ORDER BY started_at DESC
        """
    )
    fun observeOwnedOrUnowned(userId: String): Flow<List<SessionEntity>>

    /**
     * Observes all local sessions regardless of owner (used when no identity is cached
     * yet — a never-logged-in device). Newest first. Per ADR-007.
     */
    @Query("SELECT * FROM sessions ORDER BY started_at DESC")
    fun observeAllLocal(): Flow<List<SessionEntity>>

    /**
     * Owned, non-exempt sessions still awaiting cloud upload, oldest first so the sync
     * pass pushes them in creation order. Per ADR-007.
     */
    @Query(
        """
        SELECT * FROM sessions
        WHERE user_id = :userId AND claim_exempt = 0
          AND supabase_status IN ('pending', 'sync_failed')
        ORDER BY started_at ASC
        """
    )
    suspend fun getSessionsPendingSync(userId: String): List<SessionEntity>

    /**
     * Unowned (`user_id IS NULL`) sessions that have not been opted out, newest first.
     * Drives the login-time claim. Per ADR-007.
     */
    @Query(
        """
        SELECT * FROM sessions
        WHERE user_id IS NULL AND claim_exempt = 0
        ORDER BY started_at DESC
        """
    )
    suspend fun getClaimableSessions(): List<SessionEntity>

    /** Updates the Room-only cloud sync status for a session. Per ADR-007. */
    @Query("UPDATE sessions SET supabase_status = :status WHERE session_id = :sessionId")
    suspend fun updateSupabaseStatus(sessionId: String, status: String)

    /** Toggles the claim-exempt flag for a session. Per ADR-007. */
    @Query("UPDATE sessions SET claim_exempt = :exempt WHERE session_id = :sessionId")
    suspend fun setClaimExempt(sessionId: String, exempt: Boolean)

    /**
     * Claims all unowned, non-exempt sessions for [userId], marking them pending sync.
     * Only touches `user_id IS NULL` rows so it is idempotent. Per ADR-007.
     */
    @Query(
        """
        UPDATE sessions
        SET user_id = :userId, supabase_status = 'pending'
        WHERE user_id IS NULL AND claim_exempt = 0
        """
    )
    suspend fun claimUnownedSessions(userId: String)

    /**
     * Claims a single unowned session by id (the manual "Link to account" action).
     * Per ADR-007.
     */
    @Query(
        """
        UPDATE sessions
        SET user_id = :userId, supabase_status = 'pending', claim_exempt = 0
        WHERE session_id = :sessionId AND user_id IS NULL
        """
    )
    suspend fun claimSession(sessionId: String, userId: String)

    /**
     * Live count of owned, non-exempt sessions still awaiting cloud upload (`pending`
     * only, not `sync_failed`). Drives the Settings Data & Sync section. Per ADR-007.
     */
    @Query(
        """
        SELECT COUNT(*) FROM sessions
        WHERE user_id = :userId AND claim_exempt = 0 AND supabase_status = 'pending'
        """,
    )
    fun observePendingCount(userId: String): Flow<Int>

    /**
     * Live count of owned, non-exempt sessions whose last sync attempt failed. Drives
     * the Settings Data & Sync section. Per ADR-007.
     */
    @Query(
        """
        SELECT COUNT(*) FROM sessions
        WHERE user_id = :userId AND claim_exempt = 0 AND supabase_status = 'sync_failed'
        """,
    )
    fun observeFailedCount(userId: String): Flow<Int>

    /**
     * Observes sessions with their associated sample, verification, and EPG counts.
     */
    @Query(
        """
        SELECT s.*,
               COUNT(DISTINCT smp.sample_id) AS totalSamples,
               SUM(CASE WHEN smp.verified_at > 0 THEN 1 ELSE 0 END) AS verifiedSamples,
               SUM(
                 CASE WHEN smp.status = 'flagged' AND smp.is_repeat = 0 THEN 1 ELSE 0 END
               ) AS unverifiedSamples,
               COUNT(d.detection_id) AS totalEpg
        FROM sessions s
        LEFT JOIN samples smp ON s.session_id = smp.session_id
        LEFT JOIN detections d ON smp.sample_id = d.sample_id AND d.verdict = 'confirmed' AND smp.is_repeat = 0
        WHERE s.user_id = :userId
          AND (s.ended_at IS NULL OR s.started_at >= :sinceMillis)
        GROUP BY s.session_id
        ORDER BY s.started_at DESC
        """
    )
    fun observeSessionsWithStats(userId: String, sinceMillis: Long): Flow<List<SessionWithStats>>

    /**
     * Observes a paginated, filtered window of sessions for the Records screen.
     * Non-flagged sample counts and non-false-positive detection EPG totals are
     * pre-aggregated so the UI avoids per-session N+1 queries. The verdict filter
     * (`d.verdict != 'false_positive'`) matches [DetectionDao.getConfirmedEggCountsForSession]
     * so Records cards and Session Detail counts are always consistent.
     * Species-label lookup is done separately via [DetectionDao.getSpeciesLabelsForSessions].
     *
     * Shares [RECORDS_FILTER] with [observeSessionRecordsTotals] so filtering logic
     * can never diverge between the page and the stat-row totals.
     *
     * The parameter list maps one-to-one onto named SQL bind parameters, so it cannot
     * be collapsed into a holder type without losing Room's query binding.
     */
    @Suppress("LongParameterList")
    @Query(
        "SELECT s.*, " +
        "  COUNT(DISTINCT CASE WHEN smp.status != 'flagged' THEN smp.sample_id END) AS totalSamples, " +
        "  COUNT(d.detection_id) AS totalEpg " +
        "FROM sessions s " +
        "LEFT JOIN samples smp ON s.session_id = smp.session_id AND smp.status != 'flagged' " +
        "LEFT JOIN detections d ON smp.sample_id = d.sample_id " +
        "     AND d.verdict != 'false_positive' AND smp.is_repeat = 0" +
        RECORDS_FILTER +
        " GROUP BY s.session_id ORDER BY s.started_at DESC LIMIT :limit"
    )
    fun observeSessionRecordsPage(
        userId: String,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
        species: String?,
        limit: Int,
    ): Flow<List<SessionRecordStatsRow>>

    /**
     * Whole-filtered-set totals for the Records stat row. Applies the same [RECORDS_FILTER]
     * as [observeSessionRecordsPage] but no LIMIT, aggregated from a per-session subquery so
     * join fan-out cannot inflate the sums.
     */
    @Suppress("LongParameterList")
    @Query(
        "SELECT COUNT(*) AS sessionCount, " +
        "COALESCE(SUM(perSession.samples), 0) AS totalSamples, " +
        "COALESCE(SUM(perSession.eggs), 0) AS totalEpg " +
        "FROM (SELECT s.session_id, " +
        "  COUNT(DISTINCT CASE WHEN smp.status != 'flagged' THEN smp.sample_id END) AS samples, " +
        "  COUNT(d.detection_id) AS eggs " +
        "  FROM sessions s " +
        "  LEFT JOIN samples smp ON s.session_id = smp.session_id AND smp.status != 'flagged' " +
        "  LEFT JOIN detections d ON smp.sample_id = d.sample_id" +
        "    AND d.verdict != 'false_positive' AND smp.is_repeat = 0 " +
        RECORDS_FILTER +
        "  GROUP BY s.session_id) AS perSession"
    )
    fun observeSessionRecordsTotals(
        userId: String,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
        species: String?,
    ): Flow<RecordsTotalsRow>

    // -------------------------------------------------------------------------
    // Sessions screen: paginated, filtered, with active-session priority
    // -------------------------------------------------------------------------

    /**
     * Observes a paginated, filtered window of sessions for the Sessions screen.
     * Aggregate columns mirror [observeSessionsWithStats] so [SessionCard] can display
     * the same metrics. Active sessions (`ended_at IS NULL`) are always included;
     * ended sessions appear only within the recent window or the explicit date range.
     *
     * Shares [SESSIONS_FILTER] with [observeSessionsCounts] so count and list can
     * never disagree on the filtered universe. Per ADR-007.
     *
     * The parameter list maps one-to-one onto named SQL bind parameters, so it cannot
     * be collapsed into a holder type without losing Room's query binding.
     */
    @Suppress("LongParameterList")
    @Query(
        "SELECT s.*, " +
        "  COUNT(DISTINCT smp.sample_id) AS totalSamples, " +
        "  SUM(CASE WHEN smp.verified_at > 0 THEN 1 ELSE 0 END) AS verifiedSamples, " +
        "  SUM(CASE WHEN smp.status = 'flagged' AND smp.is_repeat = 0 THEN 1 ELSE 0 END) AS unverifiedSamples, " +
        "  COUNT(d.detection_id) AS totalEpg " +
        "FROM sessions s " +
        "LEFT JOIN samples smp ON s.session_id = smp.session_id " +
        "LEFT JOIN detections d ON smp.sample_id = d.sample_id AND d.verdict = 'confirmed' AND smp.is_repeat = 0" +
        SESSIONS_FILTER +
        " GROUP BY s.session_id ORDER BY s.started_at DESC LIMIT :limit"
    )
    fun observeSessionsPage(
        userId: String,
        sinceMillis: Long,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
        limit: Int,
    ): Flow<List<SessionWithStats>>

    /**
     * Live count of total and active sessions matching [SESSIONS_FILTER].
     * Shares the predicate with [observeSessionsPage] so the header counts
     * and the list can never disagree. Per ADR-007.
     */
    @Suppress("LongParameterList")
    @Query(
        "SELECT COUNT(*) AS totalCount, " +
        "COALESCE(SUM(CASE WHEN s.ended_at IS NULL THEN 1 ELSE 0 END), 0) AS activeCount " +
        "FROM sessions s" +
        SESSIONS_FILTER
    )
    fun observeSessionsCounts(
        userId: String,
        sinceMillis: Long,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
    ): Flow<SessionsCountsRow>

    /**
     * Observes a paginated, filtered window of local sessions for a never-logged-in device.
     * All sessions are visible (no user_id guard); active sessions are always included.
     * Shares [LOCAL_SESSIONS_FILTER] with [observeAllLocalCounts]. Per ADR-007.
     */
    @Suppress("LongParameterList")
    @Query("SELECT * FROM sessions" + LOCAL_SESSIONS_FILTER + " ORDER BY started_at DESC LIMIT :limit")
    fun observeAllLocalPage(
        startMillis: Long?,
        endMillis: Long?,
        query: String,
        limit: Int,
    ): Flow<List<SessionEntity>>

    /**
     * Live count of total and active local sessions matching [LOCAL_SESSIONS_FILTER].
     * Shares the predicate with [observeAllLocalPage]. Per ADR-007.
     */
    @Suppress("LongParameterList")
    @Query(
        "SELECT COUNT(*) AS totalCount, " +
        "COALESCE(SUM(CASE WHEN ended_at IS NULL THEN 1 ELSE 0 END), 0) AS activeCount " +
        "FROM sessions" +
        LOCAL_SESSIONS_FILTER
    )
    fun observeAllLocalCounts(
        startMillis: Long?,
        endMillis: Long?,
        query: String,
    ): Flow<SessionsCountsRow>
}

/**
 * Shared WHERE predicate for the Records paginated page and totals queries.
 * Extracted here so both queries apply identical filtering and can never disagree.
 * Search LIKE clauses use `ESCAPE '\'` so the caller can safely escape `%`, `_`,
 * and `\` in the needle before passing it in. Species LIKE is intentionally left
 * without ESCAPE since species needles come from a fixed enum, not free text.
 */
private const val RECORDS_FILTER = """
  WHERE s.user_id = :userId
    AND (:startMillis IS NULL OR s.started_at >= :startMillis)
    AND (:endMillis   IS NULL OR s.started_at <= :endMillis)
    AND (:query = ''
         OR s.session_id LIKE '%' || :query || '%' ESCAPE '\'
         OR s.label      LIKE '%' || :query || '%' ESCAPE '\'
         OR s.notes      LIKE '%' || :query || '%' ESCAPE '\'
         OR EXISTS (SELECT 1 FROM detections dq JOIN samples sq ON sq.sample_id = dq.sample_id
                    WHERE sq.session_id = s.session_id AND sq.is_repeat = 0
                      AND dq.verdict != 'false_positive'
                      AND COALESCE(dq.expert_class, dq.class_label) LIKE '%' || :query || '%' ESCAPE '\'))
    AND (:species IS NULL
         OR EXISTS (SELECT 1 FROM detections ds JOIN samples ss ON ss.sample_id = ds.sample_id
                    WHERE ss.session_id = s.session_id AND ss.is_repeat = 0
                      AND ds.verdict != 'false_positive'
                      AND COALESCE(ds.expert_class, ds.class_label) LIKE '%' || :species || '%'))
"""

/**
 * Shared WHERE predicate for the Sessions paginated page and counts queries.
 * Active sessions (`ended_at IS NULL`) are always visible; ended sessions appear
 * when they fall within the recent window (`:sinceMillis`) or within an explicit
 * date range (`:startMillis`/`:endMillis`).
 *
 * Search LIKE clauses use `ESCAPE '\'` so the caller can safely escape `%`, `_`,
 * and `\` in the needle before passing it in.
 */
private const val SESSIONS_FILTER = """
  WHERE s.user_id = :userId
    AND ( s.ended_at IS NULL
          OR (:startMillis IS NULL AND :endMillis IS NULL AND s.started_at >= :sinceMillis)
          OR (:startMillis IS NOT NULL AND s.started_at >= :startMillis AND s.started_at <= :endMillis) )
    AND (:query = ''
         OR s.session_id LIKE '%' || :query || '%' ESCAPE '\'
         OR s.label      LIKE '%' || :query || '%' ESCAPE '\'
         OR s.notes      LIKE '%' || :query || '%' ESCAPE '\')
"""

/**
 * Shared WHERE predicate for the local-only (never-logged-in) paginated page and
 * counts queries. No user_id guard; active sessions are always visible.
 *
 * Search LIKE clauses use `ESCAPE '\'` so the caller can safely escape `%`, `_`,
 * and `\` in the needle before passing it in.
 */
private const val LOCAL_SESSIONS_FILTER = """
  WHERE ( ended_at IS NULL
          OR (:startMillis IS NULL AND :endMillis IS NULL)
          OR (:startMillis IS NOT NULL AND started_at >= :startMillis AND started_at <= :endMillis) )
    AND (:query = ''
         OR session_id LIKE '%' || :query || '%' ESCAPE '\'
         OR label      LIKE '%' || :query || '%' ESCAPE '\'
         OR notes      LIKE '%' || :query || '%' ESCAPE '\')
"""

/**
 * Aggregate row returned by [SessionDao.observeSessionsCounts] and
 * [SessionDao.observeAllLocalCounts].
 */
data class SessionsCountsRow(
    @ColumnInfo(name = "totalCount") val totalCount: Int,
    @ColumnInfo(name = "activeCount") val activeCount: Int,
)

data class SessionWithStats(
    @Embedded val session: SessionEntity,
    @androidx.room.ColumnInfo(name = "totalSamples") val totalSamples: Int,
    @androidx.room.ColumnInfo(name = "verifiedSamples") val verifiedSamples: Int,
    /**
     * Frames still awaiting review, excluding repeats — the same set that blocks ending
     * a session, so the row and the end-session dialog can never disagree.
     */
    @androidx.room.ColumnInfo(name = "unverifiedSamples") val unverifiedSamples: Int,
    @androidx.room.ColumnInfo(name = "totalEpg") val totalEpg: Int
)

/**
 * Slim projection returned by [SessionDao.observeSessionRecordsPage].
 * Carries only the aggregate columns needed by the Records screen; species labels
 * are fetched separately via [DetectionDao.getSpeciesLabelsForSessions].
 */
data class SessionRecordStatsRow(
    @Embedded val session: SessionEntity,
    @androidx.room.ColumnInfo(name = "totalSamples") val totalSamples: Int,
    @androidx.room.ColumnInfo(name = "totalEpg") val totalEpg: Int,
)

/**
 * Aggregate totals returned by [SessionDao.observeSessionRecordsTotals].
 * All counts apply the same [RECORDS_FILTER] as the page query.
 */
data class RecordsTotalsRow(
    @ColumnInfo(name = "sessionCount") val sessionCount: Int,
    @ColumnInfo(name = "totalSamples") val totalSamples: Int,
    @ColumnInfo(name = "totalEpg") val totalEpg: Int,
)
