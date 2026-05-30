package com.agarthavision.domain.model

import java.time.Instant

/**
 * Snapshot of the device's GPS position at the moment of a sample capture.
 *
 * @param latitude Decimal degrees, WGS-84.
 * @param longitude Decimal degrees, WGS-84.
 * @param accuracyMeters Horizontal accuracy radius reported by the OS.
 * @param capturedAt Wall-clock time when the fix was obtained.
 */
data class LocationResult(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val capturedAt: Instant,
)
