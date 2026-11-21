package es.upm.sensoasis.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import es.upm.sensoasis.di.AppContainer
import es.upm.sensoasis.domain.contract.DistanceCalculator
import es.upm.sensoasis.domain.contract.LocationProvider
import es.upm.sensoasis.domain.contract.MqttRepository
import es.upm.sensoasis.domain.contract.OpenDataRepository
import es.upm.sensoasis.domain.contract.PrivateSpeechController
import es.upm.sensoasis.domain.contract.ProximityStateProvider
import es.upm.sensoasis.domain.contract.ReportUseCase
import es.upm.sensoasis.domain.model.AppLanguage
import es.upm.sensoasis.domain.model.ConnectionState
import es.upm.sensoasis.domain.model.Constants
import es.upm.sensoasis.domain.model.Coordinates
import es.upm.sensoasis.domain.model.DataResult
import es.upm.sensoasis.domain.model.Fountain
import es.upm.sensoasis.domain.model.FountainReport
import es.upm.sensoasis.domain.model.FountainStatus
import es.upm.sensoasis.domain.model.LightCaptureResult
import es.upm.sensoasis.domain.model.ProximityState
import es.upm.sensoasis.domain.model.SpeechState
import es.upm.sensoasis.domain.model.SpeechSummaryInput
import es.upm.sensoasis.domain.model.SubmitReportResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant

/**
 * Main application ViewModel orchestrating:
 * - Fountain open-data loading, selection, and hourly temperature curve loading
 * - MQTT broker lifecycle and community report subscription + active expiry filtering
 * - Two-phase ambient-light capture and report submission
 * - Proximity sensor lifecycle ownership (`needProximity = reportScreenActive || privateSpeechEnabled`)
 * - Trilingual private speech coordination (`PrivateSpeechController`)
 * - Interaction lock derivation (`privateSpeechEnabled && (proximity == NEAR || speech == SPEAKING)`)
 * - One-time UI effects via [effects] channel
 */
class SensOasisViewModel(
    private val openDataRepository: OpenDataRepository,
    private val mqttRepository: MqttRepository,
    private val reportUseCase: ReportUseCase,
    private val proximityStateProvider: ProximityStateProvider,
    private val privateSpeechController: PrivateSpeechController,
    private val locationProvider: LocationProvider,
    private val distanceCalculator: DistanceCalculator,
    private val nowProvider: () -> Instant = { Instant.now() },
    enablePeriodicExpiryTicker: Boolean = true,
    /**
     * Scope that outlives this ViewModel, used for cleanup work in [onCleared]
     * (viewModelScope is already cancelled by the time onCleared runs).
     */
    private val cleanupScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : ViewModel() {

    private val _uiState = MutableStateFlow(SensOasisUiState(now = nowProvider()))
    val uiState: StateFlow<SensOasisUiState> = _uiState.asStateFlow()

    private val _speechSummaryInput = MutableStateFlow<SpeechSummaryInput?>(null)

    private val _effects = Channel<UiEffect>(Channel.BUFFERED)
    val effects: Flow<UiEffect> = _effects.receiveAsFlow()

    private var wasProximityNeeded: Boolean = false
    private var isAppInBackground: Boolean = false
    private var captureJob: Job? = null
    private var fountainsJob: Job? = null
    private var weatherJob: Job? = null

    /**
     * Fountain id requested via [UiAction.SelectFountain] before the fountain list finished loading
     * (e.g. navigation back stack restored after process death). Applied once the list arrives.
     */
    private var pendingSelectedFountainId: String? = null

    init {
        observeMqttConnection()
        observeCommunityReports()
        observeProximityAndSpeech()
        observeLocation()
        refreshLocation()
        loadFountains()
        connectMqtt()
        if (enablePeriodicExpiryTicker) {
            startExpiryTicker()
        }
    }

    fun onAction(action: UiAction) {
        when (action) {
            is UiAction.SelectFountain -> selectFountain(action.fountainId)
            is UiAction.SetLanguage -> setLanguage(action.language)
            UiAction.OpenReport -> openReport()
            UiAction.CaptureLight -> captureLight()
            is UiAction.ChangeFountainStatus -> changeDraftStatus(action.status)
            UiAction.SubmitReport -> submitReport()
            UiAction.LeaveReportScreen -> leaveReportScreen()
            is UiAction.EnablePrivateSpeech -> setPrivateSpeechEnabled(action.enabled)
            UiAction.StopSpeech -> privateSpeechController.stop()
            UiAction.OpenWalkingRoute -> openWalkingRoute()
            UiAction.RefreshFountains -> refreshFountains()
            UiAction.RefreshLocation -> refreshLocation()
            UiAction.ClearError -> _uiState.update { it.copy(error = null) }
        }
    }

    private fun setLanguage(language: AppLanguage) {
        if (_uiState.value.language == language) return
        _uiState.update { it.copy(language = language) }
        syncDerivedState()
    }

    /**
     * Called when the application moves to the background (via ProcessLifecycleOwner)
     * so sensors and TTS do not stay active when the user leaves the app, while surviving
     * screen rotations without interruption.
     */
    fun onAppBackgrounded() {
        isAppInBackground = true
        cancelActiveLightCapture()
        if (_uiState.value.privateSpeechEnabled) {
            setPrivateSpeechEnabled(false)
        }
        if (wasProximityNeeded) {
            proximityStateProvider.stop()
            wasProximityNeeded = false
        }
    }

    private fun cancelActiveLightCapture() {
        captureJob?.cancel()
        captureJob = null
        if (_uiState.value.reportState == ReportFlowState.CAPTURING_LIGHT) {
            _uiState.update { current ->
                val restoredState = when (current.lightState) {
                    is LightCaptureResult.Valid -> ReportFlowState.READY
                    LightCaptureResult.Occluded -> ReportFlowState.OCCLUDED
                    is LightCaptureResult.Unavailable, is LightCaptureResult.Error -> ReportFlowState.UNAVAILABLE
                    null -> ReportFlowState.IDLE
                }
                current.copy(reportState = restoredState)
            }
        }
    }

    /**
     * Called when the application returns to the foreground (via ProcessLifecycleOwner).
     * Ignores initial observer registration and screen-rotation re-registrations when the
     * process never actually entered the background ([isAppInBackground] == false).
     */
    fun onAppForegrounded() {
        if (!isAppInBackground) return
        isAppInBackground = false
        syncDerivedState()
        refreshLocation()
    }

    private fun loadFountains(notifyUser: Boolean = false) {
        fountainsJob?.cancel()
        fountainsJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingFountains = true, error = null) }
            when (val result = openDataRepository.getFountains()) {
                is DataResult.Success -> {
                    val list = result.value
                    val pendingId = pendingSelectedFountainId
                    pendingSelectedFountainId = null
                    val now = nowProvider()
                    _uiState.update { current ->
                        val distances = computeDistances(current.userLocation, list)
                        val orderedFountains = sortFountainsByDistance(list, distances)
                        val selected = pendingId?.let { id -> list.find { it.id == id } }
                            ?: current.selectedFountain?.let { prev -> list.find { it.id == prev.id } }
                            ?: orderedFountains.firstOrNull()
                        val selectedDist = selected?.let { distances[it.id] }
                        val latestForSelected = selected?.let {
                            current.communityReportsByFountain[it.id]?.takeIf { r -> !r.isExpired(now) }
                        }
                        val updatedDraftStatus = if (pendingId != null || current.selectedFountain == null) {
                            selected?.officialStatus?.takeIf { it != FountainStatus.UNKNOWN }
                                ?: FountainStatus.WORKING
                        } else {
                            current.reportDraftStatus
                        }
                        current.copy(
                            fountains = orderedFountains,
                            isLoadingFountains = false,
                            selectedFountain = selected,
                            fountainDistancesMeters = distances,
                            selectedFountainDistanceMeters = selectedDist,
                            latestCommunityReport = latestForSelected,
                            reportDraftStatus = updatedDraftStatus
                        )
                    }
                    syncDerivedState()
                    _uiState.value.selectedFountain?.let(::loadHourlyTemperature)
                    if (notifyUser) {
                        val count = list.size
                        val msg = when (_uiState.value.language) {
                            AppLanguage.EN -> "Refreshed ($count fountains)"
                            AppLanguage.ES -> "Datos actualizados ($count fuentes)"
                            AppLanguage.CA -> "Dades actualitzades ($count fonts)"
                        }
                        _effects.send(UiEffect.ShowMessage(msg))
                    }
                }

                is DataResult.Unavailable -> {
                    val errMsg = "Fountain data unavailable: ${result.reason}"
                    _uiState.update {
                        it.copy(
                            isLoadingFountains = false,
                            error = errMsg
                        )
                    }
                    if (notifyUser) {
                        _effects.send(UiEffect.ShowMessage(errMsg))
                    }
                }

                is DataResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoadingFountains = false,
                            error = result.message
                        )
                    }
                    if (notifyUser) {
                        _effects.send(UiEffect.ShowMessage(result.message))
                    }
                }
            }
        }
    }

    private fun refreshFountains() {
        loadFountains(notifyUser = true)
        val mqtt = _uiState.value.mqttState
        if (mqtt == ConnectionState.DISCONNECTED || mqtt == ConnectionState.ERROR) {
            connectMqtt()
        }
    }

    private fun connectMqtt() {
        viewModelScope.launch {
            mqttRepository.connect()
        }
    }

    private fun observeMqttConnection() {
        viewModelScope.launch {
            mqttRepository.connectionState.collect { conn ->
                _uiState.update { it.copy(mqttState = conn) }
            }
        }
        viewModelScope.launch {
            mqttRepository.receiveState.collect { receiveState ->
                _uiState.update { it.copy(mqttReceiveState = receiveState) }
            }
        }
    }

    private fun observeCommunityReports() {
        viewModelScope.launch {
            mqttRepository.observeAllReports().collect { report ->
                val now = nowProvider()
                if (report.isExpired(now)) return@collect
                _uiState.update { current ->
                    val updatedMap = current.communityReportsByFountain.toMutableMap().apply {
                        put(report.fountainId, report)
                    }
                    val latestForSelected = current.selectedFountain?.id?.let { updatedMap[it] }
                    current.copy(
                        communityReportsByFountain = updatedMap,
                        latestCommunityReport = latestForSelected,
                        now = now
                    )
                }
                syncDerivedState()
            }
        }
    }

    private fun observeProximityAndSpeech() {
        viewModelScope.launch {
            proximityStateProvider.state.collect { proximity ->
                _uiState.update { it.copy(proximityState = proximity) }
                syncDerivedState()
            }
        }
        viewModelScope.launch {
            privateSpeechController.state.collect { speech ->
                _uiState.update { it.copy(speechState = speech) }
                syncDerivedState()
            }
        }
    }

    private fun observeLocation() {
        viewModelScope.launch {
            locationProvider.currentLocation.collect { result ->
                if (result is DataResult.Success) {
                    val snapshot = result.value
                    _uiState.update { current ->
                        val distances = computeDistances(snapshot.coordinates, current.fountains)
                        val orderedFountains = sortFountainsByDistance(current.fountains, distances)
                        val selectedDist = current.selectedFountain?.let { distances[it.id] }
                        current.copy(
                            fountains = orderedFountains,
                            userLocation = snapshot.coordinates,
                            isFallbackLocation = snapshot.isFallback,
                            fountainDistancesMeters = distances,
                            selectedFountainDistanceMeters = selectedDist
                        )
                    }
                    syncDerivedState()
                }
            }
        }
    }

    private fun refreshLocation() {
        viewModelScope.launch {
            locationProvider.refreshLocation()
        }
    }

    private fun selectFountain(fountainId: String) {
        if (_uiState.value.fountains.none { it.id == fountainId }) {
            // List not loaded yet: remember the request and apply it in loadFountains().
            pendingSelectedFountainId = fountainId
            return
        }
        _uiState.update { current ->
            val found = current.fountains.find { it.id == fountainId } ?: return@update current
            val dist = current.fountainDistancesMeters[fountainId]
            val report = current.communityReportsByFountain[fountainId]?.takeIf { !it.isExpired(nowProvider()) }
            current.copy(
                selectedFountain = found,
                selectedFountainDistanceMeters = dist,
                latestCommunityReport = report,
                reportDraftStatus = found.officialStatus.takeIf { it != FountainStatus.UNKNOWN }
                    ?: FountainStatus.WORKING
            )
        }
        syncDerivedState()
        _uiState.value.selectedFountain?.let(::loadHourlyTemperature)
    }

    private fun loadHourlyTemperature(fountain: Fountain) {
        weatherJob?.cancel()
        weatherJob = viewModelScope.launch {
            _uiState.update { current ->
                current.copy(isLoadingHourlyTemperature = current.hourlyTemperature == null)
            }
            when (val result = openDataRepository.getHourlyTemperature(fountain.coordinates)) {
                is DataResult.Success -> {
                    _uiState.update { current ->
                        if (current.selectedFountain?.id == fountain.id) {
                            current.copy(
                                hourlyTemperature = result.value,
                                isLoadingHourlyTemperature = false
                            )
                        } else {
                            current.copy(isLoadingHourlyTemperature = false)
                        }
                    }
                }
                is DataResult.Unavailable, is DataResult.Error -> {
                    _uiState.update { it.copy(isLoadingHourlyTemperature = false) }
                }
            }
        }
    }

    private fun openReport() {
        _uiState.update { current ->
            val defaultStatus = current.selectedFountain?.officialStatus
                ?.takeIf { it != FountainStatus.UNKNOWN } ?: FountainStatus.WORKING
            current.copy(
                reportScreenActive = true,
                reportDraftStatus = defaultStatus,
                lightState = null,
                reportState = ReportFlowState.IDLE,
                error = null
            )
        }
        if (_uiState.value.privateSpeechEnabled) {
            setPrivateSpeechEnabled(false)
        } else {
            syncDerivedState()
        }
    }

    private fun captureLight() {
        if (_uiState.value.interactionLocked) return
        captureJob?.cancel()
        captureJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    reportState = ReportFlowState.CAPTURING_LIGHT,
                    error = null
                )
            }
            val result = reportUseCase.captureObservation()
            val nextReportState = when (result) {
                is LightCaptureResult.Valid -> ReportFlowState.READY
                LightCaptureResult.Occluded -> ReportFlowState.OCCLUDED
                is LightCaptureResult.Unavailable -> ReportFlowState.UNAVAILABLE
                is LightCaptureResult.Error -> ReportFlowState.UNAVAILABLE
            }
            _uiState.update {
                it.copy(
                    lightState = result,
                    reportState = nextReportState,
                    now = nowProvider()
                )
            }
            syncDerivedState()
        }
    }

    private fun changeDraftStatus(status: FountainStatus) {
        _uiState.update { it.copy(reportDraftStatus = status) }
    }

    private fun submitReport() {
        val current = _uiState.value
        if (current.interactionLocked || !current.reportState.canSubmit) return
        val fountain = current.selectedFountain ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(reportState = ReportFlowState.PUBLISHING, error = null) }
            val mqtt = _uiState.value.mqttState
            if (mqtt == ConnectionState.DISCONNECTED || mqtt == ConnectionState.ERROR) {
                withTimeoutOrNull(PUBLISH_TIMEOUT_MS) {
                    mqttRepository.connect()
                }
            }
            val result = withTimeoutOrNull(PUBLISH_TIMEOUT_MS) {
                reportUseCase.submitReport(
                    fountainId = fountain.id,
                    status = current.reportDraftStatus,
                    lightObservation = current.submittableLightObservation
                )
            } ?: SubmitReportResult.Offline
            when (result) {
                SubmitReportResult.Success -> {
                    _uiState.update { it.copy(reportState = ReportFlowState.PUBLISHED, now = nowProvider()) }
                    val msg = when (_uiState.value.language) {
                        AppLanguage.EN -> "Report published for ${fountain.name}"
                        AppLanguage.ES -> "Aviso enviado para ${fountain.name}"
                        AppLanguage.CA -> "Avís enviat per a ${fountain.name}"
                    }
                    _effects.send(UiEffect.ShowMessage(msg))
                }

                SubmitReportResult.Offline -> {
                    val (errText, toastText) = when (_uiState.value.language) {
                        AppLanguage.EN -> "Broker offline: could not publish report" to "Offline: could not publish report"
                        AppLanguage.ES -> "Sin conexión al servidor: no se pudo enviar el aviso" to "Sin conexión: no se pudo enviar el aviso"
                        AppLanguage.CA -> "Sense connexió al servidor: no s'ha pogut enviar l'avís" to "Sense connexió: no s'ha pogut enviar l'avís"
                    }
                    _uiState.update {
                        it.copy(
                            reportState = ReportFlowState.OFFLINE,
                            error = errText
                        )
                    }
                    _effects.send(UiEffect.ShowMessage(toastText))
                }

                is SubmitReportResult.Error -> {
                    val prefix = when (_uiState.value.language) {
                        AppLanguage.EN -> "Error"
                        AppLanguage.ES -> "Error"
                        AppLanguage.CA -> "Error"
                    }
                    _uiState.update {
                        it.copy(
                            reportState = ReportFlowState.ERROR,
                            error = result.message
                        )
                    }
                    _effects.send(UiEffect.ShowMessage("$prefix: ${result.message}"))
                }
            }
            syncDerivedState()
        }
    }

    private fun leaveReportScreen() {
        captureJob?.cancel()
        _uiState.update {
            it.copy(
                reportScreenActive = false,
                lightState = null,
                reportState = ReportFlowState.IDLE
            )
        }
        syncDerivedState()
    }

    private fun setPrivateSpeechEnabled(enabled: Boolean) {
        _uiState.update { it.copy(privateSpeechEnabled = enabled) }
        syncDerivedState()
        if (enabled) {
            privateSpeechController.enable(_speechSummaryInput.asStateFlow())
        } else {
            privateSpeechController.disable()
        }
        syncDerivedState()
    }

    private fun openWalkingRoute() {
        val current = _uiState.value
        if (current.interactionLocked) return
        val fountain = current.selectedFountain ?: return
        viewModelScope.launch {
            _effects.send(
                UiEffect.OpenWalkingRoute(
                    destination = fountain.coordinates,
                    fountainName = fountain.name
                )
            )
        }
    }

    /**
     * Active UI expiry filtering:
     * Re-evaluates held community reports so expired observations age out automatically.
     */
    fun purgeExpiredReports(now: Instant = nowProvider()) {
        _uiState.update { current ->
            val validReports = current.communityReportsByFountain.filterValues { !it.isExpired(now) }
            val latestForSelected = current.selectedFountain?.id?.let { validReports[it] }
            current.copy(
                communityReportsByFountain = validReports,
                latestCommunityReport = latestForSelected,
                now = now
            )
        }
        syncDerivedState()
    }

    private fun startExpiryTicker() {
        viewModelScope.launch {
            while (isActive) {
                delay(Constants.UI_EXPIRY_REFRESH_INTERVAL_MS)
                purgeExpiredReports(nowProvider())
            }
        }
    }

    private fun syncDerivedState() {
        val snapshot = _uiState.value
        val needProximity = snapshot.reportScreenActive || snapshot.privateSpeechEnabled
        if (needProximity && !wasProximityNeeded) {
            proximityStateProvider.start()
            wasProximityNeeded = true
        } else if (!needProximity && wasProximityNeeded) {
            proximityStateProvider.stop()
            wasProximityNeeded = false
        }

        // Interaction lock formula while private speech is active (never lock on ERROR)
        val locked = snapshot.privateSpeechEnabled &&
            snapshot.speechState != SpeechState.ERROR &&
            (snapshot.proximityState == ProximityState.NEAR || snapshot.speechState == SpeechState.SPEAKING)

        if (snapshot.interactionLocked != locked) {
            _uiState.update { it.copy(interactionLocked = locked) }
        }

        // Assemble SpeechSummaryInput for the speech summary builder
        val currentAfterLock = _uiState.value
        val selected = currentAfterLock.selectedFountain
        _speechSummaryInput.value = selected?.let { fountain ->
            SpeechSummaryInput(
                fountain = fountain,
                officialStatus = fountain.officialStatus,
                communityReport = currentAfterLock.latestCommunityReport,
                lightObservation = currentAfterLock.latestCommunityReport?.lightObservation
                    ?: currentAfterLock.submittableLightObservation,
                distanceMeters = currentAfterLock.selectedFountainDistanceMeters,
                language = currentAfterLock.language,
                now = currentAfterLock.now
            )
        }
    }

    private fun computeDistances(
        from: Coordinates?,
        fountains: List<Fountain>
    ): Map<String, Int> {
        if (from == null) return emptyMap()
        return fountains.associate { fountain ->
            fountain.id to distanceCalculator.distanceMeters(from, fountain.coordinates)
        }
    }

    private fun sortFountainsByDistance(
        fountains: List<Fountain>,
        distances: Map<String, Int>
    ): List<Fountain> = fountains.sortedBy { fountain ->
        distances[fountain.id] ?: Int.MAX_VALUE
    }

    override fun onCleared() {
        super.onCleared()
        captureJob?.cancel()
        captureJob = null
        weatherJob?.cancel()
        weatherJob = null
        privateSpeechController.disable()
        if (wasProximityNeeded) {
            proximityStateProvider.stop()
            wasProximityNeeded = false
        }
        cleanupScope.launch {
            mqttRepository.disconnect()
        }
    }

    companion object {
        private const val PUBLISH_TIMEOUT_MS: Long = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SensOasisViewModel(
                        openDataRepository = container.openDataRepository,
                        mqttRepository = container.mqttRepository,
                        reportUseCase = container.reportUseCase,
                        proximityStateProvider = container.proximityStateProvider,
                        privateSpeechController = container.privateSpeechController,
                        locationProvider = container.locationProvider,
                        distanceCalculator = container.distanceCalculator,
                        cleanupScope = container.applicationScope
                    ) as T
                }
            }
    }
}
