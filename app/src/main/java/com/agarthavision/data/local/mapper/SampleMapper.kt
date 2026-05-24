package com.agarthavision.data.local.mapper

import com.agarthavision.data.local.entity.SampleEntity
import com.agarthavision.domain.model.Sample
import com.agarthavision.domain.model.SampleStatus

fun Sample.toEntity(): SampleEntity =
    SampleEntity(
        sampleId = id,
        sessionId = sessionId,
        deviceId = deviceId,
        timestamp = timestamp,
        imagePath = filePath,
        gpsLatitude = latitude,
        gpsLongitude = longitude,
        gpsAccuracy = accuracyMeters,
        status = status.value,
    )

fun SampleEntity.toDomain(): Sample =
    Sample(
        id = sampleId,
        timestamp = timestamp,
        deviceId = deviceId,
        sessionId = sessionId,
        filePath = imagePath,
        latitude = gpsLatitude,
        longitude = gpsLongitude,
        accuracyMeters = gpsAccuracy,
        status = SampleStatus.entries.firstOrNull { it.value == status } ?: SampleStatus.FLAGGED,
    )
