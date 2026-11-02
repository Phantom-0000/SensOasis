package es.upm.sensoasis.fake

import es.upm.sensoasis.domain.contract.MqttRepository
import es.upm.sensoasis.domain.model.ConnectionState
import es.upm.sensoasis.domain.model.FountainReport
import es.upm.sensoasis.domain.model.PublishResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import java.time.Instant

/**
 * In-memory loopback fake implementation of [MqttRepository].
 *
 * Key behavior:
 * - [publishReport] validates expiry and immediately emits the report into [observeAllReports]
 *   and [observeReports] so the full "Report -> MQTT -> UI display" loop works on a single device.
 * - Supports simulating [ConnectionState.DISCONNECTED] / [PublishResult.Offline] for offline tests.
 * - Enforces ingress expiry filtering.
 */
class FakeMqttRepository(
    var simulatedConnectDelayMs: Long = 150L,
    simulateOffline: Boolean = false,
    var nowProvider: () -> Instant = { Instant.now() },
    seedInitialSampleReport: Boolean = true
) : MqttRepository {

    private val _connectionState = MutableStateFlow(
        if (simulateOffline) ConnectionState.ERROR else ConnectionState.DISCONNECTED
    )
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    var simulateOffline: Boolean = simulateOffline
        set(value) {
            field = value
            if (value) {
                _connectionState.value = ConnectionState.ERROR
            } else if (_connectionState.value == ConnectionState.ERROR) {
                _connectionState.value = ConnectionState.CONNECTED
            }
        }

    private val reportsFlow = MutableSharedFlow<FountainReport>(replay = 16, extraBufferCapacity = 32)

    val publishedReports = mutableListOf<FountainReport>()

    init {
        if (seedInitialSampleReport) {
            val initial = SampleData.sampleValidReport(now = nowProvider())
            reportsFlow.tryEmit(initial)
        }
    }

    override suspend fun connect() {
        if (simulateOffline) {
            _connectionState.value = ConnectionState.ERROR
            return
        }
        _connectionState.value = ConnectionState.CONNECTING
        if (simulatedConnectDelayMs > 0) delay(simulatedConnectDelayMs)
        _connectionState.value = ConnectionState.CONNECTED
    }

    override suspend fun disconnect() {
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    override suspend fun publishReport(report: FountainReport): PublishResult {
        if (simulateOffline || _connectionState.value != ConnectionState.CONNECTED) {
            return PublishResult.Offline
        }
        publishedReports.add(report)
        if (!report.isExpired(nowProvider())) {
            reportsFlow.emit(report)
        }
        return PublishResult.Published
    }

    /**
     * Simulates receiving an incoming or retained message from another device on the broker.
     * Discards already-expired messages.
     */
    suspend fun emitIncomingBrokerReport(report: FountainReport) {
        if (!report.isExpired(nowProvider())) {
            reportsFlow.emit(report)
        }
    }

    override fun observeReports(fountainId: String): Flow<FountainReport> =
        reportsFlow.filter { it.fountainId == fountainId && !it.isExpired(nowProvider()) }

    override fun observeAllReports(): Flow<FountainReport> =
        reportsFlow.filter { !it.isExpired(nowProvider()) }
}
