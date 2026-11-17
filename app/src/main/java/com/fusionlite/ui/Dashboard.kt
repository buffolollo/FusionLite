package com.fusionlite.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fusionlite.UiState
import com.fusionlite.data.FlowAllocation
import com.fusionlite.data.FlowMetrics
import com.fusionlite.data.StationMetrics
import com.fusionlite.data.allocation
import com.fusionlite.data.clean
import kotlinx.coroutines.delay

/**
 * Live home tab.
 *
 * Hierarchy: screen header → solar hero (current power + freshness) → four
 * today KPIs → energy flow → battery → session controls. Every interactive
 * element has a ≥48dp target, a visible focus ring and a screen-reader label.
 */
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
    val design = LocalFusionDesign.current
    val secondsAgo = if (state.lastUpdated == 0L) null
    else ((now - state.lastUpdated) / 1000L).coerceAtLeast(0)

    FusionPage(bottomPadding = design.navClearance) {
        item {
            FusionScreenHeader(
                eyebrow = "Live home",
                title = station?.name ?: "Solar plant",
                accent = palette.gold,
                trailing = {
                    FusionIconButton(
                        icon = FusionIcon.REFRESH,
                        contentDescription = if (state.loading) "Refreshing live data" else "Refresh live data",
                        onClick = onRefresh,
                        enabled = !state.loading,
                        tint = palette.gold,
                        container = palette.glass,
                        borderColor = palette.border,
                        spinning = state.loading,
                    )
                },
            )
        }

        item {
            HeroCard(
                stationName = station?.name ?: "Solar plant",
                metrics = metrics,
                secondsAgo = secondsAgo,
                loading = state.loading,
            )
        }

        if (state.stations.size > 1) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(design.spacingSm)) {
                    Text(
                        "SWITCH PLANT",
                        style = design.type.labelMicro,
                        color = palette.textSecondary,
                    )
                    Row(
                        Modifier
                            .horizontalScroll(rememberScrollState())
                            .selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(design.spacingSm),
                    ) {
                        state.stations.forEach { s ->
                            val name = s.name ?: s.dn ?: "Plant"
                            FusionChip(
                                label = name,
                                selected = s.dn == state.selectedDn,
                                accent = palette.gold,
                                onClick = { s.dn?.let(onSelectStation) },
                            )
                        }
                    }
                }
            }
        }

        state.error?.let { error ->
            item {
                FusionBanner(
                    tone = FusionTone.DANGER,
                    title = "Live data unavailable",
                    message = error,
                    actionLabel = "Retry",
                    onAction = onRefresh,
                )
            }
        }

        item {
            FusionSectionHeader(title = "Today", subtitle = "Energy recorded so far", accent = palette.gold)
        }

        item {
            FusionGrid(itemCount = 4, minItemWidth = 150.dp) { index, tileModifier ->
                when (index) {
                    0 -> FusionStatTile(
                        label = "Produced",
                        value = metrics.dailyEnergy?.clean(1) ?: "—",
                        unit = "kWh",
                        icon = FusionIcon.SUN,
                        tone = FusionTone.ACCENT,
                        caption = station?.plantStatus?.let { "Plant status: $it" },
                        loading = state.loading && metrics.dailyEnergy == null,
                        modifier = tileModifier,
                    )
                    1 -> FusionStatTile(
                        label = "Consumed",
                        // Same StatisticsSnapshot.periodConsumption as Analytics.
                        value = state.today?.periodConsumption?.clean(1) ?: "—",
                        unit = "kWh",
                        icon = FusionIcon.BOLT,
                        tone = FusionTone.INFO,
                        caption = if (state.todayLoading) "Updating…" else "Household load today",
                        loading = state.todayLoading && state.today == null,
                        modifier = tileModifier,
                    )
                    2 -> FusionStatTile(
                        label = "Battery",
                        value = (metrics.batterySoc ?: flow?.soc)?.clean(0) ?: "—",
                        unit = "%",
                        icon = FusionIcon.BATTERY,
                        tone = FusionTone.SUCCESS,
                        caption = "State of charge",
                        loading = state.loading && (metrics.batterySoc ?: flow?.soc) == null,
                        modifier = tileModifier,
                    )
                    else -> FusionStatTile(
                        label = "Capacity",
                        value = metrics.capacity?.clean(1) ?: "—",
                        unit = "kWp",
                        icon = FusionIcon.PANEL,
                        tone = FusionTone.NEUTRAL,
                        caption = "Installed DC capacity",
                        loading = state.loading && metrics.capacity == null,
                        modifier = tileModifier,
                    )
                }
            }
        }

        item {
            EnergyFlowCard(
                metrics = flow,
                error = state.flowError,
                loading = state.loading,
            )
        }

        item {
            BatteryCard(soc = metrics.batterySoc ?: flow?.soc, batteryPower = flow?.battery)
        }

        item {
            SessionCard(
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
) {
    val design = LocalFusionDesign.current
    val palette = LocalFusionPalette.current
    val shape = RoundedCornerShape(design.radiusHero)
    val stale = secondsAgo != null && secondsAgo >= 120
    val freshness = when {
        secondsAgo == null -> "Connecting"
        loading -> "Syncing"
        secondsAgo < 5 -> "Live"
        stale -> "Stale · ${secondsAgo / 60}m ago"
        else -> "Live · ${secondsAgo}s ago"
    }
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 248.dp)
            .clip(shape)
            .background(palette.surfaceHigh, shape)
            .border(1.dp, palette.border, shape)
    ) {
        SolarPanelScene(metrics.powerFraction, Modifier.matchParentSize())
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            palette.heroScrim.copy(alpha = 0.95f),
                            palette.heroScrim.copy(alpha = 0.60f),
                            Color.Transparent,
                        )
                    )
                )
        )
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, palette.heroScrim.copy(alpha = 0.72f))
                    )
                )
        )
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 248.dp)
                .padding(design.spacingXl),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "FUSIONLITE · LIVE",
                        style = design.type.labelMicro,
                        color = SolarCyan,
                    )
                    Text(
                        stationName,
                        style = design.type.heading,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                FusionStatusPill(
                    label = freshness,
                    tone = if (stale) FusionTone.WARNING else FusionTone.SUCCESS,
                    pulsing = !stale && !loading,
                    // The hero is always a dark scrim, so use the bright artwork
                    // accents rather than the darkened light-theme tokens.
                    colorOverride = if (stale) SolarAmber else SolarGreen,
                )
            }
            Column {
                Text(
                    "CURRENT POWER",
                    style = design.type.labelMicro,
                    color = SolarGold,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        metrics.currentPower?.clean(1) ?: "—",
                        style = design.type.display,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        " kW",
                        style = design.type.heading,
                        color = SolarGold,
                        modifier = Modifier.padding(bottom = 10.dp, start = 4.dp),
                    )
                }
                Text(
                    if (metrics.capacity != null) {
                        "${metrics.capacity.clean()} kWp installed · clears every 15–60 s"
                    } else {
                        "Waiting for the first live reading"
                    },
                    style = design.type.bodySmall,
                    color = Color.White.copy(alpha = 0.82f),
                )
            }
        }
    }
}

@Composable
private fun EnergyFlowCard(metrics: FlowMetrics?, error: String?, loading: Boolean) {
    val allocation = remember(metrics) { metrics?.allocation() }
    val design = LocalFusionDesign.current
    val palette = LocalFusionPalette.current
    FusionCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Energy flow", style = design.type.heading, color = palette.textPrimary)
                Text(
                    flowSubtitle(metrics, allocation),
                    style = design.type.bodySmall,
                    color = palette.textSecondary,
                )
            }
            if (loading) {
                FusionStatusPill(label = "Refreshing", tone = FusionTone.INFO, pulsing = true)
            }
        }
        if (metrics == null) {
            FusionEmptyState(
                icon = FusionIcon.BOLT,
                title = "Waiting for live flow",
                message = error ?: "The plant has not reported an energy-flow graph yet.",
            )
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .semantics {
                        contentDescription = flowDescription(metrics, allocation)
                    },
            ) {
                EnergyFlowDiagram(
                    metrics = metrics,
                    allocation = allocation,
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                )
            }
            FlowAllocationBar(metrics = metrics, allocation = allocation)
            if (error != null) {
                Text(
                    error,
                    style = design.type.bodySmall,
                    color = palette.danger,
                )
            }
        }
    }
}

private fun flowSubtitle(metrics: FlowMetrics?, allocation: FlowAllocation?): String {
    if (metrics == null || allocation == null) return "Live power routing"
    return when {
        allocation.gridExport > 0.05 ->
            "Exporting ${allocation.gridExport.clean(1)} kW to the grid"
        allocation.gridImport > 0.05 ->
            "Importing ${allocation.gridImport.clean(1)} kW from the grid"
        allocation.batteryCharge > 0.05 ->
            "Charging the battery at ${allocation.batteryCharge.clean(1)} kW"
        allocation.batteryDischarge > 0.05 ->
            "Battery supplying ${allocation.batteryDischarge.clean(1)} kW"
        (metrics.pv ?: 0.0) > 0.05 -> "Solar covering the load"
        else -> "Live power routing"
    }
}

/** Screen-reader narrative for the hand-drawn flow diagram. */
private fun flowDescription(metrics: FlowMetrics, allocation: FlowAllocation?): String {
    val parts = mutableListOf<String>()
    metrics.pv?.let { parts += "Solar ${it.clean(1)} kilowatts" }
    metrics.load?.let { parts += "Home ${it.clean(1)} kilowatts" }
    metrics.grid?.let {
        parts += if (allocation != null && allocation.gridImport > 0.05) {
            "Grid import ${allocation.gridImport.clean(1)} kilowatts"
        } else {
            "Grid export ${(allocation?.gridExport ?: kotlin.math.abs(it)).clean(1)} kilowatts"
        }
    }
    metrics.battery?.let {
        parts += if (it >= 0) "Battery charging ${it.clean(1)} kilowatts"
        else "Battery discharging ${(-it).clean(1)} kilowatts"
    }
    metrics.soc?.let { parts += "Battery at ${it.clean(0)} percent" }
    return if (parts.isEmpty()) "Live energy flow" else "Energy flow. " + parts.joinToString(". ")
}

@Composable
private fun BatteryCard(soc: Double?, batteryPower: Double?) {
    val design = LocalFusionDesign.current
    val palette = LocalFusionPalette.current
    val target = ((soc ?: 0.0) / 100.0).coerceIn(0.0, 1.0).toFloat()
    val animated by animateFloatAsState(
        target,
        tween(fusionDuration(900)),
        label = "soc",
    )
    val color = when {
        soc == null -> palette.textTertiary
        soc < 20 -> palette.danger
        soc < 60 -> palette.gold
        else -> palette.success
    }
    FusionCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Battery storage", style = design.type.heading, color = palette.textPrimary)
                Text(
                    batteryPower?.let {
                        when {
                            it > 0.05 -> "Charging at ${it.clean()} kW"
                            it < -0.05 -> "Discharging at ${(-it).clean()} kW"
                            else -> "Idle"
                        }
                    } ?: "No battery reported",
                    style = design.type.bodySmall,
                    color = palette.textSecondary,
                )
            }
            Text(
                soc?.let { "${it.clean(0)}%" } ?: "—",
                style = design.type.numeric,
                color = color,
                modifier = if (soc != null) {
                    Modifier
                        .progressSemantics((soc / 100.0).toFloat().coerceIn(0f, 1f))
                        .semantics {
                            contentDescription = "Battery state of charge ${soc.clean(0)} percent"
                        }
                } else {
                    Modifier
                },
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(CircleShape)
                .background(palette.surfaceSunken),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animated)
                    .height(12.dp)
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.60f), color))),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0%", style = design.type.bodySmall, color = palette.textTertiary)
            Text("100%", style = design.type.bodySmall, color = palette.textTertiary)
        }
    }
}

@Composable
private fun SessionCard(
    intervalSeconds: Int,
    onIntervalChange: (Int) -> Unit,
    diag: String?,
    onLogout: () -> Unit,
) {
    val design = LocalFusionDesign.current
    val palette = LocalFusionPalette.current
    var confirmSignOut by remember { mutableStateOf(false) }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out of FusionLite?", style = design.type.heading) },
            text = {
                Text(
                    "The saved session and encrypted password will be removed from this device.",
                    style = design.type.body,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmSignOut = false
                        onLogout()
                    },
                ) {
                    Text("Sign out", color = palette.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) {
                    Text("Stay signed in", color = palette.cyan)
                }
            },
            containerColor = palette.surfaceRaised,
            titleContentColor = palette.textPrimary,
            textContentColor = palette.textSecondary,
        )
    }
    FusionCard {
        Text("Session", style = design.type.heading, color = palette.textPrimary)
        Text(
            "How often live telemetry refreshes",
            style = design.type.bodySmall,
            color = palette.textSecondary,
        )
        FusionSegmentedControl(
            options = listOf(15, 30, 60),
            selected = intervalSeconds,
            label = { "${it}s" },
            onSelect = onIntervalChange,
            accent = palette.cyan,
        )
        if (diag != null) {
            Text(
                diag,
                style = design.type.bodySmall,
                color = palette.textTertiary,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            FusionButton(
                text = "Sign out",
                onClick = { confirmSignOut = true },
                variant = FusionButtonVariant.GHOST,
                icon = FusionIcon.ARROW_UP_RIGHT,
                accent = palette.danger,
            )
        }
    }
}
