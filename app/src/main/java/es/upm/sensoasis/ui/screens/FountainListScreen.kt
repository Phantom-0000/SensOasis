package es.upm.sensoasis.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import es.upm.sensoasis.fake.SampleData
import es.upm.sensoasis.ui.theme.SensOasisTheme
import es.upm.sensoasis.viewmodel.SensOasisUiState
import es.upm.sensoasis.viewmodel.UiAction

/**
 * Main list screen displaying Madrid drinking fountains, distance from the user's current
 * (or fallback) location, official operational status, and latest community report summary.
 *
 * Signature contract: stateless composable receiving [state] and [onAction].
 */
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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "SensOasis — Drinking Fountains",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "MQTT: ${state.mqttState} | Location: ${if (state.isFallbackLocation) "Madrid Fallback" else "GPS"}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(
                onClick = {
                    onAction(UiAction.RefreshFountains)
                    onAction(UiAction.RefreshLocation)
                }
            ) {
                Text("Refresh")
            }
        }

        state.error?.let { err ->
            Text(
                text = err,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.fountains, key = { it.id }) { fountain ->
                val distMeters = state.fountainDistancesMeters[fountain.id]
                val communityReport = state.communityReportsByFountain[fountain.id]
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToDetail(fountain.id) }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = fountain.name,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Official: ${fountain.officialStatus}" +
                                (distMeters?.let { " • ${it}m away" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        communityReport?.let { report ->
                            val age = report.ageMinutes(state.now)
                            val lightText = report.lightObservation
                                ?.takeIf { it.quality == es.upm.sensoasis.domain.model.LightQuality.VALID }
                                ?.let { " • Community light reading: ${it.lightBand}" } ?: ""
                            Text(
                                text = "Community: ${report.status} (${age}m ago$lightText)",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
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
