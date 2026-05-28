package com.agarthavision.data.local.mapper

import com.agarthavision.data.local.entity.SampleEntity
import com.agarthavision.domain.model.Sample
import com.agarthavision.domain.model.SampleStatus

fun Sample.toEntity(): SampleEntity =
    SampleEntity(
        sampleId = id,
        sessionId = sessionId,
        userId = userId,
        deviceId = deviceId,
        timestamp = timestamp,
        verifiedAt = verifiedAt,
        imagePath = filePath,
        storagePath = storagePath,
        inferenceModelVersion = inferenceModelVersion,
        needsReannotation = needsReannotation,
        userNote = userNote,
        isManual = isManual,
        isRepeat = isRepeat,
        gpsLatitude = latitude,
        gpsLongitude = longitude,
        gpsAccuracy = accuracyMeters,
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
        isRepeat = isRepeat,
        latitude = gpsLatitude,
        longitude = gpsLongitude,
        accuracyMeters = gpsAccuracy,
        status = SampleStatus.entries.firstOrNull { it.value == status } ?: SampleStatus.FLAGGED,
    )
