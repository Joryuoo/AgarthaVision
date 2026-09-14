package com.agarthavision.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.agarthavision.data.local.entity.DetectionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for detections within a verified sample.
 */
@Dao
interface DetectionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDetection(detection: DetectionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDetections(detections: List<DetectionEntity>)

    @Query("SELECT * FROM detections WHERE sample_id = :sampleId")
    suspend fun getDetectionsForSample(sampleId: String): List<DetectionEntity>

    @Query("SELECT * FROM detections WHERE sample_id = :sampleId")
    fun observeDetectionsForSample(sampleId: String): Flow<List<DetectionEntity>>

    /**
     * Aggregates confirmed detections per species for a session, excluding repeat
     * samples (`samples.is_repeat = 0`). The `species` value resolves to
     * `expert_class` when present, otherwise `class_label`.
     */
    @Query(
        """
         SELECT COALESCE(d.expert_class, d.class_label) AS species,
             COUNT(*) AS eggCount
        FROM detections d
        JOIN samples s ON s.sample_id = d.sample_id
        WHERE s.session_id = :sessionId
          AND s.user_id = :userId
                    AND s.status != 'flagged'
          AND s.is_repeat = 0
          AND d.verdict != 'false_positive'
        GROUP BY species
        ORDER BY species ASC
        """,
    )

    suspend fun getConfirmedEggCountsForSession(
        sessionId: String,
        userId: String,
    ): List<SessionEggCountRow>

    /**
     * Aggregates confirmed detections per species over a time window.
     */
    @Query(
        """
         SELECT COALESCE(d.expert_class, d.class_label) AS species,
             COUNT(*) AS eggCount
        FROM detections d
        JOIN samples s ON s.sample_id = d.sample_id
        WHERE s.user_id = :userId
          AND s.timestamp >= :sinceTimestamp
          AND s.is_repeat = 0
          AND d.verdict = 'confirmed'
        GROUP BY species
        ORDER BY eggCount DESC
        """,
    )
    fun observeConfirmedEggCountsSince(
        userId: String,
        sinceTimestamp: Long,
    ): Flow<List<SessionEggCountRow>>

    /**
     * Fetches counts per sample over a time window for bucketing into daily totals.
     */
    @Query(
        """
         SELECT s.timestamp, COUNT(*) AS eggCount
        FROM detections d
        JOIN samples s ON s.sample_id = d.sample_id
        WHERE s.user_id = :userId
          AND s.timestamp >= :sinceTimestamp
          AND s.is_repeat = 0
          AND d.verdict = 'confirmed'
        GROUP BY s.sample_id
        ORDER BY s.timestamp ASC
        """,
    )
    fun observeDailyEggCountsSince(
        userId: String,
        sinceTimestamp: Long,
    ): Flow<List<DailyEggCountRow>>

    /**
     * Bulk-fetches distinct species labels for a set of sessions, excluding repeat
     * samples and false-positive detections. Used by the Records screen to avoid
     * per-session N+1 queries after the paginated session load.
     */
    @Query(
        """
        SELECT s.session_id AS sessionId,
               COALESCE(d.expert_class, d.class_label) AS species
        FROM detections d
        JOIN samples s ON s.sample_id = d.sample_id
        WHERE s.session_id IN (:sessionIds)
          AND s.is_repeat = 0
          AND d.verdict != 'false_positive'
          AND COALESCE(d.expert_class, d.class_label) IS NOT NULL
        GROUP BY s.session_id, species
        ORDER BY species ASC
        """,
    )
    suspend fun getSpeciesLabelsForSessions(sessionIds: List<String>): List<SessionSpeciesRow>
}

/**
 * Row result for per-session egg counts grouped by species.
 */
data class SessionEggCountRow(
    val species: String,
    @ColumnInfo(name = "eggCount")
    val eggCount: Int,
)

data class DailyEggCountRow(
    val timestamp: Long,
    @ColumnInfo(name = "eggCount")
    val eggCount: Int,
)

/**
 * Row result for a bulk species-per-session lookup.
 * [sessionId] is the SQL alias for `samples.session_id`;
 * [species] resolves to `expert_class` when set, otherwise `class_label`.
 */
data class SessionSpeciesRow(val sessionId: String, val species: String)
