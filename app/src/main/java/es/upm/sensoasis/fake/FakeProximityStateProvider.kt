package es.upm.sensoasis.fake

import es.upm.sensoasis.domain.contract.ProximityStateProvider
import es.upm.sensoasis.domain.model.ProximityState
import es.upm.sensoasis.domain.model.SensorAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fake implementation of [ProximityStateProvider] for parallel development, debug panel, and unit tests.
 *
 * Tracks [isActive] (`start()` / `stop()` calls from the ViewModel) and exposes
 * [setSimulatedState] so tests and debug controls can toggle between [ProximityState.FAR],
 * [ProximityState.NEAR], and [ProximityState.UNKNOWN].
 */
class FakeProximityStateProvider(
    override var availability: SensorAvailability = SensorAvailability.AVAILABLE,
    initialState: ProximityState = ProximityState.FAR
) : ProximityStateProvider {

    private val _state = MutableStateFlow(initialState)
    override val state: StateFlow<ProximityState> = _state.asStateFlow()

    var isActive: Boolean = false
        private set

    var startCount: Int = 0
        private set

    var stopCount: Int = 0
        private set

    override fun start() {
        isActive = true
        startCount++
        if (_state.value == ProximityState.UNKNOWN && availability == SensorAvailability.AVAILABLE) {
            _state.value = ProximityState.FAR
        }
    }

    override fun stop() {
        isActive = false
        stopCount++
    }

    fun setSimulatedState(newState: ProximityState) {
        _state.value = newState
    }
}
