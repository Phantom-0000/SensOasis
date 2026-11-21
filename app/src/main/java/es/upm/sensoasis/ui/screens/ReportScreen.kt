package es.upm.sensoasis.ui.screens

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatColorReset
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.upm.sensoasis.domain.model.FountainStatus
import es.upm.sensoasis.domain.model.LightCaptureResult
import es.upm.sensoasis.domain.model.formatLux
import es.upm.sensoasis.domain.model.formatUnavailableReason
import es.upm.sensoasis.fake.SampleData
import es.upm.sensoasis.ui.i18n.AppStrings
import es.upm.sensoasis.ui.i18n.LocalAppStrings
import es.upm.sensoasis.ui.theme.SensOasisTheme
import es.upm.sensoasis.ui.theme.StatusBrokenBg
import es.upm.sensoasis.ui.theme.StatusBrokenFg
import es.upm.sensoasis.ui.theme.StatusWorkingBg
import es.upm.sensoasis.ui.theme.StatusWorkingFg
import es.upm.sensoasis.viewmodel.ReportFlowState
import es.upm.sensoasis.viewmodel.SensOasisUiState
import es.upm.sensoasis.viewmodel.UiAction

/**
 * Fountain status and sunlight/shade reporting screen.
 *
 * Course Rule: Must remain 100% visible without scrolling in BOTH portrait and landscape modes,
 * including when A+ text scaling or enlarged Android system font scaling is active.
 */
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
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Cap extreme font-scale so primary actions never clip on this non-scrolling screen.
    val currentDensity = LocalDensity.current
    val safeFontScale = currentDensity.fontScale.coerceAtMost(
        if (isLandscape) 1.08f else 1.18f
    )

    CompositionLocalProvider(
        LocalDensity provides Density(
            density = currentDensity.density,
            fontScale = safeFontScale
        )
    ) {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            if (isLandscape) {
                ReportLandscapeLayout(
                    state = state,
                    onAction = onAction,
                    onNavigateBack = onNavigateBack
                )
            } else {
                ReportPortraitLayout(
                    state = state,
                    onAction = onAction,
                    onNavigateBack = onNavigateBack
                )
            }
        }
    }
}

// ----------------------------------------------------
// PORTRAIT LAYOUT (Senior-Friendly Large Icons, Zero Scroll)
// ----------------------------------------------------

@Composable
private fun ReportPortraitLayout(
    state: SensOasisUiState,
    onAction: (UiAction) -> Unit,
    onNavigateBack: () -> Unit
) {
    val strings = LocalAppStrings.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onNavigateBack,
                enabled = !state.interactionLocked,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = strings.cancel,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings.reportFountainTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = state.selectedFountain?.name ?: strings.reportFountainTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Card 1: Fountain Status Selection with High-Recognition Water Icons
        StatusSelectionCard(
            selectedStatus = state.reportDraftStatus,
            interactionLocked = state.interactionLocked,
            isLandscape = false,
            onSelectStatus = { onAction(UiAction.ChangeFountainStatus(it)) }
        )

        // Card 2: Sunlight & Shade Check
        AmbientLightCard(
            state = state,
            onCaptureLight = { onAction(UiAction.CaptureLight) },
            isLandscape = false
        )

        Spacer(modifier = Modifier.weight(1f))

        // Bottom Actions & Warnings
        ReportSubmitFooter(
            state = state,
            onAction = onAction,
            isLandscape = false
        )
    }
}

// ----------------------------------------------------
// LANDSCAPE LAYOUT (Unified Top Bar + Balanced Two-Column Row, Zero Scroll)
// ----------------------------------------------------

@Composable
private fun ReportLandscapeLayout(
    state: SensOasisUiState,
    onAction: (UiAction) -> Unit,
    onNavigateBack: () -> Unit
) {
    val strings = LocalAppStrings.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Unified Compact Header Row across both panes
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onNavigateBack,
                enabled = !state.interactionLocked,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.heightIn(min = 40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = strings.cancel,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }

            Text(
                text = state.selectedFountain?.name ?: strings.reportFountainTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = reportStateLabel(state.reportState, strings),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        // Aligned Two-Pane Content Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Left Pane: Status Selection + Error Banner
            Column(
                modifier = Modifier
                    .weight(0.45f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatusSelectionCard(
                    selectedStatus = state.reportDraftStatus,
                    interactionLocked = state.interactionLocked,
                    isLandscape = true,
                    onSelectStatus = { onAction(UiAction.ChangeFountainStatus(it)) }
                )

                state.error?.let { error ->
                    Surface(
                        color = StatusBrokenBg,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = error,
                            color = StatusBrokenFg,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // Right Pane: Sunlight Card + Primary Submit Button
            Column(
                modifier = Modifier
                    .weight(0.55f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AmbientLightCard(
                    state = state,
                    onCaptureLight = { onAction(UiAction.CaptureLight) },
                    isLandscape = true
                )

                ReportSubmitFooter(
                    state = state,
                    onAction = onAction,
                    isLandscape = true,
                    showErrorText = false
                )
            }
        }
    }
}

// ----------------------------------------------------
// SHARED CARDS & COMPONENTS
// ----------------------------------------------------

@Composable
private fun StatusSelectionCard(
    selectedStatus: FountainStatus,
    interactionLocked: Boolean,
    isLandscape: Boolean,
    onSelectStatus: (FountainStatus) -> Unit
) {
    val strings = LocalAppStrings.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (isLandscape) 14.dp else 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(if (isLandscape) 10.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (isLandscape) 6.dp else 10.dp)
        ) {
            Text(
                text = strings.step1WaterWorking,
                fontWeight = FontWeight.Bold,
                fontSize = if (isLandscape) 15.sp else 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            listOf(FountainStatus.WORKING, FountainStatus.NOT_WORKING).forEach { option ->
                val isSelected = selectedStatus == option
                val isWorking = option == FountainStatus.WORKING
                val accentFg = if (isWorking) StatusWorkingFg else StatusBrokenFg
                val accentBg = if (isWorking) StatusWorkingBg else StatusBrokenBg
                val optionLabel = if (isWorking) strings.statusWorking else strings.statusNotWorking

                Surface(
                    color = if (isSelected) accentBg else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) accentFg else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = if (isLandscape) 44.dp else 56.dp)
                        .clickable(
                            enabled = !interactionLocked,
                            role = Role.RadioButton,
                            onClick = { onSelectStatus(option) }
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = if (isLandscape) 8.dp else 12.dp,
                            vertical = if (isLandscape) 4.dp else 8.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null,
                            enabled = !interactionLocked
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = accentFg,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(if (isLandscape) 28.dp else 36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isWorking) {
                                        Icons.Default.WaterDrop
                                    } else {
                                        Icons.Default.FormatColorReset
                                    },
                                    contentDescription = optionLabel,
                                    tint = Color.White,
                                    modifier = Modifier.size(if (isLandscape) 18.dp else 24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = optionLabel,
                            fontSize = if (isLandscape) 15.sp else 18.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) accentFg else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AmbientLightCard(
    state: SensOasisUiState,
    onCaptureLight: () -> Unit,
    isLandscape: Boolean
) {
    val strings = LocalAppStrings.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (isLandscape) 14.dp else 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(if (isLandscape) 10.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (isLandscape) 6.dp else 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isLandscape) {
                        strings.step2SunlightCompact
                    } else {
                        strings.step2SunlightCheck
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isLandscape) 15.sp else 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 6.dp)
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = sensorClearLabel(state.proximityState.name, strings),
                        fontSize = if (isLandscape) 12.sp else 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            if (!isLandscape) {
                Text(
                    text = "${strings.statusPrefix}: ${reportStateLabel(state.reportState, strings)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val (lightMessage, boxBg, boxFg) = when (val light = state.lightState) {
                null -> Triple(
                    strings.lightPromptIdle,
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    MaterialTheme.colorScheme.onSurfaceVariant
                )
                is LightCaptureResult.Valid -> {
                    val sep = if (isLandscape) " · " else "\n"
                    val bandTitle = strings.formatBandTitle(light.observation.lightBand)
                    val bandDesc = strings.formatBandDesc(light.observation.lightBand)
                    Triple(
                        "${strings.lightMeasuredPrefix}: ${formatLux(light.observation.ambientLux)} ($bandTitle)$sep$bandDesc",
                        StatusWorkingBg,
                        StatusWorkingFg
                    )
                }
                LightCaptureResult.Occluded -> Triple(
                    strings.lightOccludedMsg,
                    StatusBrokenBg,
                    StatusBrokenFg
                )
                is LightCaptureResult.Unavailable -> Triple(
                    strings.lightUnavailableMsg(formatUnavailableReason(light.reason)),
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.onSurfaceVariant
                )
                is LightCaptureResult.Error -> Triple(
                    strings.lightUnavailableMsg(light.message),
                    StatusBrokenBg,
                    StatusBrokenFg
                )
            }

            Surface(
                color = boxBg,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = lightMessage,
                    fontSize = if (isLandscape) 13.sp else 15.sp,
                    lineHeight = if (isLandscape) 17.sp else 21.sp,
                    fontWeight = if (state.lightState is LightCaptureResult.Valid) {
                        FontWeight.SemiBold
                    } else {
                        FontWeight.Normal
                    },
                    color = boxFg,
                    maxLines = if (isLandscape) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(
                        horizontal = if (isLandscape) 10.dp else 12.dp,
                        vertical = if (isLandscape) 6.dp else 10.dp
                    )
                )
            }

            OutlinedButton(
                onClick = onCaptureLight,
                enabled = !state.interactionLocked &&
                    state.reportState.canSubmit &&
                    state.reportState != ReportFlowState.PUBLISHED,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    width = 1.5.dp,
                    color = if (
                        !state.interactionLocked &&
                        state.reportState.canSubmit &&
                        state.reportState != ReportFlowState.PUBLISHED
                    ) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                ),
                contentPadding = PaddingValues(
                    horizontal = 12.dp,
                    vertical = if (isLandscape) 6.dp else 10.dp
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (isLandscape) 42.dp else 50.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LightMode,
                    contentDescription = null,
                    modifier = Modifier.size(if (isLandscape) 18.dp else 22.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (state.reportState == ReportFlowState.CAPTURING_LIGHT) {
                        strings.measuringBrightnessBtn
                    } else {
                        strings.measureSunlightBtn
                    },
                    fontSize = if (isLandscape) 14.sp else 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ReportSubmitFooter(
    state: SensOasisUiState,
    onAction: (UiAction) -> Unit,
    isLandscape: Boolean,
    showErrorText: Boolean = true
) {
    val strings = LocalAppStrings.current

    Column(
        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 6.dp else 8.dp)
    ) {
        if (state.interactionLocked) {
            Button(
                onClick = { onAction(UiAction.StopSpeech) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = StatusBrokenFg,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (isLandscape) 40.dp else 50.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = strings.stopSpeaking,
                    fontSize = if (isLandscape) 14.sp else 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }

        val submitLabel = when (state.reportState) {
            ReportFlowState.PUBLISHING -> strings.sendingReportBtn
            ReportFlowState.PUBLISHED -> strings.sentSuccessBtn
            ReportFlowState.OFFLINE -> strings.offlineRetryBtn
            ReportFlowState.ERROR -> strings.retrySendBtn
            else -> strings.confirmSendBtn
        }

        Button(
            onClick = { onAction(UiAction.SubmitReport) },
            enabled = !state.interactionLocked &&
                state.reportState.canSubmit &&
                state.reportState != ReportFlowState.PUBLISHED,
            shape = RoundedCornerShape(if (isLandscape) 12.dp else 14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (isLandscape) 46.dp else 56.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = null,
                modifier = Modifier.size(if (isLandscape) 18.dp else 22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = submitLabel,
                fontSize = if (isLandscape) 15.sp else 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (showErrorText) {
            state.error?.let { error ->
                Surface(
                    color = StatusBrokenBg,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = error,
                        color = StatusBrokenFg,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------
// PLAIN-LANGUAGE ACCESSIBILITY LABELS
// ----------------------------------------------------

private fun sensorClearLabel(value: String, strings: AppStrings): String = when (value) {
    "NEAR" -> strings.sensorCovered
    "FAR" -> strings.sensorClear
    else -> strings.sensorReady
}

private fun reportStateLabel(state: ReportFlowState, strings: AppStrings): String = when (state) {
    ReportFlowState.IDLE -> strings.stateReadyToSend
    ReportFlowState.CAPTURING_LIGHT -> strings.stateMeasuring
    ReportFlowState.READY -> strings.stateReadyToSend
    ReportFlowState.OCCLUDED -> strings.stateTopCovered
    ReportFlowState.UNAVAILABLE -> strings.stateLightUnavailable
    ReportFlowState.PUBLISHING -> strings.stateSending
    ReportFlowState.PUBLISHED -> strings.stateSentSuccess
    ReportFlowState.OFFLINE -> strings.stateOffline
    ReportFlowState.ERROR -> strings.stateError
}

// ----------------------------------------------------
// ANDROID STUDIO PREVIEWS
// ----------------------------------------------------

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

@Preview(showBackground = true, name = "Report Capturing")
@Composable
private fun ReportScreenCapturingPreview() {
    SensOasisTheme {
        ReportScreen(
            state = SampleData.previewReportCapturingState(),
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
