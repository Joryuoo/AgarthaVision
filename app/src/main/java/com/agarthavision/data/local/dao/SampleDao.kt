package com.agarthavision.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.agarthavision.data.local.entity.SampleEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for verified samples.
 *
 * Room binds one DAO interface per entity — this codebase follows that convention
 * throughout, so splitting [SampleDao] across multiple interfaces would be
 * inconsistent with every other `@Dao` in the project for no functional benefit.
 */
@Suppress("TooManyFunctions")
@Dao
interface SampleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSample(sample: SampleEntity)

    /**
     * Moves a sample between the non-flagged statuses (verified / synced / sync_failed).
     *
     * **Verification is one-way.** Once a sample leaves `flagged` it never goes back, so this
     * refuses to write `flagged` onto a sample that has already been verified - only the
     * capture insert may set that status. Without the guard a careless caller could drop a
     * verified sample back into the unverified bucket, which the medtech would read as their
     * work having been thrown away.
     */
    @Query(
        """
        UPDATE samples
        SET status = :status
        WHERE sample_id = :sampleId
          AND (:status != 'flagged' OR status = 'flagged')
        """,
    )
    suspend fun updateStatus(sampleId: String, status: String)

    @Query(
        """
        UPDATE samples
        SET status = :status, storage_path = :storagePath
        WHERE sample_id = :sampleId
        """,
    )
    suspend fun updateSyncMetadata(
        sampleId: String,
        status: String,
        storagePath: String,
    )

    @Query(
        """
        SELECT * FROM samples
        WHERE user_id = :userId AND status != 'flagged' AND deleted_at is null
        ORDER BY timestamp DESC LIMIT 1
        """,
    )
    fun observeLatestSample(userId: String): Flow<SampleEntity?>

    @Query(
        """
        SELECT * FROM samples
        WHERE user_id = :userId AND status != 'flagged' AND deleted_at is null
        ORDER BY timestamp DESC
        """,
    )
    fun observeAllSamples(userId: String): Flow<List<SampleEntity>>

    @Query("SELECT * FROM samples WHERE sample_id = :sampleId AND deleted_at is null LIMIT 1")
    suspend fun getSampleById(sampleId: String): SampleEntity?

    /**
     * Reads a sample whether or not it is tombstoned.
     *
     * Exempt from the `deleted_at is null` rule by name, per the convention `SoftDeleteGuardTest`
     * enforces. Two callers need it: the delete path, which has to read a row in order to
     * tombstone it, and the sync path, which has to push the tombstone itself.
     */
    @Query("SELECT * FROM samples WHERE sample_id = :sampleId LIMIT 1")
    suspend fun getSampleByIdIncludingDeleted(sampleId: String): SampleEntity?

    @Query(
        """
        SELECT * FROM samples
        WHERE session_id = :sessionId
          AND (:userId IS NULL OR user_id = :userId OR user_id IS NULL)
          AND status != 'flagged'
          AND deleted_at is null
        ORDER BY timestamp DESC
        """,
    )
    fun observeSamplesForSession(sessionId: String, userId: String?): Flow<List<SampleEntity>>

    @Query(
        """
        SELECT * FROM samples
        WHERE session_id = :sessionId
          AND (:userId IS NULL OR user_id = :userId OR user_id IS NULL)
          AND status != 'flagged'
          AND deleted_at is null
        ORDER BY timestamp DESC
        """,
    )
    suspend fun getSamplesForSession(sessionId: String, userId: String?): List<SampleEntity>

    @Query(
        """
        SELECT * FROM samples
        WHERE session_id = :sessionId
          AND (:userId IS NULL OR user_id = :userId OR user_id IS NULL)
          AND status = 'flagged'
          AND deleted_at is null
        ORDER BY timestamp DESC
        """,
    )
    fun observeFlaggedSamplesForSession(sessionId: String, userId: String?): Flow<List<SampleEntity>>

    @Query(
        """
        SELECT * FROM samples
        WHERE session_id = :sessionId
          AND (:userId IS NULL OR user_id = :userId OR user_id IS NULL)
          AND status = 'flagged'
          AND deleted_at is null
        ORDER BY timestamp DESC
        """,
    )
    suspend fun getFlaggedSamplesForSession(sessionId: String, userId: String?): List<SampleEntity>

    /**
     * Every live sample in a session, verified or not — the union the verification queue shows.
     *
     * Deliberately carries **no `status` predicate**: that is what makes it a union rather than
     * one of the two halves. Verified samples stay in the queue and stay editable, so the
     * medtech can correct a mistake instead of living with it.
     *
     * The confirmed-detection count is a correlated subquery rather than a join, so one row
     * comes back per sample and the caller does not have to collapse duplicates.
     */
    @Query(
        """
        SELECT s.*,
               (SELECT COUNT(*) FROM detections d
                 WHERE d.sample_id = s.sample_id AND d.verdict != 'false_positive')
                 AS confirmedDetections
        FROM samples s
        WHERE s.session_id = :sessionId
          AND (:userId IS NULL OR s.user_id = :userId OR s.user_id IS NULL)
          AND s.deleted_at is null
        ORDER BY s.timestamp DESC
        """,
    )
    fun observeQueueRowsForSession(sessionId: String, userId: String?): Flow<List<QueueSampleRow>>

    @Query("DELETE FROM samples WHERE sample_id = :sampleId")
    suspend fun deleteSample(sampleId: String)

    /**
     * Tombstones a verified sample.
     *
     * Hides it from every queue, count and report while its detections, its findings rows and
     * its Storage object all stay exactly where they are - which is what keeps a delete inside
     * C8. The status is reset alongside, so the row re-enters the pending-sync set and the
     * tombstone itself reaches Supabase.
     */
    @Query(
        """
        UPDATE samples
        SET deleted_at = :deletedAt, status = :status
        WHERE sample_id = :sampleId
        """,
    )
    suspend fun tombstoneSample(sampleId: String, deletedAt: Long, status: String)

    @Query(
        """
        DELETE FROM samples
        WHERE session_id = :sessionId
          AND (:userId IS NULL OR user_id = :userId OR user_id IS NULL)
          AND status = 'flagged'
        """,
    )
    suspend fun deleteFlaggedSamplesForSession(sessionId: String, userId: String?)

    @Query(
        """
        UPDATE samples
        SET status = :status,
            verified_at = :verifiedAt,
            needs_reannotation = :needsReannotation,
            user_note = :userNote,
            gps_latitude = :gpsLatitude,
            gps_longitude = :gpsLongitude,
            gps_accuracy = :gpsAccuracy
        WHERE sample_id = :sampleId
        """,
    )
    // Each parameter binds a distinct SET column in a single verify-commit UPDATE. Converting
    // this to a partial-entity @Update would change the actual persistence mechanism, which is
    // out of scope for a lint-only chore.
    @Suppress("LongParameterList")
    suspend fun updateSampleOnVerify(
        sampleId: String,
        status: String,
        verifiedAt: Long,
        needsReannotation: Boolean,
        userNote: String?,
        gpsLatitude: Double?,
        gpsLongitude: Double?,
        gpsAccuracy: Float?,
    )

    @Query(
        """
        SELECT * FROM samples
        WHERE user_id = :userId AND status IN ('verified', 'sync_failed')
        ORDER BY timestamp ASC
        """,
    )
    suspend fun getSamplesPendingSyncIncludingDeleted(userId: String): List<SampleEntity>

    /**
     * Live count of owned samples still awaiting cloud upload (`verified` only, not
     * `sync_failed`). Drives the Settings Data & Sync section. Per ADR-007.
     */
    @Query(
        "SELECT COUNT(*) FROM samples " +
            "WHERE user_id = :userId AND status = 'verified' AND deleted_at is null",
    )
    fun observePendingCount(userId: String): Flow<Int>

    /**
     * Live count of owned samples whose last sync attempt failed. Drives the Settings
     * Data & Sync section. Per ADR-007.
     */
    @Query(
        "SELECT COUNT(*) FROM samples " +
            "WHERE user_id = :userId AND status = 'sync_failed' AND deleted_at is null",
    )
    fun observeFailedCount(userId: String): Flow<Int>

    /**
     * Claims samples belonging to the given sessions for [userId]. Only touches
     * currently-unowned rows so it is idempotent. Per ADR-007.
     */
    @Query(
        """
        UPDATE samples
        SET user_id = :userId
        WHERE session_id IN (:sessionIds) AND user_id IS NULL
        """,
    )
    suspend fun claimSamplesForSessions(sessionIds: List<String>, userId: String)
}

/** Projection for [SampleDao.observeQueueRowsForSession]: the sample plus its counted detections. */
data class QueueSampleRow(
    @Embedded val sample: SampleEntity,
    @ColumnInfo(name = "confirmedDetections") val confirmedDetections: Int,
)
