package es.upm.sensoasis.speech

import es.upm.sensoasis.data.opendata.HourlyWeatherDataSource
import es.upm.sensoasis.domain.contract.SpeechController
import es.upm.sensoasis.domain.model.AppLanguage
import es.upm.sensoasis.domain.model.Constants
import es.upm.sensoasis.domain.model.FountainReport
import es.upm.sensoasis.domain.model.FountainStatus
import es.upm.sensoasis.domain.model.LightBand
import es.upm.sensoasis.domain.model.LightObservation
import es.upm.sensoasis.domain.model.LightQuality
import es.upm.sensoasis.domain.model.ProximityState
import es.upm.sensoasis.domain.model.SpeechState
import es.upm.sensoasis.domain.model.SpeechSummaryInput
import es.upm.sensoasis.domain.model.SpeechText
import es.upm.sensoasis.fake.FakeProximityStateProvider
import es.upm.sensoasis.fake.FakeSpeechController
import es.upm.sensoasis.fake.SampleData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PrivateSpeechAndSummaryTest {

    private val fixedNow: Instant = Instant.parse("2026-11-21T12:00:00Z")
    private val fountain = SampleData.fountains.first()

    @Test
    fun `RealSpeechSummaryUseCase builds localized EN, ES, and CA summaries and omits occluded sensor noise`() {
        val useCase = RealSpeechSummaryUseCase()
        val validLight = LightObservation(
            ambientLux = 2800f,
            lightBand = LightBand.MODERATE,
            quality = LightQuality.VALID,
            observedAt = fixedNow.minusSeconds(120)
        )
        val report = FountainReport(
            fountainId = fountain.id,
            status = FountainStatus.WORKING,
            lightObservation = validLight,
            observedAt = fixedNow.minusSeconds(120),
            expiresAt = fixedNow.plus(Constants.REPORT_TTL)
        )

        val en = useCase.buildSummary(
            SpeechSummaryInput(
                fountain = fountain,
                officialStatus = FountainStatus.WORKING,
                communityReport = report,
                lightObservation = validLight,
                distanceMeters = 174,
                language = AppLanguage.EN,
                now = fixedNow
            )
        )
        assertEquals(AppLanguage.EN, en.language)
        assertTrue(en.value.contains("Distance: 174 meters."))
        assertTrue(en.value.contains("moderate light with comfortable visibility"))

        val es = useCase.buildSummary(
            SpeechSummaryInput(
                fountain = fountain,
                officialStatus = FountainStatus.WORKING,
                communityReport = report,
                lightObservation = validLight,
                distanceMeters = 174,
                language = AppLanguage.ES,
                now = fixedNow
            )
        )
        assertEquals(AppLanguage.ES, es.language)
        assertTrue(es.value.startsWith("${fountain.name}."))
        assertFalse("Must not stutter 'Fuente: Fuente'", es.value.contains("Fuente: Fuente"))
        assertTrue(es.value.contains("Distancia: 174 metros."))
        assertTrue(es.value.contains("visibilidad cómoda"))

        val ca = useCase.buildSummary(
            SpeechSummaryInput(
                fountain = fountain,
                officialStatus = FountainStatus.NOT_WORKING,
                communityReport = null,
                lightObservation = LightObservation(
                    ambientLux = null,
                    lightBand = null,
                    quality = LightQuality.OCCLUDED,
                    observedAt = fixedNow
                ),
                distanceMeters = 90,
                language = AppLanguage.CA,
                now = fixedNow
            )
        )
        assertEquals(AppLanguage.CA, ca.language)
        assertTrue(ca.value.contains("Font: ${fountain.name}."))
        assertTrue(ca.value.contains("Distància: 90 metres."))
        assertTrue(ca.value.contains("Estat oficial: no operativa."))
        assertFalse("Occluded light noise must be omitted from speech", ca.value.contains("Llum registrada"))
    }

    @Test
    fun `RealPrivateSpeechController enforces 2000ms NEAR debounce, single trigger per gesture, and 400ms FAR stop loop`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controllerScope = TestScope(dispatcher)
        val proximity = FakeProximityStateProvider(initialState = ProximityState.FAR)
        val speechController = FakeSpeechController()
        val controller = RealPrivateSpeechController(
            proximityStateProvider = proximity,
            summaryUseCase = RealSpeechSummaryUseCase(),
            speechController = speechController,
            scope = controllerScope
        )
        val inputFlow = MutableStateFlow<SpeechSummaryInput?>(
            SpeechSummaryInput(
                fountain = fountain,
                officialStatus = FountainStatus.WORKING,
                communityReport = null,
                lightObservation = null,
                distanceMeters = 120,
                language = AppLanguage.ES,
                now = fixedNow
            )
        )

        controller.enable(inputFlow)
        runCurrent()
        assertEquals(SpeechState.WAITING_FOR_NEAR, controller.state.value)

        // Brief NEAR (< 2.0s) followed by FAR must NOT trigger speech
        proximity.setSimulatedState(ProximityState.NEAR)
        advanceTimeBy(1_500L)
        runCurrent()
        assertNull(speechController.lastSpokenText)
        proximity.setSimulatedState(ProximityState.FAR)
        advanceTimeBy(400L)
        runCurrent()
        assertNull(speechController.lastSpokenText)

        // Stable NEAR for full 2.0s triggers Spanish speech once
        proximity.setSimulatedState(ProximityState.NEAR)
        advanceTimeBy(2_000L)
        runCurrent()
        assertEquals(SpeechState.SPEAKING, controller.state.value)
        assertNotNull(speechController.lastSpokenText)
        assertEquals(AppLanguage.ES, speechController.lastSpokenText?.language)

        // Moving away (FAR for 400ms) stops playback and returns to WAITING_FOR_NEAR
        proximity.setSimulatedState(ProximityState.FAR)
        advanceTimeBy(400L)
        runCurrent()
        assertEquals(SpeechState.WAITING_FOR_NEAR, controller.state.value)

        // Subsequent 2.0s NEAR gesture triggers speech again
        proximity.setSimulatedState(ProximityState.NEAR)
        advanceTimeBy(2_000L)
        runCurrent()
        assertEquals(SpeechState.SPEAKING, controller.state.value)

        controller.disable()
        runCurrent()
        assertEquals(SpeechState.IDLE, controller.state.value)
    }

    @Test
    fun `missing voice pack surfaces ERROR immediately, survives FAR debounce, and recovers when switching language`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controllerScope = TestScope(dispatcher)
        val proximity = FakeProximityStateProvider(initialState = ProximityState.FAR)
        // Simulate a phone with EN & ES installed, but Catalan (CA) voice pack missing
        val speechController = FakeSpeechController(
            supportedLanguages = setOf(AppLanguage.EN, AppLanguage.ES)
        )
        val controller = RealPrivateSpeechController(
            proximityStateProvider = proximity,
            summaryUseCase = RealSpeechSummaryUseCase(),
            speechController = speechController,
            scope = controllerScope
        )
        val baseInput = SpeechSummaryInput(
            fountain = fountain,
            officialStatus = FountainStatus.WORKING,
            communityReport = null,
            lightObservation = null,
            distanceMeters = 50,
            language = AppLanguage.CA,
            now = fixedNow
        )
        val inputFlow = MutableStateFlow<SpeechSummaryInput?>(baseInput)

        controller.enable(inputFlow)
        runCurrent()
        assertEquals(SpeechState.ERROR, controller.state.value)

        // After 400ms in FAR, ERROR must NOT be overwritten with WAITING_FOR_NEAR
        advanceTimeBy(500L)
        runCurrent()
        assertEquals(SpeechState.ERROR, controller.state.value)

        // Switching UI language to Spanish (installed) automatically clears ERROR
        inputFlow.value = baseInput.copy(language = AppLanguage.ES)
        runCurrent()
        assertEquals(SpeechState.WAITING_FOR_NEAR, controller.state.value)

        controller.disable()
    }

    @Test
    fun `moving away during pre-onStart speech startup still invokes stop so phone never speaks on table`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controllerScope = TestScope(dispatcher)
        val proximity = FakeProximityStateProvider(initialState = ProximityState.FAR)

        // Simulates Android TTS where speak() queues audio asynchronously before onStart() flips state to SPEAKING
        val delayedStartTts = object : SpeechController {
            private val _state = MutableStateFlow(SpeechState.IDLE)
            override val state: StateFlow<SpeechState> = _state.asStateFlow()
            var pendingQueued = false
            var stopCount = 0

            override fun speak(text: SpeechText) {
                pendingQueued = true
                // State remains IDLE while audio HAL / TTS engine prepares utterance
            }

            override fun stop() {
                stopCount++
                pendingQueued = false
                _state.value = SpeechState.IDLE
            }
        }

        val controller = RealPrivateSpeechController(
            proximityStateProvider = proximity,
            summaryUseCase = RealSpeechSummaryUseCase(),
            speechController = delayedStartTts,
            scope = controllerScope
        )
        val inputFlow = MutableStateFlow<SpeechSummaryInput?>(
            SpeechSummaryInput(
                fountain = fountain,
                officialStatus = FountainStatus.WORKING,
                communityReport = null,
                lightObservation = null,
                distanceMeters = 80,
                language = AppLanguage.EN,
                now = fixedNow
            )
        )

        controller.enable(inputFlow)
        runCurrent()

        // Hold near ear for 2.0s -> speak() is called, utterance is queued, state still IDLE
        proximity.setSimulatedState(ProximityState.NEAR)
        advanceTimeBy(2_000L)
        runCurrent()
        assertTrue(delayedStartTts.pendingQueued)

        // User immediately pulls phone away from ear -> FAR for 400ms must still call stop()!
        proximity.setSimulatedState(ProximityState.FAR)
        advanceTimeBy(400L)
        runCurrent()
        assertFalse("Pending utterance must be cancelled on FAR", delayedStartTts.pendingQueued)
        assertTrue(delayedStartTts.stopCount >= 1)

        controller.disable()
    }

    @Test
    fun `HourlyWeatherDataSource parses Open-Meteo daytime hours cleanly`() {
        val sampleJson = """
            {
              "hourly": {
                "time": [
                  "2026-11-21T06:00",
                  "2026-11-21T08:00",
                  "2026-11-21T10:00",
                  "2026-11-21T12:00",
                  "2026-11-21T14:00",
                  "2026-11-21T16:00",
                  "2026-11-21T18:00",
                  "2026-11-21T20:00"
                ],
                "temperature_2m": [7.5, 9.0, 11.5, 13.0, 14.2, 13.8, 11.0, 9.5]
              }
            }
        """.trimIndent()

        val snapshot = HourlyWeatherDataSource.parseOpenMeteoResponse(
            json = Json { ignoreUnknownKeys = true },
            body = sampleJson,
            nowSec = 1_700_000_000L
        )
        assertNotNull(snapshot)
        assertEquals(listOf(8, 10, 12, 14, 16, 18, 20), snapshot?.hours)
        assertEquals("2026-11-21", snapshot?.dateLabel)
    }
}
