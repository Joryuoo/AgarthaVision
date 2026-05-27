package com.agarthavision.domain.model

data class Detection(
    val id: String,
    val sampleId: String,
    val classLabel: String,
    val confidence: Float,
    val bboxX: Float,
    val bboxY: Float,
    val bboxW: Float,
    val bboxH: Float,
    val verdict: DetectionVerdict,
    val expertClass: String?,
    val verifiedByUser: Boolean,
)
