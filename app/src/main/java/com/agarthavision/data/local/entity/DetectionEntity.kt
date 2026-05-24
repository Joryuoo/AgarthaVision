package com.agarthavision.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for a single detection inside a verified sample.
 *
 * Mirrors the Supabase `detections` table. Bounding box coords are normalized 0–1.
 * Each verified sample has zero or more detections.
 */
@Entity(
    tableName = "detections",
    foreignKeys = [
        ForeignKey(
            entity = SampleEntity::class,
            parentColumns = ["sample_id"],
            childColumns = ["sample_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sample_id"), Index("class_label")],
)
data class DetectionEntity(
    @PrimaryKey
    @ColumnInfo(name = "detection_id")
    val detectionId: String,

    @ColumnInfo(name = "sample_id")
    val sampleId: String,

    @ColumnInfo(name = "class_label")
    val classLabel: String,

    @ColumnInfo(name = "confidence")
    val confidence: Float,

    @ColumnInfo(name = "bbox_x")
    val bboxX: Float,

    @ColumnInfo(name = "bbox_y")
    val bboxY: Float,

    @ColumnInfo(name = "bbox_w")
    val bboxW: Float,

    @ColumnInfo(name = "bbox_h")
    val bboxH: Float,

    @ColumnInfo(name = "verified_by_user")
    val verifiedByUser: Boolean,
)
