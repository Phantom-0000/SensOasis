
package es.upm.sensoasis.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.upm.sensoasis.domain.model.FountainStatus
import es.upm.sensoasis.domain.model.formatLightBand
import es.upm.sensoasis.domain.model.formatObservationAge
import es.upm.sensoasis.fake.SampleData
import es.upm.sensoasis.ui.theme.SensOasisTheme
import es.upm.sensoasis.viewmodel.SensOasisUiState
import es.upm.sensoasis.viewmodel.UiAction

@Composable
fun FountainListScreen(
    state: SensOasisUiState,
    onAction: (UiAction) -> Unit,
    onNavigateToDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SensOasis",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "Drinking fountains near you",
                    fontSize = 16.sp
                )

                Text(
                    text = "MQTT: ${mqttLabel(state.mqttState.name)} | " +
                            "Location: ${if (state.isFallbackLocation) "Madrid fallback" else "GPS"}",
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Button(
                onClick = {
                    onAction(UiAction.RefreshFountains)
                    onAction(UiAction.RefreshLocation)
                },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh fountains",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = "Refresh",
                    fontSize = 14.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        if (state.isLoadingFountains) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Text(
                    text = "Loading fountains...",
                    modifier = Modifier.padding(start = 12.dp),
                    fontSize = 16.sp
                )
            }
        }

        state.error?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp
            )
        }

        if (!state.isLoadingFountains && state.fountains.isEmpty()) {
            Text(
                text = "No fountains available. Please refresh.",
                fontSize = 16.sp
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                items = state.fountains,
                key = { fountain -> fountain.id }
            ) { fountain ->

                val distance = state.fountainDistancesMeters[fountain.id]
                val report = state.communityReportsByFountain[fountain.id]

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp)
                        .clickable(
                            role = Role.Button,
                            onClick = {
                                onNavigateToDetail(fountain.id)
                            }
                        )
                        .semantics {
                            contentDescription = "Open details for ${fountain.name}"
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = fountain.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        FountainStatusView(status = fountain.officialStatus)

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "Distance",
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = distance?.let {
                                    formatDistance(it) + " away"
                                } ?: "Distance unavailable",
                                fontSize = 14.sp
                            )
                        }

                        if (report != null) {
                            Text(
                                text = "Latest community report",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )

                            // Community report status with an intuitive icon.
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val reportIcon = when (report.status) {
                                    FountainStatus.WORKING -> Icons.Default.CheckCircle
                                    FountainStatus.NOT_WORKING -> Icons.Default.Warning
                                    FountainStatus.UNKNOWN -> Icons.Default.Info
                                }

                                val reportColor = when (report.status) {
                                    FountainStatus.WORKING -> Color(0xFF247A45)
                                    FountainStatus.NOT_WORKING -> Color(0xFFB3261E)
                                    FountainStatus.UNKNOWN -> Color(0xFF555555)
                                }

                                Icon(
                                    imageVector = reportIcon,
                                    contentDescription = friendlyStatus(report.status),
                                    tint = reportColor,
                                    modifier = Modifier.size(22.dp)
                                )

                                Text(
                                    text = "Reported status: ${friendlyStatus(report.status)} " +
                                            "(${formatObservationAge(report.observedAt, state.now)})",
                                    fontSize = 14.sp
                                )
                            }

                            // Shared light description helper, shown only when lightBand is available
                            val band = report.lightObservation?.lightBand
                            if (band != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LightMode,
                                        contentDescription = "Ambient light",
                                        modifier = Modifier.size(22.dp)
                                    )

                                    Text(
                                        text = formatLightBand(band),
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FountainStatusView(status: FountainStatus) {
    val icon: ImageVector
    val label: String
    val description: String
    val tint: Color

    when (status) {
        FountainStatus.WORKING -> {
            icon = Icons.Default.CheckCircle
            label = "Working"
            description = "Water is available"
            tint = Color(0xFF247A45)
        }

        FountainStatus.NOT_WORKING -> {
            icon = Icons.Default.Warning
            label = "Not Working"
            description = "Water may not be available"
            tint = Color(0xFFB3261E)
        }

        FountainStatus.UNKNOWN -> {
            icon = Icons.Default.Info
            label = "Status Unknown"
            description = "Check before visiting"
            tint = Color(0xFF555555)
        }
    }

    Surface(
        color = tint.copy(alpha = 0.10f),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )

            Column {
                Text(
                    text = label,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = tint
                )
                Text(
                    text = description,
                    fontSize = 14.sp
                )
            }
        }
    }
}

private fun friendlyStatus(status: FountainStatus): String {
    return when (status) {
        FountainStatus.WORKING -> "Working"
        FountainStatus.NOT_WORKING -> "Not Working"
        FountainStatus.UNKNOWN -> "Unknown"
    }
}

private fun mqttLabel(status: String): String {
    return when (status) {
        "CONNECTED" -> "Connected"
        "CONNECTING" -> "Connecting"
        "DISCONNECTED" -> "Offline"
        "ERROR" -> "Connection problem"
        else -> "Unknown"
    }
}

private fun formatDistance(meters: Int): String {
    return if (meters >= 1000) {
        String.format(
            java.util.Locale.getDefault(),
            "%.1f km",
            meters / 1000f
        )
    } else {
        "$meters m"
    }
}

@Preview(showBackground = true)
@Composable
private fun FountainListScreenPreview() {
    SensOasisTheme {
        FountainListScreen(
            state = SampleData.previewListLoadedState(),
            onAction = {},
            onNavigateToDetail = {}
        )
    }
}
