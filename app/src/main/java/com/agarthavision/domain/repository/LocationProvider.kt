package com.agarthavision.domain.repository

import com.agarthavision.domain.model.LocationResult

/**
 * Abstraction over the device location API.
 *
 * Returns [LocationResult] when a GPS fix is available, or `null` when:
 * - `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` is not granted
 * - No fix arrives within [timeoutMillis]
 * - The OS returns no location
 *
 * Implementations must never throw [SecurityException].
 */
interface LocationProvider {
    suspend fun getCurrentLocation(timeoutMillis: Long = 5_000L): LocationResult?
}
