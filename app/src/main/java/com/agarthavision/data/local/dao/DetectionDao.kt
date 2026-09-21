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

    /**
     * Removes detection rows by id.
     *
     * Exists for one narrow job: an added species whose count the medtech lowered on re-open
     * leaves slots behind, because [insertDetections] replaces and never deletes, and a stale
     * slot would keep a null-`bbox_*` row alive for an egg that is no longer claimed. The caller
     * (`SubmitVerificationUseCase`) computes the ids and excludes every prediction-backed one, so
     * a box the model produced cannot reach this even in principle — those are the rows C8
     * protects, and a rejection is kept as a labelled FALSE_POSITIVE rather than deleted.
     */
    @Query("DELETE FROM detections WHERE detection_id IN (:detectionIds)")
    suspend fun deleteDetectionsByIds(detectionIds: List<String>)

    @Query("SELECT * FROM detections WHERE sample_id = :sampleId")
    fun observeDetectionsForSample(sampleId: String): Flow<List<DetectionEntity>>

    /**
     * Aggregates confirmed detections per species for a session, excluding repeat
     * samples. The `species` value resolves to
     * `expert_class` when present, otherwise `class_label`.
     */
    @Query(
        """
         SELECT COALESCE(d.expert_class, d.class_label) AS species,
             COUNT(*) AS eggCount
        FROM detections d
        JOIN samples s ON s.sample_id = d.sample_id
        WHERE s.deleted_at is null
          AND s.session_id = :sessionId
          AND (s.user_id = :userId OR s.user_id IS NULL)
          AND s.status != 'flagged'
          AND d.verdict != 'false_positive'
        GROUP BY species
        ORDER BY species ASC
        """,
    )

    suspend fun getConfirmedEggCountsForSession(
        sessionId: String,
        userId: String?,
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
        WHERE s.deleted_at is null
          AND s.user_id = :userId
          AND s.timestamp >= :sinceTimestamp
          AND d.verdict = 'confirmed'
        GROUP BY species
        ORDER BY eggCount DESC
        """,
    )
    fun observeConfirmedEggCountsSince(
        userId: String,
        sinceTimestamp: Long,
    ): Flow<List<SessionEggCountRow>>

    // observeDailyEggCountsSince went with the Home tab's sparkline (PB-23). It bucketed
    // counts per sample into daily totals for that chart and nothing else ever read it.

    /**
     * Bulk-fetches distinct species labels for a set of sessions, excluding deleted
     * samples and false-positive detections. Used by the Records screen to avoid
     * per-session N+1 queries after the paginated session load.
     *
     * The exclusion used to read `s.is_repeat = 0`. That flag is gone (86d4ab4vm) — a
     * duplicate is deleted now rather than marked — so the tombstone carries the same
     * intent, and this query keeps reporting one species list per session rather than
     * counting the same field twice.
     */
    @Query(
        """
        SELECT s.session_id AS sessionId,
               COALESCE(d.expert_class, d.class_label) AS species
        FROM detections d
        JOIN samples s ON s.sample_id = d.sample_id
        WHERE s.session_id IN (:sessionIds)
          AND s.deleted_at is null
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

/**
 * Row result for a bulk species-per-session lookup.
 * [sessionId] is the SQL alias for `samples.session_id`;
 * [species] resolves to `expert_class` when set, otherwise `class_label`.
 */
data class SessionSpeciesRow(val sessionId: String, val species: String)
