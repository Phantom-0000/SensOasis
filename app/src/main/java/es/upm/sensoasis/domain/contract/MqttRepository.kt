package es.upm.sensoasis.domain.contract

import es.upm.sensoasis.domain.model.ConnectionState
import es.upm.sensoasis.domain.model.FountainReport
import es.upm.sensoasis.domain.model.PublishResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Publishes fountain status reports and subscribes to community reports using MQTTastic.
 * Enforces ingress expiry filtering (discarding incoming or retained messages whose
 * [FountainReport.expiresAt] is already in the past).
 */
interface MqttRepository {
    val connectionState: StateFlow<ConnectionState>

    suspend fun connect()

    suspend fun disconnect()

    suspend fun publishReport(report: FountainReport): PublishResult

    fun observeReports(fountainId: String): Flow<FountainReport>

    fun observeAllReports(): Flow<FountainReport>
}
