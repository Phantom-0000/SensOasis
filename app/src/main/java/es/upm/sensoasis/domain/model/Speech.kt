package es.upm.sensoasis.domain.model

import java.time.Instant

/**
 * Lifecycle state of the proximity-triggered accessibility speech feature:
 * - privateSpeechEnabled=true: IDLE -> WAITING_FOR_NEAR -> SPEAKING -> WAITING_FOR_NEAR
 * - privateSpeechEnabled=false: ANY -> IDLE
 * - Missing voice pack or TTS engine failure: ERROR
 */
enum class SpeechState {
    IDLE,
    WAITING_FOR_NEAR,
    SPEAKING,
    ERROR
}

/**
 * Formatted spoken summary text ready for TTS playback in the selected [language].
 */
data class SpeechText(
    val value: String,
    val language: AppLanguage = AppLanguage.EN
)

/**
 * Structured input assembled by the ViewModel for the speech summary builder.
 */
data class SpeechSummaryInput(
    val fountain: Fountain,
    val officialStatus: FountainStatus,
    val communityReport: FountainReport?,
    val lightObservation: LightObservation?,
    val distanceMeters: Int?,
    val language: AppLanguage = AppLanguage.EN,
    val now: Instant = Instant.now()
)
