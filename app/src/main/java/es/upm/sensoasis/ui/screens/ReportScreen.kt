package es.upm.sensoasis.ui.screens

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import es.upm.sensoasis.domain.model.FountainStatus
import es.upm.sensoasis.domain.model.LightCaptureResult
import es.upm.sensoasis.domain.model.formatLux
import es.upm.sensoasis.fake.SampleData
import es.upm.sensoasis.ui.theme.SensOasisTheme
import es.upm.sensoasis.viewmodel.SensOasisUiState
import es.upm.sensoasis.viewmodel.UiAction

/**
 * Designated NO-SCROLL target screen in both portrait and landscape orientations.
 * Uses distinct portrait (`Column`) and landscape (`Row`) layouts so all controls and the
 * captured ambient-light preview remain 100% visible without scrolling on the moto g35 5G.
 *
 * Lifecycle Rule:
 * Dispatches [UiAction.LeaveReportScreen] only when the user explicitly navigates back,
 * NOT inside an unconditional `DisposableEffect.onDispose`, so screen rotation preserves state.
 */
@Composable
fun ReportScreen(
    state: SensOasisUiState,
    onAction: (UiAction) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        onNavigateBack()
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                ReportHeaderAndStatusSection(
                    state = state,
                    onAction = onAction,
                    onNavigateBack = onNavigateBack
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                ReportLightPreviewAndSubmitSection(
                    state = state,
                    onAction = onAction
                )
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            ReportHeaderAndStatusSection(
                state = state,
                onAction = onAction,
                onNavigateBack = onNavigateBack
            )
            ReportLightPreviewAndSubmitSection(
                state = state,
                onAction = onAction
            )
        }
    }
}

@Composable
private fun ReportHeaderAndStatusSection(
    state: SensOasisUiState,
    onAction: (UiAction) -> Unit,
    onNavigateBack: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onNavigateBack) {
                Text("Cancel")
            }
            Text(
                text = state.selectedFountain?.name ?: "Report Fountain",
                style = MaterialTheme.typography.titleMedium
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "1. Select Fountain Status",
                    style = MaterialTheme.typography.titleSmall
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf(FountainStatus.WORKING, FountainStatus.NOT_WORKING).forEach { option ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = state.reportDraftStatus == option,
                                onClick = { onAction(UiAction.ChangeFountainStatus(option)) },
                                enabled = !state.interactionLocked
                            )
                            Text(text = option.name.replace('_', ' '))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportLightPreviewAndSubmitSection(
    state: SensOasisUiState,
    onAction: (UiAction) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "2. Ambient-Light Observation (Optional)",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "Proximity: ${state.proximityState} | Flow: ${state.reportState}",
                    style = MaterialTheme.typography.bodySmall
                )
                val lightPreviewText = when (val light = state.lightState) {
                    null -> "Not captured yet. You can capture light or submit status directly."
                    is LightCaptureResult.Valid ->
                        "VALID: ${formatLux(light.observation.ambientLux)} (${light.observation.lightBand})"
                    LightCaptureResult.Occluded ->
                        "OCCLUDED: Sensor covered (NEAR). Report will submit without light."
                    is LightCaptureResult.Unavailable ->
                        "UNAVAILABLE (${light.reason}). Report will submit without light."
                    is LightCaptureResult.Error ->
                        "ERROR (${light.message}). Report will submit without light."
                }
                Text(
                    text = lightPreviewText,
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(
                    onClick = { onAction(UiAction.CaptureLight) },
                    enabled = !state.interactionLocked && state.reportState.canSubmit,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Capture Ambient Light (1.5s)")
                }
            }
        }

        Button(
            onClick = { onAction(UiAction.SubmitReport) },
            enabled = !state.interactionLocked && state.reportState.canSubmit,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Confirm & Publish MQTT Report")
        }
    }
}

@Preview(showBackground = true, name = "Report Portrait")
@Composable
private fun ReportScreenPortraitPreview() {
    SensOasisTheme {
        ReportScreen(
            state = SampleData.previewReportReadyState(),
            onAction = {},
            onNavigateBack = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 360, name = "Report Landscape")
@Composable
private fun ReportScreenLandscapePreview() {
    SensOasisTheme {
        ReportScreen(
            state = SampleData.previewReportOccludedState(),
            onAction = {},
            onNavigateBack = {}
        )
    }
}
