package es.upm.sensoasis.domain.model

import java.time.Duration
import java.time.Instant

/**
 * Qualitative outdoor/indoor ambient-light classification band.
 */
enum class LightBand {
    LOW,
    MODERATE,
    BRIGHT
}

/**
 * Sensor quality classification for an ambient-light capture attempt.
 */
enum class LightQuality {
    VALID,
    OCCLUDED,
    UNAVAILABLE
}

/**
 * Short-lived ambient-light observation captured during a fountain report.
 * Note: report expiry ([FountainReport.expiresAt]) belongs to the parent [FountainReport].
 */
data class LightObservation(
    val ambientLux: Float?,
    val lightBand: LightBand?,
    val quality: LightQuality,
    val observedAt: Instant
)

/**
 * Community fountain status report published and received via MQTT.
 */
data class FountainReport(
    val fountainId: String,
    val status: FountainStatus,
    val lightObservation: LightObservation?,
    val observedAt: Instant,
    val expiresAt: Instant
) {
    init {
        require('/' !in fountainId) { "fountainId must not contain '/': $fountainId" }
    }

    fun isExpired(now: Instant = Instant.now()): Boolean = !now.isBefore(expiresAt)

    fun ageMinutes(now: Instant = Instant.now()): Long =
        Duration.between(observedAt, now).toMinutes().coerceAtLeast(0L)
}
