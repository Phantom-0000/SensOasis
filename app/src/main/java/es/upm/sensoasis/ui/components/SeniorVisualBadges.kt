package es.upm.sensoasis.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.FormatColorReset
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import es.upm.sensoasis.domain.model.Coordinates
import es.upm.sensoasis.domain.model.FountainStatus
import es.upm.sensoasis.domain.model.LightBand
import es.upm.sensoasis.domain.model.formatLux
import es.upm.sensoasis.ui.i18n.LocalAppStrings
import es.upm.sensoasis.ui.theme.LightNightBg
import es.upm.sensoasis.ui.theme.LightNightFg
import es.upm.sensoasis.ui.theme.LightShadeBg
import es.upm.sensoasis.ui.theme.LightShadeFg
import es.upm.sensoasis.ui.theme.LightSunBg
import es.upm.sensoasis.ui.theme.LightSunFg
import es.upm.sensoasis.ui.theme.OasisPrimary
import es.upm.sensoasis.ui.theme.StatusBrokenBg
import es.upm.sensoasis.ui.theme.StatusBrokenFg
import es.upm.sensoasis.ui.theme.StatusUnknownBg
import es.upm.sensoasis.ui.theme.StatusUnknownFg
import es.upm.sensoasis.ui.theme.StatusWorkingBg
import es.upm.sensoasis.ui.theme.StatusWorkingFg
import kotlin.math.roundToInt

/**
 * Large, unmistakable graphical badge for fountain water availability.
 * Uses bold silhouettes (WaterDrop vs Crossed-Out WaterDrop vs Question Mark)
 * inside a high-contrast 48dp icon tile so older adults understand status without reading text.
 */
@Composable
fun LargeWaterStatusBadge(
    status: FountainStatus,
    prefix: String? = null,
    showSubtitle: Boolean = true,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val icon: ImageVector
    val label: String
    val subtitle: String
    val fgColor: Color
    val bgColor: Color

    when (status) {
        FountainStatus.WORKING -> {
            icon = Icons.Default.WaterDrop
            label = strings.statusWorking
            subtitle = strings.statusWorkingDesc
            fgColor = StatusWorkingFg
            bgColor = StatusWorkingBg
        }
        FountainStatus.NOT_WORKING -> {
            icon = Icons.Default.FormatColorReset
            label = strings.statusNotWorking
            subtitle = strings.statusNotWorkingDesc
            fgColor = StatusBrokenFg
            bgColor = StatusBrokenBg
        }
        FountainStatus.UNKNOWN -> {
            icon = Icons.AutoMirrored.Filled.HelpOutline
            label = strings.statusUnknown
            subtitle = strings.statusUnknownDesc
            fgColor = StatusUnknownFg
            bgColor = StatusUnknownBg
        }
    }

    val titleText = if (prefix != null) "$prefix: $label" else label

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, fgColor.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = fgColor,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = fgColor
                )
                if (showSubtitle) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = fgColor.copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}

/**
 * High-recognition graphical badge for outdoor sunlight vs shade conditions
 * (Sun ☀️ / Shade Tree 🌳 / Crescent Moon 🌙).
 */
@Composable
fun LargeLightBandBadge(
    band: LightBand,
    lux: Float? = null,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val icon: ImageVector
    val title: String
    val desc: String
    val fgColor: Color
    val bgColor: Color

    when (band) {
        LightBand.BRIGHT -> {
            icon = Icons.Default.WbSunny
            title = strings.lightBrightTitle
            desc = strings.lightBrightDesc
            fgColor = LightSunFg
            bgColor = LightSunBg
        }
        LightBand.MODERATE -> {
            icon = Icons.Default.Park
            title = strings.lightModerateTitle
            desc = strings.lightModerateDesc
            fgColor = LightShadeFg
            bgColor = LightShadeBg
        }
        LightBand.LOW -> {
            icon = Icons.Default.NightsStay
            title = strings.lightLowTitle
            desc = strings.lightLowDesc
            fgColor = LightNightFg
            bgColor = LightNightBg
        }
    }

    val headerText = if (lux != null) "$title · ${formatLux(lux)}" else title

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, fgColor.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = fgColor,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = headerText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = fgColor
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = fgColor.copy(alpha = 0.9f)
                )
            }
        }
    }
}

/**
 * Real hourly outdoor temperature curve (08h–20h Madrid time) fetched from Open-Meteo
 * (`https://api.open-meteo.com/v1/forecast?...&hourly=temperature_2m`) and persisted locally
 * in SharedPreferences so the cached curve remains visible even when the phone is offline.
 */
@Composable
fun MadridHourlyTempChartCard(
    coordinates: Coordinates,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val gridKey = remember(coordinates) { HourlyWeatherLoader.gridKey(coordinates) }

    var snapshot by remember(gridKey) {
        mutableStateOf(HourlyWeatherLoader.readCached(context, coordinates))
    }
    var isFetching by remember(gridKey) { mutableStateOf(snapshot == null) }

    LaunchedEffect(gridKey) {
        isFetching = snapshot == null
        val loaded = HourlyWeatherLoader.loadOrFetch(context, coordinates)
        if (loaded != null) {
            snapshot = loaded
        }
        isFetching = false
    }

    val modelProducer = remember { CartesianChartModelProducer() }
    val currentSnapshot = snapshot

    LaunchedEffect(currentSnapshot) {
        if (currentSnapshot != null && currentSnapshot.hours.isNotEmpty()) {
            modelProducer.runTransaction {
                lineSeries {
                    series(
                        x = currentSnapshot.hours,
                        y = currentSnapshot.tempsCelsius
                    )
                }
            }
        }
    }

    val bottomFormatter = remember {
        CartesianValueFormatter { _, value, _ -> "${value.roundToInt()}h" }
    }
    val startFormatter = remember {
        CartesianValueFormatter { _, value, _ -> "${value.roundToInt()}°C" }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
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
            // Card Header + Live/Cached Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = LightSunBg,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.DeviceThermostat,
                            contentDescription = null,
                            tint = LightSunFg,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.chartTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = strings.chartSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (currentSnapshot != null) {
                    val badgeBg = if (currentSnapshot.isOfflineCached) {
                        StatusUnknownBg
                    } else {
                        StatusWorkingBg
                    }
                    val badgeFg = if (currentSnapshot.isOfflineCached) {
                        StatusUnknownFg
                    } else {
                        StatusWorkingFg
                    }
                    Surface(
                        color = badgeBg,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = if (currentSnapshot.isOfflineCached) {
                                strings.chartBadgeCached(currentSnapshot.dateLabel)
                            } else {
                                strings.chartBadgeLive(currentSnapshot.dateLabel)
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeFg,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (currentSnapshot != null) {
                val maxIndex = currentSnapshot.tempsCelsius.indices.maxByOrNull {
                    currentSnapshot.tempsCelsius[it]
                } ?: 0
                val maxTemp = currentSnapshot.tempsCelsius[maxIndex].roundToInt()
                val peakHour = currentSnapshot.hours[maxIndex]
                val morningTemp = currentSnapshot.tempsCelsius.first().roundToInt()
                val isHotDay = maxTemp >= 30
                val curveColor = if (isHotDay) StatusBrokenFg else OasisPrimary

                ProvideVicoTheme(rememberM3VicoTheme()) {
                    val tempLine = LineCartesianLayer.rememberLine(
                        fill = LineCartesianLayer.LineFill.single(fill(curveColor)),
                        pointConnector = remember {
                            LineCartesianLayer.PointConnector.cubic(curvature = 0.45f)
                        }
                    )

                    CartesianChartHost(
                        chart = rememberCartesianChart(
                            rememberLineCartesianLayer(
                                lineProvider = LineCartesianLayer.LineProvider.series(tempLine),
                                rangeProvider = remember { CartesianLayerRangeProvider.auto() }
                            ),
                            startAxis = VerticalAxis.rememberStart(
                                valueFormatter = startFormatter
                            ),
                            bottomAxis = HorizontalAxis.rememberBottom(
                                valueFormatter = bottomFormatter
                            )
                        ),
                        modelProducer = modelProducer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(185.dp)
                    )
                }

                // Senior Hydration & Peak-Hour Takeaway
                Surface(
                    color = if (isHotDay) StatusBrokenBg else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isHotDay) {
                            strings.chartHeatAlert(maxTemp, peakHour)
                        } else {
                            strings.chartPeakSummary(maxTemp, peakHour, morningTemp)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isHotDay) StatusBrokenFg else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            } else if (isFetching) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = strings.chartNoCacheYet,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}
