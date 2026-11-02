package es.upm.sensoasis.sensor.light

import es.upm.sensoasis.domain.model.Constants
import es.upm.sensoasis.domain.model.LightBand
import es.upm.sensoasis.domain.model.LightCaptureResult
import es.upm.sensoasis.domain.model.LightObservation
import es.upm.sensoasis.domain.model.LightQuality
import es.upm.sensoasis.domain.model.ProximityState
import es.upm.sensoasis.domain.model.SensorAvailability
import java.time.Instant

/**
 * Pure-Kotlin calculation and outcome evaluation helper for ambient-light capture:
 * - Filters out invalid hardware readings (negative, NaN, Infinite)
 * - Computes the median lux across odd, even, or single-element sample lists
 * - Classifies ambient lux into [LightBand.LOW] (< 500), [LightBand.MODERATE] (500..5000),
 *   or [LightBand.BRIGHT] (> 5000)
 * - Evaluates the final [LightCaptureResult] with deterministic occlusion and availability priority
 */
object LightObservationCalculator {

    const val REASON_TYPE_LIGHT_MISSING: String = "TYPE_LIGHT_MISSING"
    const val REASON_SENSOR_REGISTRATION_FAILED: String = "SENSOR_REGISTRATION_FAILED"
    const val REASON_PROXIMITY_UNCONFIRMED: String = "PROXIMITY_UNCONFIRMED"
    const val REASON_LIGHT_TIMEOUT: String = "LIGHT_TIMEOUT"

    /**
     * Retains only finite, non-negative lux values.
     */
    fun sanitizeReadings(rawReadings: List<Float>): List<Float> =
        rawReadings.filter { it.isFinite() && it >= 0f }

    /**
     * Computes the median of the sanitized readings, or returns `null` if no valid reading exists.
     * Supports single-element lists (common on stationary `ON_CHANGE` light sensors) as well as
     * odd and even sample counts.
     */
    fun computeMedianLux(rawReadings: List<Float>): Float? {
        val sorted = sanitizeReadings(rawReadings).sorted()
        if (sorted.isEmpty()) return null
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2f
        }
    }

    /**
     * Classifies a valid lux measurement into [LightBand] using the frozen project thresholds:
     * - `< 500.0` lux -> [LightBand.LOW]
     * - `500.0 .. 5000.0` lux -> [LightBand.MODERATE]
     * - `> 5000.0` lux -> [LightBand.BRIGHT]
     */
    fun classifyBand(lux: Float): LightBand = when {
        lux < Constants.LIGHT_LOW_MAX_LUX -> LightBand.LOW
        lux <= Constants.LIGHT_MODERATE_MAX_LUX -> LightBand.MODERATE
        else -> LightBand.BRIGHT
    }

    /**
     * Evaluates the outcome of a single capture window:
     * 1. Missing `TYPE_LIGHT` sensor -> [LightCaptureResult.Unavailable] (`TYPE_LIGHT_MISSING`)
     * 2. Any [ProximityState.NEAR] event before or during the window -> [LightCaptureResult.Occluded]
     * 3. Failed `SensorManager.registerListener` -> [LightCaptureResult.Unavailable] (`SENSOR_REGISTRATION_FAILED`)
     * 4. Unconfirmed proximity ([ProximityState.UNKNOWN]) at window end -> [LightCaptureResult.Unavailable] (`PROXIMITY_UNCONFIRMED`)
     * 5. Zero valid readings within the window -> [LightCaptureResult.Unavailable] (`LIGHT_TIMEOUT`)
     * 6. Valid reading(s) with confirmed [ProximityState.FAR] -> [LightCaptureResult.Valid]
     */
    fun evaluateCapture(
        sensorAvailability: SensorAvailability,
        proximityAvailability: SensorAvailability = SensorAvailability.AVAILABLE,
        registrationSucceeded: Boolean,
        occludedDuringWindow: Boolean,
        finalProximityState: ProximityState,
        rawReadings: List<Float>,
        observedAt: Instant
    ): LightCaptureResult {
        if (sensorAvailability == SensorAvailability.UNAVAILABLE) {
            return LightCaptureResult.Unavailable(REASON_TYPE_LIGHT_MISSING)
        }
        if (proximityAvailability == SensorAvailability.UNAVAILABLE) {
            return LightCaptureResult.Unavailable(REASON_PROXIMITY_UNCONFIRMED)
        }
        if (occludedDuringWindow || finalProximityState == ProximityState.NEAR) {
            return LightCaptureResult.Occluded
        }
        if (!registrationSucceeded) {
            return LightCaptureResult.Unavailable(REASON_SENSOR_REGISTRATION_FAILED)
        }
        if (finalProximityState != ProximityState.FAR) {
            return LightCaptureResult.Unavailable(REASON_PROXIMITY_UNCONFIRMED)
        }
        val medianLux = computeMedianLux(rawReadings)
            ?: return LightCaptureResult.Unavailable(REASON_LIGHT_TIMEOUT)

        return LightCaptureResult.Valid(
            LightObservation(
                ambientLux = medianLux,
                lightBand = classifyBand(medianLux),
                quality = LightQuality.VALID,
                observedAt = observedAt
            )
        )
    }
}
