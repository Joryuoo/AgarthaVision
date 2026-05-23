package com.agarthavision.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.agarthavision.domain.model.LocationResult
import com.agarthavision.domain.repository.LocationProvider
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * [LocationProvider] backed by [FusedLocationProviderClient].
 *
 * Returns `null` (never throws) when permission is denied, when the OS
 * delivers no fix, or when [timeoutMillis] elapses before a fix arrives.
 * See ADR-001 for rationale.
 */
@Singleton
class FusedLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: FusedLocationProviderClient,
) : LocationProvider {

    override suspend fun getCurrentLocation(timeoutMillis: Long): LocationResult? {
        val fineGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) return null

        return withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine { continuation ->
                val cts = CancellationTokenSource()

                continuation.invokeOnCancellation { cts.cancel() }

                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                    .addOnSuccessListener { location ->
                        continuation.resume(
                            location?.let {
                                LocationResult(
                                    latitude = it.latitude,
                                    longitude = it.longitude,
                                    accuracyMeters = it.accuracy,
                                    capturedAt = Instant.now(),
                                )
                            },
                        )
                    }
                    .addOnFailureListener {
                        continuation.resume(null)
                    }
            }
        }
    }
}
