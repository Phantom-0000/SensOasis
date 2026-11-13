package es.upm.sensoasis.data.opendata

import es.upm.sensoasis.domain.model.Coordinates
import es.upm.sensoasis.domain.model.Fountain
import es.upm.sensoasis.domain.model.FountainStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale
import kotlin.math.pow

/** Parses the current Madrid open-data schema and the app's simplified bundled fallback. */
object MadridFountainsParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(source: String): List<Fountain> {
        val root = json.parseToJsonElement(source)
        val elements = when (root) {
            is JsonArray -> root.jsonArray
            else -> root.jsonObject["@graph"]?.jsonArray
                ?: root.jsonObject["records"]?.jsonArray
                ?: error("Fountain JSON must be an array or contain @graph/records")
        }
        return elements.mapNotNull { element -> parseOne(element.jsonObject) }.distinctBy { it.id }
    }

    private fun parseOne(fields: Map<String, JsonElement>): Fountain? {
        fun value(vararg keys: String): String? {
            val entry = fields.entries.firstOrNull { candidate -> keys.any { candidate.key.equals(it, ignoreCase = true) } }
                ?.value ?: return null
            if (entry == JsonNull) return null
            return runCatching { entry.jsonPrimitive.content }.getOrNull()?.takeIf { it.isNotBlank() }
        }

        val latitude = value("LATITUD", "latitude", "lat")?.toDoubleOrNull()
        val longitude = value("LONGITUD", "longitude", "lon", "lng")?.toDoubleOrNull()
        val coordinates = if (latitude != null && longitude != null && latitude in -90.0..90.0 && longitude in -180.0..180.0) {
            Coordinates(latitude, longitude)
        } else {
            val x = value("COORD_GIS_X", "x")?.toDoubleOrNull() ?: return null
            val y = value("COORD_GIS_Y", "y")?.toDoubleOrNull() ?: return null
            runCatching { utm30Etrs89ToWgs84(x, y) }.getOrNull() ?: return null
        }

        val id = value("ID", "id", "CODIGO_INTERNO", "code") ?: return null
        val internalCode = value("CODIGO_INTERNO", "code")
        val road = listOfNotNull(
            value("TIPO_VIA", "road_type"),
            value("NOM_VIA", "NOMBRE_VIA", "street", "road"),
            value("NUM_VIA", "NUMERO", "number")
        ).joinToString(" ")
        val extraAddress = value("DIRECCION_AUX", "address", "DIRECCION")
        val address = listOf(road, extraAddress.orEmpty()).filter { it.isNotBlank() }.distinct().joinToString(", ")
        // Madrid's fountain feed has no canonical display-name column in every release.
        // Prefer its human-readable location fields over opaque dataset/internal IDs.
        val neighborhood = value("BARRIO", "neighborhood")
        val descriptiveLocation = listOfNotNull(extraAddress, road, neighborhood)
            .map(::formatLocationName)
            .distinctBy { it.lowercase(Locale.ROOT) }
            .joinToString(" · ")
        val name = value("NOMBRE", "name", "title", "DENOMINACION")
            ?: descriptiveLocation.takeIf { it.isNotBlank() }?.let { "Fuente · $it" }
            ?: internalCode?.let { "Fuente $it" }
            ?: "Fuente $id"
        val status = when (value("ESTADO", "officialStatus", "status")?.uppercase()) {
            "OPERATIVO", "WORKING", "EN SERVICIO" -> FountainStatus.WORKING
            "NO OPERATIVO", "CERRADA TEMPORALMENTE", "NOT_WORKING", "CLOSED" -> FountainStatus.NOT_WORKING
            else -> FountainStatus.UNKNOWN
        }
        return Fountain(id, name, coordinates, status, address = address)
    }

    private fun formatLocationName(value: String): String {
        val locale = Locale.forLanguageTag("es-ES")
        val particles = setOf("de", "del", "la", "las", "los", "y", "en", "el")
        return value.trim().lowercase(locale).split(Regex("\\s+")).mapIndexed { index, word ->
            if (index == 0 || word !in particles) word.replaceFirstChar { it.titlecase(locale) } else word
        }.joinToString(" ")
    }

    /** Madrid reports ETRS89 UTM zone 30 coordinates in metres. */
    private fun utm30Etrs89ToWgs84(easting: Double, northing: Double): Coordinates {
        require(easting in 100_000.0..900_000.0 && northing in 0.0..10_000_000.0)
        val a = 6_378_137.0
        val eccentricitySquared = 0.0066943799901413165
        val k0 = 0.9996
        val x = easting - 500_000.0
        val m = northing / k0
        val mu = m / (a * (1.0 - eccentricitySquared / 4.0 - 3.0 * eccentricitySquared * eccentricitySquared / 64.0 - 5.0 * eccentricitySquared.pow(3) / 256.0))
        val e1 = (1.0 - kotlin.math.sqrt(1.0 - eccentricitySquared)) / (1.0 + kotlin.math.sqrt(1.0 - eccentricitySquared))
        val j1 = 3.0 * e1 / 2.0 - 27.0 * e1.pow(3) / 32.0
        val j2 = 21.0 * e1 * e1 / 16.0 - 55.0 * e1.pow(4) / 32.0
        val j3 = 151.0 * e1.pow(3) / 96.0
        val j4 = 1097.0 * e1.pow(4) / 512.0
        val fp = mu + j1 * kotlin.math.sin(2.0 * mu) + j2 * kotlin.math.sin(4.0 * mu) + j3 * kotlin.math.sin(6.0 * mu) + j4 * kotlin.math.sin(8.0 * mu)
        val ePrimeSquared = eccentricitySquared / (1.0 - eccentricitySquared)
        val c1 = ePrimeSquared * kotlin.math.cos(fp).pow(2)
        val t1 = kotlin.math.tan(fp).pow(2)
        val n1 = a / kotlin.math.sqrt(1.0 - eccentricitySquared * kotlin.math.sin(fp).pow(2))
        val r1 = a * (1.0 - eccentricitySquared) / (1.0 - eccentricitySquared * kotlin.math.sin(fp).pow(2)).pow(1.5)
        val d = x / (n1 * k0)
        val lat = fp - (n1 * kotlin.math.tan(fp) / r1) * (
            d * d / 2.0 - (5.0 + 3.0 * t1 + 10.0 * c1 - 4.0 * c1 * c1 - 9.0 * ePrimeSquared) * d.pow(4) / 24.0 +
                (61.0 + 90.0 * t1 + 298.0 * c1 + 45.0 * t1 * t1 - 252.0 * ePrimeSquared - 3.0 * c1 * c1) * d.pow(6) / 720.0
        )
        val lon = Math.toRadians(-3.0) + (
            d - (1.0 + 2.0 * t1 + c1) * d.pow(3) / 6.0 +
                (5.0 - 2.0 * c1 + 28.0 * t1 - 3.0 * c1 * c1 + 8.0 * ePrimeSquared + 24.0 * t1 * t1) * d.pow(5) / 120.0
            ) / kotlin.math.cos(fp)
        return Coordinates(Math.toDegrees(lat), Math.toDegrees(lon))
    }

    private fun Double.pow(exponent: Int): Double = this.pow(exponent.toDouble())
}
