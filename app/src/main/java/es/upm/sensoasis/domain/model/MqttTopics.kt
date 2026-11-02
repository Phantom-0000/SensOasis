package es.upm.sensoasis.domain.model

/**
 * Transport protocol used to connect to the Mosquitto MQTT broker.
 */
enum class MqttTransport {
    TCP,
    WS
}

/**
 * Broker connection and topic namespace configuration injected into [es.upm.sensoasis.domain.contract.MqttRepository].
 */
data class MqttConfig(
    val host: String,
    val port: Int = 1883,
    val wsPort: Int = 8080,
    val transport: MqttTransport = MqttTransport.TCP,
    val topicRoot: String = MqttTopics.DEFAULT_TOPIC_ROOT,
    val clientId: String
)

/**
 * MQTT topic conventions for publishing and subscribing to fountain reports.
 * Publish & single-fountain subscribe: `{topicRoot}/fountains/{fountainId}/reports`
 * All-fountains wildcard subscribe: `{topicRoot}/fountains/+/reports`
 */
object MqttTopics {
    const val DEFAULT_TOPIC_ROOT: String = "sensoasis"

    fun validateFountainId(fountainId: String) {
        require(fountainId.isNotBlank() && '/' !in fountainId) {
            "fountainId must be non-blank and must not contain '/': '$fountainId'"
        }
    }

    fun reportTopic(
        fountainId: String,
        topicRoot: String = DEFAULT_TOPIC_ROOT
    ): String {
        validateFountainId(fountainId)
        val normalizedRoot = topicRoot.trim('/')
        return "$normalizedRoot/fountains/$fountainId/reports"
    }

    fun allReportsTopic(
        topicRoot: String = DEFAULT_TOPIC_ROOT
    ): String {
        val normalizedRoot = topicRoot.trim('/')
        return "$normalizedRoot/fountains/+/reports"
    }
}
