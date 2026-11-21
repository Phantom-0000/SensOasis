package es.upm.sensoasis.domain.model

/**
 * Snapshot of real daytime hourly temperatures (08h–20h Madrid time) fetched from Open-Meteo
 * and persisted locally so the temperature curve remains available offline.
 */
data class HourlyTemperatureSnapshot(
    val hours: List<Int>,
    val tempsCelsius: List<Double>,
    val dateLabel: String,
    val fetchedAtEpochSec: Long,
    val isOfflineCached: Boolean = false
)
