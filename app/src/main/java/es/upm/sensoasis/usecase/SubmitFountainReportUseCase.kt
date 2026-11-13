package es.upm.sensoasis.usecase

import es.upm.sensoasis.domain.contract.LightSensorController
import es.upm.sensoasis.domain.contract.MqttRepository
import es.upm.sensoasis.domain.contract.ReportUseCase
import es.upm.sensoasis.domain.model.Constants
import es.upm.sensoasis.domain.model.FountainReport
import es.upm.sensoasis.domain.model.FountainStatus
import es.upm.sensoasis.domain.model.LightCaptureResult
import es.upm.sensoasis.domain.model.LightObservation
import es.upm.sensoasis.domain.model.LightQuality
import es.upm.sensoasis.domain.model.PublishResult
import es.upm.sensoasis.domain.model.SubmitReportResult
import java.time.Instant

class SubmitFountainReportUseCase(
    private val lightSensorController: LightSensorController,
    private val mqttRepository: MqttRepository,
    private val nowProvider: () -> Instant = { Instant.now() }
) : ReportUseCase {
    override suspend fun captureObservation(): LightCaptureResult =
        lightSensorController.captureOnce(Constants.LIGHT_CAPTURE_TIMEOUT_MS)

    override suspend fun submitReport(fountainId: String, status: FountainStatus, lightObservation: LightObservation?): SubmitReportResult {
        val now = nowProvider()
        val report = FountainReport(
            fountainId = fountainId,
            status = status,
            lightObservation = lightObservation?.takeIf { it.quality == LightQuality.VALID },
            observedAt = now,
            expiresAt = now.plus(Constants.REPORT_TTL)
        )
        return when (val result = mqttRepository.publishReport(report)) {
            PublishResult.Published -> SubmitReportResult.Success
            PublishResult.Offline -> SubmitReportResult.Offline
            is PublishResult.Error -> SubmitReportResult.Error(result.message)
        }
    }
}
