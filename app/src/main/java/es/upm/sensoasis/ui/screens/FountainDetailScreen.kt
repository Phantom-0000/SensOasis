
package es.upm.sensoasis.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.upm.sensoasis.domain.model.FountainStatus
import es.upm.sensoasis.domain.model.ProximityState
import es.upm.sensoasis.domain.model.SpeechState
import es.upm.sensoasis.domain.model.formatLux
import es.upm.sensoasis.domain.model.formatLightBand
import es.upm.sensoasis.domain.model.formatLightQuality
import es.upm.sensoasis.domain.model.formatObservationAge
import es.upm.sensoasis.fake.SampleData
import es.upm.sensoasis.ui.theme.SensOasisTheme
import es.upm.sensoasis.viewmodel.SensOasisUiState
import es.upm.sensoasis.viewmodel.UiAction

@Composable
fun FountainDetailScreen(
    state: SensOasisUiState,
    onAction: (UiAction) -> Unit,
    onNavigateToReport: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Consume system Back while private speech locks the interface.
    BackHandler {
        if (!state.interactionLocked) {
            onNavigateBack()
        }
    }

    val fountain = state.selectedFountain

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onNavigateBack,
                enabled = !state.interactionLocked,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Back", fontSize = 16.sp)
            }

            Text(
                text = "Fountain Details",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        if (fountain == null) {
            Text(
                text = "No fountain selected.",
                fontSize = 18.sp
            )
            return@Column
        }

        // Fountain information
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = fountain.name,
                    style = MaterialTheme.typography.titleLarge
                )

                Text("ID: ${fountain.id}", fontSize = 14.sp)

                Text(
                    text = "Coordinates: ${fountain.coordinates.latitude}, " +
                            "${fountain.coordinates.longitude}",
                    fontSize = 14.sp
                )

                Text(
                    text = "Distance: ${
                        state.selectedFountainDistanceMeters?.let {
                            if (it >= 1000) {
                                String.format(
                                    java.util.Locale.getDefault(),
                                    "%.1f km",
                                    it / 1000f
                                )
                            } else {
                                "$it m"
                            }
                        } ?: "Unknown"
                    }",
                    fontSize = 16.sp
                )

                StatusLine(
                    status = fountain.officialStatus,
                    prefix = "Official status"
                )

                state.airQuality?.let { aq ->
                    Text(
                        text = "Air Quality: ${aq.category} (Index ${aq.value})",
                        fontSize = 16.sp
                    )
                }
            }
        }

        // Latest community report
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Latest Community Report",
                    style = MaterialTheme.typography.titleMedium
                )

                val report = state.latestCommunityReport

                if (report == null) {
                    Text(
                        text = "No active community report or report expired.",
                        fontSize = 15.sp
                    )
                } else {
                    StatusLine(
                        status = report.status,
                        prefix = "Reported status"
                    )

                    Text(
                        text = "Report age: ${
                            formatObservationAge(report.observedAt, state.now)
                        }",
                        fontSize = 14.sp
                    )

                    val light = report.lightObservation

                    if (light != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.LightMode,
                                contentDescription = "Ambient light observation",
                                modifier = Modifier.padding(end = 8.dp)
                            )

                            Text(
                                text = "Ambient light observation",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        }

                        if (light.ambientLux != null) {
                            Text(
                                text = "Light level: ${formatLux(light.ambientLux)}",
                                fontSize = 15.sp
                            )

                            Text(
                                text = formatLightBand(light.lightBand),
                                fontSize = 14.sp
                            )
                        }

                        Text(
                            text = "Quality: ${formatLightQuality(light.quality)}",
                            fontSize = 14.sp
                        )

                        Text(
                            text = "Light captured: ${
                                formatObservationAge(light.observedAt, state.now)
                            }",
                            fontSize = 14.sp
                        )
                    } else {
                        Text(
                            text = "Ambient light: Not included in this report.",
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // Private speech controls
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = "Private speech",
                                modifier = Modifier.padding(end = 6.dp)
                            )

                            Text(
                                text = "Proximity Private Speech",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        Text(
                            text = "Hear fountain details privately.",
                            fontSize = 14.sp
                        )
                    }

                    Switch(
                        checked = state.privateSpeechEnabled,
                        onCheckedChange = {
                            onAction(UiAction.EnablePrivateSpeech(it))
                        },
                        modifier = Modifier.semantics {
                            contentDescription =
                                "Enable proximity private speech"
                        }
                    )
                }

                Text(
                    text = "Speech: ${friendlySpeech(state.speechState)}",
                    fontSize = 14.sp
                )

                Text(
                    text = "Proximity: ${friendlyProximity(state.proximityState)}",
                    fontSize = 14.sp
                )

                if (state.interactionLocked) {
                    Text(
                        text = "Interaction locked while private speech is active.",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    Button(
                        onClick = {
                            onAction(UiAction.StopSpeech)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text("Stop Speech", fontSize = 16.sp)
                    }
                }
            }
        }

        // Walking navigation and report actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    onAction(UiAction.OpenWalkingRoute)
                },
                enabled = !state.interactionLocked,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 4.dp)
                )
                Text("Walking Route", fontSize = 14.sp)
            }

            Button(
                onClick = {
                    onNavigateToReport(fountain.id)
                },
                enabled = !state.interactionLocked,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.RateReview,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 4.dp)
                )
                Text("Report Status", fontSize = 14.sp)
            }
        }

        if (state.interactionLocked) {
            Text(
                text = "Navigation and reporting are disabled until speech stops.",
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp
            )
        }
    }
}

// Status icon and readable status label
@Composable
private fun StatusIcon(status: FountainStatus) {
    val image = when (status) {
        FountainStatus.WORKING -> Icons.Default.CheckCircle
        FountainStatus.NOT_WORKING -> Icons.Default.Warning
        FountainStatus.UNKNOWN -> Icons.Default.Info
    }

    val tint = when (status) {
        FountainStatus.WORKING -> Color(0xFF247A45)
        FountainStatus.NOT_WORKING -> Color(0xFFB3261E)
        FountainStatus.UNKNOWN -> Color(0xFF555555)
    }

    val description = friendlyStatus(status)

    Icon(
        imageVector = image,
        contentDescription = description,
        tint = tint,
        modifier = Modifier.padding(end = 6.dp)
    )
}

@Composable
private fun StatusLine(
    status: FountainStatus,
    prefix: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusIcon(status)

        Text(
            text = "$prefix: ${friendlyStatus(status)}",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun friendlyStatus(status: FountainStatus): String {
    return when (status) {
        FountainStatus.WORKING -> "Working"
        FountainStatus.NOT_WORKING -> "Not Working"
        FountainStatus.UNKNOWN -> "Unknown"
    }
}

private fun friendlySpeech(state: SpeechState): String {
    return when (state) {
        SpeechState.IDLE -> "Off"
        SpeechState.WAITING_FOR_NEAR ->
            "Ready — hold phone to your ear to listen"
        SpeechState.SPEAKING -> "Speaking privately"
        SpeechState.ERROR -> "Speech unavailable"
    }
}

private fun friendlyProximity(state: ProximityState): String {
    return when (state) {
        ProximityState.NEAR -> "Phone near ear"
        ProximityState.FAR -> "Phone away from ear"
        ProximityState.UNKNOWN -> "Waiting for sensor reading"
    }
}

// Previews
@Preview(showBackground = true)
@Composable
private fun FountainDetailScreenPreview() {
    SensOasisTheme {
        FountainDetailScreen(
            state = SampleData.previewDetailWithReportState(),
            onAction = {},
            onNavigateToReport = {},
            onNavigateBack = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FountainDetailScreenLockedPreview() {
    SensOasisTheme {
        FountainDetailScreen(
            state = SampleData.previewSpeechLockedState(),
            onAction = {},
            onNavigateToReport = {},
            onNavigateBack = {}
        )
    }
}
