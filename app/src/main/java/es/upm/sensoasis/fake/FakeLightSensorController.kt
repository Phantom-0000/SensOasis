package es.upm.sensoasis.fake

import es.upm.sensoasis.domain.contract.LightSensorController
import es.upm.sensoasis.domain.contract.ProximityStateProvider
import es.upm.sensoasis.domain.model.LightBand
import es.upm.sensoasis.domain.model.LightCaptureResult
import es.upm.sensoasis.domain.model.LightObservation
import es.upm.sensoasis.domain.model.LightQuality
import es.upm.sensoasis.domain.model.ProximityState
import es.upm.sensoasis.domain.model.SensorAvailability
import kotlinx.coroutines.delay
import java.time.Instant

/**
 * Fake implementation of [LightSensorController].
 *
 * Enforces the cross-sensor occlusion rule:
 * - Checks [proximityStateProvider.state] during capture:
 *   - [ProximityState.NEAR] -> returns [LightCaptureResult.Occluded]
 *   - [ProximityState.UNKNOWN] -> returns [LightCaptureResult.Unavailable]("PROXIMITY_UNCONFIRMED")
 *   - [ProximityState.FAR] -> returns a valid reading (defaulting to 3200 lux).
 */
class FakeLightSensorController(
    private val proximityStateProvider: ProximityStateProvider,
    override var availability: SensorAvailability = SensorAvailability.AVAILABLE,
    var simulatedCaptureDelayMs: Long = 400L,
    var simulatedLux: Float = 3200f,
    var simulatedBand: LightBand = LightBand.MODERATE,
    var forcedOutcome: LightCaptureResult? = null,
    var nowProvider: () -> Instant = { Instant.now() }
) : LightSensorController {

    override suspend fun captureOnce(timeoutMs: Long): LightCaptureResult {
        if (availability == SensorAvailability.UNAVAILABLE) {
            return LightCaptureResult.Unavailable("TYPE_LIGHT_MISSING")
        }
        val waitTime = simulatedCaptureDelayMs.coerceAtMost(timeoutMs)
        if (waitTime > 0) delay(waitTime)

        forcedOutcome?.let { return it }

        return when (proximityStateProvider.state.value) {
            ProximityState.NEAR -> LightCaptureResult.Occluded
            ProximityState.UNKNOWN -> LightCaptureResult.Unavailable("PROXIMITY_UNCONFIRMED")
            ProximityState.FAR -> LightCaptureResult.Valid(
                LightObservation(
                    ambientLux = simulatedLux,
                    lightBand = simulatedBand,
                    quality = LightQuality.VALID,
                    observedAt = nowProvider()
                )
            )
        }
    }
}
