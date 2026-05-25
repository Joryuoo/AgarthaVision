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
)
