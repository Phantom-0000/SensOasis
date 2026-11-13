package es.upm.sensoasis.data.opendata

import android.content.Context
import es.upm.sensoasis.domain.contract.OpenDataRepository
import es.upm.sensoasis.domain.model.AirQualitySummary
import es.upm.sensoasis.domain.model.Coordinates
import es.upm.sensoasis.domain.model.DataResult
import es.upm.sensoasis.domain.model.Fountain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/** Open Madrid fountain feed plus a parsed open air-quality observation near a location. */
class RealOpenDataRepository(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
    private val fountainsUrl: String = MADRID_FOUNTAINS_URL,
    private val airQualityUrl: (Coordinates) -> String = { coordinates ->
        "https://api.open-meteo.com/v1/air-quality?latitude=${coordinates.latitude}" +
            "&longitude=${coordinates.longitude}&current=european_aqi"
    }
) : OpenDataRepository {

    override suspend fun getFountains(): DataResult<List<Fountain>> {
        val remote = try {
            withContext(Dispatchers.IO) {
                val request = Request.Builder().url(fountainsUrl).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        DataResult.Error("Madrid data service returned HTTP ${response.code}")
                    } else {
                        val body = response.body?.string()
                        if (body == null) DataResult.Unavailable("Madrid data service returned an empty response")
                        else parseFountains(body)
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            DataResult.Error(e.message ?: "Madrid data service unavailable")
        }
        return when (remote) {
            is DataResult.Success -> remote
            is DataResult.Error -> readBundledFountains(remote.message)
            is DataResult.Unavailable -> readBundledFountains(remote.reason)
        }
    }

    private suspend fun readBundledFountains(remoteFailure: String): DataResult<List<Fountain>> = try {
        withContext(Dispatchers.IO) {
        val bundledJson = context.assets.open(FOUNTAINS_ASSET).bufferedReader().use { it.readText() }
        when (val result = parseFountains(bundledJson)) {
            is DataResult.Success -> result
            is DataResult.Error -> DataResult.Error("$remoteFailure; bundled fountain data is invalid: ${result.message}")
            is DataResult.Unavailable -> DataResult.Error("$remoteFailure; bundled fountain data is unavailable: ${result.reason}")
        }
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        DataResult.Error("$remoteFailure; unable to read bundled fountain data: ${e.message}")
    }

    private fun parseFountains(source: String): DataResult<List<Fountain>> = try {
        val fountains = MadridFountainsParser.parse(source)
        if (fountains.isEmpty()) DataResult.Unavailable("Fountain dataset is empty") else DataResult.Success(fountains)
    } catch (e: Exception) {
        DataResult.Error(e.message ?: "Unable to parse fountain JSON")
    }

    override suspend fun getAirQuality(location: Coordinates): DataResult<AirQualitySummary> = try {
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(airQualityUrl(location)).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    DataResult.Error("Air-quality service returned HTTP ${response.code}")
                } else {
                    val body = response.body?.string()
                    if (body == null) {
                        DataResult.Unavailable("Empty air-quality response")
                    } else {
                        val root = json.parseToJsonElement(body).jsonObject
                        val current = root["current"]?.jsonObject
                        val value = current?.get("european_aqi")?.jsonPrimitive?.content?.toDoubleOrNull()?.toInt()
                        if (current == null || value == null) {
                            DataResult.Unavailable("Current air-quality index is missing")
                        } else {
                            val timestamp = current["time"]?.jsonPrimitive?.content?.let {
                                runCatching { Instant.parse(it) }.getOrNull()
                                    ?: runCatching { java.time.LocalDateTime.parse(it).toInstant(java.time.ZoneOffset.UTC) }.getOrNull()
                            } ?: Instant.now()
                            DataResult.Success(AirQualitySummary(value, categoryFor(value), timestamp))
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        DataResult.Error(e.message ?: "Unable to fetch air quality")
    }

    private fun categoryFor(aqi: Int): String = when {
        aqi <= 20 -> "GOOD"
        aqi <= 40 -> "FAIR"
        aqi <= 60 -> "MODERATE"
        aqi <= 80 -> "POOR"
        aqi <= 100 -> "VERY_POOR"
        else -> "EXTREMELY_POOR"
    }

    companion object {
        const val FOUNTAINS_ASSET = "samples/fountains_sample.json"
        // Madrid open-data JSON catalog, downloadable without credentials.
        // Override via constructor for an updated catalog URL if Madrid changes its feed.
        const val MADRID_FOUNTAINS_URL =
            "https://datos.madrid.es/dataset/300051-0-fuentes/resource/300051-0-fuentes/download/300051-0-fuentes.json"
    }
}
