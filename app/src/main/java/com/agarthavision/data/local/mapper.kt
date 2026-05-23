package com.agarthavision.data.local
import com.agarthavision.domain.model.Sample
import com.agarthavision.domain.model.SampleStatus

/**
 * Converts a domain [Sample] into a Room [SampleEntity].
 */
fun Sample.toEntity(): SampleEntity =
    SampleEntity(
        sampleId = id,
        timestamp = timestamp,
        deviceId = deviceId,
        sessionId = sessionId,
        imagePath = filePath,
        gpsLatitude = latitude,
        gpsLongitude = longitude,
        status = status.value,
    )

/**
 * Converts a Room [SampleEntity] into a domain [Sample].
 */
fun SampleEntity.toDomain(): Sample =
    Sample(
        id = sampleId,
        timestamp = timestamp,
        deviceId = deviceId,
        sessionId = sessionId,
        filePath = imagePath,
        latitude = gpsLatitude,
        longitude = gpsLongitude,
        status = SampleStatus.entries.firstOrNull { it.value == status } ?: SampleStatus.CAPTURED,
    )