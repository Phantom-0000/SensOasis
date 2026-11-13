
package es.upm.sensoasis.ui.screens

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.upm.sensoasis.domain.model.FountainStatus
import es.upm.sensoasis.domain.model.LightCaptureResult
import es.upm.sensoasis.domain.model.formatLightBand
import es.upm.sensoasis.domain.model.formatLux
import es.upm.sensoasis.domain.model.formatUnavailableReason
import es.upm.sensoasis.fake.SampleData
import es.upm.sensoasis.ui.theme.SensOasisTheme
import es.upm.sensoasis.viewmodel.ReportFlowState
import es.upm.sensoasis.viewmodel.SensOasisUiState
import es.upm.sensoasis.viewmodel.UiAction

@Composable
fun ReportScreen(
    state: SensOasisUiState,
    onAction: (UiAction) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        if (!state.interactionLocked) {
            onNavigateBack()
        }
    }

    val isLandscape =
        LocalConfiguration.current.orientation ==
                Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                ReportHeaderSection(
                    state = state,
                    onAction = onAction,
                    onNavigateBack = onNavigateBack,
                    isLandscape = true
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                ReportLightSection(
                    state = state,
                    onAction = onAction,
                    isLandscape = true
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
            ReportHeaderSection(
                state = state,
                onAction = onAction,
                onNavigateBack = onNavigateBack,
                isLandscape = false
            )

            ReportLightSection(
                state = state,
                onAction = onAction,
                isLandscape = false
            )
        }
    }
}

// ----------------------------------------------------
// HEADER AND FOUNTAIN STATUS
// ----------------------------------------------------

@Composable
private fun ReportHeaderSection(
    state: SensOasisUiState,
    onAction: (UiAction) -> Unit,
    onNavigateBack: () -> Unit,
    isLandscape: Boolean = false
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 6.dp else 8.dp)
    ) {
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
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 4.dp)
                )
                Text(
                    text = "Cancel",
                    fontSize = 14.sp,
                    maxLines = 1
                )
            }

            Text(
                text = state.selectedFountain?.name ?: "Report Fountain",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontSize = if (isLandscape) 16.sp else 17.sp,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(if (isLandscape) 10.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(if (isLandscape) 2.dp else 6.dp)
            ) {
                Text(
                    text = "1. Select Fountain Status",
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isLandscape) 16.sp else 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    listOf(
                        FountainStatus.WORKING,
                        FountainStatus.NOT_WORKING
                    ).forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable(
                                    enabled = !state.interactionLocked,
                                    role = Role.RadioButton,
                                    onClick = {
                                        onAction(
                                            UiAction.ChangeFountainStatus(option)
                                        )
                                    }
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.reportDraftStatus == option,
                                onClick = null,
                                enabled = !state.interactionLocked
                            )

                            Icon(
                                imageVector = if (
                                    option == FountainStatus.WORKING
                                ) {
                                    Icons.Default.CheckCircle
                                } else {
                                    Icons.Default.Warning
                                },
                                contentDescription = if (
                                    option == FountainStatus.WORKING
                                ) {
                                    "Working"
                                } else {
                                    "Not working"
                                },
                                tint = if (
                                    option == FountainStatus.WORKING
                                ) {
                                    Color(0xFF247A45)
                                } else {
                                    Color(0xFFB3261E)
                                },
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .size(22.dp)
                            )

                            Text(
                                text = if (option == FountainStatus.WORKING) {
                                    "Working"
                                } else {
                                    "Not Working"
                                },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// AMBIENT LIGHT AND REPORT SUBMISSION
// ----------------------------------------------------

@Composable
private fun ReportLightSection(
    state: SensOasisUiState,
    onAction: (UiAction) -> Unit,
    isLandscape: Boolean = false
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 6.dp else 8.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(if (isLandscape) 8.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(if (isLandscape) 4.dp else 8.dp)
            ) {
                Text(
                    text = "2. Ambient-Light Observation (Optional)",
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isLandscape) 15.sp else 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "Proximity: ${proximityLabel(state.proximityState.name)} | " +
                            "Report: ${reportStateLabel(state.reportState)}",
                    fontSize = if (isLandscape) 13.sp else 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val lightMessage = when (val light = state.lightState) {
                    null ->
                        "Not captured yet. You can capture ambient light or submit the status directly."

                    is LightCaptureResult.Valid -> {
                        val sep = if (isLandscape) " — " else "\n"
                        "Captured: ${formatLux(light.observation.ambientLux)}$sep" +
                                formatLightBand(light.observation.lightBand)
                    }

                    LightCaptureResult.Occluded ->
                        "Sensor covered. Report will be submitted without light data."

                    is LightCaptureResult.Unavailable ->
                        "Sensor unavailable: ${formatUnavailableReason(light.reason)}. You can still submit the report."

                    is LightCaptureResult.Error ->
                        "Sensor error: ${light.message}. You can still submit the report."
                }

                Text(
                    text = lightMessage,
                    fontSize = if (isLandscape) 13.sp else 14.sp,
                    lineHeight = if (isLandscape) 17.sp else 19.sp,
                    maxLines = if (isLandscape) 2 else 3,
                    overflow = TextOverflow.Ellipsis
                )

                OutlinedButton(
                    onClick = {
                        onAction(UiAction.CaptureLight)
                    },
                    enabled = !state.interactionLocked &&
                            state.reportState.canSubmit &&
                            state.reportState != ReportFlowState.PUBLISHED,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LightMode,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Text(
                        text = if (
                            state.reportState == ReportFlowState.CAPTURING_LIGHT
                        ) {
                            "Measuring light..."
                        } else {
                            "Capture Ambient Light (1.5s)"
                        },
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (state.interactionLocked) {
            Button(
                onClick = {
                    onAction(UiAction.StopSpeech)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Text(
                    text = "Stop Speech",
                    fontSize = 16.sp,
                    maxLines = 1
                )
            }
        }

        val submitLabel = when (state.reportState) {
            ReportFlowState.PUBLISHING ->
                "Publishing MQTT Report..."

            ReportFlowState.PUBLISHED ->
                "Report Published Successfully!"

            ReportFlowState.OFFLINE ->
                "MQTT Offline - Retry"

            ReportFlowState.ERROR ->
                "Retry Report Submission"

            else ->
                "Confirm & Publish MQTT Report"
        }

        Button(
            onClick = {
                onAction(UiAction.SubmitReport)
            },
            enabled = !state.interactionLocked &&
                    state.reportState.canSubmit &&
                    state.reportState != ReportFlowState.PUBLISHED,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (isLandscape) 48.dp else 52.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = null,
                modifier = Modifier.padding(end = 6.dp)
            )
            Text(
                text = submitLabel,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        state.error?.let { error ->
            Text(
                text = "Warning: $error",
                color = MaterialTheme.colorScheme.error,
                fontSize = if (isLandscape) 13.sp else 14.sp,
                maxLines = if (isLandscape) 1 else 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ----------------------------------------------------
// ACCESSIBILITY LABELS
// ----------------------------------------------------

private fun proximityLabel(value: String): String {
    return when (value) {
        "NEAR" -> "Near"
        "FAR" -> "Far"
        else -> "Waiting for sensor reading"
    }
}

private fun reportStateLabel(state: ReportFlowState): String {
    return when (state) {
        ReportFlowState.IDLE -> "Ready to report"
        ReportFlowState.CAPTURING_LIGHT -> "Measuring ambient light"
        ReportFlowState.READY -> "Ready to publish"
        ReportFlowState.OCCLUDED -> "Light sensor covered"
        ReportFlowState.UNAVAILABLE -> "Light sensor unavailable"
        ReportFlowState.PUBLISHING -> "Sending report"
        ReportFlowState.PUBLISHED -> "Report sent successfully"
        ReportFlowState.OFFLINE -> "MQTT connection unavailable"
        ReportFlowState.ERROR -> "Report error"
    }
}

// ----------------------------------------------------
// ANDROID STUDIO PREVIEWS
// ----------------------------------------------------

@Preview(
    showBackground = true,
    name = "Report Portrait"
)
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

@Preview(
    showBackground = true,
    widthDp = 800,
    heightDp = 360,
    name = "Report Landscape"
)
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