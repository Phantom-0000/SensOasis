package es.upm.sensoasis.data.opendata

import android.content.Context
import android.util.Log
import es.upm.sensoasis.domain.contract.OpenDataRepository
import es.upm.sensoasis.domain.model.Coordinates
import es.upm.sensoasis.domain.model.DataResult
import es.upm.sensoasis.domain.model.Fountain
import es.upm.sensoasis.domain.model.HourlyTemperatureSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Open Madrid fountain feed with bundled asset fallback, plus daytime hourly outdoor
 * temperature forecast cached locally in the data layer.
 */
class RealOpenDataRepository(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
    private val fountainsUrl: String = MADRID_FOUNTAINS_URL
) : OpenDataRepository {

    private val weatherDataSource = HourlyWeatherDataSource(
        context = context,
        client = client,
        json = json
    )

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
            is DataResult.Error -> {
                Log.w(TAG, "Madrid fountain download/parse failed; using bundled sample: ${remote.message}")
                readBundledFountains(remote.message)
            }
            is DataResult.Unavailable -> {
                Log.w(TAG, "Madrid fountain feed unavailable; using bundled sample: ${remote.reason}")
                readBundledFountains(remote.reason)
            }
        }
    }

    override suspend fun getHourlyTemperature(location: Coordinates): DataResult<HourlyTemperatureSnapshot> =
        weatherDataSource.loadOrFetch(location)

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

    companion object {
        private const val TAG = "MadridOpenData"
        const val FOUNTAINS_ASSET = "samples/fountains_sample.json"
        // Madrid open-data JSON catalog, downloadable without credentials.
        const val MADRID_FOUNTAINS_URL =
            "https://datos.madrid.es/dataset/300051-0-fuentes/resource/300051-0-fuentes/download/300051-0-fuentes.json"
    }
}
