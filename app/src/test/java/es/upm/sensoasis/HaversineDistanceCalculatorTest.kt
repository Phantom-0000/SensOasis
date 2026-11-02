package es.upm.sensoasis

import es.upm.sensoasis.domain.model.Coordinates
import es.upm.sensoasis.location.HaversineDistanceCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HaversineDistanceCalculatorTest {

    private val calculator = HaversineDistanceCalculator()

    @Test
    fun `distance between identical coordinates is zero`() {
        val sol = Coordinates(latitude = 40.4169, longitude = -3.7035)
        assertEquals(0, calculator.distanceMeters(sol, sol))
    }

    @Test
    fun `distance between Puerta del Sol and Plaza de Callao is approximately 420m`() {
        val puertaDelSol = Coordinates(latitude = 40.4169, longitude = -3.7035)
        val plazaDelCallao = Coordinates(latitude = 40.4201, longitude = -3.7058)
        val distance = calculator.distanceMeters(puertaDelSol, plazaDelCallao)
        assertTrue("Expected distance in [380, 440], got $distance", distance in 380..440)
    }

    @Test
    fun `distance between Puerta del Sol and Retiro Entrance is approximately 1250m`() {
        val puertaDelSol = Coordinates(latitude = 40.4169, longitude = -3.7035)
        val retiro = Coordinates(latitude = 40.4153, longitude = -3.6889)
        val distance = calculator.distanceMeters(puertaDelSol, retiro)
        assertTrue("Expected distance in [1200, 1300], got $distance", distance in 1200..1300)
    }
}
