package es.upm.sensoasis.domain.model

/**
 * Geographic coordinates in WGS84 decimal degrees.
 */
data class Coordinates(
    val latitude: Double,
    val longitude: Double
)

/**
 * Operational status of a public drinking fountain.
 */
enum class FountainStatus {
    WORKING,
    NOT_WORKING,
    UNKNOWN
}

/**
 * Public drinking fountain domain model.
 * Note: [id] must not contain '/' so MQTT topics remain well-formed.
 */
data class Fountain(
    val id: String,
    val name: String,
    val coordinates: Coordinates,
    val officialStatus: FountainStatus,
    val address: String = ""
) {
    init {
        require('/' !in id) { "Fountain id must not contain '/': $id" }
    }
}
