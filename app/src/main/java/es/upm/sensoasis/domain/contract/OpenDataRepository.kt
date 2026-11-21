package es.upm.sensoasis.domain.contract

import es.upm.sensoasis.domain.model.Coordinates
import es.upm.sensoasis.domain.model.DataResult
import es.upm.sensoasis.domain.model.Fountain
import es.upm.sensoasis.domain.model.HourlyTemperatureSnapshot

/**
 * Downloads and parses open-data JSON datasets (primarily Madrid public drinking fountains
 * from datos.madrid.es, plus daytime hourly outdoor temperature forecast with local cache).
 */
interface OpenDataRepository {
    suspend fun getFountains(): DataResult<List<Fountain>>

    suspend fun getHourlyTemperature(location: Coordinates): DataResult<HourlyTemperatureSnapshot>
}
