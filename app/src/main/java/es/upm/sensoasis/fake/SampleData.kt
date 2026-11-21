package es.upm.sensoasis.fake

import es.upm.sensoasis.domain.model.ConnectionState
import es.upm.sensoasis.domain.model.Constants
import es.upm.sensoasis.domain.model.Coordinates
import es.upm.sensoasis.domain.model.Fountain
import es.upm.sensoasis.domain.model.FountainReport
import es.upm.sensoasis.domain.model.FountainStatus
import es.upm.sensoasis.domain.model.HourlyTemperatureSnapshot
import es.upm.sensoasis.domain.model.LightBand
import es.upm.sensoasis.domain.model.LightCaptureResult
import es.upm.sensoasis.domain.model.LightObservation
import es.upm.sensoasis.domain.model.LightQuality
import es.upm.sensoasis.domain.model.ProximityState
import es.upm.sensoasis.domain.model.SpeechState
import es.upm.sensoasis.location.HaversineDistanceCalculator
import es.upm.sensoasis.viewmodel.ReportFlowState
import es.upm.sensoasis.viewmodel.SensOasisUiState
import java.time.Duration
import java.time.Instant

/**
 * Shared sample fountains, community reports, and preset [SensOasisUiState] instances
 * for parallel development, Compose `@Preview`s, and unit tests.
 */
object SampleData {
    private val distanceCalculator = HaversineDistanceCalculator()

    val referenceNow: Instant = Instant.parse("2026-11-02T14:15:00Z")

    val sampleHourlyTemperature = HourlyTemperatureSnapshot(
        hours = listOf(8, 10, 12, 14, 16, 18, 20),
        tempsCelsius = listOf(10.0, 12.5, 15.0, 17.5, 18.0, 15.5, 13.0),
        dateLabel = "2026-11-02",
        fetchedAtEpochSec = referenceNow.epochSecond,
        isOfflineCached = false
    )

    val fountains: List<Fountain> = listOf(
        Fountain(
            id = "MADRID_FT_042",
            name = "Fuente Puerta del Sol",
            coordinates = Coordinates(latitude = 40.4169, longitude = -3.7035),
            officialStatus = FountainStatus.WORKING,
            address = "Plaza de la Puerta del Sol, Madrid"
        ),
        Fountain(
            id = "MADRID_FT_108",
            name = "Fuente Parque del Retiro - Estanque",
            coordinates = Coordinates(latitude = 40.4172, longitude = -3.6838),
            officialStatus = FountainStatus.WORKING,
            address = "Plaza de la Independencia, 7, Madrid"
        ),
        Fountain(
            id = "MADRID_FT_215",
            name = "Fuente UPM Campus Sur - ETSIST",
            coordinates = Coordinates(latitude = 40.3898, longitude = -3.6283),
            officialStatus = FountainStatus.WORKING,
            address = "Calle Nikola Tesla, s/n, Madrid"
        ),
        Fountain(
            id = "MADRID_FT_309",
            name = "Fuente Plaza Mayor",
            coordinates = Coordinates(latitude = 40.4155, longitude = -3.7074),
            officialStatus = FountainStatus.NOT_WORKING,
            address = "Plaza Mayor, Madrid"
        ),
        Fountain(
            id = "MADRID_FT_412",
            name = "Fuente Paseo del Prado - Cibeles",
            coordinates = Coordinates(latitude = 40.4189, longitude = -3.6929),
            officialStatus = FountainStatus.UNKNOWN,
            address = "Paseo del Prado, 1, Madrid"
        ),
        Fountain(
            id = "MADRID_FT_520",
            name = "Fuente Atocha - Jardín Tropical",
            coordinates = Coordinates(latitude = 40.4065, longitude = -3.6896),
            officialStatus = FountainStatus.WORKING,
            address = "Plaza del Emperador Carlos V, Madrid"
        )
    )

    fun sampleLightObservation(
        now: Instant = Instant.now(),
        lux: Float = 3200f,
        band: LightBand = LightBand.MODERATE
    ): LightObservation = LightObservation(
        ambientLux = lux,
        lightBand = band,
        quality = LightQuality.VALID,
        observedAt = now.minus(Duration.ofMinutes(4))
    )

    fun sampleValidReport(
        fountainId: String = "MADRID_FT_042",
        now: Instant = Instant.now()
    ): FountainReport {
        val observedAt = now.minus(Duration.ofMinutes(4))
        return FountainReport(
            fountainId = fountainId,
            status = FountainStatus.WORKING,
            lightObservation = LightObservation(
                ambientLux = 3200f,
                lightBand = LightBand.MODERATE,
                quality = LightQuality.VALID,
                observedAt = observedAt
            ),
            observedAt = observedAt,
            expiresAt = observedAt.plus(Constants.REPORT_TTL)
        )
    }

    fun sampleNullLightReport(
        fountainId: String = "MADRID_FT_108",
        now: Instant = Instant.now()
    ): FountainReport {
        val observedAt = now.minus(Duration.ofMinutes(7))
        return FountainReport(
            fountainId = fountainId,
            status = FountainStatus.WORKING,
            lightObservation = null,
            observedAt = observedAt,
            expiresAt = observedAt.plus(Constants.REPORT_TTL)
        )
    }

    fun sampleExpiredReport(
        fountainId: String = "MADRID_FT_309",
        now: Instant = Instant.now()
    ): FountainReport {
        val observedAt = now.minus(Duration.ofMinutes(45))
        return FountainReport(
            fountainId = fountainId,
            status = FountainStatus.NOT_WORKING,
            lightObservation = null,
            observedAt = observedAt,
            expiresAt = observedAt.plus(Constants.REPORT_TTL)
        )
    }

    private val defaultDistances: Map<String, Int> = fountains.associate { fountain ->
        fountain.id to distanceCalculator.distanceMeters(
            Constants.MADRID_FALLBACK_COORDINATES,
            fountain.coordinates
        )
    }

    /** Preset 1: Fountain list loaded and MQTT connected. */
    fun previewListLoadedState(now: Instant = referenceNow): SensOasisUiState {
        val report = sampleValidReport(now = now)
        return SensOasisUiState(
            fountains = fountains,
            selectedFountain = fountains.first(),
            userLocation = Constants.MADRID_FALLBACK_COORDINATES,
            isFallbackLocation = true,
            selectedFountainDistanceMeters = defaultDistances[fountains.first().id],
            fountainDistancesMeters = defaultDistances,
            latestCommunityReport = report,
            communityReportsByFountain = mapOf(report.fountainId to report),
            hourlyTemperature = sampleHourlyTemperature,
            mqttState = ConnectionState.CONNECTED,
            proximityState = ProximityState.FAR,
            now = now
        )
    }

    /** Preset 2: Fountain detail screen with active community report and waiting for NEAR speech. */
    fun previewDetailWithReportState(now: Instant = referenceNow): SensOasisUiState =
        previewListLoadedState(now).copy(
            privateSpeechEnabled = true,
            speechState = SpeechState.WAITING_FOR_NEAR
        )

    /** Preset 3: Report screen actively capturing light. */
    fun previewReportCapturingState(now: Instant = referenceNow): SensOasisUiState =
        previewListLoadedState(now).copy(
            reportScreenActive = true,
            reportState = ReportFlowState.CAPTURING_LIGHT,
            proximityState = ProximityState.FAR
        )

    /** Preset 4: Report screen with valid light preview ready to publish. */
    fun previewReportReadyState(now: Instant = referenceNow): SensOasisUiState {
        val obs = sampleLightObservation(now = now)
        return previewListLoadedState(now).copy(
            reportScreenActive = true,
            lightState = LightCaptureResult.Valid(obs),
            reportState = ReportFlowState.READY,
            proximityState = ProximityState.FAR
        )
    }

    /** Preset 5: Report screen where light sensor was covered (OCCLUDED), still submittable without light. */
    fun previewReportOccludedState(now: Instant = referenceNow): SensOasisUiState =
        previewListLoadedState(now).copy(
            reportScreenActive = true,
            lightState = LightCaptureResult.Occluded,
            reportState = ReportFlowState.OCCLUDED,
            proximityState = ProximityState.NEAR
        )

    /** Preset 6: Detail screen while phone is near ear and speaking (interactionLocked = true). */
    fun previewSpeechLockedState(now: Instant = referenceNow): SensOasisUiState =
        previewDetailWithReportState(now).copy(
            proximityState = ProximityState.NEAR,
            speechState = SpeechState.SPEAKING,
            interactionLocked = true
        )

    /** Preset 7: Offline / error state. */
    fun previewOfflineErrorState(now: Instant = referenceNow): SensOasisUiState =
        previewListLoadedState(now).copy(
            mqttState = ConnectionState.ERROR,
            reportState = ReportFlowState.OFFLINE,
            error = "MQTT broker unreachable (offline mode)"
        )
}
