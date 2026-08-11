package com.agarthavision.data.local.dao

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
}

data class SessionWithStats(
    @Embedded val session: SessionEntity,
    @androidx.room.ColumnInfo(name = "totalSamples") val totalSamples: Int,
    @androidx.room.ColumnInfo(name = "verifiedSamples") val verifiedSamples: Int,
    @androidx.room.ColumnInfo(name = "totalEpg") val totalEpg: Int
)
