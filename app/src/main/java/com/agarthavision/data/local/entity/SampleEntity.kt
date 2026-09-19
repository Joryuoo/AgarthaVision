package com.agarthavision.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for verified samples persisted locally.
 *
 * Mirrors the Supabase `samples` table. The image itself lives on disk (path in
 * `image_path`) and in Supabase Storage (`storage_path` after sync). This entity
 * only stores the metadata needed for traceability and sync.
 */
/**
 * **The session foreign key mirrors the server, the delete rule deliberately does not.**
 * `0001_init.sql:183` declares `session_id uuid not null references public.sessions(id) on
 * delete cascade`; Room declared no foreign key at all, so a sample could outlive its session
 * locally, pointing at an id nothing resolves. Every other parent/child pair here already
 * declares one.
 *
 * `NO_ACTION` rather than `CASCADE` because of C8. A cascade would let any session delete take
 * its verified samples and their detections with it, silently, and `docs/non-negotiables.md`
 * forbids exactly that. `NO_ACTION` makes such a delete fail loudly instead, so a caller has to
 * decide what happens to the samples rather than not notice. The server can afford the cascade:
 * its `detections` rows are the corpus and `0003_storage_rls.sql` grants no DELETE on the bucket
 * anyway.
 *
 * The one caller that deletes a session today, `DiscardUnsyncedDataUseCase`, already removes
 * that session's samples first, so it is unaffected either way.
 */
@Entity(
    tableName = "samples",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["session_id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [Index("session_id")],
)
data class SampleEntity(
    @PrimaryKey
    @ColumnInfo(name = "sample_id")
    val sampleId: String,

    @ColumnInfo(name = "session_id")
    val sessionId: String,

    /**
     * Owning medtech's Supabase user id, or `null` for a sample captured before any
     * medtech signed in on this device. Per ADR-007 the owner is claimed at the next
     * login before sync; the remote `samples.user_id` stays NOT NULL. **Room-only**
     * nullability.
     */
    @ColumnInfo(name = "user_id")
    val userId: String?,

    @ColumnInfo(name = "device_id")
    val deviceId: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "verified_at", defaultValue = "0")
    val verifiedAt: Long = 0L,

    @ColumnInfo(name = "image_path")
    val imagePath: String,

    @ColumnInfo(name = "storage_path")
    val storagePath: String? = null,

    @ColumnInfo(name = "inference_model_version", defaultValue = "'unknown'")
    val inferenceModelVersion: String = "unknown",

    @ColumnInfo(name = "needs_reannotation", defaultValue = "0")
    val needsReannotation: Boolean = false,

    // The three gps_* columns are gone as of Room 13. The fix was taken at the
    // microscope, so it recorded where the smear was read, not where the infection came
    // from — plotted, it mapped laboratories. Geospatial mapping keys on the patient's
    // barangay now. Nothing ever read them.

    @ColumnInfo(name = "status")
    val status: String,

    /**
     * Free-form medtech notes captured in the verification sheet's
     * input row. The Supabase `samples.user_note` column has existed since
     * `0001_init.sql` but was never wired client-side; per ADR-005 Sprint 2
     * lights it up. No Supabase migration needed.
     */
    @ColumnInfo(name = "user_note")
    val userNote: String? = null,

    /**
     * `true` when this sample was taken via the Capture button on
     * `CaptureScreen` (no AI inference involved). Syncs to Supabase via
     * migration `0006_sample_is_manual.sql`. Counted identically to
     * AI-confirmed samples. Per ADR-005.
     */
    @ColumnInfo(name = "is_manual", defaultValue = "0")
    val isManual: Boolean = false,

    @ColumnInfo(name = "predictions_json")
    val predictionsJson: String? = null,

    @ColumnInfo(name = "image_width")
    val imageWidth: Int? = null,

    @ColumnInfo(name = "image_height")
    val imageHeight: Int? = null,

    /**
     * Tombstone instant (epoch millis), or null for a live sample.
     *
     * A **verified** sample is never hard-deleted (C8) — it is tombstoned, which hides it
     * from every queue, count and report while its detections stay in the retraining corpus
     * and its Storage object stays put. Unverified frames are hard-deleted instead, which is
     * C8's existing local exception.
     *
     * **Every query that lists or counts samples must filter `deleted_at IS NULL`.** Miss one
     * and a deleted duplicate reappears in a report. `SoftDeleteGuardTest` enforces this: a
     * DAO method that SELECTs over `samples` must carry the predicate unless its name ends in
     * `IncludingDeleted`.
     *
     * Syncs to Supabase via `0013_sample_soft_delete.sql`.
     */
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null,
)
