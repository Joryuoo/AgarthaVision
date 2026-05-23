package com.agarthavision.core.location

/**
 * Interface for providing device location (GPS).
 */
interface LocationProvider {
    suspend fun getCurrentLocation(): LocationData?
}

data class LocationData(
    val latitude: Double,
    val longitude: Double
)
