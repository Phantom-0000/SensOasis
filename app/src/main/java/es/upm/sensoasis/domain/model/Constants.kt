package es.upm.sensoasis.domain.model

import java.time.Duration

/**
 * Shared timing, threshold, and coordinate constants across SensOasis layers.
 */
object Constants {
    /** Default validity window (20 minutes, within the 15-30 min spec) for a community report. */
    const val REPORT_TTL_MINUTES: Long = 20L
    val REPORT_TTL: Duration = Duration.ofMinutes(REPORT_TTL_MINUTES)

    /** Sampling window for a single TYPE_LIGHT capture (1.0 - 1.5 seconds). */
    const val LIGHT_CAPTURE_TIMEOUT_MS: Long = 1_500L

    /** Stable NEAR duration required before triggering private speech (2.0 seconds). */
    const val PROXIMITY_NEAR_TRIGGER_MS: Long = 2_000L

    /** Stable FAR duration required to interrupt active speech (0.3 - 0.5 seconds). */
    const val PROXIMITY_FAR_STOP_MS: Long = 400L

    /** Periodic ViewModel ticker interval for refreshing observation age and expiring reports. */
    const val UI_EXPIRY_REFRESH_INTERVAL_MS: Long = 30_000L

    /** Maximum wait time for a fresh GPS/network fix before falling back to Madrid reference coordinates. */
    const val LOCATION_TIMEOUT_MS: Long = 5_000L

    /** Default ambient-light classification thresholds (in lux). */
    const val LIGHT_LOW_MAX_LUX: Float = 500f
    const val LIGHT_MODERATE_MAX_LUX: Float = 5_000f

    /** Deterministic fallback coordinates in central Madrid (Puerta del Sol) for indoor classroom demos. */
    val MADRID_FALLBACK_COORDINATES = Coordinates(
        latitude = 40.4168,
        longitude = -3.7038
    )

    /** Reference coordinates for UPM Campus Sur. */
    val UPM_CAMPUS_SUR_COORDINATES = Coordinates(
        latitude = 40.3896,
        longitude = -3.6285
    )
}
