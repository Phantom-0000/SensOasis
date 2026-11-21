package es.upm.sensoasis.data.opendata

import android.content.Context
import es.upm.sensoasis.domain.model.Coordinates
import es.upm.sensoasis.domain.model.DataResult
import es.upm.sensoasis.domain.model.HourlyTemperatureSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.util.Locale

/**
 * Data-layer source that fetches real daytime hourly temperatures (08h–20h Madrid time)
 * from the credential-free Open-Meteo Forecast API and caches snapshots in SharedPreferences
 * for offline resilience.
 */
class HourlyWeatherDataSource(
    context: Context,
    private val client: OkHttpClient,
    private val json: Json,
    private val forecastUrlBuilder: (Coordinates) -> String = ::defaultForecastUrl
) {
    private val appContext = context.applicationContext

    suspend fun loadOrFetch(location: Coordinates): DataResult<HourlyTemperatureSnapshot> {
        val prefs = runCatching {
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }.getOrNull()

        val key = gridKey(location)
        val nowSec = Instant.now().epochSecond
        val existingExact = prefs?.getString(key, null)?.let { decodeSnapshot(json, it) }

        if (existingExact != null && (nowSec - existingExact.fetchedAtEpochSec) < FRESH_TTL_SECONDS) {
            return DataResult.Success(existingExact.copy(isOfflineCached = false))
        }

        val fetched = try {
            withContext(Dispatchers.IO) {
                val request = Request.Builder().url(forecastUrlBuilder(location)).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        null
                    } else {
                        val body = response.body?.string()
                        if (body != null) parseOpenMeteoResponse(json, body, nowSec) else null
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }

        if (fetched != null) {
            val encoded = encodeSnapshot(fetched)
            prefs?.edit()
                ?.putString(key, encoded)
                ?.putString(KEY_FALLBACK_ANY, encoded)
                ?.apply()
            return DataResult.Success(fetched.copy(isOfflineCached = false))
        }

        val fallback = existingExact
            ?: prefs?.getString(KEY_FALLBACK_ANY, null)?.let { decodeSnapshot(json, it) }

        return if (fallback != null) {
            DataResult.Success(fallback.copy(isOfflineCached = true))
        } else {
            DataResult.Unavailable("NO_WEATHER_CACHE")
        }
    }

    companion object {
        private const val PREFS_NAME = "sensoasis_hourly_weather_cache"
        private const val KEY_FALLBACK_ANY = "last_madrid_forecast"
        private const val FRESH_TTL_SECONDS = 30L * 60L // 30 minutes
        private val TARGET_HOURS = listOf(8, 10, 12, 14, 16, 18, 20)

        fun gridKey(coordinates: Coordinates): String = String.format(
            Locale.US,
            "grid_%.2f_%.2f",
            coordinates.latitude,
            coordinates.longitude
        )

        fun defaultForecastUrl(coordinates: Coordinates): String = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&hourly=temperature_2m&forecast_days=1&timezone=Europe%%2FMadrid",
            coordinates.latitude,
            coordinates.longitude
        )

        internal fun parseOpenMeteoResponse(
            json: Json,
            body: String,
            nowSec: Long
        ): HourlyTemperatureSnapshot? = runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            val hourly = root["hourly"]?.jsonObject ?: return null
            val times = hourly["time"]?.jsonArray?.map { it.jsonPrimitive.content } ?: return null
            val temps = hourly["temperature_2m"]?.jsonArray?.mapNotNull { it.jsonPrimitive.doubleOrNull }
                ?: return null
            if (times.size != temps.size || times.isEmpty()) return null

            val extractedHours = mutableListOf<Int>()
            val extractedTemps = mutableListOf<Double>()
            var dateLabel = ""

            times.forEachIndexed { idx, isoLocal ->
                val parts = isoLocal.split('T')
                if (parts.size == 2) {
                    if (dateLabel.isEmpty()) dateLabel = parts[0]
                    val hour = parts[1].substringBefore(':').toIntOrNull()
                    val temp = temps[idx]
                    if (hour != null && hour in TARGET_HOURS && temp.isFinite()) {
                        extractedHours.add(hour)
                        extractedTemps.add(temp)
                    }
                }
            }

            if (extractedHours.size < 3) return null
            HourlyTemperatureSnapshot(
                hours = extractedHours,
                tempsCelsius = extractedTemps,
                dateLabel = dateLabel.ifEmpty { "Today" },
                fetchedAtEpochSec = nowSec,
                isOfflineCached = false
            )
        }.getOrNull()

        private fun encodeSnapshot(snapshot: HourlyTemperatureSnapshot): String {
            val obj = buildJsonObject {
                put("hours", JsonArray(snapshot.hours.map { JsonPrimitive(it) }))
                put("temps", JsonArray(snapshot.tempsCelsius.map { JsonPrimitive(it) }))
                put("date", snapshot.dateLabel)
                put("fetchedAt", snapshot.fetchedAtEpochSec)
            }
            return obj.toString()
        }

        private fun decodeSnapshot(json: Json, raw: String): HourlyTemperatureSnapshot? = runCatching {
            val obj = json.parseToJsonElement(raw).jsonObject
            val hours = obj["hours"]?.jsonArray?.mapNotNull { it.jsonPrimitive.intOrNull } ?: return null
            val temps = obj["temps"]?.jsonArray?.mapNotNull { it.jsonPrimitive.doubleOrNull } ?: return null
            if (hours.size != temps.size || hours.size < 3) return null
            val date = obj["date"]?.jsonPrimitive?.content ?: ""
            val fetchedAt = obj["fetchedAt"]?.jsonPrimitive?.longOrNull ?: 0L
            HourlyTemperatureSnapshot(
                hours = hours,
                tempsCelsius = temps,
                dateLabel = date,
                fetchedAtEpochSec = fetchedAt
            )
        }.getOrNull()
    }
}
