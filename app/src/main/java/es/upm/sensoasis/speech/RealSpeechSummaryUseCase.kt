package es.upm.sensoasis.speech

import es.upm.sensoasis.domain.contract.SpeechSummaryUseCase
import es.upm.sensoasis.domain.model.AppLanguage
import es.upm.sensoasis.domain.model.FountainStatus
import es.upm.sensoasis.domain.model.LightBand
import es.upm.sensoasis.domain.model.LightObservation
import es.upm.sensoasis.domain.model.LightQuality
import es.upm.sensoasis.domain.model.SpeechSummaryInput
import es.upm.sensoasis.domain.model.SpeechText

/**
 * Builds a concise, senior-friendly spoken summary of the selected fountain
 * in English (EN), Spanish (ES), or Catalan (CA).
 */
class RealSpeechSummaryUseCase : SpeechSummaryUseCase {

    override fun buildSummary(input: SpeechSummaryInput): SpeechText {
        val text = when (input.language) {
            AppLanguage.EN -> buildEnglishSummary(input)
            AppLanguage.ES -> buildSpanishSummary(input)
            AppLanguage.CA -> buildCatalanSummary(input)
        }
        return SpeechText(value = text, language = input.language)
    }

    private fun resolveValidLight(input: SpeechSummaryInput): LightObservation? {
        val activeReport = input.communityReport?.takeUnless { it.isExpired(input.now) }
        val candidate = activeReport?.lightObservation ?: input.lightObservation
        return candidate?.takeIf { it.quality == LightQuality.VALID && it.lightBand != null }
    }

    private fun buildEnglishSummary(input: SpeechSummaryInput): String {
        val parts = mutableListOf<String>()
        val name = input.fountain.name.trim()
        parts.add(
            if (name.startsWith("Fountain", ignoreCase = true)) "$name." else "Fountain: $name."
        )

        val distance = input.distanceMeters
        parts.add(
            when {
                distance == null || distance < 0 -> "Distance is currently unknown."
                distance == 1 -> "Distance: 1 meter."
                else -> "Distance: $distance meters."
            }
        )

        parts.add(
            when (input.officialStatus) {
                FountainStatus.WORKING -> "Official status: working."
                FountainStatus.NOT_WORKING -> "Official status: not working."
                FountainStatus.UNKNOWN -> "Official status: unknown."
            }
        )

        val report = input.communityReport
        if (report == null || report.isExpired(input.now)) {
            parts.add("There is no recent community report.")
        } else {
            val age = report.ageMinutes(input.now)
            val reportAge = when {
                age == 0L -> "just now"
                age == 1L -> "1 minute ago"
                else -> "$age minutes ago"
            }
            val reportStatus = when (report.status) {
                FountainStatus.WORKING -> "working"
                FountainStatus.NOT_WORKING -> "not working"
                FountainStatus.UNKNOWN -> "unknown"
            }
            parts.add("The latest community report, $reportAge, says the fountain is $reportStatus.")
        }

        resolveValidLight(input)?.lightBand?.let { band ->
            val lightDescription = when (band) {
                LightBand.LOW -> "low light, shaded or dim area"
                LightBand.MODERATE -> "moderate light with comfortable visibility"
                LightBand.BRIGHT -> "bright sunlight with high glare"
            }
            parts.add("Reported lighting: $lightDescription.")
        }

        return parts.joinToString(" ")
    }

    private fun buildSpanishSummary(input: SpeechSummaryInput): String {
        val parts = mutableListOf<String>()
        val name = input.fountain.name.trim()
        parts.add(
            if (name.startsWith("Fuente", ignoreCase = true)) "$name." else "Fuente: $name."
        )

        val distance = input.distanceMeters
        parts.add(
            when {
                distance == null || distance < 0 -> "Distancia no disponible."
                distance == 1 -> "Distancia: 1 metro."
                else -> "Distancia: $distance metros."
            }
        )

        parts.add(
            when (input.officialStatus) {
                FountainStatus.WORKING -> "Estado oficial: operativa."
                FountainStatus.NOT_WORKING -> "Estado oficial: no operativa."
                FountainStatus.UNKNOWN -> "Estado oficial: desconocido."
            }
        )

        val report = input.communityReport
        if (report == null || report.isExpired(input.now)) {
            parts.add("No hay avisos ciudadanos recientes.")
        } else {
            val age = report.ageMinutes(input.now)
            val reportAge = when {
                age == 0L -> "hace un momento"
                age == 1L -> "hace 1 minuto"
                else -> "hace $age minutos"
            }
            val reportStatus = when (report.status) {
                FountainStatus.WORKING -> "operativa"
                FountainStatus.NOT_WORKING -> "no operativa"
                FountainStatus.UNKNOWN -> "en estado desconocido"
            }
            parts.add("El último aviso ciudadano, $reportAge, indica que la fuente está $reportStatus.")
        }

        resolveValidLight(input)?.lightBand?.let { band ->
            val lightDescription = when (band) {
                LightBand.LOW -> "poca luz, zona sombreada o tenue"
                LightBand.MODERATE -> "luz moderada con visibilidad cómoda"
                LightBand.BRIGHT -> "luz brillante con alto deslumbramiento"
            }
            parts.add("Luz registrada: $lightDescription.")
        }

        return parts.joinToString(" ")
    }

    private fun buildCatalanSummary(input: SpeechSummaryInput): String {
        val parts = mutableListOf<String>()
        val name = input.fountain.name.trim()
        parts.add(
            if (name.startsWith("Font", ignoreCase = true)) "$name." else "Font: $name."
        )

        val distance = input.distanceMeters
        parts.add(
            when {
                distance == null || distance < 0 -> "Distància no disponible."
                distance == 1 -> "Distància: 1 metre."
                else -> "Distància: $distance metres."
            }
        )

        parts.add(
            when (input.officialStatus) {
                FountainStatus.WORKING -> "Estat oficial: operativa."
                FountainStatus.NOT_WORKING -> "Estat oficial: no operativa."
                FountainStatus.UNKNOWN -> "Estat oficial: desconegut."
            }
        )

        val report = input.communityReport
        if (report == null || report.isExpired(input.now)) {
            parts.add("No hi ha avisos ciutadans recents.")
        } else {
            val age = report.ageMinutes(input.now)
            val reportAge = when {
                age == 0L -> "ara mateix"
                age == 1L -> "fa 1 minut"
                else -> "fa $age minuts"
            }
            val reportStatus = when (report.status) {
                FountainStatus.WORKING -> "operativa"
                FountainStatus.NOT_WORKING -> "no operativa"
                FountainStatus.UNKNOWN -> "en estat desconegut"
            }
            parts.add("L'últim avís ciutadà, $reportAge, indica que la font està $reportStatus.")
        }

        resolveValidLight(input)?.lightBand?.let { band ->
            val lightDescription = when (band) {
                LightBand.LOW -> "poca llum, zona ombrejada o tènue"
                LightBand.MODERATE -> "llum moderada amb visibilitat còmoda"
                LightBand.BRIGHT -> "llum brillant amb alt enlluernament"
            }
            parts.add("Llum registrada: $lightDescription.")
        }

        return parts.joinToString(" ")
    }
}
