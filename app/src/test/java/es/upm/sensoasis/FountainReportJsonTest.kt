package es.upm.sensoasis

import es.upm.sensoasis.data.mqtt.FountainReportJson
import es.upm.sensoasis.domain.model.FountainStatus
import es.upm.sensoasis.domain.model.LightQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class FountainReportJsonTest {
    @Test fun decodesNullLightStatusReport() {
        val report = FountainReportJson.decode(fixture("report_null_light.json"))
        assertEquals(FountainStatus.NOT_WORKING, report.status)
        assertNull(report.lightObservation)
    }

    @Test fun decodesOccludedLightAsAValidStatusReport() {
        val report = FountainReportJson.decode(fixture("report_occluded.json"))
        assertEquals(LightQuality.OCCLUDED, report.lightObservation?.quality)
        assertNull(report.lightObservation?.ambientLux)
    }

    @Test fun validReportRoundTripsWithItsLightObservation() {
        val report = FountainReportJson.decode(fixture("report_valid.json"))
        assertEquals(report, FountainReportJson.decode(FountainReportJson.encode(report)))
    }

    @Test fun expiredReportRemainsParsableForExpiryFiltering() {
        val report = FountainReportJson.decode(fixture("report_expired.json"))
        assertEquals("MADRID_FT_042", report.fountainId)
        assertEquals(true, report.isExpired(java.time.Instant.parse("2026-10-21T10:21:00Z")))
    }

    private fun fixture(name: String): String = File("src/test/resources/mqtt/$name").readText()
}
