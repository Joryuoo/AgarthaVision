package com.agarthavision.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.agarthavision.domain.model.DetectionVerdict

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
    indices = [Index("sample_id"), Index("class_label"), Index("verdict")],
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

    /**
     * Bounding-box geometry in source-image pixels (top-left + size). Nullable
     * to support manual captures (`samples.is_manual = true`) where the medtech
     * tags the whole frame without drawing a box — all four columns are null for
     * those rows. AI-confirmed detections always have non-null values. Per ADR-005.
     */
    @ColumnInfo(name = "bbox_x")
    val bboxX: Float?,

    @ColumnInfo(name = "bbox_y")
    val bboxY: Float?,

    @ColumnInfo(name = "bbox_w")
    val bboxW: Float?,

    @ColumnInfo(name = "bbox_h")
    val bboxH: Float?,

    @ColumnInfo(name = "verdict", defaultValue = "'confirmed'")
    val verdict: String = DetectionVerdict.CONFIRMED.value,

    @ColumnInfo(name = "expert_class")
    val expertClass: String? = null,

    @ColumnInfo(name = "verified_by_user")
    val verifiedByUser: Boolean = true,

    @ColumnInfo(name = "stage")
    val stage: String? = null,

    /**
     * `true` when the medtech made a deliberate species selection on this box, including
     * re-picking the value that was pre-filled from the model output.
     *
     * Provenance, not a verdict. Species fields are pre-filled from the model so the medtech
     * edits only what is wrong, which means an untouched submission produces
     * [DetectionVerdict.CONFIRMED] — "a human did not object" silently recorded as "a human
     * confirmed this". Since `detections` doubles as the retraining corpus, that distinction
     * matters: retraining should weight `false` rows lower. `verdict` keeps its existing
     * meaning and is unaffected.
     *
     * Deliberately **not** `verified_by_user`, which is dead drift — dropped from Supabase in
     * `0002_verification_fields.sql`, still hardcoded `true` at every write site here, and
     * cleaned up separately by ticket 86d4akgmf.
     */
    @ColumnInfo(name = "species_touched", defaultValue = "0")
    val speciesTouched: Boolean = false,
)
