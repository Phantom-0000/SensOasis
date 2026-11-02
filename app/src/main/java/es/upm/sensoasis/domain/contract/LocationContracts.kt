package es.upm.sensoasis.domain.contract

import es.upm.sensoasis.domain.model.Coordinates
import es.upm.sensoasis.domain.model.DataResult
import kotlinx.coroutines.flow.StateFlow

/**
 * Snapshot of the user's current coordinates, indicating whether the deterministic
 * Madrid fallback coordinate is being used (e.g., indoors without GPS or when permission is denied).
 */
data class LocationSnapshot(
    val coordinates: Coordinates,
    val isFallback: Boolean
)

/**
 * Provides the user's current device location with automatic fallback to Madrid reference
 * coordinates when GPS is unavailable indoors or location permission is denied.
 */
interface LocationProvider {
    val currentLocation: StateFlow<DataResult<LocationSnapshot>>

    suspend fun refreshLocation(): DataResult<LocationSnapshot>
}

/**
 * Computes great-circle distance in meters between two geographic coordinates.
 */
interface DistanceCalculator {
    fun distanceMeters(from: Coordinates, to: Coordinates): Int
}
