package es.upm.sensoasis.domain.model

import kotlin.math.roundToLong

/**
 * Display formatting for ambient-light values.
 *
 * Rule: round only for display (e.g. `3200.4f` -> `"3200 lux"`). The stored
 * [LightObservation.ambientLux] stays a raw `Float` and the MQTT JSON payload is unchanged.
 * Locale-independent (no thousands separators), so the text is the same on every phone.
 */
fun formatLux(lux: Float?): String {
    if (lux == null || !lux.isFinite()) return "-- lux"
    val rounded = lux.coerceAtLeast(0f).roundToLong()
    return "$rounded lux"
}
