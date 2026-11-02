package es.upm.sensoasis.fake

import es.upm.sensoasis.domain.contract.LocationProvider
import es.upm.sensoasis.domain.contract.LocationSnapshot
import es.upm.sensoasis.domain.model.Constants
import es.upm.sensoasis.domain.model.Coordinates
import es.upm.sensoasis.domain.model.DataResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fake implementation of [LocationProvider] returning the Madrid fallback reference coordinates
 * by default, with helper methods to simulate live GPS fixes or errors in tests.
 */
class FakeLocationProvider(
    initialSnapshot: LocationSnapshot = LocationSnapshot(
        coordinates = Constants.MADRID_FALLBACK_COORDINATES,
        isFallback = true
    )
) : LocationProvider {

    private val _currentLocation = MutableStateFlow<DataResult<LocationSnapshot>>(
        DataResult.Success(initialSnapshot)
    )
    override val currentLocation: StateFlow<DataResult<LocationSnapshot>> = _currentLocation.asStateFlow()

    override suspend fun refreshLocation(): DataResult<LocationSnapshot> = _currentLocation.value

    fun setSimulatedLocation(coordinates: Coordinates, isFallback: Boolean = false) {
        _currentLocation.value = DataResult.Success(
            LocationSnapshot(coordinates = coordinates, isFallback = isFallback)
        )
    }

    fun setSimulatedResult(result: DataResult<LocationSnapshot>) {
        _currentLocation.value = result
    }
}
