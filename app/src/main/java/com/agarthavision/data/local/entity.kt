package com.agarthavision.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for locally persisted captured microscopy samples.
 *
 * The image itself is stored on disk. This entity only stores the file path and
 * capture metadata required for traceability.
 */
@Entity(tableName = "samples")
data class SampleEntity(
    @PrimaryKey
    @ColumnInfo(name = "sample_id")
    val sampleId: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "device_id")
    val deviceId: String,

    @ColumnInfo(name = "session_id")
    val sessionId: String,

    @ColumnInfo(name = "image_path")
    val imagePath: String,

    @ColumnInfo(name = "gps_latitude")
    val gpsLatitude: Double?,

    @ColumnInfo(name = "gps_longitude")
    val gpsLongitude: Double?,

    @ColumnInfo(name = "status")
    val status: String,
)