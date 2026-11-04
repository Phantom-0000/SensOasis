package es.upm.sensoasis.speech

import es.upm.sensoasis.domain.contract.SpeechSummaryUseCase
import es.upm.sensoasis.domain.model.FountainStatus
import es.upm.sensoasis.domain.model.LightQuality
import es.upm.sensoasis.domain.model.SpeechSummaryInput
import es.upm.sensoasis.domain.model.SpeechText

/**
 * Builds a concise and accessible English spoken summary
 * using the latest fountain information.
 */
class RealSpeechSummaryUseCase : SpeechSummaryUseCase {

    override fun buildSummary(input: SpeechSummaryInput): SpeechText {
        val parts = mutableListOf<String>()

        // Fountain identification
        parts.add("Fountain: ${input.fountain.name}.")

        // Distance
        val distance = input.distanceMeters
        parts.add(
            if (distance != null && distance >= 0) {
                "Distance: $distance meters."
            } else {
                "Distance is currently unknown."
            }
        )

        // Official fountain status
        parts.add(
            when (input.officialStatus) {
                FountainStatus.WORKING ->
                    "Official status: working."

                FountainStatus.NOT_WORKING ->
                    "Official status: not working."

                FountainStatus.UNKNOWN ->
                    "Official status: unknown."
            }
        )

        // Community report
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

            parts.add(
                "The latest community report, $reportAge, says the fountain is $reportStatus."
            )

            // Optional light information from the community report
            report.lightObservation?.let { observation ->
                when (observation.quality) {
                    LightQuality.VALID -> {
                        observation.lightBand?.let { band ->
                            val lightDescription = when (band) {
                                es.upm.sensoasis.domain.model.LightBand.LOW ->
                                    "low ambient light"

                                es.upm.sensoasis.domain.model.LightBand.MODERATE ->
                                    "moderate ambient light"

                                es.upm.sensoasis.domain.model.LightBand.BRIGHT ->
                                    "bright ambient light"
                            }

                            parts.add("The reported lighting is $lightDescription.")
                        }
                    }

                    LightQuality.OCCLUDED ->
                        parts.add("The light sensor was obstructed.")

                    LightQuality.UNAVAILABLE ->
                        parts.add("Light information is unavailable.")
                }
            }
        }

        // Optional air quality
        input.airQuality?.let { airQuality ->
            parts.add("Air quality is ${airQuality.category.lowercase()}.")
        }

        return SpeechText(parts.joinToString(" "))
    }
}
