package es.upm.sensoasis

import es.upm.sensoasis.domain.model.ConnectionState
import es.upm.sensoasis.domain.model.Constants
import es.upm.sensoasis.domain.model.FountainReport
import es.upm.sensoasis.domain.model.FountainStatus
import es.upm.sensoasis.domain.model.LightBand
import es.upm.sensoasis.domain.model.LightCaptureResult
import es.upm.sensoasis.domain.model.ProximityState
import es.upm.sensoasis.domain.model.SpeechState
import es.upm.sensoasis.fake.FakeLightSensorController
import es.upm.sensoasis.fake.FakeLocationProvider
import es.upm.sensoasis.fake.FakeMqttRepository
import es.upm.sensoasis.fake.FakeOpenDataRepository
import es.upm.sensoasis.fake.FakePrivateSpeechController
import es.upm.sensoasis.fake.FakeProximityStateProvider
import es.upm.sensoasis.fake.FakeReportUseCase
import es.upm.sensoasis.location.HaversineDistanceCalculator
import es.upm.sensoasis.viewmodel.ReportFlowState
import es.upm.sensoasis.viewmodel.SensOasisViewModel
import es.upm.sensoasis.viewmodel.UiAction
import es.upm.sensoasis.viewmodel.UiEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SensOasisContractAndViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private var currentNow: Instant = Instant.parse("2026-06-15T14:00:00Z")

    private lateinit var openDataRepository: FakeOpenDataRepository
    private lateinit var mqttRepository: FakeMqttRepository
    private lateinit var proximityStateProvider: FakeProximityStateProvider
    private lateinit var lightSensorController: FakeLightSensorController
    private lateinit var reportUseCase: FakeReportUseCase
    private lateinit var privateSpeechController: FakePrivateSpeechController
    private lateinit var locationProvider: FakeLocationProvider
    private lateinit var viewModel: SensOasisViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        currentNow = Instant.parse("2026-06-15T14:00:00Z")

        openDataRepository = FakeOpenDataRepository(simulatedDelayMs = 0L)
        mqttRepository = FakeMqttRepository(
            simulatedConnectDelayMs = 0L,
            nowProvider = { currentNow },
            seedInitialSampleReport = false
        )
        proximityStateProvider = FakeProximityStateProvider(
            initialState = ProximityState.FAR
        )
        lightSensorController = FakeLightSensorController(
            proximityStateProvider = proximityStateProvider,
            simulatedCaptureDelayMs = 0L,
            simulatedLux = 420f,
            simulatedBand = LightBand.LOW,
            nowProvider = { currentNow }
        )
        reportUseCase = FakeReportUseCase(
            lightSensorController = lightSensorController,
            mqttRepository = mqttRepository,
            nowProvider = { currentNow }
        )
        privateSpeechController = FakePrivateSpeechController(
            proximityStateProvider = proximityStateProvider,
            simulatedNearDebounceMs = 0L,
            simulatedFarDebounceMs = 0L,
            simulatedUtteranceDurationMs = 0L
        )
        locationProvider = FakeLocationProvider()

        viewModel = SensOasisViewModel(
            openDataRepository = openDataRepository,
            mqttRepository = mqttRepository,
            reportUseCase = reportUseCase,
            proximityStateProvider = proximityStateProvider,
            privateSpeechController = privateSpeechController,
            locationProvider = locationProvider,
            distanceCalculator = HaversineDistanceCalculator(),
            nowProvider = { currentNow },
            enablePeriodicExpiryTicker = false
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `valid light capture when proximity is FAR publishes report with lightObservation and loops back to UI`() = runTest {
        viewModel.onAction(UiAction.SelectFountain("MADRID_FT_042"))
        viewModel.onAction(UiAction.OpenReport)
        assertTrue("Proximity sensor should be active on ReportScreen", proximityStateProvider.isActive)

        viewModel.onAction(UiAction.CaptureLight)
        val afterCapture = viewModel.uiState.value
        assertEquals(ReportFlowState.READY, afterCapture.reportState)
        assertTrue(afterCapture.lightState is LightCaptureResult.Valid)
        assertNotNull(afterCapture.submittableLightObservation)

        viewModel.onAction(UiAction.ChangeFountainStatus(FountainStatus.WORKING))
        viewModel.onAction(UiAction.SubmitReport)

        val afterSubmit = viewModel.uiState.value
        assertEquals(ReportFlowState.PUBLISHED, afterSubmit.reportState)
        assertNotNull(afterSubmit.latestCommunityReport)
        assertEquals(FountainStatus.WORKING, afterSubmit.latestCommunityReport?.status)
        assertEquals(LightBand.LOW, afterSubmit.latestCommunityReport?.lightObservation?.lightBand)
    }

    @Test
    fun `occluded light capture when proximity is NEAR still allows report submission with null lightObservation`() = runTest {
        viewModel.onAction(UiAction.SelectFountain("MADRID_FT_042"))
        viewModel.onAction(UiAction.OpenReport)
        proximityStateProvider.setSimulatedState(ProximityState.NEAR)

        viewModel.onAction(UiAction.CaptureLight)
        val afterCapture = viewModel.uiState.value
        assertEquals(ReportFlowState.OCCLUDED, afterCapture.reportState)
        assertEquals(LightCaptureResult.Occluded, afterCapture.lightState)
        assertNull("Occluded light capture must yield null submittableLightObservation", afterCapture.submittableLightObservation)
        assertTrue("ReportFlowState.OCCLUDED must still allow submission", afterCapture.reportState.canSubmit)

        viewModel.onAction(UiAction.ChangeFountainStatus(FountainStatus.NOT_WORKING))
        viewModel.onAction(UiAction.SubmitReport)

        val afterSubmit = viewModel.uiState.value
        assertEquals(ReportFlowState.PUBLISHED, afterSubmit.reportState)
        assertEquals(FountainStatus.NOT_WORKING, afterSubmit.latestCommunityReport?.status)
        assertNull(afterSubmit.latestCommunityReport?.lightObservation)
    }

    @Test
    fun `proximity lifecycle starts only when reportScreenActive or privateSpeechEnabled and stops when neither is active`() = runTest {
        assertFalse(proximityStateProvider.isActive)

        // Open ReportScreen -> starts proximity
        viewModel.onAction(UiAction.OpenReport)
        assertTrue(proximityStateProvider.isActive)

        // Also enable Private Speech -> stays active
        viewModel.onAction(UiAction.EnablePrivateSpeech(true))
        assertTrue(proximityStateProvider.isActive)

        // Leave ReportScreen while Private Speech is still enabled -> stays active
        viewModel.onAction(UiAction.LeaveReportScreen)
        assertTrue(proximityStateProvider.isActive)

        // Disable Private Speech -> stops proximity
        viewModel.onAction(UiAction.EnablePrivateSpeech(false))
        assertFalse(proximityStateProvider.isActive)
    }

    @Test
    fun `interactionLocked updates with proximity and PrivateSpeechController transitions WAITING_FOR_NEAR to SPEAKING`() = runTest {
        // Keep utterance active until stopped or FAR
        privateSpeechController.simulatedUtteranceDurationMs = 0L
        proximityStateProvider.setSimulatedState(ProximityState.FAR)

        viewModel.onAction(UiAction.EnablePrivateSpeech(true))
        assertEquals(SpeechState.WAITING_FOR_NEAR, viewModel.uiState.value.speechState)
        assertFalse(viewModel.uiState.value.interactionLocked)

        // Simulate user bringing phone to ear (NEAR)
        proximityStateProvider.setSimulatedState(ProximityState.NEAR)
        assertEquals(SpeechState.SPEAKING, viewModel.uiState.value.speechState)
        assertTrue("Interaction must be locked when privateSpeechEnabled and NEAR/SPEAKING", viewModel.uiState.value.interactionLocked)
        assertNotNull("PrivateSpeechController should have built spoken summary", privateSpeechController.lastSpokenSummary)

        // Simulate user moving phone away from ear (FAR) -> stops speaking and returns to WAITING_FOR_NEAR
        proximityStateProvider.setSimulatedState(ProximityState.FAR)
        assertEquals(SpeechState.WAITING_FOR_NEAR, viewModel.uiState.value.speechState)
        assertFalse("Interaction lock must release when FAR and WAITING_FOR_NEAR", viewModel.uiState.value.interactionLocked)
    }

    @Test
    fun `expired community reports are ignored on ingress and purged on active expiry check`() = runTest {
        viewModel.onAction(UiAction.SelectFountain("MADRID_FT_042"))

        val freshReport = FountainReport(
            fountainId = "MADRID_FT_042",
            status = FountainStatus.WORKING,
            lightObservation = null,
            observedAt = currentNow,
            expiresAt = currentNow.plus(Constants.REPORT_TTL)
        )
        mqttRepository.emitIncomingBrokerReport(freshReport)
        assertNotNull(viewModel.uiState.value.latestCommunityReport)

        // Advance clock past the 20-minute TTL
        currentNow = currentNow.plusSeconds(21 * 60L)
        viewModel.purgeExpiredReports(currentNow)

        assertNull("Expired report must be removed from latestCommunityReport", viewModel.uiState.value.latestCommunityReport)
    }

    @Test
    fun `RefreshLocation updates user coordinates, fallback flag, and fountain distances`() = runTest {
        assertTrue(viewModel.uiState.value.isFallbackLocation)

        // Simulate receiving a live GPS fix at UPM Campus Sur
        locationProvider.setSimulatedLocation(
            coordinates = Constants.UPM_CAMPUS_SUR_COORDINATES,
            isFallback = false
        )
        viewModel.onAction(UiAction.RefreshLocation)

        val state = viewModel.uiState.value
        assertFalse(state.isFallbackLocation)
        assertEquals(Constants.UPM_CAMPUS_SUR_COORDINATES, state.userLocation)
        assertNotNull(state.selectedFountainDistanceMeters)
        assertTrue((state.selectedFountainDistanceMeters ?: 0) > 6000)
    }

    @Test
    fun `onAppBackgrounded disables private speech and stops proximity sensor`() = runTest {
        viewModel.onAction(UiAction.OpenReport)
        viewModel.onAction(UiAction.EnablePrivateSpeech(true))
        assertTrue(proximityStateProvider.isActive)
        assertTrue(viewModel.uiState.value.privateSpeechEnabled)

        viewModel.onAppBackgrounded()
        assertFalse(viewModel.uiState.value.privateSpeechEnabled)
        assertFalse(proximityStateProvider.isActive)
        assertEquals(SpeechState.IDLE, viewModel.uiState.value.speechState)
    }

    @Test
    fun `submitting report while broker is offline sets ReportFlowState to OFFLINE`() = runTest {
        viewModel.onAction(UiAction.SelectFountain("MADRID_FT_042"))
        viewModel.onAction(UiAction.OpenReport)
        mqttRepository.simulateOffline = true

        viewModel.onAction(UiAction.SubmitReport)
        assertEquals(ReportFlowState.OFFLINE, viewModel.uiState.value.reportState)
        assertNotNull(viewModel.uiState.value.error)
    }

    @Test
    fun `OpenWalkingRoute emits UiEffect when unlocked and is blocked when interactionLocked`() = runTest {
        viewModel.onAction(UiAction.SelectFountain("MADRID_FT_042"))
        viewModel.onAction(UiAction.OpenWalkingRoute)

        val effect = viewModel.effects.first()
        assertTrue(effect is UiEffect.OpenWalkingRoute)
        assertEquals("Fuente Puerta del Sol", (effect as UiEffect.OpenWalkingRoute).fountainName)

        // Lock interaction: private speech enabled + phone near ear
        viewModel.onAction(UiAction.EnablePrivateSpeech(true))
        proximityStateProvider.setSimulatedState(ProximityState.NEAR)
        assertTrue(viewModel.uiState.value.interactionLocked)

        viewModel.onAction(UiAction.OpenWalkingRoute)
        val blocked = withTimeoutOrNull(100L) { viewModel.effects.first() }
        assertNull("OpenWalkingRoute must be ignored while interactionLocked", blocked)
    }

    @Test
    fun `returning to foreground on ReportScreen restarts the proximity sensor`() = runTest {
        viewModel.onAction(UiAction.OpenReport)
        assertTrue(proximityStateProvider.isActive)

        viewModel.onAppBackgrounded()
        assertFalse(proximityStateProvider.isActive)

        viewModel.onAppForegrounded()
        assertTrue("Proximity must restart when user is still on ReportScreen", proximityStateProvider.isActive)
    }

    @Test
    fun `SelectFountain before fountain list is loaded is applied once the list arrives`() = runTest {
        val slowRepo = FakeOpenDataRepository(simulatedDelayMs = 1_000L)
        val vm = SensOasisViewModel(
            openDataRepository = slowRepo,
            mqttRepository = mqttRepository,
            reportUseCase = reportUseCase,
            proximityStateProvider = proximityStateProvider,
            privateSpeechController = privateSpeechController,
            locationProvider = locationProvider,
            distanceCalculator = HaversineDistanceCalculator(),
            nowProvider = { currentNow },
            enablePeriodicExpiryTicker = false
        )
        vm.onAction(UiAction.SelectFountain("MADRID_FT_309"))
        assertNull(vm.uiState.value.selectedFountain)

        advanceTimeBy(1_500L)
        assertEquals("MADRID_FT_309", vm.uiState.value.selectedFountain?.id)
    }

    @Test
    fun `onAppBackgrounded during active light capture cancels captureJob and restores reportState`() = runTest {
        lightSensorController.simulatedCaptureDelayMs = 1_500L
        viewModel.onAction(UiAction.OpenReport)
        viewModel.onAction(UiAction.CaptureLight)
        assertEquals(ReportFlowState.CAPTURING_LIGHT, viewModel.uiState.value.reportState)

        advanceTimeBy(400L)
        viewModel.onAppBackgrounded()

        assertEquals(
            "Backgrounding mid-capture must restore reportState so UI is not stuck in CAPTURING_LIGHT",
            ReportFlowState.IDLE,
            viewModel.uiState.value.reportState
        )
        assertTrue(viewModel.uiState.value.reportState.canSubmit)
    }

    @Test
    fun `OpenReport disables privateSpeechEnabled so covering sensor on ReportScreen does not lock interaction`() = runTest {
        viewModel.onAction(UiAction.EnablePrivateSpeech(true))
        assertTrue(viewModel.uiState.value.privateSpeechEnabled)
        assertTrue(proximityStateProvider.isActive)

        viewModel.onAction(UiAction.OpenReport)
        assertFalse("Entering ReportScreen must disable private speech", viewModel.uiState.value.privateSpeechEnabled)
        assertEquals(SpeechState.IDLE, viewModel.uiState.value.speechState)
        assertTrue("Proximity sensor must stay active for ReportScreen occlusion check", proximityStateProvider.isActive)

        // Covering the top sensor to demonstrate OCCLUDED light capture must not lock ReportScreen buttons
        proximityStateProvider.setSimulatedState(ProximityState.NEAR)
        assertFalse("interactionLocked must remain false on ReportScreen", viewModel.uiState.value.interactionLocked)
    }

    @Test
    fun `RefreshFountains and retry SubmitReport reconnect MQTT when previously disconnected`() = runTest {
        viewModel.onAction(UiAction.SelectFountain("MADRID_FT_042"))
        viewModel.onAction(UiAction.OpenReport)

        // Disconnect broker -> submission fails with OFFLINE
        mqttRepository.disconnect()
        assertEquals(ConnectionState.DISCONNECTED, viewModel.uiState.value.mqttState)

        mqttRepository.simulateOffline = true
        viewModel.onAction(UiAction.SubmitReport)
        assertEquals(ReportFlowState.OFFLINE, viewModel.uiState.value.reportState)

        // Broker becomes reachable again (still DISCONNECTED until connect() is retried)
        mqttRepository.disconnect()
        mqttRepository.simulateOffline = false
        assertEquals(ConnectionState.DISCONNECTED, viewModel.uiState.value.mqttState)

        // Tapping Retry (SubmitReport) reconnects and publishes immediately
        viewModel.onAction(UiAction.SubmitReport)
        assertEquals(ConnectionState.CONNECTED, viewModel.uiState.value.mqttState)
        assertEquals(ReportFlowState.PUBLISHED, viewModel.uiState.value.reportState)

        // Likewise, RefreshFountains reconnects when DISCONNECTED
        mqttRepository.disconnect()
        assertEquals(ConnectionState.DISCONNECTED, viewModel.uiState.value.mqttState)
        viewModel.onAction(UiAction.RefreshFountains)
        assertEquals(ConnectionState.CONNECTED, viewModel.uiState.value.mqttState)
    }
}
