package es.upm.sensoasis.domain.model

import java.time.Duration
import java.time.Instant
import kotlin.math.roundToLong

/**
 * Formats ambient-light values for display.
 * The original sensor value remains unchanged.
 */
fun formatLux(lux: Float?): String {
    if (lux == null || !lux.isFinite()) return "-- lux"

    val rounded = lux.coerceAtLeast(0f).roundToLong()
    return "$rounded lux"
}

/**
 * Converts light bands into understandable descriptions.
 */
fun formatLightBand(band: LightBand?): String {
    return when (band) {
        LightBand.BRIGHT ->
            "Bright sunlight (high glare / exposed)"

        LightBand.MODERATE ->
            "Moderate light (comfortable visibility)"

        LightBand.LOW ->
            "Low light (shaded or dim area)"

        null ->
            "Light level unavailable"
    }
}

/**
 * Converts sensor quality into understandable descriptions.
 */
fun formatLightQuality(quality: LightQuality): String {
    return when (quality) {
        LightQuality.VALID ->
            "Valid reading"

        LightQuality.OCCLUDED ->
            "Sensor covered"

        LightQuality.UNAVAILABLE ->
            "Sensor unavailable"
    }
}

/**
 * Shows how long ago the light observation was captured.
 */
fun formatObservationAge(
    observedAt: Instant,
    now: Instant = Instant.now()
): String {
    val minutes = Duration.between(observedAt, now)
        .toMinutes()
        .coerceAtLeast(0L)

    return when {
        minutes < 1 -> "Just now"
        minutes == 1L -> "1 minute ago"
        minutes < 60 -> "$minutes minutes ago"
        minutes / 60 == 1L -> "1 hour ago"
        minutes < 24 * 60 -> "${minutes / 60} hours ago"
        minutes / (24 * 60) == 1L -> "1 day ago"
        else -> "${minutes / (24 * 60)} days ago"
    }
}

/**
 * Maps raw sensor unavailable reason codes to friendly plain-language explanations.
 */
fun formatUnavailableReason(reason: String): String {
    return when (reason) {
        "TYPE_LIGHT_MISSING" -> "Light sensor not supported on this device"
        "PROXIMITY_UNCONFIRMED" -> "Proximity sensor check unconfirmed"
        "SENSOR_REGISTRATION_FAILED" -> "Failed to start light sensor"
        "LIGHT_TIMEOUT" -> "Measurement timed out"
        else -> reason.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
    }
}
