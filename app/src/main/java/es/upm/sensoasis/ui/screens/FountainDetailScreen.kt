package es.upm.sensoasis.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import es.upm.sensoasis.domain.model.formatLux
import es.upm.sensoasis.fake.SampleData
import es.upm.sensoasis.ui.theme.SensOasisTheme
import es.upm.sensoasis.viewmodel.SensOasisUiState
import es.upm.sensoasis.viewmodel.UiAction

/**
 * Fountain detail screen displaying official status, latest community report, distance,
 * walking navigation trigger, and proximity-triggered private speech controls.
 * Reflects [SensOasisUiState.interactionLocked] by disabling navigation and report actions
 * while proximity speech is active.
 */
@Composable
fun FountainDetailScreen(
    state: SensOasisUiState,
    onAction: (UiAction) -> Unit,
    onNavigateToReport: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        onNavigateBack()
    }

    val fountain = state.selectedFountain
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onNavigateBack) {
                Text("Back")
            }
            Text(
                text = "Fountain Details",
                style = MaterialTheme.typography.titleLarge
            )
        }

        if (fountain == null) {
            Text("No fountain selected.")
            return@Column
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = fountain.name, style = MaterialTheme.typography.titleMedium)
                Text(text = "ID: ${fountain.id}")
                Text(text = "Coordinates: ${fountain.coordinates.latitude}, ${fountain.coordinates.longitude}")
                Text(text = "Distance: ${state.selectedFountainDistanceMeters?.let { "${it}m" } ?: "Unknown"}")
                Text(text = "Official Status: ${fountain.officialStatus}")
                state.airQuality?.let { aq ->
                    Text(text = "Air Quality (Optional): ${aq.category} (Index ${aq.value})")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = "Latest Community Report", style = MaterialTheme.typography.titleSmall)
                val report = state.latestCommunityReport
                if (report == null) {
                    Text("No active community report (or expired).")
                } else {
                    Text("Reported Status: ${report.status} (${report.ageMinutes(state.now)} min ago)")
                    val light = report.lightObservation
                    if (light != null && light.ambientLux != null) {
                        Text("Ambient Light: ${light.lightBand} (${formatLux(light.ambientLux)}, ${light.quality})")
                    } else if (light != null) {
                        // e.g. OCCLUDED payload with null lux / null band (report_occluded.json)
                        Text("Ambient Light: No reading (${light.quality})")
                    } else {
                        Text("Ambient Light: Not included / Unavailable")
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Proximity Private Speech",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Speech: ${state.speechState} | Proximity: ${state.proximityState}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = state.privateSpeechEnabled,
                        onCheckedChange = { enabled ->
                            onAction(UiAction.EnablePrivateSpeech(enabled))
                        }
                    )
                }

                if (state.interactionLocked) {
                    Text(
                        text = "Interaction locked while phone is near ear or speaking.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(onClick = { onAction(UiAction.StopSpeech) }) {
                        Text("Stop Speech")
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { onAction(UiAction.OpenWalkingRoute) },
                enabled = !state.interactionLocked,
                modifier = Modifier.weight(1f)
            ) {
                Text("Walking Route")
            }
            Button(
                onClick = { onNavigateToReport(fountain.id) },
                enabled = !state.interactionLocked,
                modifier = Modifier.weight(1f)
            ) {
                Text("Report Status")
            }
        }
    }
}

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
