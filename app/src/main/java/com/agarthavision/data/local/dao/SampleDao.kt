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
     * Writes a frame's model output back onto its row, as `predictions_json` holds it.
     *
     * The pull's counterpart to capture writing the column: a sample verified on another device
     * arrives with no model output, and this is where the `predictions` rows that came down with
     * it land, so reopening it draws the model's real boxes rather than rebuilding them from the
     * detection rows.
     */
    @Query("UPDATE samples SET predictions_json = :predictionsJson WHERE sample_id = :sampleId")
    suspend fun updatePredictionsJson(sampleId: String, predictionsJson: String)

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

    @Query(
        "SELECT COUNT(*) FROM samples " +
            "WHERE user_id = :userId AND status = 'flagged' AND deleted_at is null",
    )
    fun observeFlaggedCount(userId: String): Flow<Int>

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

    @Query(
        """
        SELECT sample_id AS sampleId, status, timestamp AS capturedAt, verified_at AS verifiedAt
        FROM samples WHERE user_id = :userId AND deleted_at is null
          AND ((timestamp >= :fromMillis AND timestamp < :toMillis) OR (verified_at >= :fromMillis AND verified_at < :toMillis))
        """,
    )
    fun observeSampleTimesBetween(
        userId: String,
        fromMillis: Long,
        toMillis: Long,
    ): Flow<List<SampleTimeRow>>

    @Query(
        """
        SELECT sa.session_id AS sessionId, se.label AS sessionLabel, COUNT(*) AS frameCount, MAX(sa.timestamp) AS occurredAt
        FROM samples sa JOIN sessions se ON se.session_id = sa.session_id
        WHERE sa.user_id = :userId AND sa.deleted_at is null
        GROUP BY sa.session_id, strftime('%Y-%m-%d', sa.timestamp/1000, 'unixepoch', '+8 hours')
        ORDER BY occurredAt DESC LIMIT :limit
        """,
    )
    fun observeCaptureActivity(userId: String, limit: Int): Flow<List<SessionSampleActivityRow>>

    @Query(
        """
        SELECT sa.session_id AS sessionId, se.label AS sessionLabel, COUNT(*) AS frameCount, MAX(sa.verified_at) AS occurredAt
        FROM samples sa JOIN sessions se ON se.session_id = sa.session_id
        WHERE sa.user_id = :userId AND sa.deleted_at is null AND sa.status != 'flagged' AND sa.verified_at > 0
        GROUP BY sa.session_id, strftime('%Y-%m-%d', sa.verified_at/1000, 'unixepoch', '+8 hours')
        ORDER BY occurredAt DESC LIMIT :limit
        """,
    )
    fun observeVerifyActivity(userId: String, limit: Int): Flow<List<SessionSampleActivityRow>>

    // ── The background inference queue ──────────────────────────────────────────────────
    //
    // Every transition below is a single UPDATE that names the state it expects to find, and
    // returns how many rows it changed. That is the whole concurrency model: the queue, a cancel
    // and a delete can race on one row, and whichever writes second finds the row already moved
    // and changes nothing. `status = 'flagged'` rides along on each one because a verified sample
    // is past the point a model output may be written onto it.

    /**
     * Puts interrupted frames back in the queue. Only the queue's own consumer calls this, at the
     * start of a pass, when nothing of its own is in flight.
     */
    @Query("UPDATE samples SET inference_state = 'queued' WHERE inference_state = 'in_inference'")
    suspend fun requeueInterruptedInference(): Int

    /** The inference queue, oldest capture first. */
    @Query(
        """
        SELECT sample_id FROM samples
        WHERE inference_state = 'queued'
          AND is_manual = 0
          AND status = 'flagged'
          AND deleted_at is null
        ORDER BY timestamp ASC, sample_id ASC
        """,
    )
    suspend fun getQueuedInferenceSampleIds(): List<String>

    /** Queued → in inference. Returns 0 when the frame is no longer queued. */
    @Query(
        """
        UPDATE samples
        SET inference_state = 'in_inference'
        WHERE sample_id = :sampleId
          AND inference_state = 'queued'
          AND is_manual = 0
          AND status = 'flagged'
          AND deleted_at IS NULL
        """,
    )
    suspend fun claimForInference(sampleId: String): Int

    /** Where the frame being inferred is on disk. */
    @Query("SELECT image_path FROM samples WHERE sample_id = :sampleId AND deleted_at is null LIMIT 1")
    suspend fun getImagePath(sampleId: String): String?

    /**
     * In inference → ready, carrying the model output. Returns 0 when the frame was cancelled or
     * deleted while it ran, in which case the output is not written anywhere.
     */
    @Suppress("LongParameterList")
    @Query(
        """
        UPDATE samples
        SET inference_state = 'ready',
            predictions_json = :predictionsJson,
            inference_model_version = :modelVersion,
            image_width = :imageWidth,
            image_height = :imageHeight
        WHERE sample_id = :sampleId
          AND inference_state = 'in_inference'
          AND is_manual = 0
          AND status = 'flagged'
        """,
    )
    suspend fun completeInference(
        sampleId: String,
        predictionsJson: String?,
        modelVersion: String,
        imageWidth: Int?,
        imageHeight: Int?,
    ): Int

    /**
     * Counts one failure of both engines on an in-inference frame. Below [maxAttempts] the frame
     * goes back to the queue; at [maxAttempts] it becomes manual, exactly as a cancel would leave
     * it. One statement, so the count and the state cannot disagree. SQLite evaluates every SET
     * expression against the row as it was before the update.
     */
    @Query(
        """
        UPDATE samples
        SET inference_attempts = inference_attempts + 1,
            inference_state = CASE
                WHEN inference_attempts + 1 >= :maxAttempts THEN 'manual' ELSE 'queued' END,
            is_manual = CASE
                WHEN inference_attempts + 1 >= :maxAttempts THEN 1 ELSE is_manual END,
            inference_model_version = CASE
                WHEN inference_attempts + 1 >= :maxAttempts THEN 'manual'
                ELSE inference_model_version END
        WHERE sample_id = :sampleId
          AND inference_state = 'in_inference'
          AND is_manual = 0
          AND status = 'flagged'
        """,
    )
    suspend fun recordInferenceFailure(sampleId: String, maxAttempts: Int): Int

    /**
     * A sample's inference state with `is_manual` taken into account, the same rule as
     * `SampleEntity.effectiveInferenceState()`. Null when the sample is gone.
     */
    @Query(
        """
        SELECT CASE WHEN is_manual = 1 THEN 'manual' ELSE inference_state END
        FROM samples
        WHERE sample_id = :sampleId AND deleted_at is null
        LIMIT 1
        """,
    )
    suspend fun getEffectiveInferenceState(sampleId: String): String?

    /**
     * Queued or in inference → manual, for good. Returns 0 when there was nothing pending to
     * cancel, most often because the result landed first. A result arriving after this finds
     * the row manual and is refused by [completeInference].
     *
     * `inference_model_version = 'manual'` is what keeps the retraining corpus honest: a frame a
     * human annotated alone never carries a model's name.
     */
    @Query(
        """
        UPDATE samples
        SET inference_state = 'manual',
            is_manual = 1,
            inference_model_version = 'manual'
        WHERE sample_id = :sampleId
          AND inference_state IN ('queued', 'in_inference')
          AND is_manual = 0
          AND status = 'flagged'
        """,
    )
    suspend fun cancelInference(sampleId: String): Int
}

data class SessionSampleActivityRow(
    val sessionId: String,
    val sessionLabel: String?,
    val frameCount: Int,
    val occurredAt: Long,
)

data class SampleTimeRow(
    val sampleId: String,
    val status: String,
    val capturedAt: Long,
    val verifiedAt: Long,
)

// QueueSampleRow is gone with the union query above. It existed to carry a correlated
// count of confirmed detections alongside each sample, which only ever meant anything for a
// verified row — for a flagged one it is zero by definition, since detections are written on
// submit. A queue of flagged rows only would have rendered that count as a column of zeroes.
