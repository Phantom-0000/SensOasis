package es.upm.sensoasis.speech

import es.upm.sensoasis.domain.contract.PrivateSpeechController
import es.upm.sensoasis.domain.contract.ProximityStateProvider
import es.upm.sensoasis.domain.contract.SpeechController
import es.upm.sensoasis.domain.contract.SpeechSummaryUseCase
import es.upm.sensoasis.domain.model.Constants
import es.upm.sensoasis.domain.model.ProximityState
import es.upm.sensoasis.domain.model.SpeechState
import es.upm.sensoasis.domain.model.SpeechSummaryInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Real implementation of [PrivateSpeechController].
 *
 * Handles proximity-based private speech activation:
 * - Pre-checks selected language pack availability immediately on [enable] and language changes.
 * - NEAR must remain stable for [Constants.PROXIMITY_NEAR_TRIGGER_MS] (2.0 seconds).
 * - FAR must remain stable for [Constants.PROXIMITY_FAR_STOP_MS] (400 milliseconds).
 * - Only one summary is triggered per continuous NEAR gesture.
 * - Preserves [SpeechState.ERROR] across FAR events when TTS or a language pack is unavailable,
 *   and always cancels pending/starting playback when moving away to FAR.
 *
 * Proximity sensor lifecycle remains managed by the ViewModel.
 */
class RealPrivateSpeechController(
    private val proximityStateProvider: ProximityStateProvider,
    private val summaryUseCase: SpeechSummaryUseCase,
    private val speechController: SpeechController,
    private val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : PrivateSpeechController {

    private val _state = MutableStateFlow(SpeechState.IDLE)

    override val state: StateFlow<SpeechState> = _state.asStateFlow()

    @Volatile
    private var isEnabled = false

    @Volatile
    private var hasTriggeredForCurrentNear = false

    private var monitorJob: Job? = null
    private var speechStateJob: Job? = null
    private var languageJob: Job? = null

    override fun enable(inputFlow: StateFlow<SpeechSummaryInput?>) {
        if (isEnabled) return

        isEnabled = true
        hasTriggeredForCurrentNear = false

        // Pre-validate the currently selected language immediately so missing voice packs
        // surface SpeechState.ERROR right away without waiting 2s at the ear.
        inputFlow.value?.language?.let(speechController::setLanguage)
        _state.value = if (speechController.state.value == SpeechState.ERROR) {
            SpeechState.ERROR
        } else {
            SpeechState.WAITING_FOR_NEAR
        }

        // Keep TTS engine locale synchronized whenever the user switches EN / ES / CA.
        languageJob?.cancel()
        languageJob = scope.launch {
            inputFlow
                .map { it?.language }
                .filterNotNull()
                .distinctUntilChanged()
                .collect { language ->
                    if (!isEnabled) return@collect
                    speechController.setLanguage(language)
                }
        }

        // Observe the low-level TTS state.
        speechStateJob?.cancel()
        speechStateJob = scope.launch {
            speechController.state.collectLatest { speechState ->
                if (!isEnabled) return@collectLatest

                when (speechState) {
                    SpeechState.SPEAKING -> {
                        _state.value = SpeechState.SPEAKING
                    }

                    SpeechState.ERROR -> {
                        _state.value = SpeechState.ERROR
                    }

                    SpeechState.IDLE,
                    SpeechState.WAITING_FOR_NEAR -> {
                        _state.value = SpeechState.WAITING_FOR_NEAR
                    }
                }
            }
        }

        // Observe proximity without managing the sensor lifecycle.
        monitorJob?.cancel()
        monitorJob = scope.launch {
            proximityStateProvider.state.collectLatest { proximity ->
                if (!isEnabled) return@collectLatest

                when (proximity) {
                    ProximityState.NEAR -> {
                        if (
                            !hasTriggeredForCurrentNear &&
                            _state.value != SpeechState.ERROR &&
                            speechController.state.value != SpeechState.ERROR
                        ) {
                            delay(Constants.PROXIMITY_NEAR_TRIGGER_MS)

                            if (
                                !isEnabled ||
                                proximityStateProvider.state.value != ProximityState.NEAR ||
                                speechController.state.value == SpeechState.ERROR
                            ) {
                                return@collectLatest
                            }

                            try {
                                val input = inputFlow.first { it != null }
                                    ?: return@collectLatest

                                if (
                                    !isEnabled ||
                                    proximityStateProvider.state.value != ProximityState.NEAR
                                ) {
                                    return@collectLatest
                                }

                                hasTriggeredForCurrentNear = true
                                val summary = summaryUseCase.buildSummary(input)

                                if (
                                    isEnabled &&
                                    proximityStateProvider.state.value == ProximityState.NEAR
                                ) {
                                    speechController.speak(summary)
                                }
                            } catch (exception: CancellationException) {
                                throw exception
                            } catch (_: Exception) {
                                if (isEnabled) {
                                    hasTriggeredForCurrentNear = true
                                    _state.value = SpeechState.ERROR
                                }
                            }
                        }
                    }

                    ProximityState.FAR -> {
                        delay(Constants.PROXIMITY_FAR_STOP_MS)

                        if (
                            isEnabled &&
                            proximityStateProvider.state.value == ProximityState.FAR
                        ) {
                            hasTriggeredForCurrentNear = false

                            // Always invoke stop() to clear both active playback AND queued/pending
                            // utterances that have not yet delivered UtteranceProgressListener.onStart().
                            speechController.stop()

                            // Preserve ERROR when TTS or the selected voice pack is unavailable.
                            if (
                                speechController.state.value != SpeechState.ERROR &&
                                _state.value != SpeechState.ERROR
                            ) {
                                _state.value = SpeechState.WAITING_FOR_NEAR
                            }
                        }
                    }

                    ProximityState.UNKNOWN -> {
                        // UNKNOWN never triggers speech; collectLatest cancels pending debounces.
                    }
                }
            }
        }
    }

    /**
     * Stop current playback without disabling private speech.
     * The current NEAR gesture remains consumed.
     */
    override fun stop() {
        speechController.stop()

        if (!isEnabled) {
            _state.value = SpeechState.IDLE
        } else if (
            speechController.state.value != SpeechState.ERROR &&
            _state.value != SpeechState.ERROR
        ) {
            _state.value = SpeechState.WAITING_FOR_NEAR
        }
    }

    /**
     * Completely disable private speech and cancel all monitoring.
     */
    override fun disable() {
        isEnabled = false
        hasTriggeredForCurrentNear = false

        monitorJob?.cancel()
        monitorJob = null

        speechStateJob?.cancel()
        speechStateJob = null

        languageJob?.cancel()
        languageJob = null

        speechController.stop()

        _state.value = SpeechState.IDLE
    }
}
