package es.upm.sensoasis.fake

import es.upm.sensoasis.domain.contract.PrivateSpeechController
import es.upm.sensoasis.domain.contract.ProximityStateProvider
import es.upm.sensoasis.domain.contract.SpeechController
import es.upm.sensoasis.domain.contract.SpeechSummaryUseCase
import es.upm.sensoasis.domain.model.ProximityState
import es.upm.sensoasis.domain.model.SpeechState
import es.upm.sensoasis.domain.model.SpeechSummaryInput
import es.upm.sensoasis.domain.model.SpeechText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Fake implementation of [SpeechSummaryUseCase] producing a readable English spoken summary.
 */
class FakeSpeechSummaryUseCase : SpeechSummaryUseCase {
    override fun buildSummary(input: SpeechSummaryInput): SpeechText {
        val distancePart = input.distanceMeters?.let { "$it meters away." } ?: "Distance unknown."
        val officialPart = "Official status: ${input.officialStatus.name.lowercase().replace('_', ' ')}."
        val communityPart = input.communityReport?.let { report ->
            val age = report.ageMinutes(input.now)
            val lightInfo = report.lightObservation?.lightBand?.let { band ->
                ", ambient light ${band.name.lowercase()}"
            } ?: ""
            "Latest community report ($age minutes ago): ${report.status.name.lowercase().replace('_', ' ')}$lightInfo."
        } ?: "No recent community reports."
        val airPart = input.airQuality?.let { "Air quality is ${it.category.lowercase()}." } ?: ""
        return SpeechText(
            listOf(input.fountain.name + ".", distancePart, officialPart, communityPart, airPart)
                .filter { it.isNotBlank() }
                .joinToString(" ")
        )
    }
}

/**
 * Fake implementation of low-level [SpeechController].
 */
class FakeSpeechController : SpeechController {
    private val _state = MutableStateFlow(SpeechState.IDLE)
    override val state: StateFlow<SpeechState> = _state.asStateFlow()

    var lastSpokenText: SpeechText? = null
        private set

    override fun speak(text: SpeechText) {
        lastSpokenText = text
        _state.value = SpeechState.SPEAKING
    }

    override fun stop() {
        _state.value = SpeechState.IDLE
    }
}

/**
 * Fake implementation of [PrivateSpeechController].
 *
 * State transition loop when enabled (`privateSpeechEnabled = true`):
 * - Starts in [SpeechState.WAITING_FOR_NEAR].
 * - When [ProximityStateProvider.state] transitions to [ProximityState.NEAR], enters [SpeechState.SPEAKING]
 *   for [simulatedUtteranceDurationMs] (or until [ProximityState.FAR] / [stop] is called).
 * - Does not re-trigger while remaining in the same [ProximityState.NEAR] gesture.
 * - Returns to [SpeechState.WAITING_FOR_NEAR] after playback finishes or is stopped while still enabled.
 * - Returns to [SpeechState.IDLE] when [disable] is called.
 */
class FakePrivateSpeechController(
    private val proximityStateProvider: ProximityStateProvider,
    private val summaryUseCase: SpeechSummaryUseCase = FakeSpeechSummaryUseCase(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    var simulatedNearDebounceMs: Long = 0L,
    var simulatedFarDebounceMs: Long = 0L,
    var simulatedUtteranceDurationMs: Long = 2_500L
) : PrivateSpeechController {

    private val _state = MutableStateFlow(SpeechState.IDLE)
    override val state: StateFlow<SpeechState> = _state.asStateFlow()

    var lastSpokenSummary: SpeechText? = null
        private set

    private var isEnabled: Boolean = false
    private var monitorJob: Job? = null
    private var utteranceJob: Job? = null
    private var hasTriggeredForCurrentNear: Boolean = false

    override fun enable(inputFlow: StateFlow<SpeechSummaryInput?>) {
        if (isEnabled) return
        isEnabled = true
        hasTriggeredForCurrentNear = false
        _state.value = SpeechState.WAITING_FOR_NEAR

        monitorJob?.cancel()
        monitorJob = scope.launch {
            proximityStateProvider.state.collect { proximity ->
                if (!isEnabled) return@collect
                when (proximity) {
                    ProximityState.NEAR -> {
                        if (!hasTriggeredForCurrentNear && _state.value == SpeechState.WAITING_FOR_NEAR) {
                            if (simulatedNearDebounceMs > 0) delay(simulatedNearDebounceMs)
                            if (isEnabled && proximityStateProvider.state.value == ProximityState.NEAR) {
                                hasTriggeredForCurrentNear = true
                                val summaryInput = inputFlow.value
                                if (summaryInput != null) {
                                    lastSpokenSummary = summaryUseCase.buildSummary(summaryInput)
                                }
                                startSimulatedUtterance()
                            }
                        }
                    }

                    ProximityState.FAR -> {
                        hasTriggeredForCurrentNear = false
                        if (_state.value == SpeechState.SPEAKING) {
                            if (simulatedFarDebounceMs > 0) delay(simulatedFarDebounceMs)
                            if (isEnabled && proximityStateProvider.state.value == ProximityState.FAR) {
                                utteranceJob?.cancel()
                                _state.value = SpeechState.WAITING_FOR_NEAR
                            }
                        }
                    }

                    ProximityState.UNKNOWN -> Unit
                }
            }
        }
    }

    private fun startSimulatedUtterance() {
        utteranceJob?.cancel()
        _state.value = SpeechState.SPEAKING
        utteranceJob = scope.launch {
            if (simulatedUtteranceDurationMs > 0) {
                delay(simulatedUtteranceDurationMs)
                if (isEnabled && _state.value == SpeechState.SPEAKING) {
                    _state.value = SpeechState.WAITING_FOR_NEAR
                }
            }
        }
    }

    override fun stop() {
        utteranceJob?.cancel()
        if (isEnabled) {
            _state.value = SpeechState.WAITING_FOR_NEAR
        } else {
            _state.value = SpeechState.IDLE
        }
    }

    override fun disable() {
        isEnabled = false
        hasTriggeredForCurrentNear = false
        utteranceJob?.cancel()
        monitorJob?.cancel()
        _state.value = SpeechState.IDLE
    }
}
