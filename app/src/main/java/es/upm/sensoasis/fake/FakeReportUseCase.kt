package es.upm.sensoasis.fake

import es.upm.sensoasis.domain.contract.LightSensorController
import es.upm.sensoasis.domain.contract.MqttRepository
import es.upm.sensoasis.domain.contract.ReportUseCase
import es.upm.sensoasis.domain.model.Constants
import es.upm.sensoasis.domain.model.FountainReport
import es.upm.sensoasis.domain.model.FountainStatus
import es.upm.sensoasis.domain.model.LightCaptureResult
import es.upm.sensoasis.domain.model.LightObservation
import es.upm.sensoasis.domain.model.PublishResult
import es.upm.sensoasis.domain.model.SubmitReportResult
import java.time.Instant

/**
 * Reference implementation of [ReportUseCase] connecting [LightSensorController]
 * and [MqttRepository] with the 20-minute TTL rule.
 */
class FakeReportUseCase(
    private val lightSensorController: LightSensorController,
    private val mqttRepository: MqttRepository,
    var nowProvider: () -> Instant = { Instant.now() }
) : ReportUseCase {

    override suspend fun captureObservation(): LightCaptureResult =
        lightSensorController.captureOnce(Constants.LIGHT_CAPTURE_TIMEOUT_MS)

    override suspend fun submitReport(
        fountainId: String,
        status: FountainStatus,
        lightObservation: LightObservation?
    ): SubmitReportResult {
        val now = nowProvider()
        val validLight = lightObservation?.takeIf {
            it.quality == es.upm.sensoasis.domain.model.LightQuality.VALID
        }
        val report = FountainReport(
            fountainId = fountainId,
            status = status,
            lightObservation = validLight,
            observedAt = now,
            expiresAt = now.plus(Constants.REPORT_TTL)
        )
        return when (val result = mqttRepository.publishReport(report)) {
            is PublishResult.Published -> SubmitReportResult.Success
            is PublishResult.Offline -> SubmitReportResult.Offline
            is PublishResult.Error -> SubmitReportResult.Error(result.message)
        }
    }
}
