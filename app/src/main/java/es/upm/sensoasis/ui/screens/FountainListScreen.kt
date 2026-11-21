package es.upm.sensoasis.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import es.upm.sensoasis.domain.model.ConnectionState
import es.upm.sensoasis.domain.model.FountainStatus
import es.upm.sensoasis.fake.SampleData
import es.upm.sensoasis.ui.components.LargeLightBandBadge
import es.upm.sensoasis.ui.components.LargeWaterStatusBadge
import es.upm.sensoasis.ui.i18n.LocalAppStrings
import es.upm.sensoasis.ui.theme.SensOasisTheme
import es.upm.sensoasis.ui.theme.StatusBrokenBg
import es.upm.sensoasis.ui.theme.StatusBrokenFg
import es.upm.sensoasis.ui.theme.StatusUnknownBg
import es.upm.sensoasis.ui.theme.StatusUnknownFg
import es.upm.sensoasis.ui.theme.StatusWorkingBg
import es.upm.sensoasis.ui.theme.StatusWorkingFg
import es.upm.sensoasis.viewmodel.SensOasisUiState
import es.upm.sensoasis.viewmodel.UiAction
import java.util.Locale

@Composable
fun FountainListScreen(
    state: SensOasisUiState,
    onAction: (UiAction) -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    var showWorkingOnly by rememberSaveable { mutableStateOf(false) }

    val displayedFountains = if (showWorkingOnly) {
        state.fountains.filter { fountain ->
            val reportedStatus = state.communityReportsByFountain[fountain.id]
                ?.status
                ?.takeIf { it != FountainStatus.UNKNOWN }
            val effectiveStatus = reportedStatus ?: fountain.officialStatus
            effectiveStatus == FountainStatus.WORKING
        }
    } else {
        state.fountains
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Bar: Back to Home + Title + Refresh
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onNavigateBack,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = strings.home,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Text(
                    text = strings.nearbyListHeader,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                )

                Button(
                    onClick = {
                        onAction(UiAction.RefreshFountains)
                        onAction(UiAction.RefreshLocation)
                    },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = strings.refresh,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Status Badges Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val (syncIcon, syncText, syncFg, syncBg) = when (state.mqttState) {
                    ConnectionState.CONNECTED -> StatusChipStyle(
                        icon = Icons.Default.CloudDone,
                        label = strings.liveOnline,
                        fg = StatusWorkingFg,
                        bg = StatusWorkingBg
                    )
                    ConnectionState.CONNECTING -> StatusChipStyle(
                        icon = Icons.Default.CloudSync,
                        label = strings.liveConnecting,
                        fg = MaterialTheme.colorScheme.primary,
                        bg = MaterialTheme.colorScheme.primaryContainer
                    )
                    ConnectionState.DISCONNECTED -> StatusChipStyle(
                        icon = Icons.Default.CloudOff,
                        label = strings.liveOffline,
                        fg = StatusUnknownFg,
                        bg = StatusUnknownBg
                    )
                    ConnectionState.ERROR -> StatusChipStyle(
                        icon = Icons.Default.CloudOff,
                        label = strings.liveOffline,
                        fg = StatusBrokenFg,
                        bg = StatusBrokenBg
                    )
                }

                StatusPill(
                    icon = syncIcon,
                    text = syncText,
                    contentColor = syncFg,
                    containerColor = syncBg
                )

                StatusPill(
                    icon = Icons.Default.LocationOn,
                    text = if (state.isFallbackLocation) strings.madridCenter else strings.gpsActive,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            }

            // Senior-Friendly "Working Fountains Only" Toggle Bar
            Surface(
                color = if (showWorkingOnly) {
                    StatusWorkingBg
                } else {
                    MaterialTheme.colorScheme.surface
                },
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(
                    width = if (showWorkingOnly) 1.5.dp else 1.dp,
                    color = if (showWorkingOnly) {
                        StatusWorkingFg
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        role = Role.Switch,
                        onClick = { showWorkingOnly = !showWorkingOnly }
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WaterDrop,
                            contentDescription = null,
                            tint = if (showWorkingOnly) {
                                StatusWorkingFg
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(26.dp)
                        )
                        Column {
                            Text(
                                text = strings.workingOnlyTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (showWorkingOnly) {
                                    StatusWorkingFg
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                            if (!state.isLoadingFountains && state.fountains.isNotEmpty()) {
                                Text(
                                    text = if (showWorkingOnly) {
                                        strings.showingWorkingCount(
                                            displayedFountains.size,
                                            state.fountains.size
                                        )
                                    } else {
                                        strings.showingAllCount(state.fountains.size)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Switch(
                        checked = showWorkingOnly,
                        onCheckedChange = { showWorkingOnly = it },
                        modifier = Modifier.semantics {
                            contentDescription = strings.workingOnlyTitle
                        }
                    )
                }
            }

            if (state.isLoadingFountains) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(26.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = strings.loadingFountains,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }

            state.error?.let { error ->
                Surface(
                    color = StatusBrokenBg,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = StatusBrokenFg,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = error,
                            color = StatusBrokenFg,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            if (!state.isLoadingFountains && state.fountains.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = strings.noFountainsAvailable,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            } else if (!state.isLoadingFountains && displayedFountains.isEmpty() && showWorkingOnly) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = strings.noWorkingMatchFilter,
                            style = MaterialTheme.typography.titleSmall
                        )
                        OutlinedButton(
                            onClick = { showWorkingOnly = false },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(
                                text = strings.showAllFountains,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            // Fountain Cards List (every card opens FountainDetailScreen)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(
                    items = displayedFountains,
                    key = { fountain -> fountain.id }
                ) { fountain ->
                    val distance = state.fountainDistancesMeters[fountain.id]
                    val report = state.communityReportsByFountain[fountain.id]

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp)
                            .clickable(
                                role = Role.Button,
                                onClick = { onNavigateToDetail(fountain.id) }
                            )
                            .semantics {
                                contentDescription = fountain.name
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Title + Chevron
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = fountain.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                )

                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(30.dp)
                                )
                            }

                            // Prominent Distance Row
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = distance?.let { strings.distanceAway(formatDistance(it)) }
                                        ?: strings.distanceUnavailable,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            // High-Recognition Graphical Water Status Badge
                            LargeWaterStatusBadge(status = fountain.officialStatus)

                            // Community Report Box (when available)
                            if (report != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = strings.communityReportTitle,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = strings.formatAge(report.observedAt, state.now),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                                        LargeWaterStatusBadge(
                                            status = report.status,
                                            prefix = strings.reportedStatusPrefix,
                                            showSubtitle = false
                                        )

                                        report.lightObservation?.lightBand?.let { band ->
                                            LargeLightBandBadge(
                                                band = band,
                                                lux = report.lightObservation.ambientLux
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
    }
}

private data class StatusChipStyle(
    val icon: ImageVector,
    val label: String,
    val fg: Color,
    val bg: Color
)

@Composable
private fun StatusPill(
    icon: ImageVector,
    text: String,
    contentColor: Color,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(50),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatDistance(meters: Int): String = if (meters >= 1000) {
    String.format(Locale.getDefault(), "%.1f km", meters / 1000f)
} else {
    "$meters m"
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

@Preview(showBackground = true)
@Composable
private fun FountainListScreenOfflinePreview() {
    SensOasisTheme {
        FountainListScreen(
            state = SampleData.previewOfflineErrorState(),
            onAction = {},
            onNavigateToDetail = {}
        )
    }
}
