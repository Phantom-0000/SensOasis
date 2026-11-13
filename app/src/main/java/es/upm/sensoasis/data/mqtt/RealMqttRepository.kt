package es.upm.sensoasis.data.mqtt

import android.content.Context
import es.upm.sensoasis.domain.contract.MqttRepository
import es.upm.sensoasis.domain.model.ConnectionState
import es.upm.sensoasis.domain.model.FountainReport
import es.upm.sensoasis.domain.model.MqttConfig
import es.upm.sensoasis.domain.model.MqttTopics
import es.upm.sensoasis.domain.model.MqttTransport
import es.upm.sensoasis.domain.model.PublishResult
import es.upm.sensoasis.domain.model.ReportReceiveState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.meshtastic.mqtt.ConnectionState as ClientState
import org.meshtastic.mqtt.MqttClient
import org.meshtastic.mqtt.MqttEndpoint
import org.meshtastic.mqtt.PublishProperties
import org.meshtastic.mqtt.QoS
import org.meshtastic.mqtt.transport.tcp.TcpTransportFactory
import org.meshtastic.mqtt.transport.ws.WebSocketTransportFactory
import java.time.Instant
import java.time.Duration

/** MQTTastic-backed publisher/subscriber. Incoming retained and live reports share one parsed flow. */
class RealMqttRepository(
    context: Context,
    private val config: MqttConfig,
    private val nowProvider: () -> Instant = { Instant.now() },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : MqttRepository {
    private val client = MqttClient(config.clientId, scope) {
        autoReconnect = true
        defaultQos = QoS.AT_LEAST_ONCE
        defaultRetain = true
        transportFactory = when (config.transport) {
            MqttTransport.TCP -> TcpTransportFactory()
            MqttTransport.WS -> WebSocketTransportFactory()
        }
    }
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    private val _receiveState = MutableStateFlow(ReportReceiveState.WAITING)
    override val receiveState: StateFlow<ReportReceiveState> = _receiveState.asStateFlow()
    private val reports = MutableSharedFlow<FountainReport>(replay = 64, extraBufferCapacity = 64)
    private var messageCollectorStarted = false
    private val reportCache = context.applicationContext.getSharedPreferences(REPORT_CACHE, Context.MODE_PRIVATE)

    init {
        restoreUnexpiredReports()
    }

    override suspend fun connect() {
        _connectionState.value = ConnectionState.CONNECTING
        observeClientState()
        try {
            when (config.transport) {
                MqttTransport.TCP -> client.connect(MqttEndpoint.Tcp(config.host, config.port))
                MqttTransport.WS -> client.connect(MqttEndpoint.WebSocket("ws://${config.host}:${config.wsPort}/mqtt"))
            }
            _connectionState.value = ConnectionState.CONNECTED
            subscribeCommunityReports()
        } catch (_: Exception) {
            _connectionState.value = ConnectionState.ERROR
        }
    }

    private fun observeClientState() {
        scope.launch {
            client.connectionState.collect { state ->
                _connectionState.value = when (state) {
                    is ClientState.Connected -> ConnectionState.CONNECTED
                    is ClientState.Connecting, is ClientState.Reconnecting -> ConnectionState.CONNECTING
                    is ClientState.Disconnected -> if (state.reason == null) ConnectionState.DISCONNECTED else ConnectionState.ERROR
                }
            }
        }
    }

    override suspend fun disconnect() {
        try { client.disconnect() } finally { _connectionState.value = ConnectionState.DISCONNECTED }
    }

    override suspend fun publishReport(report: FountainReport): PublishResult {
        val now = nowProvider()
        if (_connectionState.value != ConnectionState.CONNECTED) return PublishResult.Offline
        if (report.isExpired(now)) return PublishResult.Error("Report is already expired")
        return try {
            val ttlSeconds = Duration.between(now, report.expiresAt).seconds.coerceAtLeast(0L)
            client.publish(
                topic = MqttTopics.reportTopic(report.fountainId, config.topicRoot),
                payload = FountainReportJson.encode(report),
                qos = QoS.AT_LEAST_ONCE,
                retain = true,
                properties = PublishProperties(
                    messageExpiryInterval = ttlSeconds,
                    contentType = "application/json",
                    payloadFormatIndicator = true
                )
            )
            PublishResult.Published
        } catch (e: Exception) {
            PublishResult.Error(e.message ?: "MQTT publish failed")
        }
    }

    private suspend fun subscribeCommunityReports() {
        if (_connectionState.value != ConnectionState.CONNECTED) return
        if (!messageCollectorStarted) {
            messageCollectorStarted = true
            scope.launch {
                client.messages.collect { message ->
                    try {
                        val report = FountainReportJson.decode(message.payloadAsString())
                        val expectedTopic = MqttTopics.reportTopic(report.fountainId, config.topicRoot)
                        if (message.topic != expectedTopic) {
                            _receiveState.value = ReportReceiveState.INVALID_PAYLOAD
                        } else if (report.isExpired(nowProvider())) {
                            val key = cacheKey(report.fountainId)
                            val cached = reportCache.getString(key, null)
                                ?.let { runCatching { FountainReportJson.decode(it) }.getOrNull() }
                            if (cached == null || !report.observedAt.isBefore(cached.observedAt)) {
                                reportCache.edit().remove(key).apply()
                            }
                            if (message.retain) {
                                // MQTT retained messages are deleted by publishing an empty retained payload.
                                client.publish(message.topic, "", qos = QoS.AT_LEAST_ONCE, retain = true)
                            }
                            _receiveState.value = ReportReceiveState.EXPIRED
                        } else {
                            persistAndEmitIfLatest(report)
                            _receiveState.value = ReportReceiveState.RECEIVED
                        }
                    } catch (_: Exception) {
                        _receiveState.value = ReportReceiveState.INVALID_PAYLOAD
                    }
                }
            }
        }
        client.subscribe(
            MqttTopics.allReportsTopic(config.topicRoot),
            qos = QoS.AT_LEAST_ONCE,
            retainAsPublished = true
        )
    }

    override fun observeReports(fountainId: String): Flow<FountainReport> = reports
        .filter { it.fountainId == fountainId && !it.isExpired(nowProvider()) }

    override fun observeAllReports(): Flow<FountainReport> = reports
        .filter { !it.isExpired(nowProvider()) }

    private fun restoreUnexpiredReports() {
        val editor = reportCache.edit()
        reportCache.all.forEach { (key, value) ->
            if (!key.startsWith(REPORT_KEY_PREFIX) || value !is String) return@forEach
            val report = runCatching { FountainReportJson.decode(value) }.getOrNull()
            if (report == null || report.isExpired(nowProvider())) {
                editor.remove(key)
            } else {
                reports.tryEmit(report)
            }
        }
        editor.apply()
    }

    private fun persistAndEmitIfLatest(report: FountainReport) {
        val key = cacheKey(report.fountainId)
        val cached = reportCache.getString(key, null)?.let { runCatching { FountainReportJson.decode(it) }.getOrNull() }
        if (cached == null || !cached.isExpired(nowProvider()) && !report.observedAt.isBefore(cached.observedAt)) {
            reportCache.edit().putString(key, FountainReportJson.encode(report)).apply()
            reports.tryEmit(report)
        }
    }

    private fun cacheKey(fountainId: String): String = "$REPORT_KEY_PREFIX$fountainId"

    private companion object {
        const val REPORT_CACHE = "sensoasis_community_reports"
        const val REPORT_KEY_PREFIX = "fountain_"
    }
}
