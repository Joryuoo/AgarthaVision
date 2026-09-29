package com.agarthavision.data.local.mapper

import com.agarthavision.data.local.entity.SampleEntity
import com.agarthavision.domain.inference.InferenceState
import com.agarthavision.domain.model.Sample
import com.agarthavision.domain.model.SampleStatus

fun Sample.toEntity(): SampleEntity =
    SampleEntity(
        sampleId = id,
        sessionId = sessionId,
        userId = requireNotNull(userId) { "Sample $id must be claimed before mapping to a sync-eligible entity." },
        deviceId = deviceId,
        timestamp = timestamp,
        verifiedAt = verifiedAt,
        imagePath = filePath,
        storagePath = storagePath,
        inferenceModelVersion = inferenceModelVersion,
        needsReannotation = needsReannotation,
        userNote = userNote,
        isManual = isManual,
        isEdited = isEdited,
        status = status.value,
    )

fun SampleEntity.toDomain(): Sample =
    Sample(
        id = sampleId,
        userId = userId,
        timestamp = timestamp,
        verifiedAt = verifiedAt.takeIf { it > 0L } ?: timestamp,
        deviceId = deviceId,
        sessionId = sessionId,
        filePath = imagePath,
        storagePath = storagePath,
        inferenceModelVersion = inferenceModelVersion,
        needsReannotation = needsReannotation,
        userNote = userNote,
        isManual = isManual,
        isEdited = isEdited,
        status = SampleStatus.entries.firstOrNull { it.value == status } ?: SampleStatus.FLAGGED,
    )

/**
 * This sample's [InferenceState], with `is_manual` taken into account.
 *
 * `is_manual` wins. A manual sample has no model output whatever `inference_state` says, and
 * two kinds of row carry the column's `ready` default without having earned it: rows written
 * before version 23, and rows pulled from Supabase, which never sees the column. An unknown
 * value reads as ready, the state every row was implicitly in before the queue existed.
 */
fun SampleEntity.effectiveInferenceState(): InferenceState = when {
    isManual -> InferenceState.MANUAL
    else -> InferenceState.fromValue(inferenceState) ?: InferenceState.READY
}
