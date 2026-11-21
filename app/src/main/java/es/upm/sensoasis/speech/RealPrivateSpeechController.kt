package es.upm.sensoasis.speech

import es.upm.sensoasis.domain.contract.PrivateSpeechController
import es.upm.sensoasis.domain.contract.ProximityStateProvider
import es.upm.sensoasis.domain.contract.SpeechController
import es.upm.sensoasis.domain.contract.SpeechSummaryUseCase
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Real implementation of PrivateSpeechController.
 *
 * Handles proximity-based private speech activation:
 * - NEAR must remain stable for 2 seconds.
 * - FAR must remain stable for 400 milliseconds.
 * - Only one summary is triggered per continuous NEAR gesture.
 * - UNKNOWN never triggers speech.
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

    companion object {
        private const val NEAR_DEBOUNCE_MS = 2_000L
        private const val FAR_DEBOUNCE_MS = 400L
    }

    private val _state = MutableStateFlow(SpeechState.IDLE)

    override val state: StateFlow<SpeechState> = _state.asStateFlow()

    @Volatile
    private var isEnabled = false

    @Volatile
    private var hasTriggeredForCurrentNear = false

    private var monitorJob: Job? = null
    private var speechStateJob: Job? = null

    override fun enable(inputFlow: StateFlow<SpeechSummaryInput?>) {
        if (isEnabled) return

        isEnabled = true
        hasTriggeredForCurrentNear = false

        _state.value = SpeechState.WAITING_FOR_NEAR

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
                            _state.value != SpeechState.ERROR
                        ) {
                            // Require continuous NEAR for 2 seconds.
                            delay(NEAR_DEBOUNCE_MS)

                            if (
                                !isEnabled ||
                                proximityStateProvider.state.value !=
                                ProximityState.NEAR
                            ) {
                                return@collectLatest
                            }

                            try {
                                // Wait until valid summary data is available.
                                val input = inputFlow.first { it != null }
                                    ?: return@collectLatest

                                if (
                                    !isEnabled ||
                                    proximityStateProvider.state.value !=
                                    ProximityState.NEAR
                                ) {
                                    return@collectLatest
                                }

                                // Prevent repeated triggers during this NEAR gesture.
                                hasTriggeredForCurrentNear = true

                                val summary =
                                    summaryUseCase.buildSummary(input)

                                if (
                                    isEnabled &&
                                    proximityStateProvider.state.value ==
                                    ProximityState.NEAR
                                ) {
                                    speechController.speak(summary)
                                }

                            } catch (exception: CancellationException) {
                                throw exception
                            } catch (exception: Exception) {
                                if (isEnabled) {
                                    hasTriggeredForCurrentNear = true
                                    _state.value = SpeechState.ERROR
                                }
                            }
                        }
                    }

                    ProximityState.FAR -> {

                        // Require continuous FAR for 400 milliseconds.
                        delay(FAR_DEBOUNCE_MS)

                        if (
                            isEnabled &&
                            proximityStateProvider.state.value ==
                            ProximityState.FAR
                        ) {
                            // A new NEAR gesture can now trigger another summary.
                            hasTriggeredForCurrentNear = false

                            if (
                                speechController.state.value ==
                                SpeechState.SPEAKING
                            ) {
                                speechController.stop()
                            }

                            _state.value = SpeechState.WAITING_FOR_NEAR
                        }
                    }

                    ProximityState.UNKNOWN -> {
                        // UNKNOWN never triggers speech.
                        // collectLatest cancels any pending NEAR/FAR debounce.
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

        if (isEnabled) {
            _state.value = SpeechState.WAITING_FOR_NEAR
        } else {
            _state.value = SpeechState.IDLE
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

        speechController.stop()

        _state.value = SpeechState.IDLE
    }
}
