package es.upm.sensoasis.domain.model

import java.time.Instant

/**
 * Lifecycle state of the proximity-triggered accessibility speech feature:
 * - privateSpeechEnabled=true: IDLE -> WAITING_FOR_NEAR -> SPEAKING -> WAITING_FOR_NEAR
 * - privateSpeechEnabled=false: ANY -> IDLE
 */
enum class SpeechState {
    IDLE,
    WAITING_FOR_NEAR,
    SPEAKING,
    ERROR
}

/**
 * Formatted spoken summary text ready for TTS playback.
 */
@JvmInline
value class SpeechText(val value: String)

/**
 * Structured input assembled by the ViewModel for the speech summary builder.
 * Note: [airQuality] is optional and may be null.
 */
data class SpeechSummaryInput(
    val fountain: Fountain,
    val officialStatus: FountainStatus,
    val communityReport: FountainReport?,
    val airQuality: AirQualitySummary?,
    val lightObservation: LightObservation?,
    val distanceMeters: Int?,
    val now: Instant = Instant.now()
)
