package es.upm.sensoasis

import es.upm.sensoasis.domain.model.LightBand
import es.upm.sensoasis.domain.model.LightQuality
import es.upm.sensoasis.domain.model.formatLightBand
import es.upm.sensoasis.domain.model.formatLightQuality
import es.upm.sensoasis.domain.model.formatLux
import es.upm.sensoasis.domain.model.formatObservationAge
import es.upm.sensoasis.domain.model.formatUnavailableReason
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

class LightFormattingTest {

    @Test
    fun `formatLux rounds for display only and handles null or invalid values`() {
        assertEquals("3200 lux", formatLux(3200f))
        assertEquals("3200 lux", formatLux(3200.4f))
        assertEquals("3201 lux", formatLux(3200.5f))
        assertEquals("3 lux", formatLux(3.0517578f))
        assertEquals("0 lux", formatLux(0f))
        assertEquals("12000 lux", formatLux(12_000f))
        assertEquals("-- lux", formatLux(null))
        assertEquals("-- lux", formatLux(Float.NaN))
        assertEquals("-- lux", formatLux(Float.POSITIVE_INFINITY))
        assertEquals("0 lux", formatLux(-5f))
    }

    @Test
    fun `formatLightBand returns senior-friendly descriptions matching addendum`() {
        assertEquals("Bright sunlight (high glare / exposed)", formatLightBand(LightBand.BRIGHT))
        assertEquals("Moderate light (comfortable visibility)", formatLightBand(LightBand.MODERATE))
        assertEquals("Low light (shaded or dim area)", formatLightBand(LightBand.LOW))
        assertEquals("Light level unavailable", formatLightBand(null))
    }

    @Test
    fun `formatLightQuality returns understandable labels`() {
        assertEquals("Valid reading", formatLightQuality(LightQuality.VALID))
        assertEquals("Sensor covered", formatLightQuality(LightQuality.OCCLUDED))
        assertEquals("Sensor unavailable", formatLightQuality(LightQuality.UNAVAILABLE))
    }

    @Test
    fun `formatObservationAge formats relative time intervals correctly`() {
        val now = Instant.parse("2026-11-09T20:00:00Z")
        assertEquals("Just now", formatObservationAge(now, now))
        assertEquals("Just now", formatObservationAge(now.minusSeconds(30), now))
        assertEquals("1 minute ago", formatObservationAge(now.minus(Duration.ofMinutes(1)), now))
        assertEquals("15 minutes ago", formatObservationAge(now.minus(Duration.ofMinutes(15)), now))
        assertEquals("1 hour ago", formatObservationAge(now.minus(Duration.ofHours(1)), now))
        assertEquals("3 hours ago", formatObservationAge(now.minus(Duration.ofHours(3)), now))
        assertEquals("1 day ago", formatObservationAge(now.minus(Duration.ofDays(1)), now))
        assertEquals("2 days ago", formatObservationAge(now.minus(Duration.ofDays(2)), now))
    }

    @Test
    fun `formatUnavailableReason maps raw sensor codes to plain language`() {
        assertEquals("Light sensor not supported on this device", formatUnavailableReason("TYPE_LIGHT_MISSING"))
        assertEquals("Proximity sensor check unconfirmed", formatUnavailableReason("PROXIMITY_UNCONFIRMED"))
        assertEquals("Failed to start light sensor", formatUnavailableReason("SENSOR_REGISTRATION_FAILED"))
        assertEquals("Measurement timed out", formatUnavailableReason("LIGHT_TIMEOUT"))
        assertEquals("Unknown hardware issue", formatUnavailableReason("UNKNOWN_HARDWARE_ISSUE"))
    }
}
