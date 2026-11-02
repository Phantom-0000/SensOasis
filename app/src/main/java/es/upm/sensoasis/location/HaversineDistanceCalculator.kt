package es.upm.sensoasis.location

import es.upm.sensoasis.domain.contract.DistanceCalculator
import es.upm.sensoasis.domain.model.Coordinates
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure-Kotlin Haversine implementation of [DistanceCalculator].
 * Zero Android framework dependencies so it runs directly in JVM unit tests.
 */
class HaversineDistanceCalculator : DistanceCalculator {

    override fun distanceMeters(from: Coordinates, to: Coordinates): Int {
        val dLat = Math.toRadians(to.latitude - from.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)

        val sinLat = sin(dLat / 2.0)
        val sinLon = sin(dLon / 2.0)
        val a = sinLat * sinLat + cos(lat1) * cos(lat2) * sinLon * sinLon
        val c = 2.0 * atan2(sqrt(a), sqrt((1.0 - a).coerceAtLeast(0.0)))
        return (EARTH_RADIUS_METERS * c).roundToInt()
    }

    companion object {
        private const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}
