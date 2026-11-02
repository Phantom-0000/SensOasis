package es.upm.sensoasis.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import es.upm.sensoasis.domain.contract.LocationProvider
import es.upm.sensoasis.domain.contract.LocationSnapshot
import es.upm.sensoasis.domain.model.Constants
import es.upm.sensoasis.domain.model.Coordinates
import es.upm.sensoasis.domain.model.DataResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Real implementation of [LocationProvider] backed by Google Play Services
 * [FusedLocationProviderClient].
 *
 * Behavior:
 * - Immediately seeds [currentLocation] with [Constants.MADRID_FALLBACK_COORDINATES] (`isFallback = true`)
 *   so fountain distance calculations work out-of-the-box before permissions are granted.
 * - Checks `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` at runtime.
 * - When granted, attempts a fresh fix via `getCurrentLocation` (`PRIORITY_BALANCED_POWER_ACCURACY`)
 *   with a [Constants.LOCATION_TIMEOUT_MS] (5-second) timeout first, and falls back to `lastLocation`
 *   if the fresh request times out or returns null.
 * - When permission is denied, location services are disabled, or both `getCurrentLocation` and
 *   `lastLocation` return null indoors, gracefully falls back to [Constants.MADRID_FALLBACK_COORDINATES]
 *   (`isFallback = true`).
 */
class FusedLocationProvider(
    context: Context,
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context.applicationContext)
) : LocationProvider {

    private val appContext: Context = context.applicationContext

    private val fallbackSnapshot = LocationSnapshot(
        coordinates = Constants.MADRID_FALLBACK_COORDINATES,
        isFallback = true
    )

    private val _currentLocation = MutableStateFlow<DataResult<LocationSnapshot>>(
        DataResult.Success(fallbackSnapshot)
    )
    override val currentLocation: StateFlow<DataResult<LocationSnapshot>> = _currentLocation.asStateFlow()

    fun hasLocationPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineGranted || coarseGranted
    }

    @SuppressLint("MissingPermission")
    override suspend fun refreshLocation(): DataResult<LocationSnapshot> {
        if (!hasLocationPermission()) {
            val fallbackResult = DataResult.Success(fallbackSnapshot)
            _currentLocation.value = fallbackResult
            return fallbackResult
        }

        val resolvedLocation: Location? = try {
            withTimeoutOrNull(Constants.LOCATION_TIMEOUT_MS) {
                awaitCurrentLocation()
            } ?: withTimeoutOrNull(LAST_LOCATION_TIMEOUT_MS) {
                awaitLastLocation()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }

        val snapshot = if (resolvedLocation != null &&
            !resolvedLocation.latitude.isNaN() &&
            !resolvedLocation.longitude.isNaN()
        ) {
            LocationSnapshot(
                coordinates = Coordinates(
                    latitude = resolvedLocation.latitude,
                    longitude = resolvedLocation.longitude
                ),
                isFallback = false
            )
        } else {
            // Keep a previously obtained real fix instead of jumping back to Puerta del Sol
            // when a later refresh fails (e.g. walking indoors).
            val previous = (_currentLocation.value as? DataResult.Success)?.value
            if (previous != null && !previous.isFallback) previous else fallbackSnapshot
        }

        val result = DataResult.Success(snapshot)
        _currentLocation.value = result
        return result
    }

    @SuppressLint("MissingPermission")
    private suspend fun awaitLastLocation(): Location? =
        suspendCancellableCoroutine { continuation ->
            fusedClient.lastLocation
                .addOnSuccessListener { location ->
                    if (continuation.isActive) {
                        continuation.resume(location)
                    }
                }
                .addOnFailureListener {
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
                .addOnCanceledListener {
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
        }

    @SuppressLint("MissingPermission")
    private suspend fun awaitCurrentLocation(): Location? =
        suspendCancellableCoroutine { continuation ->
            val tokenSource = CancellationTokenSource()
            continuation.invokeOnCancellation {
                tokenSource.cancel()
            }
            fusedClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                tokenSource.token
            )
                .addOnSuccessListener { location ->
                    if (continuation.isActive) {
                        continuation.resume(location)
                    }
                }
                .addOnFailureListener {
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
                .addOnCanceledListener {
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
        }

    private companion object {
        const val LAST_LOCATION_TIMEOUT_MS: Long = 2_000L
    }
}
