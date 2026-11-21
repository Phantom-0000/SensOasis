package es.upm.sensoasis.ui.components

import android.content.Context
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
import java.util.concurrent.TimeUnit
import es.upm.sensoasis.domain.model.Coordinates

/**
 * Snapshot of real daytime hourly temperatures (08h–20h Madrid time) fetched from Open-Meteo
 * and persisted in local SharedPreferences so the chart remains available offline.
 */
data class HourlyTemperatureSnapshot(
    val hours: List<Int>,
    val tempsCelsius: List<Double>,
    val dateLabel: String,
    val fetchedAtEpochSec: Long,
    val isOfflineCached: Boolean = false
)

object HourlyWeatherLoader {

    private const val PREFS_NAME = "sensoasis_hourly_weather_cache"
    private const val KEY_FALLBACK_ANY = "last_madrid_forecast"
    private const val FRESH_TTL_SECONDS = 30L * 60L // 30 minutes

    private val targetHours = listOf(8, 10, 12, 14, 16, 18, 20)

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(7, TimeUnit.SECONDS)
            .build()
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun gridKey(coordinates: Coordinates): String = String.format(
        Locale.US,
        "grid_%.2f_%.2f",
        coordinates.latitude,
        coordinates.longitude
    )

    /**
     * Synchronously reads the locally cached forecast from SharedPreferences (if any)
     * so offline screens render immediately without waiting for network timeout.
     */
    fun readCached(context: Context, coordinates: Coordinates): HourlyTemperatureSnapshot? {
        val prefs = runCatching {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }.getOrNull() ?: return null

        val key = gridKey(coordinates)
        val raw = prefs.getString(key, null) ?: prefs.getString(KEY_FALLBACK_ANY, null) ?: return null
        return decodeSnapshot(raw)?.copy(isOfflineCached = true)
    }

    /**
     * Returns fresh forecast data from cache if <30 min old, otherwise fetches live hourly
     * temperature (`temperature_2m`) from Open-Meteo and persists it to local disk cache.
     * When offline, seamlessly returns the previously cached snapshot.
     */
    suspend fun loadOrFetch(
        context: Context,
        coordinates: Coordinates
    ): HourlyTemperatureSnapshot? {
        val appContext = context.applicationContext
        val prefs = runCatching {
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }.getOrNull()

        val key = gridKey(coordinates)
        val nowSec = Instant.now().epochSecond
        val existingExact = prefs?.getString(key, null)?.let(::decodeSnapshot)

        if (existingExact != null && (nowSec - existingExact.fetchedAtEpochSec) < FRESH_TTL_SECONDS) {
            return existingExact.copy(isOfflineCached = false)
        }

        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&hourly=temperature_2m&forecast_days=1&timezone=Europe%%2FMadrid",
            coordinates.latitude,
            coordinates.longitude
        )

        val fetched = try {
            withContext(Dispatchers.IO) {
                val request = Request.Builder().url(url).build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        null
                    } else {
                        val body = response.body?.string()
                        if (body != null) parseOpenMeteoResponse(body, nowSec) else null
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
            return fetched.copy(isOfflineCached = false)
        }

        val fallback = existingExact ?: prefs?.getString(KEY_FALLBACK_ANY, null)?.let(::decodeSnapshot)
        return fallback?.copy(isOfflineCached = true)
    }

    private fun parseOpenMeteoResponse(
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
            // Format: "2026-11-21T14:00"
            val parts = isoLocal.split('T')
            if (parts.size == 2) {
                if (dateLabel.isEmpty()) dateLabel = parts[0]
                val hour = parts[1].substringBefore(':').toIntOrNull()
                val temp = temps[idx]
                if (hour != null && hour in targetHours && temp.isFinite()) {
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

    private fun decodeSnapshot(raw: String): HourlyTemperatureSnapshot? = runCatching {
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
