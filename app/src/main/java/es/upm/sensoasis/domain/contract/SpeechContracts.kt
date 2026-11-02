package es.upm.sensoasis.domain.contract

import es.upm.sensoasis.domain.model.SpeechState
import es.upm.sensoasis.domain.model.SpeechSummaryInput
import es.upm.sensoasis.domain.model.SpeechText
import kotlinx.coroutines.flow.StateFlow

/**
 * Builds a concise spoken summary from [SpeechSummaryInput] (distance, official status,
 * community report, age, optional air quality, and optional light observation).
 */
interface SpeechSummaryUseCase {
    fun buildSummary(input: SpeechSummaryInput): SpeechText
}

/**
 * Low-level TTS playback and audio-routing controller (preferring earpiece with speaker fallback).
 */
interface SpeechController {
    val state: StateFlow<SpeechState>

    fun speak(text: SpeechText)

    fun stop()
}

/**
 * High-level coordinator that observes [ProximityStateProvider.state] while enabled:
 * - Waits for a stable NEAR state (~2.0s) to build and speak one summary from the latest [SpeechSummaryInput].
 * - Prevents repeated triggers while remaining in the same NEAR gesture.
 * - Stops playback after a stable FAR state (~0.3-0.5s) and returns to [SpeechState.WAITING_FOR_NEAR]
 *   (as long as private speech remains enabled).
 */
interface PrivateSpeechController {
    val state: StateFlow<SpeechState>

    fun enable(inputFlow: StateFlow<SpeechSummaryInput?>)

    fun disable()

    fun stop()
}
