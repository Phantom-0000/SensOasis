package es.upm.sensoasis.domain.model

/**
 * Current proximity sensor state.
 * [UNKNOWN] means no stable reading has been received yet and must NOT be treated as [NEAR].
 */
enum class ProximityState {
    NEAR,
    FAR,
    UNKNOWN
}

/**
 * Hardware availability of a built-in device sensor.
 */
enum class SensorAvailability {
    AVAILABLE,
    UNAVAILABLE
}

/**
 * Result of a single short-window ambient-light capture.
 */
sealed interface LightCaptureResult {
    data class Valid(val observation: LightObservation) : LightCaptureResult
    data object Occluded : LightCaptureResult
    data class Unavailable(val reason: String) : LightCaptureResult
    data class Error(val message: String) : LightCaptureResult
}

/**
 * Generic data result wrapper for repository and location calls.
 */
sealed interface DataResult<out T> {
    data class Success<T>(val value: T) : DataResult<T>
    data class Unavailable(val reason: String) : DataResult<Nothing>
    data class Error(val message: String) : DataResult<Nothing>
}

/**
 * Result of publishing an MQTT message to the broker.
 */
sealed interface PublishResult {
    data object Published : PublishResult
    data object Offline : PublishResult
    data class Error(val message: String) : PublishResult
}

/**
 * Result of submitting a fountain status report through [es.upm.sensoasis.domain.contract.ReportUseCase].
 */
sealed interface SubmitReportResult {
    data object Success : SubmitReportResult
    data object Offline : SubmitReportResult
    data class Error(val message: String) : SubmitReportResult
}

/**
 * MQTT broker connection lifecycle state.
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}
