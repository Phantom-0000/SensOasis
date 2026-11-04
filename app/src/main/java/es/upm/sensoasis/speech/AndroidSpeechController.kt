package es.upm.sensoasis.speech

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioAttributes
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import es.upm.sensoasis.domain.contract.SpeechController
import es.upm.sensoasis.domain.model.SpeechState
import es.upm.sensoasis.domain.model.SpeechText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Real Android TextToSpeech implementation.
 *
 * Prefers the built-in earpiece for private speech.
 * Falls back to the built-in speaker when necessary.
 */
class AndroidSpeechController(context: Context) : SpeechController {

    private val appContext = context.applicationContext

    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _state = MutableStateFlow(SpeechState.IDLE)

    override val state: StateFlow<SpeechState> = _state.asStateFlow()

    private var textToSpeech: TextToSpeech? = null
    private var isReady = false
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

                val languageResult = engine.setLanguage(Locale.UK)
                val fallbackResult = if (
                    languageResult == TextToSpeech.LANG_MISSING_DATA ||
                    languageResult == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    engine.setLanguage(Locale.US)
                } else {
                    languageResult
                }

                if (
                    fallbackResult == TextToSpeech.LANG_MISSING_DATA ||
                    fallbackResult == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    _state.value = SpeechState.ERROR
                    return@TextToSpeech
                }

                engine.setOnUtteranceProgressListener(
                    object : UtteranceProgressListener() {

                        override fun onStart(utteranceId: String?) {
                            _state.value = SpeechState.SPEAKING
                        }

                        override fun onDone(utteranceId: String?) {
                            restoreAudioRoute()
                            _state.value = SpeechState.IDLE
                        }

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
                            _state.value = SpeechState.IDLE
                        }
                    }
                )

                isReady = true

                pendingText?.let {
                    pendingText = null
                    startSpeaking(it)
                }
            } else {
                _state.value = SpeechState.ERROR
            }
        }
    }

    override fun speak(text: SpeechText) {
        if (!isReady) {
            pendingText = text
            return
        }

        startSpeaking(text)
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
        } catch (exception: RuntimeException) {
            restoreAudioRoute()
            _state.value = SpeechState.ERROR
        }
    }

    override fun stop() {
        pendingText = null
        textToSpeech?.stop()
        restoreAudioRoute()
        _state.value = SpeechState.IDLE
    }

    /**
     * Prefer the built-in earpiece.
     * Use the speaker if the earpiece is unavailable.
     */
    private fun routeAudioToEarpiece() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val devices = audioManager.availableCommunicationDevices

                val earpiece = devices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                }

                val speaker = devices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                }

                previousCommunicationDevice =
                    audioManager.communicationDevice

                val preferredDevice = earpiece ?: speaker

                if (preferredDevice != null) {
                    communicationDeviceChanged =
                        audioManager.setCommunicationDevice(preferredDevice)

                    if (!communicationDeviceChanged && speaker != null) {
                        communicationDeviceChanged =
                            audioManager.setCommunicationDevice(speaker)
                    }
                }
            } else {
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
        } catch (exception: RuntimeException) {
            // Keep the default Android audio route if routing fails.
        }
    }

    /**
     * Restore the previous audio configuration after playback.
     */
    private fun restoreAudioRoute() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (communicationDeviceChanged) {
                    val previous = previousCommunicationDevice

                    if (previous != null) {
                        audioManager.setCommunicationDevice(previous)
                    } else {
                        audioManager.clearCommunicationDevice()
                    }

                    communicationDeviceChanged = false
                    previousCommunicationDevice = null
                }
            } else {
                previousMode?.let {
                    audioManager.mode = it
                }

                previousSpeakerphone?.let {
                    audioManager.isSpeakerphoneOn = it
                }

                previousMode = null
                previousSpeakerphone = null
            }
        } catch (exception: RuntimeException) {
            // Do not crash if the audio route cannot be restored.
        }
    }

    /**
     * Release the Android TTS engine when the controller is no longer needed.
     */
    fun shutdown() {
        pendingText = null
        isReady = false

        textToSpeech?.stop()
        restoreAudioRoute()

        textToSpeech?.shutdown()
        textToSpeech = null

        _state.value = SpeechState.IDLE
    }
}