package com.agarthavision.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
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
    /**
     * Writes a sample, inserting or updating in place.
     *
     * **`@Upsert`, not `@Insert(REPLACE)`, and that is not a style choice.** SQLite resolves a
     * REPLACE conflict by *deleting* the existing row and inserting a new one, and that delete
     * fires every foreign-key cascade hanging off it. `detections.sample_id` and
     * `sample_species_findings.sample_id` are both `onDelete = CASCADE`, so re-inserting a
     * sample the device already holds silently took its detections and its per-species counts
     * with it — including the rows C8 exists to protect, since `detections` doubles as the
     * retraining corpus.
     *
     * Nothing threw and nothing logged. The parent row read back correctly updated and the
     * children were simply gone.
     *
     * `@Upsert` compiles to INSERT-then-UPDATE and never deletes, so no cascade fires. The
     * written columns are identical either way — the entity covers every column, so a REPLACE
     * and an UPDATE leave the same row behind. Only the children differ.
     * `SampleDaoUpsertCascadeTest` pins this; it fails on REPLACE.
     *
     * The safety used to live in the caller: the two pull paths in `FetchRemoteDataUseCase`
     * carry an E4 guard that happened to keep them off this edge. That is what made it a
     * defect rather than an outage — the next call site would have got silent data loss with
     * no compile error and no runtime error.
     */
    @Upsert
    suspend fun upsertSample(sample: SampleEntity)

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

    /**
     * Points a sample's row at the JPEG now held on this device.
     *
     * Needed because a pulled row arrives with `image_path` empty — the server has no notion
     * of this device's disk — so caching the image has to tell the row where it went.
     */
    @Query("UPDATE samples SET image_path = :imagePath WHERE sample_id = :sampleId")
    suspend fun updateImagePath(sampleId: String, imagePath: String)

    /**
     * Live samples whose frame exists in Storage, newest verification first.
     *
     * The ordering **is** the retention policy: the prefetch fills from the top and the
     * eviction trims from the bottom, so a device that cannot hold everything holds the most
     * recent work rather than an arbitrary slice of it.
     *
     * Deleted rows are excluded. A tombstoned sample keeps its detections (C8), but no screen
     * can open its frame, so holding the JPEG buys nothing and spends the budget.
     */
    @Query(
        """
        SELECT * FROM samples
        WHERE user_id = :userId
          AND deleted_at IS NULL
          AND storage_path IS NOT NULL
          AND TRIM(storage_path) <> ''
        ORDER BY verified_at DESC, timestamp DESC
        """,
    )
    suspend fun getCacheableSamples(userId: String): List<SampleEntity>

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
          AND (user_id = :userId OR user_id IS NULL)
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
          AND (user_id = :userId OR user_id IS NULL)
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
          AND (user_id = :userId OR user_id IS NULL)
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
          AND (user_id = :userId OR user_id IS NULL)
          AND status = 'flagged'
          AND deleted_at is null
        ORDER BY timestamp DESC
        """,
    )
    suspend fun getFlaggedSamplesForSession(sessionId: String, userId: String?): List<SampleEntity>

    /**
     * How many samples in a session have already been verified.
     *
     * Not shown as a number anywhere — it answers one question the verification queue cannot
     * answer from its own rows. An empty queue means "nothing captured yet" or "everything
     * captured has been checked", and only the second deserves a completion message and a route
     * into the session's records (86d4ayefd). The queue lists flagged rows only, so both cases
     * look identical from inside it.
     *
     * `status != 'flagged'` rather than `= 'verified'`: the three non-flagged states (verified,
     * syncing, sync_failed) are all past the point a human checked the sample, and verification
     * is one-way — an edit moves SYNCED back to VERIFIED and a failed push moves VERIFIED to
     * SYNC_FAILED, and neither crosses back into flagged.
     */
    @Query(
        """
        SELECT COUNT(*) FROM samples
        WHERE session_id = :sessionId
          AND (user_id = :userId OR user_id IS NULL)
          AND status != 'flagged'
          AND deleted_at is null
        """,
    )
    fun observeVerifiedCountForSession(sessionId: String, userId: String?): Flow<Int>

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
          AND (user_id = :userId OR user_id IS NULL)
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
            is_edited = :isEdited
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
        isEdited: Boolean = false,
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

// QueueSampleRow is gone with the union query above. It existed to carry a correlated
// count of confirmed detections alongside each sample, which only ever meant anything for a
// verified row — for a flagged one it is zero by definition, since detections are written on
// submit. A queue of flagged rows only would have rendered that count as a column of zeroes.
