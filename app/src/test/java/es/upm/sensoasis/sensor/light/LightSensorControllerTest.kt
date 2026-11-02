package es.upm.sensoasis.sensor.light

import es.upm.sensoasis.domain.model.LightBand
import es.upm.sensoasis.domain.model.LightCaptureResult
import es.upm.sensoasis.domain.model.LightQuality
import es.upm.sensoasis.domain.model.ProximityState
import es.upm.sensoasis.domain.model.SensorAvailability
import es.upm.sensoasis.fake.FakeProximityStateProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class LightSensorControllerTest {

    private val fixedInstant: Instant = Instant.parse("2026-06-15T15:30:00Z")

    @Test
    fun `computeMedianLux handles odd, even, single, and sanitized sample lists`() {
        // Odd count
        assertEquals(
            350f,
            LightObservationCalculator.computeMedianLux(listOf(120f, 800f, 350f)) ?: -1f,
            0.001f
        )

        // Even count
        assertEquals(
            300f,
            LightObservationCalculator.computeMedianLux(listOf(100f, 200f, 400f, 900f)) ?: -1f,
            0.001f
        )

        // Single reading (stationary ON_CHANGE sensor)
        assertEquals(
            420f,
            LightObservationCalculator.computeMedianLux(listOf(420f)) ?: -1f,
            0.001f
        )

        // Invalid readings filtered before computing median
        assertEquals(
            600f,
            LightObservationCalculator.computeMedianLux(
                listOf(-10f, Float.NaN, 600f, Float.POSITIVE_INFINITY)
            ) ?: -1f,
            0.001f
        )

        // Empty or all-invalid returns null
        assertNull(LightObservationCalculator.computeMedianLux(emptyList()))
        assertNull(LightObservationCalculator.computeMedianLux(listOf(-1f, Float.NaN)))
    }

    @Test
    fun `classifyBand enforces exact LOW, MODERATE, and BRIGHT thresholds`() {
        assertEquals(LightBand.LOW, LightObservationCalculator.classifyBand(0f))
        assertEquals(LightBand.LOW, LightObservationCalculator.classifyBand(499.9f))
        assertEquals(LightBand.MODERATE, LightObservationCalculator.classifyBand(500.0f))
        assertEquals(LightBand.MODERATE, LightObservationCalculator.classifyBand(2_500.0f))
        assertEquals(LightBand.MODERATE, LightObservationCalculator.classifyBand(5_000.0f))
        assertEquals(LightBand.BRIGHT, LightObservationCalculator.classifyBand(5_000.1f))
        assertEquals(LightBand.BRIGHT, LightObservationCalculator.classifyBand(12_000.0f))
    }

    @Test
    fun `captureOnce with FAR proximity returns median lux and unregisters listener`() = runTest {
        val proximity = FakeProximityStateProvider(initialState = ProximityState.FAR)
        val hardware = TestLightSensorHardware(
            initialSamples = listOf(200f, 6_000f, 1_400f)
        )
        val controller = RealLightSensorController(
            hardware = hardware,
            proximityStateProvider = proximity,
            nowProvider = { fixedInstant }
        )

        val deferred = async { controller.captureOnce(timeoutMs = 1_500L) }
        advanceTimeBy(1_500L)
        runCurrent()

        val result = deferred.await()
        assertTrue(result is LightCaptureResult.Valid)
        val valid = result as LightCaptureResult.Valid
        assertEquals(1_400f, valid.observation.ambientLux ?: -1f, 0.001f)
        assertEquals(LightBand.MODERATE, valid.observation.lightBand)
        assertEquals(LightQuality.VALID, valid.observation.quality)
        assertEquals(fixedInstant, valid.observation.observedAt)
        assertEquals(1, hardware.registerCount)
        assertEquals(1, hardware.unregisterCount)
    }

    @Test
    fun `captureOnce accepts a single registration-time reading but never reuses readings from a previous capture`() = runTest {
        val proximity = FakeProximityStateProvider(initialState = ProximityState.FAR)
        val hardware = TestLightSensorHardware(initialSamples = listOf(820f))
        val controller = RealLightSensorController(
            hardware = hardware,
            proximityStateProvider = proximity,
            nowProvider = { fixedInstant }
        )

        // Stationary ON_CHANGE sensor: only the initial event delivered on registration
        val firstResult = controller.captureOnce(timeoutMs = 100L)
        assertTrue(firstResult is LightCaptureResult.Valid)
        assertEquals(820f, (firstResult as LightCaptureResult.Valid).observation.ambientLux ?: -1f, 0.001f)
        assertEquals(LightBand.MODERATE, firstResult.observation.lightBand)

        // A later capture that receives no event must NOT report the stale 820 lux
        hardware.initialSamples = emptyList()
        val secondResult = controller.captureOnce(timeoutMs = 100L)
        assertEquals(
            LightCaptureResult.Unavailable(LightObservationCalculator.REASON_LIGHT_TIMEOUT),
            secondResult
        )
    }

    @Test
    fun `captureOnce short-circuits immediately with Occluded when proximity is NEAR before capture`() = runTest {
        val proximity = FakeProximityStateProvider(initialState = ProximityState.NEAR)
        val hardware = TestLightSensorHardware(initialSamples = listOf(500f))
        val controller = RealLightSensorController(
            hardware = hardware,
            proximityStateProvider = proximity,
            nowProvider = { fixedInstant }
        )

        val result = controller.captureOnce(timeoutMs = 1_500L)
        assertEquals(LightCaptureResult.Occluded, result)
        assertEquals("Should not register light sensor when already occluded", 0, hardware.registerCount)
    }

    @Test
    fun `captureOnce short-circuits mid-window with Occluded as soon as proximity flips to NEAR`() = runTest {
        val proximity = FakeProximityStateProvider(initialState = ProximityState.FAR)
        val hardware = TestLightSensorHardware(initialSamples = listOf(1_200f, 1_300f))
        val controller = RealLightSensorController(
            hardware = hardware,
            proximityStateProvider = proximity,
            nowProvider = { fixedInstant }
        )

        val deferred = async { controller.captureOnce(timeoutMs = 1_500L) }
        runCurrent()

        // User covers top of phone at t = 300ms
        advanceTimeBy(300L)
        proximity.setSimulatedState(ProximityState.NEAR)
        runCurrent()

        val result = deferred.await()
        assertEquals(LightCaptureResult.Occluded, result)
        assertEquals("Must short-circuit at 300ms without waiting full 1500ms", 300L, currentTime)
        assertEquals(1, hardware.unregisterCount)
    }

    @Test
    fun `captureOnce succeeds when proximity starts UNKNOWN and resolves to FAR before window ends`() = runTest {
        val proximity = FakeProximityStateProvider(initialState = ProximityState.UNKNOWN)
        val hardware = TestLightSensorHardware(initialSamples = listOf(420f))
        val controller = RealLightSensorController(
            hardware = hardware,
            proximityStateProvider = proximity,
            nowProvider = { fixedInstant }
        )

        val deferred = async { controller.captureOnce(timeoutMs = 1_500L) }
        runCurrent()

        // Proximity ON_CHANGE sensor delivers initial FAR state at t = 80ms
        advanceTimeBy(80L)
        proximity.setSimulatedState(ProximityState.FAR)
        advanceTimeBy(1_420L)
        runCurrent()

        val result = deferred.await()
        assertTrue(result is LightCaptureResult.Valid)
        assertEquals(LightBand.LOW, (result as LightCaptureResult.Valid).observation.lightBand)
    }

    @Test
    fun `captureOnce returns PROXIMITY_UNCONFIRMED when proximity stays UNKNOWN for entire window`() = runTest {
        val proximity = FakeProximityStateProvider(initialState = ProximityState.UNKNOWN)
        val hardware = TestLightSensorHardware(initialSamples = listOf(420f))
        val controller = RealLightSensorController(
            hardware = hardware,
            proximityStateProvider = proximity,
            nowProvider = { fixedInstant }
        )

        val result = controller.captureOnce(timeoutMs = 500L)
        assertEquals(
            LightCaptureResult.Unavailable(LightObservationCalculator.REASON_PROXIMITY_UNCONFIRMED),
            result
        )
        assertEquals(1, hardware.unregisterCount)
    }

    @Test
    fun `captureOnce handles missing hardware, registration failure, and timeout`() = runTest {
        val proximity = FakeProximityStateProvider(initialState = ProximityState.FAR)

        // 1. Missing TYPE_LIGHT hardware
        val missingHardware = TestLightSensorHardware(isAvailable = false)
        val missingController = RealLightSensorController(missingHardware, proximity) { fixedInstant }
        assertEquals(SensorAvailability.UNAVAILABLE, missingController.availability)
        assertEquals(
            LightCaptureResult.Unavailable(LightObservationCalculator.REASON_TYPE_LIGHT_MISSING),
            missingController.captureOnce(timeoutMs = 500L)
        )

        // 2. SensorManager.registerListener returns false
        val failedRegHardware = TestLightSensorHardware(registrationSucceeds = false)
        val failedRegController = RealLightSensorController(failedRegHardware, proximity) { fixedInstant }
        assertEquals(
            LightCaptureResult.Unavailable(LightObservationCalculator.REASON_SENSOR_REGISTRATION_FAILED),
            failedRegController.captureOnce(timeoutMs = 500L)
        )

        // 3. Zero samples emitted and no prior cached reading
        val silentHardware = TestLightSensorHardware(initialSamples = emptyList())
        val silentController = RealLightSensorController(silentHardware, proximity) { fixedInstant }
        assertEquals(
            LightCaptureResult.Unavailable(LightObservationCalculator.REASON_LIGHT_TIMEOUT),
            silentController.captureOnce(timeoutMs = 500L)
        )

        // 4. Proximity sensor hardware itself is unavailable
        val unavailableProximity = FakeProximityStateProvider(
            availability = SensorAvailability.UNAVAILABLE,
            initialState = ProximityState.UNKNOWN
        )
        val proxMissingHardware = TestLightSensorHardware(initialSamples = listOf(400f))
        val proxMissingController = RealLightSensorController(proxMissingHardware, unavailableProximity) { fixedInstant }
        assertEquals(
            LightCaptureResult.Unavailable(LightObservationCalculator.REASON_PROXIMITY_UNCONFIRMED),
            proxMissingController.captureOnce(timeoutMs = 500L)
        )
        assertEquals("Should not register light sensor when proximity hardware is unavailable", 0, proxMissingHardware.registerCount)
    }

    @Test
    fun `cancelling captureOnce mid-window guarantees listener unregistration`() = runTest {
        val proximity = FakeProximityStateProvider(initialState = ProximityState.FAR)
        val hardware = TestLightSensorHardware(initialSamples = listOf(500f))
        val controller = RealLightSensorController(
            hardware = hardware,
            proximityStateProvider = proximity,
            nowProvider = { fixedInstant }
        )

        val job = launch {
            controller.captureOnce(timeoutMs = 1_500L)
        }
        runCurrent()
        assertEquals(1, hardware.registerCount)
        assertEquals(0, hardware.unregisterCount)

        // User navigates away from ReportScreen at t = 400ms
        delay(400L)
        job.cancel()
        runCurrent()

        assertEquals("Listener must be unregistered when coroutine is cancelled", 1, hardware.unregisterCount)
    }

    private class TestLightSensorHardware(
        override val isAvailable: Boolean = true,
        val registrationSucceeds: Boolean = true,
        var initialSamples: List<Float> = emptyList()
    ) : LightSensorHardware {

        var registerCount: Int = 0
            private set

        var unregisterCount: Int = 0
            private set

        override fun register(onLuxSample: (Float) -> Unit): LightSensorSession? {
            registerCount++
            if (!isAvailable || !registrationSucceeds) return null
            initialSamples.forEach(onLuxSample)
            return LightSensorSession {
                unregisterCount++
            }
        }
    }
}
