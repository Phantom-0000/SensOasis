package es.upm.sensoasis.domain.contract

import es.upm.sensoasis.domain.model.Constants
import es.upm.sensoasis.domain.model.LightCaptureResult
import es.upm.sensoasis.domain.model.SensorAvailability

/**
 * Captures a short window (1.0 - 1.5 seconds) of TYPE_LIGHT readings during the report flow,
 * verifies through the injected [ProximityStateProvider] that the sensor is not occluded (NEAR),
 * computes the median, and classifies the result.
 */
interface LightSensorController {
    val availability: SensorAvailability

    suspend fun captureOnce(
        timeoutMs: Long = Constants.LIGHT_CAPTURE_TIMEOUT_MS
    ): LightCaptureResult
}
