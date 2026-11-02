package es.upm.sensoasis.domain.contract

import es.upm.sensoasis.domain.model.ProximityState
import es.upm.sensoasis.domain.model.SensorAvailability
import kotlinx.coroutines.flow.StateFlow

/**
 * Exposes the current [ProximityState] (NEAR, FAR, UNKNOWN) from TYPE_PROXIMITY.
 *
 * Lifecycle Rule:
 * Only the ViewModel calls [start] and [stop] (active when ReportScreen is open or
 * private speech is enabled). Light and speech controllers read [state] without calling [start]/[stop]
 * directly so neither module accidentally stops the listener while the other needs it.
 */
interface ProximityStateProvider {
    val availability: SensorAvailability

    val state: StateFlow<ProximityState>

    fun start()

    fun stop()
}
