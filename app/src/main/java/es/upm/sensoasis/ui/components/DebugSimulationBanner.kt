package es.upm.sensoasis.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.upm.sensoasis.di.AppContainer
import es.upm.sensoasis.domain.model.LightBand
import es.upm.sensoasis.domain.model.ProximityState

/**
 * Debug-only simulation bar allowing developers to toggle fake sensor states
 * (`FAR` / `NEAR`, `MODERATE` / `BRIGHT` lux, MQTT online/offline) directly on screen while
 * developing in parallel before physical sensor/MQTT modules are connected.
 */
@Composable
fun DebugSimulationBanner(
    container: AppContainer,
    currentProximity: ProximityState,
    modifier: Modifier = Modifier
) {
    // Hide entirely once all simulated modules have been replaced by real implementations.
    if (!container.useFakeProximity && !container.useFakeLightSensor && !container.useFakeMqtt) return

    var expanded by rememberSaveable { mutableStateOf(false) }
    var isBrightLux by rememberSaveable { mutableStateOf(false) }
    var isOffline by rememberSaveable { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DEV FAKE CONTROLS (Prox: $currentProximity)",
                    style = MaterialTheme.typography.labelMedium
                )
                FilterChip(
                    selected = expanded,
                    onClick = { expanded = !expanded },
                    label = { Text(if (expanded) "Hide" else "Simulate") }
                )
            }

            if (expanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (container.useFakeProximity) {
                        FilterChip(
                            selected = currentProximity == ProximityState.FAR,
                            onClick = {
                                container.fakeProximityStateProvider.setSimulatedState(ProximityState.FAR)
                            },
                            label = { Text("Prox: FAR") }
                        )
                        FilterChip(
                            selected = currentProximity == ProximityState.NEAR,
                            onClick = {
                                container.fakeProximityStateProvider.setSimulatedState(ProximityState.NEAR)
                            },
                            label = { Text("Prox: NEAR (Ear/Cover)") }
                        )
                    }
                    if (container.useFakeLightSensor) {
                        FilterChip(
                            selected = isBrightLux,
                            onClick = {
                                isBrightLux = !isBrightLux
                                if (isBrightLux) {
                                    container.fakeLightSensorController.simulatedLux = 9500f
                                    container.fakeLightSensorController.simulatedBand = LightBand.BRIGHT
                                } else {
                                    container.fakeLightSensorController.simulatedLux = 3200f
                                    container.fakeLightSensorController.simulatedBand = LightBand.MODERATE
                                }
                            },
                            label = { Text(if (isBrightLux) "Lux: 9500" else "Lux: 3200") }
                        )
                    }
                    if (container.useFakeMqtt) {
                        FilterChip(
                            selected = isOffline,
                            onClick = {
                                isOffline = !isOffline
                                container.fakeMqttRepository.simulateOffline = isOffline
                            },
                            label = { Text(if (isOffline) "MQTT: Offline" else "MQTT: Online") }
                        )
                    }
                }
            }
        }
    }
}
