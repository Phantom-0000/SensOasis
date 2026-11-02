package es.upm.sensoasis

import es.upm.sensoasis.domain.model.formatLux
import org.junit.Assert.assertEquals
import org.junit.Test

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
}
