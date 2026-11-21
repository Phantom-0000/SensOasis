package es.upm.sensoasis.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.upm.sensoasis.domain.model.AppLanguage
import es.upm.sensoasis.domain.model.ConnectionState
import es.upm.sensoasis.domain.model.FountainStatus
import es.upm.sensoasis.fake.SampleData
import es.upm.sensoasis.ui.components.LargeLightBandBadge
import es.upm.sensoasis.ui.components.LargeWaterStatusBadge
import es.upm.sensoasis.ui.i18n.AppFontScale
import es.upm.sensoasis.ui.i18n.LocalAppPreferences
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

/**
 * Calm, senior-first initial hub screen designed for adults >65 years old.
 * Presents only four focused elements:
 * 1. Trilingual switcher (EN / ES / CA) + Text Size toggle (A / A+)
 * 2. Live connection & location status bar
 * 3. Nearest Working Fountain quick-action hero card
 * 4. Full-width button to open the nearby fountains list
 */
@Composable
fun HomeScreen(
    state: SensOasisUiState,
    onAction: (UiAction) -> Unit,
    onOpenNearbyList: () -> Unit,
    onOpenFountainDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val prefs = LocalAppPreferences.current
    val strings = LocalAppStrings.current

    // Pick the nearest fountain whose effective status is WORKING (fallback to closest overall).
    val nearestWorking = state.fountains.firstOrNull { fountain ->
        val reported = state.communityReportsByFountain[fountain.id]
            ?.takeIf { !it.isExpired(state.now) }
            ?.status
            ?.takeIf { it != FountainStatus.UNKNOWN }
        (reported ?: fountain.officialStatus) == FountainStatus.WORKING
    }
    val featuredFountain = nearestWorking ?: state.fountains.firstOrNull()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Brand Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.size(50.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.WaterDrop,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "SensOasis",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = strings.appSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = {
                        onAction(UiAction.RefreshFountains)
                        onAction(UiAction.RefreshLocation)
                    },
                    enabled = !state.isLoadingFountains,
                    modifier = Modifier.size(48.dp)
                ) {
                    if (state.isLoadingFountains) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = strings.refresh,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            // 2. Language (EN | ES | CA) & Font Size (A | A+) Controls Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Language Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = strings.languageLabel,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AppLanguage.entries.forEach { lang ->
                                SegmentedOptionPill(
                                    label = lang.shortLabel,
                                    selected = prefs.language == lang,
                                    onClick = { prefs.updateLanguage(lang) }
                                )
                            }
                        }
                    }

                    // Font Size Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatSize,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = strings.textSizeLabel,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppFontScale.entries.forEach { scale ->
                                SegmentedOptionPill(
                                    label = scale.shortLabel,
                                    selected = prefs.fontScale == scale,
                                    onClick = { prefs.updateFontScale(scale) }
                                )
                            }
                        }
                    }
                }
            }

            // 3. Connection & Location Status Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val (syncIcon, syncLabel, syncFg, syncBg) = when (state.mqttState) {
                    ConnectionState.CONNECTED -> HomeBadgeConfig(
                        icon = Icons.Default.CloudDone,
                        text = strings.liveOnline,
                        fg = StatusWorkingFg,
                        bg = StatusWorkingBg
                    )
                    ConnectionState.CONNECTING -> HomeBadgeConfig(
                        icon = Icons.Default.CloudSync,
                        text = strings.liveConnecting,
                        fg = MaterialTheme.colorScheme.primary,
                        bg = MaterialTheme.colorScheme.primaryContainer
                    )
                    ConnectionState.DISCONNECTED -> HomeBadgeConfig(
                        icon = Icons.Default.CloudOff,
                        text = strings.liveOffline,
                        fg = StatusUnknownFg,
                        bg = StatusUnknownBg
                    )
                    ConnectionState.ERROR -> HomeBadgeConfig(
                        icon = Icons.Default.CloudOff,
                        text = strings.liveOffline,
                        fg = StatusBrokenFg,
                        bg = StatusBrokenBg
                    )
                }

                HomeStatusPill(
                    config = syncIcon.let { HomeBadgeConfig(it, syncLabel, syncFg, syncBg) },
                    modifier = Modifier.weight(1.15f)
                )

                HomeStatusPill(
                    config = HomeBadgeConfig(
                        icon = Icons.Default.LocationOn,
                        text = if (state.isFallbackLocation) strings.madridCenter else strings.gpsActive,
                        fg = MaterialTheme.colorScheme.onSecondaryContainer,
                        bg = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier.weight(0.85f)
                )
            }

            // 4. Hero Card: Nearest Working Fountain
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (nearestWorking != null) {
                            strings.nearestWorkingTitle
                        } else {
                            strings.nearestFallbackTitle
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    if (state.isLoadingFountains && featuredFountain == null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = strings.loadingFountains,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    } else if (featuredFountain == null) {
                        Text(
                            text = strings.noFountainsAvailable,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val distance = state.fountainDistancesMeters[featuredFountain.id]
                        val report = state.communityReportsByFountain[featuredFountain.id]
                            ?.takeIf { !it.isExpired(state.now) }
                        val effectiveStatus = report?.status?.takeIf { it != FountainStatus.UNKNOWN }
                            ?: featuredFountain.officialStatus

                        Text(
                            text = featuredFountain.name,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (featuredFountain.address.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = featuredFountain.address,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Large Prominent Distance Pill
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = distance?.let { strings.distanceAway(formatHomeDistance(it)) }
                                        ?: strings.distanceUnavailable,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        // High-Recognition Graphical Water Status Badge
                        LargeWaterStatusBadge(status = effectiveStatus)

                        // Optional Sunlight/Shade Graphical Badge from latest report
                        report?.lightObservation?.lightBand?.let { band ->
                            LargeLightBandBadge(
                                band = band,
                                lux = report.lightObservation.ambientLux
                            )
                        }

                        // Quick Senior Actions: 1-Tap Walk + Details/Voice
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    onAction(UiAction.SelectFountain(featuredFountain.id))
                                    onAction(UiAction.OpenWalkingRoute)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = StatusWorkingFg,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 56.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = strings.walkThere,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            OutlinedButton(
                                onClick = { onOpenFountainDetail(featuredFountain.id) },
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 56.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhoneInTalk,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = strings.detailsAndVoice,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 5. Full-Width Button to Browse All Nearby Fountains
            Button(
                onClick = onOpenNearbyList,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (state.fountains.isNotEmpty()) {
                        strings.browseNearbyFountains(state.fountains.size)
                    } else {
                        strings.browseNearbySimple
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}

private data class HomeBadgeConfig(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val text: String,
    val fg: Color,
    val bg: Color
)

@Composable
private fun HomeStatusPill(
    config: HomeBadgeConfig,
    modifier: Modifier = Modifier
) {
    Surface(
        color = config.bg,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.heightIn(min = 44.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = config.icon,
                contentDescription = null,
                tint = config.fg,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = config.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = config.fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SegmentedOptionPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clickable(role = Role.RadioButton, onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

private fun formatHomeDistance(meters: Int): String = if (meters >= 1000) {
    String.format(Locale.getDefault(), "%.1f km", meters / 1000f)
} else {
    "$meters m"
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    SensOasisTheme {
        HomeScreen(
            state = SampleData.previewListLoadedState(),
            onAction = {},
            onOpenNearbyList = {},
            onOpenFountainDetail = {}
        )
    }
}
