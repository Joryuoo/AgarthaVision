package com.agarthavision.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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
}
