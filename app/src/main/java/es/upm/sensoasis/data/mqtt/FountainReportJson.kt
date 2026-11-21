package es.upm.sensoasis.data.mqtt

import es.upm.sensoasis.domain.model.FountainReport
import es.upm.sensoasis.domain.model.FountainStatus
import es.upm.sensoasis.domain.model.LightBand
import es.upm.sensoasis.domain.model.LightObservation
import es.upm.sensoasis.domain.model.LightQuality
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

/** Stable JSON wire contract shared by every fountain-report publisher/subscriber. */
object FountainReportJson {
    private val json = Json { prettyPrint = false; explicitNulls = true }

    fun encode(report: FountainReport): String = buildString {
        append("{\"fountainId\":${quote(report.fountainId)},")
        append("\"status\":${quote(report.status.name)},")
        append("\"lightObservation\":")
        val light = report.lightObservation
        if (light == null) append("null") else {
            append("{\"ambientLux\":${light.ambientLux?.toString() ?: "null"},")
            append("\"lightBand\":${light.lightBand?.let { quote(it.name) } ?: "null"},")
            append("\"quality\":${quote(light.quality.name)},")
            append("\"observedAt\":${quote(light.observedAt.toString())}}")
        }
        append(",\"observedAt\":${quote(report.observedAt.toString())},")
        append("\"expiresAt\":${quote(report.expiresAt.toString())}}")
    }

    fun decode(payload: String): FountainReport {
        val root = json.parseToJsonElement(payload).jsonObject
        val fountainId = root.getValue("fountainId").jsonPrimitive.content
        val status = FountainStatus.valueOf(root.getValue("status").jsonPrimitive.content)
        val lightElement = root["lightObservation"]
        val light = if (lightElement == null || lightElement == JsonNull) null else {
            val obj = lightElement.jsonObject
            val lux = obj["ambientLux"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.content?.toFloatOrNull()
            val band = obj["lightBand"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.content
                ?.let { runCatching { LightBand.valueOf(it) }.getOrNull() }
            val quality = obj["quality"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.content
                ?.let { runCatching { LightQuality.valueOf(it) }.getOrNull() }
            val lightAt = obj["observedAt"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.content
                ?.let { runCatching { Instant.parse(it) }.getOrNull() }
            if (quality == LightQuality.VALID && lux != null && band != null && lightAt != null) {
                LightObservation(lux, band, LightQuality.VALID, lightAt)
            } else if (lightAt != null && quality != null) {
                // Occluded/unavailable observations may not include a numeric lux or band.
                LightObservation(null, null, quality, lightAt)
            } else null
        }
        return FountainReport(
            fountainId = fountainId,
            status = status,
            lightObservation = light,
            observedAt = Instant.parse(root.getValue("observedAt").jsonPrimitive.content),
            expiresAt = Instant.parse(root.getValue("expiresAt").jsonPrimitive.content)
        )
    }

    private fun quote(value: String): String = json.encodeToString(kotlinx.serialization.serializer(), value)
}
