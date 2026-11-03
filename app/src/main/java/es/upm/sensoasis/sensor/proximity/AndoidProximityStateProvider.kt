package es.upm.sensoasis.sensor.proximity

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import es.upm.sensoasis.domain.contract.ProximityStateProvider
import es.upm.sensoasis.domain.model.ProximityState
import es.upm.sensoasis.domain.model.SensorAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidProximityStateProvider(
    context: Context
) : ProximityStateProvider, SensorEventListener {

    private val sensorManager =
        context.applicationContext.getSystemService(
            Context.SENSOR_SERVICE
        ) as SensorManager

    private val proximitySensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    private val _state =
        MutableStateFlow(ProximityState.UNKNOWN)

    override val state: StateFlow<ProximityState> =
        _state.asStateFlow()

    private val _availability =
        MutableStateFlow(SensorAvailability.UNAVAILABLE)

    override val availability: SensorAvailability =
        if (proximitySensor != null) SensorAvailability.AVAILABLE
        else SensorAvailability.UNAVAILABLE

    override fun start() {
        _state.value = ProximityState.UNKNOWN

        val sensor = proximitySensor

        if (sensor == null) {
            _availability.value = SensorAvailability.UNAVAILABLE
            return
        }

        _availability.value = SensorAvailability.AVAILABLE

        sensorManager.registerListener(
            this,
            sensor,
            SensorManager.SENSOR_DELAY_NORMAL
        )
    }

    override fun stop() {
        sensorManager.unregisterListener(this)
        _state.value = ProximityState.UNKNOWN
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val sensor = proximitySensor ?: return

        if (event == null || event.values.isEmpty()) return

        _state.value =
            if (event.values[0] < sensor.maximumRange) {
                ProximityState.NEAR
            } else {
                ProximityState.FAR
            }
    }

    override fun onAccuracyChanged(
        sensor: Sensor?,
        accuracy: Int
    ) = Unit
}