package es.upm.sensoasis.domain.contract

import es.upm.sensoasis.domain.model.AirQualitySummary
import es.upm.sensoasis.domain.model.Coordinates
import es.upm.sensoasis.domain.model.DataResult
import es.upm.sensoasis.domain.model.Fountain

/**
 * Downloads and parses open-data JSON datasets (primarily Madrid public drinking fountains
 * from datos.madrid.es, plus optional air-quality summary).
 */
interface OpenDataRepository {
    suspend fun getFountains(): DataResult<List<Fountain>>

    suspend fun getAirQuality(location: Coordinates): DataResult<AirQualitySummary>
}
