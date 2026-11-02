package es.upm.sensoasis.fake

import es.upm.sensoasis.domain.contract.OpenDataRepository
import es.upm.sensoasis.domain.model.AirQualitySummary
import es.upm.sensoasis.domain.model.Coordinates
import es.upm.sensoasis.domain.model.DataResult
import es.upm.sensoasis.domain.model.Fountain
import kotlinx.coroutines.delay

/**
 * Configurable fake implementation of [OpenDataRepository] for parallel development and unit tests.
 */
class FakeOpenDataRepository(
    var simulatedDelayMs: Long = 250L,
    var fountainsResult: DataResult<List<Fountain>> = DataResult.Success(SampleData.fountains),
    var airQualityResult: DataResult<AirQualitySummary> = DataResult.Success(SampleData.sampleAirQualityGood)
) : OpenDataRepository {

    override suspend fun getFountains(): DataResult<List<Fountain>> {
        if (simulatedDelayMs > 0) delay(simulatedDelayMs)
        return fountainsResult
    }

    override suspend fun getAirQuality(location: Coordinates): DataResult<AirQualitySummary> {
        if (simulatedDelayMs > 0) delay(simulatedDelayMs / 2)
        return airQualityResult
    }
}
