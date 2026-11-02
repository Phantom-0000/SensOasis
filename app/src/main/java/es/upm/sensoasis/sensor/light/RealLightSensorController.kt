package es.upm.sensoasis.sensor.light

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import es.upm.sensoasis.domain.contract.LightSensorController
import es.upm.sensoasis.domain.contract.ProximityStateProvider
import es.upm.sensoasis.domain.model.LightCaptureResult
import es.upm.sensoasis.domain.model.ProximityState
import es.upm.sensoasis.domain.model.SensorAvailability
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Handle for unregistering an active `TYPE_LIGHT` listener session.
 */
fun interface LightSensorSession {
    fun unregister()
}

/**
 * Thin hardware abstraction over Android's [SensorManager] for `TYPE_LIGHT` so that
 * [RealLightSensorController] can be deterministically unit-tested on the JVM.
 */
interface LightSensorHardware {
    val isAvailable: Boolean
    fun register(onLuxSample: (Float) -> Unit): LightSensorSession?
}

/**
 * Production [LightSensorHardware] backed by Android's [SensorManager] and [Sensor.TYPE_LIGHT].
 * Uses [SensorManager.SENSOR_DELAY_UI] (~60 ms) for responsive short-window sampling.
 */
internal class AndroidLightSensorHardware(context: Context) : LightSensorHardware {

    private val sensorManager: SensorManager? =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val lightSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)

    override val isAvailable: Boolean
        get() = sensorManager != null && lightSensor != null

    override fun register(onLuxSample: (Float) -> Unit): LightSensorSession? {
        val manager = sensorManager ?: return null
        val sensor = lightSensor ?: return null

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null || event.sensor?.type != Sensor.TYPE_LIGHT) return
                val values = event.values
                if (values == null || values.isEmpty()) return
                val lux = values[0]
                if (lux.isFinite() && lux >= 0f) {
                    onLuxSample(lux)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        val registered = manager.registerListener(
            listener,
            sensor,
            SensorManager.SENSOR_DELAY_UI
        )
        if (!registered) {
            manager.unregisterListener(listener)
            return null
        }

        val unregistered = AtomicBoolean(false)
        return LightSensorSession {
            if (unregistered.compareAndSet(false, true)) {
                manager.unregisterListener(listener)
            }
        }
    }
}

/**
 * Real implementation of [LightSensorController] using Android's `TYPE_LIGHT` sensor and
 * cross-checking [ProximityStateProvider] to reject covered/occluded captures:
 * - Short-circuits immediately with [LightCaptureResult.Occluded] if [ProximityState.NEAR]
 *   is detected before or at any point during the sampling window.
 * - Samples `TYPE_LIGHT` for up to `timeoutMs` (default 1,500 ms), computing the median lux
 *   across readings collected in this window (a single registration-time reading from an
 *   `ON_CHANGE` sensor under unchanged illumination is accepted as-is).
 * - Guarantees `SensorManager.unregisterListener` inside a `finally` block even if the
 *   calling coroutine is cancelled when the user navigates away mid-capture.
 */
class RealLightSensorController internal constructor(
    private val hardware: LightSensorHardware,
    private val proximityStateProvider: ProximityStateProvider,
    private val nowProvider: () -> Instant = { Instant.now() }
) : LightSensorController {

    constructor(
        context: Context,
        proximityStateProvider: ProximityStateProvider,
        nowProvider: () -> Instant = { Instant.now() }
    ) : this(
        hardware = AndroidLightSensorHardware(context),
        proximityStateProvider = proximityStateProvider,
        nowProvider = nowProvider
    )

    override val availability: SensorAvailability
        get() = if (hardware.isAvailable) {
            SensorAvailability.AVAILABLE
        } else {
            SensorAvailability.UNAVAILABLE
        }

    override suspend fun captureOnce(timeoutMs: Long): LightCaptureResult {
        if (availability == SensorAvailability.UNAVAILABLE) {
            return LightCaptureResult.Unavailable(
                LightObservationCalculator.REASON_TYPE_LIGHT_MISSING
            )
        }

        if (proximityStateProvider.availability == SensorAvailability.UNAVAILABLE) {
            return LightCaptureResult.Unavailable(
                LightObservationCalculator.REASON_PROXIMITY_UNCONFIRMED
            )
        }

        if (proximityStateProvider.state.value == ProximityState.NEAR) {
            return LightCaptureResult.Occluded
        }

        val samples = CopyOnWriteArrayList<Float>()
        val occludedDuringWindow = AtomicBoolean(false)
        var session: LightSensorSession? = null
        var registrationSucceeded = false

        try {
            session = hardware.register { lux ->
                if (proximityStateProvider.state.value == ProximityState.NEAR) {
                    occludedDuringWindow.set(true)
                } else if (lux.isFinite() && lux >= 0f) {
                    samples.add(lux)
                }
            }
            registrationSucceeded = session != null

            if (registrationSucceeded) {
                if (proximityStateProvider.state.value == ProximityState.NEAR) {
                    occludedDuringWindow.set(true)
                }

                if (!occludedDuringWindow.get() && timeoutMs > 0L) {
                    withTimeoutOrNull(timeoutMs) {
                        proximityStateProvider.state.first { state ->
                            if (state == ProximityState.NEAR) {
                                occludedDuringWindow.set(true)
                                true
                            } else {
                                false
                            }
                        }
                    }
                }
            }
        } finally {
            session?.unregister()
        }

        // Only readings delivered during THIS window are used. Android delivers an initial
        // event when an on-change sensor is activated, so a stationary capture still yields
        // one reading; readings from earlier captures are never reused (they could be stale
        // or come from a different place).
        return LightObservationCalculator.evaluateCapture(
            sensorAvailability = availability,
            proximityAvailability = proximityStateProvider.availability,
            registrationSucceeded = registrationSucceeded,
            occludedDuringWindow = occludedDuringWindow.get(),
            finalProximityState = proximityStateProvider.state.value,
            rawReadings = samples.toList(),
            observedAt = nowProvider()
        )
    }
}
