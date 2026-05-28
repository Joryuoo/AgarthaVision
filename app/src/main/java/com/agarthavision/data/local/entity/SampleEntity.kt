package com.agarthavision.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for verified samples persisted locally.
 *
 * Mirrors the Supabase `samples` table. The image itself lives on disk (path in
 * `image_path`) and in Supabase Storage (`storage_path` after sync). This entity
 * only stores the metadata needed for traceability and sync.
 */
@Entity(tableName = "samples")
data class SampleEntity(
    @PrimaryKey
    @ColumnInfo(name = "sample_id")
    val sampleId: String,

    @ColumnInfo(name = "session_id")
    val sessionId: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

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

    @ColumnInfo(name = "gps_latitude")
    val gpsLatitude: Double?,

    @ColumnInfo(name = "gps_longitude")
    val gpsLongitude: Double?,

    @ColumnInfo(name = "gps_accuracy")
    val gpsAccuracy: Float?,

    @ColumnInfo(name = "status")
    val status: String,

    /**
     * Free-form medtech notes captured in the VerificationSheet / ManualSheet
     * input row. The Supabase `samples.user_note` column has existed since
     * `0001_init.sql` but was never wired client-side; per ADR-005 Sprint 2
     * lights it up. No Supabase migration needed.
     */
    @ColumnInfo(name = "user_note")
    val userNote: String? = null,

    /**
     * `true` when this sample was taken via the Capture button on
     * `CaptureScreen` (no AI inference involved). Syncs to Supabase via
     * migration `0006_sample_is_manual.sql`. Counted in EPG identically to
     * AI-confirmed samples. Per ADR-005.
     */
    @ColumnInfo(name = "is_manual", defaultValue = "0")
    val isManual: Boolean = false,

    /**
     * `true` when the medtech has flagged this sample as a duplicate of an
     * egg already counted on the same smear. **Room-only** flag — never
     * synced to Supabase. Excluded from EPG counts. Workflow aid for the
     * medtech to sift through model outputs. Per ADR-005.
     */
    @ColumnInfo(name = "is_repeat", defaultValue = "0")
    val isRepeat: Boolean = false,

    @ColumnInfo(name = "predictions_json")
    val predictionsJson: String? = null,

    @ColumnInfo(name = "image_width")
    val imageWidth: Int? = null,

    @ColumnInfo(name = "image_height")
    val imageHeight: Int? = null,
)
