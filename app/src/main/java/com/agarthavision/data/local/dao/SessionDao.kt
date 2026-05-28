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
 */
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

    /**
     * Sessions the SessionPicker should show: active ([SessionEntity.endedAt] is `null`)
     * OR started on/after [sinceMillis]. Newest first. Per ADR-005.
     */
    @Query(
        """
        SELECT * FROM sessions
        WHERE user_id = :userId
          AND (ended_at IS NULL OR started_at >= :sinceMillis)
        ORDER BY started_at DESC
        """
    )
    fun observeActiveAndRecent(userId: String, sinceMillis: Long): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE ended_at IS NULL ORDER BY started_at DESC LIMIT 1")
    suspend fun getOpenSession(): SessionEntity?

    @Query("UPDATE sessions SET label = :label WHERE session_id = :sessionId")
    suspend fun updateSessionLabel(sessionId: String, label: String)

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
