package es.upm.sensoasis.viewmodel

import es.upm.sensoasis.domain.model.AirQualitySummary
import es.upm.sensoasis.domain.model.ConnectionState
import es.upm.sensoasis.domain.model.Coordinates
import es.upm.sensoasis.domain.model.Fountain
import es.upm.sensoasis.domain.model.FountainReport
import es.upm.sensoasis.domain.model.FountainStatus
import es.upm.sensoasis.domain.model.LightCaptureResult
import es.upm.sensoasis.domain.model.LightObservation
import es.upm.sensoasis.domain.model.ProximityState
import es.upm.sensoasis.domain.model.SpeechState
import es.upm.sensoasis.domain.model.ReportReceiveState
import java.time.Instant

/**
 * State machine for the two-phase report capture and publication flow on ReportScreen.
 *
 * A fountain status report remains valid and submittable when the light sensor returns
 * [READY], [OCCLUDED], or [UNAVAILABLE] (or before light capture in [IDLE]).
 * Only [READY] attaches a non-null [LightObservation]; [OCCLUDED] and [UNAVAILABLE] submit with null.
 */
enum class ReportFlowState {
    IDLE,
    CAPTURING_LIGHT,
    READY,
    OCCLUDED,
    UNAVAILABLE,
    PUBLISHING,
    PUBLISHED,
    OFFLINE,
    ERROR;

    val canSubmit: Boolean
        get() = this != CAPTURING_LIGHT && this != PUBLISHING
}

/**
 * Single Source of Truth UI state exposed by [SensOasisViewModel] and consumed by Compose screens.
 */
data class SensOasisUiState(
    val fountains: List<Fountain> = emptyList(),
    val isLoadingFountains: Boolean = false,
    val selectedFountain: Fountain? = null,
    val userLocation: Coordinates? = null,
    val isFallbackLocation: Boolean = false,
    val selectedFountainDistanceMeters: Int? = null,
    val fountainDistancesMeters: Map<String, Int> = emptyMap(),
    val latestCommunityReport: FountainReport? = null,
    val communityReportsByFountain: Map<String, FountainReport> = emptyMap(),
    val airQuality: AirQualitySummary? = null,
    val reportDraftStatus: FountainStatus = FountainStatus.WORKING,
    val lightState: LightCaptureResult? = null,
    val reportState: ReportFlowState = ReportFlowState.IDLE,
    val reportScreenActive: Boolean = false,
    val proximityState: ProximityState = ProximityState.UNKNOWN,
    val privateSpeechEnabled: Boolean = false,
    val speechState: SpeechState = SpeechState.IDLE,
    val mqttState: ConnectionState = ConnectionState.DISCONNECTED,
    val mqttReceiveState: ReportReceiveState = ReportReceiveState.WAITING,
    val interactionLocked: Boolean = false,
    val now: Instant = Instant.now(),
    val error: String? = null
) {
    /**
     * Returns the valid [LightObservation] to attach to a report submission, or null when
     * no observation was captured or the sensor was occluded/unavailable.
     */
    val submittableLightObservation: LightObservation?
        get() = (lightState as? LightCaptureResult.Valid)?.observation
}

/**
 * User actions dispatched from Compose screens to [SensOasisViewModel.onAction].
 */
sealed interface UiAction {
    data class SelectFountain(val fountainId: String) : UiAction
    data object OpenReport : UiAction
    data object CaptureLight : UiAction
    data class ChangeFountainStatus(val status: FountainStatus) : UiAction
    data object SubmitReport : UiAction
    data object LeaveReportScreen : UiAction
    data class EnablePrivateSpeech(val enabled: Boolean = true) : UiAction
    data object StopSpeech : UiAction
    data object OpenWalkingRoute : UiAction
    data object RefreshFountains : UiAction
    data object RefreshLocation : UiAction
    data object ClearError : UiAction
}

/**
 * One-time UI side effects (never stored in persistent state to avoid re-triggering on rotation).
 */
sealed interface UiEffect {
    data class OpenWalkingRoute(
        val destination: Coordinates,
        val fountainName: String
    ) : UiEffect

    data class ShowMessage(val message: String) : UiEffect
}
