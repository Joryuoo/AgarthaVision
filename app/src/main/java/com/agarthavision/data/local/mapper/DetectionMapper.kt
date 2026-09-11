package com.agarthavision.data.local.mapper

import com.agarthavision.data.local.entity.DetectionEntity
import com.agarthavision.domain.model.Detection
import com.agarthavision.domain.model.DetectionVerdict
import com.agarthavision.domain.model.EggStage

fun DetectionEntity.toDomain(): Detection =
    Detection(
        id = detectionId,
        sampleId = sampleId,
        classLabel = classLabel,
        confidence = confidence,
        bboxX = bboxX,
        bboxY = bboxY,
        bboxW = bboxW,
        bboxH = bboxH,
        verdict = DetectionVerdict.fromValue(verdict),
        expertClass = expertClass,
        verifiedByUser = verifiedByUser,
        stage = stage?.let { EggStage.fromValue(it) },
    )

fun Detection.toEntity(): DetectionEntity =
    DetectionEntity(
        detectionId = id,
        sampleId = sampleId,
        classLabel = classLabel,
        confidence = confidence,
        bboxX = bboxX,
        bboxY = bboxY,
        bboxW = bboxW,
        bboxH = bboxH,
        verdict = verdict.value,
        expertClass = expertClass,
        verifiedByUser = verifiedByUser,
        stage = stage?.value,
    )
