package es.upm.sensoasis.domain.contract

import es.upm.sensoasis.domain.model.FountainStatus
import es.upm.sensoasis.domain.model.LightCaptureResult
import es.upm.sensoasis.domain.model.LightObservation
import es.upm.sensoasis.domain.model.SubmitReportResult

/**
 * Coordinates the two-phase fountain reporting workflow:
 * 1. [captureObservation] delegates to [LightSensorController.captureOnce] for preview on ReportScreen.
 * 2. [submitReport] combines the user's selected [FountainStatus] with the optional [LightObservation],
 *    computes [es.upm.sensoasis.domain.model.FountainReport.expiresAt], and publishes via [MqttRepository].
 */
interface ReportUseCase {
    suspend fun captureObservation(): LightCaptureResult

    suspend fun submitReport(
        fountainId: String,
        status: FountainStatus,
        lightObservation: LightObservation?
    ): SubmitReportResult
}
