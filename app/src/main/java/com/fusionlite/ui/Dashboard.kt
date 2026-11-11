package com.fusionlite.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fusionlite.UiState
import com.fusionlite.data.FlowAllocation
import com.fusionlite.data.FlowMetrics
import com.fusionlite.data.GridDirection
import com.fusionlite.data.StationMetrics
import com.fusionlite.data.allocation
import com.fusionlite.data.clean
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun DashboardScreen(
    state: UiState,
    onRefresh: () -> Unit,
    onSelectStation: (String) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onLogout: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val station = state.stations.firstOrNull { it.dn == state.selectedDn } ?: state.stations.firstOrNull()
    val metrics = StationMetrics.from(station)
    val flow = state.flowMetrics
    val palette = LocalFusionPalette.current
    val secondsAgo = if (state.lastUpdated == 0L) null
    else ((now - state.lastUpdated) / 1000L).coerceAtLeast(0)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 118.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { HeroCard(station?.name ?: "Solar plant", metrics, secondsAgo, state.loading, onRefresh) }

        if (state.stations.size > 1) {
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.stations.forEach { s ->
                        FilterChip(
                            selected = s.dn == state.selectedDn,
                            onClick = { s.dn?.let(onSelectStation) },
                            label = {
                                Text(
                                    s.name ?: s.dn ?: "Plant",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = palette.gold.copy(alpha = 0.18f),
                                selectedLabelColor = palette.gold,
                            ),
                        )
                    }
                }
            }
        }

        state.error?.let { error ->
            item { ErrorBanner(error, onRetry = onRefresh) }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KpiTile(
                    icon = "☀", label = "Today", value = metrics.dailyEnergy, unit = "kWh",
                    accent = palette.gold, modifier = Modifier.weight(1f),
                )
                KpiTile(
                    icon = "⚡", label = "Total", value = metrics.cumulativeEnergy, unit = "kWh",
                    accent = palette.cyan, modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KpiTile(
                    icon = "🔋", label = "Battery", value = metrics.batterySoc ?: flow?.soc, unit = "%",
                    accent = palette.green, modifier = Modifier.weight(1f),
                )
                KpiTile(
                    icon = "🏗", label = "Capacity", value = metrics.capacity, unit = "kWp",
                    accent = palette.blue, modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            EnergyFlowCard(
                metrics = flow,
                error = state.flowError,
                loading = state.loading,
            )
        }

        item { BatteryCard(soc = metrics.batterySoc ?: flow?.soc, batteryPower = flow?.battery) }

        item {
            AutoRefreshRow(
                intervalSeconds = state.refreshIntervalSeconds,
                onIntervalChange = onIntervalChange,
                diag = state.diag,
                onLogout = onLogout,
            )
        }
    }
}

@Composable
private fun HeroCard(
    stationName: String,
    metrics: StationMetrics,
    secondsAgo: Long?,
    loading: Boolean,
    onRefresh: () -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    val palette = LocalFusionPalette.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(238.dp)
            .clip(shape)
            .border(1.dp, SolarCyan.copy(alpha = 0.20f), shape)
    ) {
        SolarPanelScene(metrics.powerFraction, Modifier.matchParentSize())
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            palette.heroScrim.copy(alpha = 0.94f),
                            palette.heroScrim.copy(alpha = 0.55f),
                            Color.Transparent,
                        )
                    )
                )
        )
        Column(
            Modifier
                .matchParentSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "FUSIONSOLAR",
                        fontSize = 10.sp,
                        letterSpacing = 2.sp,
                        color = SolarCyan,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stationName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LivePill(secondsAgo)
                    Spacer(Modifier.width(6.dp))
                    IconButton(onClick = onRefresh, enabled = !loading) {
                        RefreshGlyph(spinning = loading)
                    }
                }
            }
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        metrics.currentPower?.clean() ?: "—",
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = (-1).sp,
                    )
                    Text(
                        " kW",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = SolarGold,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
                Text(
                    if (metrics.capacity != null) {
                        "Live production · ${metrics.capacity.clean()} kWp installed"
                    } else {
                        "Live production"
                    },
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.68f),
                )
            }
        }
    }
}

@Composable
private fun LivePill(secondsAgo: Long?) {
    val infinite = rememberInfiniteTransition(label = "live")
    val alpha by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "dot",
    )
    Row(
        Modifier
            .background(SolarGreen.copy(alpha = 0.13f), RoundedCornerShape(50))
            .border(1.dp, SolarGreen.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(7.dp)
                .background(SolarGreen.copy(alpha = alpha), CircleShape)
        )
        Text(
            text = secondsAgo?.let { if (it < 5) "LIVE" else "${it}s" } ?: "SYNC",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = SolarGreen,
        )
    }
}

@Composable
private fun RefreshGlyph(spinning: Boolean) {
    val infinite = rememberInfiniteTransition(label = "spin")
    val angle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = androidx.compose.animation.core.LinearEasing)),
        label = "angle",
    )
    Text(
        "⟳",
        fontSize = 24.sp,
        color = SolarGold,
        modifier = if (spinning) Modifier.rotate(angle) else Modifier,
    )
}

@Composable
private fun KpiTile(
    icon: String,
    label: String,
    value: Double?,
    unit: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, fontSize = 15.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    label.uppercase(),
                    fontSize = 10.sp,
                    letterSpacing = 1.4.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value?.clean() ?: "—",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
                Text(
                    " $unit",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun EnergyFlowCard(metrics: FlowMetrics?, error: String?, loading: Boolean) {
    val allocation = remember(metrics) { metrics?.allocation() }
    val palette = LocalFusionPalette.current
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Energy flow", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Live power routing",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (loading) {
                    Text("refreshing…", fontSize = 10.sp, color = palette.cyan)
                }
            }
            Spacer(Modifier.height(10.dp))
            if (metrics == null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(190.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        error ?: "Waiting for live energy flow…",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                GridSummary(allocation)
                Spacer(Modifier.height(6.dp))
                EnergyFlowDiagram(
                    metrics = metrics,
                    allocation = allocation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(258.dp),
                )
                SelfUseBar(metrics, allocation)
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun GridSummary(allocation: FlowAllocation?) {
    val palette = LocalFusionPalette.current
    val direction = allocation?.gridDirection ?: GridDirection.IDLE
    val color = when (direction) {
        GridDirection.IMPORT -> palette.amber
        GridDirection.EXPORT -> palette.green
        GridDirection.IDLE -> palette.blue
    }
    val title = when (direction) {
        GridDirection.IMPORT -> "Drawing from the grid"
        GridDirection.EXPORT -> "Feeding the grid"
        GridDirection.IDLE -> "Grid idle"
    }
    val value = when (direction) {
        GridDirection.IMPORT -> allocation?.gridImport ?: 0.0
        GridDirection.EXPORT -> allocation?.gridExport ?: 0.0
        GridDirection.IDLE -> 0.0
    }
    val detail = when (direction) {
        GridDirection.IMPORT -> "The grid is covering the shortfall right now."
        GridDirection.EXPORT -> "Surplus solar is being exported right now."
        GridDirection.IDLE -> "No import or export at this moment."
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.10f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.30f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                when (direction) {
                    GridDirection.IMPORT -> "↓"
                    GridDirection.EXPORT -> "↑"
                    GridDirection.IDLE -> "•"
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
                Text(
                    detail,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "${value.clean(1)} kW",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
    }
}

@Composable
private fun SelfUseBar(metrics: FlowMetrics, allocation: FlowAllocation?) {
    val palette = LocalFusionPalette.current
    val pv = metrics.pv ?: 0.0
    if (pv <= 0.02 || allocation == null) return
    val ratio = (allocation.selfConsumption / pv).coerceIn(0.0, 1.0)
    val animated by animateFloatAsState(ratio.toFloat(), tween(900), label = "self-use")
    val detail = buildString {
        append("${allocation.selfConsumption.clean(1)} kW used on site")
        when {
            allocation.gridExport > 0.02 -> append(" · ${allocation.gridExport.clean(1)} kW exported")
            allocation.gridImport > 0.02 -> append(" · ${allocation.gridImport.clean(1)} kW imported")
        }
    }
    Column(Modifier.padding(top = 2.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "SELF-CONSUMPTION",
                fontSize = 9.sp,
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${(ratio * 100).toInt()}%",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = palette.gold,
            )
        }
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animated)
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(palette.gold, palette.green))),
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            detail,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BatteryCard(soc: Double?, batteryPower: Double?) {
    val palette = LocalFusionPalette.current
    val target = ((soc ?: 0.0) / 100.0).coerceIn(0.0, 1.0).toFloat()
    val animated by animateFloatAsState(target, tween(900), label = "soc")
    val color = when {
        soc == null -> MaterialTheme.colorScheme.onSurfaceVariant
        soc < 20 -> Color(0xFFFF7A7A)
        soc < 60 -> palette.gold
        else -> palette.green
    }
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Battery storage", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        batteryPower?.let {
                            when {
                                it > 0.05 -> "Charging at ${it.clean()} kW"
                                it < -0.05 -> "Discharging at ${(-it).clean()} kW"
                                else -> "Idle"
                            }
                        } ?: "No battery reported",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    soc?.let { "${it.clean(0)}%" } ?: "—",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(animated)
                        .height(14.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.horizontalGradient(
                                listOf(color.copy(alpha = 0.65f), color)
                            )
                        ),
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0%", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("100%", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AutoRefreshRow(
    intervalSeconds: Int,
    onIntervalChange: (Int) -> Unit,
    diag: String?,
    onLogout: () -> Unit,
) {
    val palette = LocalFusionPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "AUTO REFRESH",
                fontSize = 10.sp,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(15, 30, 60).forEach { seconds ->
                    val selected = intervalSeconds == seconds
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (selected) palette.cyan.copy(alpha = 0.18f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .border(
                                1.dp,
                                if (selected) palette.cyan.copy(alpha = 0.6f) else Color.Transparent,
                                RoundedCornerShape(50),
                            )
                            .clickable { onIntervalChange(seconds) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            "${seconds}s",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) palette.cyan else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        diag?.let {
            Text(it, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
        TextButton(onClick = onLogout, modifier = Modifier.align(Alignment.End)) {
            Text("Sign out", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(message, fontSize = 12.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
            TextButton(onClick = onRetry) {
                Text("RETRY", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
