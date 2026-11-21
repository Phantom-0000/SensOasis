package es.upm.sensoasis.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import es.upm.sensoasis.domain.contract.SpeechController
import es.upm.sensoasis.domain.model.AppLanguage
import es.upm.sensoasis.domain.model.SpeechState
import es.upm.sensoasis.domain.model.SpeechText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Real Android TextToSpeech implementation supporting English, Spanish, and Catalan.
 *
 * - Prefers the built-in earpiece for private speech; falls back to speaker if unavailable.
 * - Verifies that the selected [AppLanguage] voice pack is installed on the device without
 *   silently cross-reading in another language; surfaces [SpeechState.ERROR] if missing, and
 *   automatically recovers when the user switches back to an installed language.
 */
class AndroidSpeechController(context: Context) : SpeechController {

    private enum class EngineStatus {
        INITIALIZING,
        READY,
        ENGINE_FAILED
    }

    private val appContext = context.applicationContext

    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _state = MutableStateFlow(SpeechState.IDLE)

    override val state: StateFlow<SpeechState> = _state.asStateFlow()

    private var textToSpeech: TextToSpeech? = null
    private var engineStatus: EngineStatus = EngineStatus.INITIALIZING
    private var currentLanguage: AppLanguage = AppLanguage.EN
    private var isLanguageSupported: Boolean = true
    private var pendingText: SpeechText? = null

    private var previousMode: Int? = null
    private var previousSpeakerphone: Boolean? = null

    private var previousCommunicationDevice: AudioDeviceInfo? = null
    private var communicationDeviceChanged = false

    init {
        textToSpeech = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = textToSpeech ?: return@TextToSpeech

                engine.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )

                engine.setOnUtteranceProgressListener(
                    object : UtteranceProgressListener() {

                        override fun onStart(utteranceId: String?) {
                            _state.value = SpeechState.SPEAKING
                        }

                        override fun onDone(utteranceId: String?) {
                            restoreAudioRoute()
                            _state.value = SpeechState.IDLE
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            restoreAudioRoute()
                            _state.value = SpeechState.ERROR
                        }

                        override fun onError(
                            utteranceId: String?,
                            errorCode: Int
                        ) {
                            restoreAudioRoute()
                            _state.value = SpeechState.ERROR
                        }

                        override fun onStop(
                            utteranceId: String?,
                            interrupted: Boolean
                        ) {
                            restoreAudioRoute()
                            if (engineStatus != EngineStatus.ENGINE_FAILED &&
                                isLanguageSupported &&
                                _state.value != SpeechState.ERROR
                            ) {
                                _state.value = SpeechState.IDLE
                            }
                        }
                    }
                )

                engineStatus = EngineStatus.READY
                val supported = applyLanguage(engine, currentLanguage)

                val queued = pendingText
                pendingText = null
                if (supported && queued != null) {
                    if (queued.language != currentLanguage) {
                        if (applyLanguage(engine, queued.language)) {
                            startSpeaking(queued)
                        }
                    } else {
                        startSpeaking(queued)
                    }
                }
            } else {
                engineStatus = EngineStatus.ENGINE_FAILED
                pendingText = null
                _state.value = SpeechState.ERROR
            }
        }
    }

    override fun setLanguage(language: AppLanguage) {
        val languageChanged = currentLanguage != language
        currentLanguage = language

        when (engineStatus) {
            EngineStatus.ENGINE_FAILED -> {
                _state.value = SpeechState.ERROR
            }
            EngineStatus.INITIALIZING -> {
                if (languageChanged) {
                    pendingText = null
                }
            }
            EngineStatus.READY -> {
                val engine = textToSpeech ?: return
                if (languageChanged && _state.value == SpeechState.SPEAKING) {
                    engine.stop()
                    restoreAudioRoute()
                    _state.value = SpeechState.IDLE
                }
                applyLanguage(engine, language)
            }
        }
    }

    override fun speak(text: SpeechText) {
        currentLanguage = text.language

        when (engineStatus) {
            EngineStatus.ENGINE_FAILED -> {
                _state.value = SpeechState.ERROR
            }
            EngineStatus.INITIALIZING -> {
                pendingText = text
            }
            EngineStatus.READY -> {
                val engine = textToSpeech ?: return
                if (applyLanguage(engine, text.language)) {
                    startSpeaking(text)
                }
            }
        }
    }

    /**
     * Checks same-language locale variants (e.g. es-ES -> es) without falling back across
     * different languages. Returns true when installed & supported, or false (setting ERROR)
     * when the language voice pack is missing on this device.
     */
    private fun applyLanguage(engine: TextToSpeech, language: AppLanguage): Boolean {
        val candidates = when (language) {
            AppLanguage.EN -> listOf(Locale.UK, Locale.US, Locale.ENGLISH)
            AppLanguage.ES -> listOf(Locale.forLanguageTag("es-ES"), Locale.forLanguageTag("es"))
            AppLanguage.CA -> listOf(Locale.forLanguageTag("ca-ES"), Locale.forLanguageTag("ca"))
        }

        val supported = candidates.any { locale ->
            val result = engine.setLanguage(locale)
            result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
        }

        isLanguageSupported = supported
        if (supported) {
            if (_state.value == SpeechState.ERROR) {
                _state.value = SpeechState.IDLE
            }
        } else {
            pendingText = null
            engine.stop()
            restoreAudioRoute()
            _state.value = SpeechState.ERROR
        }
        return supported
    }

    private fun startSpeaking(text: SpeechText) {
        val engine = textToSpeech ?: return

        try {
            routeAudioToEarpiece()

            val result = engine.speak(
                text.value,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "SENSOASIS_SPEECH"
            )

            if (result == TextToSpeech.ERROR) {
                restoreAudioRoute()
                _state.value = SpeechState.ERROR
            }
        } catch (_: RuntimeException) {
            restoreAudioRoute()
            _state.value = SpeechState.ERROR
        }
    }

    override fun stop() {
        pendingText = null
        textToSpeech?.stop()
        restoreAudioRoute()
        if (engineStatus != EngineStatus.ENGINE_FAILED &&
            isLanguageSupported &&
            _state.value != SpeechState.ERROR
        ) {
            _state.value = SpeechState.IDLE
        }
    }

    /**
     * Prefer the built-in earpiece.
     * Use the speaker if the earpiece is unavailable.
     */
    private fun routeAudioToEarpiece() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (previousMode == null) {
                    previousMode = audioManager.mode
                    previousCommunicationDevice = audioManager.communicationDevice
                }

                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

                val devices = audioManager.availableCommunicationDevices
                val earpiece = devices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                }
                val speaker = devices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                }
                val preferredDevice = earpiece ?: speaker

                if (preferredDevice != null) {
                    communicationDeviceChanged =
                        audioManager.setCommunicationDevice(preferredDevice)

                    if (!communicationDeviceChanged &&
                        speaker != null &&
                        preferredDevice != speaker
                    ) {
                        communicationDeviceChanged =
                            audioManager.setCommunicationDevice(speaker)
                    }
                }
            } else {
                routeLegacyEarpiece()
            }
        } catch (_: RuntimeException) {
            // Keep the default Android audio route if routing fails.
        }
    }

    @Suppress("DEPRECATION")
    private fun routeLegacyEarpiece() {
        previousMode = audioManager.mode
        previousSpeakerphone = audioManager.isSpeakerphoneOn
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        val hasEarpiece = audioManager.getDevices(
            AudioManager.GET_DEVICES_OUTPUTS
        ).any {
            it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
        }
        audioManager.isSpeakerphoneOn = !hasEarpiece
    }

    /**
     * Restore the previous audio configuration after playback.
     */
    private fun restoreAudioRoute() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                if (communicationDeviceChanged) {
                    val previous = previousCommunicationDevice
                    if (previous != null) {
                        audioManager.setCommunicationDevice(previous)
                    } else {
                        audioManager.clearCommunicationDevice()
                    }
                }
            } catch (_: RuntimeException) {
                // Do not crash if the device cannot be restored.
            } finally {
                communicationDeviceChanged = false
                previousCommunicationDevice = null

                try {
                    previousMode?.let {
                        audioManager.mode = it
                    }
                } catch (_: RuntimeException) {
                    // Do not crash if the mode cannot be restored.
                } finally {
                    previousMode = null
                }
            }
        } else {
            restoreLegacyAudioRoute()
        }
    }

    @Suppress("DEPRECATION")
    private fun restoreLegacyAudioRoute() {
        try {
            previousMode?.let {
                audioManager.mode = it
            }
        } catch (_: RuntimeException) {
            // Do not crash if the mode cannot be restored.
        }

        try {
            previousSpeakerphone?.let {
                audioManager.isSpeakerphoneOn = it
            }
        } catch (_: RuntimeException) {
            // Do not crash if the speakerphone cannot be restored.
        } finally {
            previousMode = null
            previousSpeakerphone = null
        }
    }

    /**
     * Release the Android TTS engine when the controller is no longer needed.
     */
    fun shutdown() {
        pendingText = null
        engineStatus = EngineStatus.INITIALIZING

        textToSpeech?.stop()
        restoreAudioRoute()

        textToSpeech?.shutdown()
        textToSpeech = null

        _state.value = SpeechState.IDLE
    }
}
