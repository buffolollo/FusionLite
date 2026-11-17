package com.fusionlite.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fusionlite.UiState
import com.fusionlite.data.SeriesPoint
import com.fusionlite.data.StatisticsPeriod
import com.fusionlite.data.StatisticsSnapshot
import com.fusionlite.data.canNavigateForward
import com.fusionlite.data.clean
import com.fusionlite.data.currentWindowLabel
import com.fusionlite.data.isCurrentWindow
import com.fusionlite.data.parseNum
import com.fusionlite.data.productionWindowTitle
import com.fusionlite.data.rangeLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class ChartMode(val label: String) {
    CURVE("Curve"),
    BARS("Bars"),
    MIX("Mix"),
}

/**
 * Historical production dashboard.
 *
 * Filters map 1:1 to the shipped app's StationKpiChartArgBean.StatDim codes
 * (2 = day, 4 = month, 5 = year, 6 = lifetime) and every number comes from
 * station-real-kpi + energy-balance (see data/Statistics.kt).
 */
@Composable
fun StatisticsScreen(
    state: UiState,
    onPeriod: (StatisticsPeriod) -> Unit,
    onShift: (Int) -> Unit,
    onRefresh: () -> Unit,
    onExpandChart: () -> Unit = {},
    onToday: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val snapshot = state.stats
    val station = state.stations.firstOrNull { it.dn == state.selectedDn } ?: state.stations.firstOrNull()
    val palette = LocalFusionPalette.current
    val design = LocalFusionDesign.current

    val production = remember(snapshot) { snapshot?.production().orEmpty() }
    val consumption = remember(snapshot) { snapshot?.consumption().orEmpty() }
    val gridExport = remember(snapshot) { snapshot?.gridExport().orEmpty() }
    val gridImport = remember(snapshot) { snapshot?.gridImport().orEmpty() }

    FusionPage(modifier = modifier, bottomPadding = design.navClearance) {
        item {
            FusionScreenHeader(
                eyebrow = "Production analytics",
                title = station?.name ?: "Solar plant",
                accent = palette.cyan,
                trailing = {
                    FusionIconButton(
                        icon = FusionIcon.REFRESH,
                        contentDescription = if (state.statsLoading) "Refreshing analytics" else "Refresh analytics",
                        onClick = onRefresh,
                        enabled = !state.statsLoading,
                        tint = palette.cyan,
                        borderColor = palette.border,
                        spinning = state.statsLoading,
                    )
                },
            )
        }

        item {
            PeriodSelector(selected = state.statsPeriod, onSelect = onPeriod)
        }

        item {
            PeriodNavigator(
                period = state.statsPeriod,
                anchorMillis = state.statsAnchor,
                onShift = onShift,
                onToday = onToday,
            )
        }

        state.statsError?.let { error ->
            item { StatsErrorBanner(error, onRefresh) }
        }

        item { HeroProduction(snapshot, state.statsPeriod, state.statsAnchor, state.statsLoading) }

        item {
            FusionGrid(itemCount = 3, minItemWidth = 140.dp) { index, tileModifier ->
                when (index) {
                    0 -> FusionStatTile(
                        label = "To grid",
                        value = snapshot?.periodExport?.clean(1) ?: "—",
                        unit = "kWh",
                        icon = FusionIcon.ARROW_UP_RIGHT,
                        tone = FusionTone.SUCCESS,
                        caption = "Exported",
                        loading = state.statsLoading && snapshot == null,
                        modifier = tileModifier,
                    )
                    1 -> FusionStatTile(
                        label = "Consumed",
                        value = snapshot?.periodConsumption?.clean(1) ?: "—",
                        unit = "kWh",
                        icon = FusionIcon.BOLT,
                        tone = FusionTone.INFO,
                        caption = "Household load",
                        loading = state.statsLoading && snapshot == null,
                        modifier = tileModifier,
                    )
                    else -> FusionStatTile(
                        label = "Self-use",
                        value = snapshot?.periodSelfUse?.clean(1) ?: "—",
                        unit = "kWh",
                        icon = FusionIcon.SUN,
                        tone = FusionTone.ACCENT,
                        caption = "Used on site",
                        loading = state.statsLoading && snapshot == null,
                        modifier = tileModifier,
                    )
                }
            }
        }

        item {
            ChartCard(
                production = production,
                consumption = consumption,
                snapshot = snapshot,
                period = state.statsPeriod,
                loading = state.statsLoading,
                onExpand = onExpandChart,
            )
        }

        item { MixCard(snapshot = snapshot, production = production) }

        item {
            BreakdownCard(
                snapshot = snapshot,
                production = production,
                consumption = consumption,
                gridExport = gridExport,
                gridImport = gridImport,
            )
        }

        item { HighlightsCard(snapshot, state.statsPeriod) }

        item {
            StatsFooter(
                snapshot = snapshot,
                lastUpdated = state.lastUpdated,
                diag = state.diag,
            )
        }
    }
}

@Composable
internal fun PeriodSelector(selected: StatisticsPeriod, onSelect: (StatisticsPeriod) -> Unit) {
    val palette = LocalFusionPalette.current
    FusionSegmentedControl(
        options = StatisticsPeriod.entries,
        selected = selected,
        label = { it.chip },
        onSelect = onSelect,
        accent = palette.cyan,
    )
}

@Composable
internal fun PeriodNavigator(
    period: StatisticsPeriod,
    anchorMillis: Long,
    onShift: (Int) -> Unit,
    onToday: (() -> Unit)? = null,
) {
    val palette = LocalFusionPalette.current
    val design = LocalFusionDesign.current
    val reduced = LocalFusionReducedMotion.current
    val unit = when (period) {
        StatisticsPeriod.DAY -> "day"
        StatisticsPeriod.MONTH -> "month"
        StatisticsPeriod.YEAR -> "year"
        StatisticsPeriod.TOTAL -> "window"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        verticalArrangement = Arrangement.spacedBy(design.spacingXs),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                rangeLabel(period, anchorMillis),
                modifier = Modifier
                    .weight(1f, fill = false)
                    .semantics { heading() },
                style = design.type.heading,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(design.spacingSm))
            if (period != StatisticsPeriod.TOTAL) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FusionIconButton(
                        icon = FusionIcon.CHEVRON_LEFT,
                        contentDescription = "Previous $unit",
                        onClick = { onShift(-1) },
                        tint = palette.cyan,
                        borderColor = palette.border,
                        iconSize = 20.dp,
                    )
                    Spacer(Modifier.width(design.spacingXs))
                    FusionIconButton(
                        icon = FusionIcon.CHEVRON_RIGHT,
                        contentDescription = "Next $unit",
                        onClick = { onShift(1) },
                        enabled = canNavigateForward(period, anchorMillis),
                        tint = palette.cyan,
                        borderColor = palette.border,
                        iconSize = 20.dp,
                    )
                }
            } else {
                Text(
                    "LIFECYCLE",
                    style = design.type.labelMicro,
                    color = palette.gold,
                )
            }
        }
        if (onToday != null) {
            AnimatedVisibility(
                visible = !isCurrentWindow(period, anchorMillis),
                enter = if (reduced) androidx.compose.animation.EnterTransition.None
                else fadeIn() + scaleIn(initialScale = 0.9f),
                exit = if (reduced) androidx.compose.animation.ExitTransition.None
                else fadeOut() + scaleOut(targetScale = 0.9f),
            ) {
                FusionTextAction(
                    text = "Jump to ${currentWindowLabel(period).lowercase()}",
                    onClick = onToday,
                    icon = FusionIcon.REFRESH,
                    accent = palette.cyan,
                )
            }
        }
    }
}

@Composable
internal fun StatsErrorBanner(message: String, onRetry: () -> Unit) {
    FusionBanner(
        tone = FusionTone.DANGER,
        title = "Analytics unavailable",
        message = message,
        actionLabel = "Retry",
        onAction = onRetry,
    )
}

@Composable
private fun HeroProduction(
    snapshot: StatisticsSnapshot?,
    period: StatisticsPeriod,
    anchorMillis: Long,
    loading: Boolean,
) {
    val palette = LocalFusionPalette.current
    val design = LocalFusionDesign.current
    val value = snapshot?.headlineProduction
    val subtitle = productionWindowTitle(period, anchorMillis)
    val shape = RoundedCornerShape(design.radiusHero)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        palette.cyan.copy(alpha = 0.16f),
                        palette.surfaceRaised,
                        palette.gold.copy(alpha = 0.10f),
                    )
                )
            )
            .border(1.dp, palette.cyan.copy(alpha = 0.26f), shape)
            .padding(design.spacingXl),
        verticalArrangement = Arrangement.spacedBy(design.spacingXs),
    ) {
        Text(
            subtitle.uppercase(),
            style = design.type.labelMicro,
            color = palette.cyan,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            if (loading && value == null) {
                FusionSkeleton(Modifier.width(150.dp).height(48.dp))
            } else {
                Text(
                    value?.clean(1) ?: "—",
                    style = design.type.display,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    " kWh",
                    style = design.type.heading,
                    color = palette.gold,
                    modifier = Modifier.padding(bottom = 9.dp, start = 4.dp),
                )
            }
        }
        Text(
            snapshot?.peak?.let { peak ->
                "Peak ${peak.value.clean(1)} ${if (period == StatisticsPeriod.DAY) "kW" else "kWh"} · ${peak.label}"
            } ?: if (loading) "Loading this window…" else "No buckets recorded for this window",
            style = design.type.bodySmall,
            color = palette.textSecondary,
        )
        snapshot?.balance?.totalProductPower?.let {
            Text(
                "Server total ${parseNum(it)?.clean(1) ?: "—"} kWh",
                style = design.type.bodySmall,
                color = palette.textTertiary,
            )
        }
    }
}

@Composable
private fun ChartCard(
    production: List<SeriesPoint>,
    consumption: List<SeriesPoint>,
    snapshot: StatisticsSnapshot?,
    period: StatisticsPeriod,
    loading: Boolean,
    onExpand: () -> Unit,
) {
    var mode by remember { mutableStateOf(ChartMode.CURVE) }
    // Evidence: PhotovoltaicStatisticsViewModel.getLeftUnit — the day series is
    // instantaneous power (kW); month/year buckets are energy (kWh).
    val chartUnit = if (period == StatisticsPeriod.DAY) "kW" else "kWh"
    val palette = LocalFusionPalette.current
    val design = LocalFusionDesign.current
    val peak = production.maxByOrNull { it.value }?.takeIf { it.value > 0.0 }
    val summary = if (production.isEmpty()) {
        "No production recorded for this window."
    } else {
        "Production chart, ${production.size} buckets in $chartUnit. " +
            "Highest ${peak?.value?.clean(1) ?: "0"} at ${peak?.label ?: "—"}."
    }

    FusionCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (period == StatisticsPeriod.DAY) "Power curve" else "Production profile",
                    style = design.type.heading,
                    color = palette.textPrimary,
                )
                Text(
                    "${production.size} buckets · $chartUnit",
                    style = design.type.bodySmall,
                    color = palette.textSecondary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(design.spacingSm)) {
                if (mode == ChartMode.CURVE) {
                    FusionIconButton(
                        icon = FusionIcon.EXPAND,
                        contentDescription = "Open chart in full screen",
                        onClick = onExpand,
                        tint = palette.cyan,
                        container = palette.glass,
                        borderColor = palette.border,
                        iconSize = 16.dp,
                    )
                }
            }
        }

        FusionSegmentedControl(
            options = ChartMode.entries,
            selected = mode,
            label = { it.label },
            onSelect = { mode = it },
            accent = palette.cyan,
        )

        when (mode) {
            ChartMode.CURVE -> {
                ProductionAreaChart(
                    points = production,
                    accent = palette.gold,
                    unit = chartUnit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .semantics { contentDescription = summary },
                    emptyLabel = if (loading) "Loading production…" else "No production recorded for this window",
                )
                Text(
                    summary,
                    style = design.type.bodySmall,
                    color = palette.textTertiary,
                )
            }

            ChartMode.BARS -> {
                GroupedEnergyBars(
                    primary = production,
                    secondary = consumption,
                    primaryColor = palette.gold,
                    secondaryColor = palette.cyan,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .semantics {
                            contentDescription = "Grouped bars comparing production and consumption " +
                                "across ${production.size} buckets in $chartUnit"
                        },
                    emptyLabel = if (loading) "Loading production…" else "No series available",
                )
                LegendRow(
                    listOf(
                        "Production" to palette.gold,
                        "Consumption" to palette.cyan,
                    )
                )
            }

            ChartMode.MIX -> {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val stacked = maxWidth < 360.dp
                    val donut: @Composable () -> Unit = {
                        EnergyDonut(
                            segments = donutSegments(snapshot),
                            modifier = Modifier.size(168.dp),
                            centerTitle = "Self-use",
                            centerValue = "${((snapshot?.selfConsumptionRate ?: 0.0) * 100).toInt()}%",
                            centerCaption = "of production",
                        )
                    }
                    val legend: @Composable () -> Unit = {
                        Column(verticalArrangement = Arrangement.spacedBy(design.spacingSm)) {
                            LegendRow(donutSegments(snapshot).map { it.label to it.color }, vertical = true)
                            Text(
                                "Self-sufficiency ${((snapshot?.selfSufficiency ?: 0.0) * 100).toInt()}%",
                                style = design.type.bodySmall,
                                color = palette.textSecondary,
                            )
                        }
                    }
                    if (stacked) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(design.spacingMd),
                        ) {
                            donut()
                            legend()
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            donut()
                            Spacer(Modifier.width(design.spacingLg))
                            Box(Modifier.weight(1f)) { legend() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendRow(items: List<Pair<String, Color>>, vertical: Boolean = false) {
    val design = LocalFusionDesign.current
    val palette = LocalFusionPalette.current
    if (vertical) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEach { (label, color) -> LegendDot(label, color) }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(design.spacingLg)) {
            items.forEach { (label, color) -> LegendDot(label, color) }
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    val design = LocalFusionDesign.current
    val palette = LocalFusionPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(6.dp))
        Text(label, style = design.type.bodySmall, color = palette.textSecondary)
    }
}

@Composable
private fun donutSegments(snapshot: StatisticsSnapshot?): List<DonutSegment> {
    if (snapshot == null) return emptyList()
    val palette = LocalFusionPalette.current
    return listOf(
        DonutSegment("Self-consumed", snapshot.periodSelfUse, palette.gold),
        DonutSegment("Exported", snapshot.periodExport, palette.green),
        DonutSegment("Charged", snapshot.charge, palette.cyan),
        DonutSegment("Imported", snapshot.periodImport, palette.blue),
    ).filter { it.value > 0.0 }
}

@Composable
private fun MixCard(snapshot: StatisticsSnapshot?, production: List<SeriesPoint>) {
    val palette = LocalFusionPalette.current
    val design = LocalFusionDesign.current
    FusionCard {
        Text("Energy balance", style = design.type.heading, color = palette.textPrimary)
        Text(
            "Where the selected window's energy went",
            style = design.type.bodySmall,
            color = palette.textSecondary,
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stacked = maxWidth < 360.dp
            val donut: @Composable () -> Unit = {
                EnergyDonut(
                    segments = donutSegments(snapshot),
                    modifier = Modifier.size(150.dp),
                    centerTitle = "Self-use",
                    centerValue = "${((snapshot?.selfConsumptionRate ?: 0.0) * 100).toInt()}%",
                    centerCaption = "of production",
                )
            }
            val detail: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(design.spacingSm)) {
                    RatioGauge(
                        ratio = (snapshot?.selfSufficiency ?: 0.0).toFloat(),
                        color = palette.green,
                        caption = "self-use",
                        modifier = Modifier.size(72.dp),
                    )
                    Text(
                        if (production.isEmpty()) {
                            "No production buckets in this window."
                        } else {
                            "${snapshot?.periodSelfUse?.clean(1) ?: "0"} kWh used on site, " +
                                "${snapshot?.periodExport?.clean(1) ?: "0"} kWh exported."
                        },
                        style = design.type.bodySmall,
                        color = palette.textSecondary,
                    )
                }
            }
            if (stacked) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(design.spacingMd),
                ) {
                    donut()
                    detail()
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    donut()
                    Spacer(Modifier.width(design.spacingLg))
                    Box(Modifier.weight(1f)) { detail() }
                }
            }
        }
    }
}

@Composable
private fun BreakdownCard(
    snapshot: StatisticsSnapshot?,
    production: List<SeriesPoint>,
    consumption: List<SeriesPoint>,
    gridExport: List<SeriesPoint>,
    gridImport: List<SeriesPoint>,
) {
    val palette = LocalFusionPalette.current
    val design = LocalFusionDesign.current
    val rows = listOf(
        Triple("PV production", snapshot?.periodProduction ?: 0.0, palette.gold),
        Triple("Consumption", snapshot?.periodConsumption ?: 0.0, palette.cyan),
        Triple("Exported to grid", snapshot?.periodExport ?: 0.0, palette.green),
        Triple("Imported from grid", snapshot?.periodImport ?: 0.0, palette.blue),
        Triple("Battery charged", snapshot?.charge ?: 0.0, palette.batteryCharge),
        Triple("Battery discharged", snapshot?.discharge ?: 0.0, palette.batteryDischarge),
    )
    val maxValue = rows.maxOfOrNull { it.second } ?: 0.0
    FusionCard {
        Text("Breakdown", style = design.type.heading, color = palette.textPrimary)
        Text(
            "Totals for the selected window",
            style = design.type.bodySmall,
            color = palette.textSecondary,
        )
        rows.forEach { (label, value, color) ->
            MetricBar(
                label = label,
                value = value,
                fraction = if (maxValue > 0.0) (value / maxValue).toFloat() else 0f,
                color = color,
            )
        }
        FusionDivider()
        FusionGrid(itemCount = 4, minItemWidth = 140.dp, maxColumns = 4) { index, chipModifier ->
            when (index) {
                0 -> BucketChip("PV buckets", production.size, chipModifier)
                1 -> BucketChip("Load buckets", consumption.size, chipModifier)
                2 -> BucketChip("Export buckets", gridExport.count { it.value > 0.0 }, chipModifier)
                else -> BucketChip("Import buckets", gridImport.count { it.value > 0.0 }, chipModifier)
            }
        }
    }
}

@Composable
private fun BucketChip(label: String, count: Int, modifier: Modifier = Modifier) {
    val design = LocalFusionDesign.current
    val palette = LocalFusionPalette.current
    Column(modifier) {
        Text(
            count.toString(),
            style = design.type.numericSmall,
            color = palette.textPrimary,
        )
        Text(
            label,
            style = design.type.bodySmall,
            color = palette.textSecondary,
        )
    }
}

@Composable
private fun HighlightsCard(snapshot: StatisticsSnapshot?, period: StatisticsPeriod) {
    val palette = LocalFusionPalette.current
    val design = LocalFusionDesign.current
    FusionCard {
        Text("Highlights", style = design.type.heading, color = palette.textPrimary)
        FusionGrid(itemCount = 4, minItemWidth = 140.dp) { index, tileModifier ->
            when (index) {
                0 -> HighlightTile(
                    label = if (period == StatisticsPeriod.DAY) "PEAK POWER" else "BEST BUCKET",
                    value = snapshot?.peak?.value?.clean(1) ?: "—",
                    caption = snapshot?.peak?.let {
                        if (period == StatisticsPeriod.DAY) "${it.label} · kW" else it.label
                    } ?: "no data",
                    accent = palette.gold,
                    modifier = tileModifier,
                )
                1 -> HighlightTile(
                    label = "AVERAGE",
                    value = snapshot?.average?.clean(1) ?: "—",
                    caption = "per active bucket",
                    accent = palette.cyan,
                    modifier = tileModifier,
                )
                2 -> HighlightTile(
                    label = "BATTERY FLOW",
                    value = snapshot?.batteryCycles?.clean(1) ?: "—",
                    caption = "kWh cycled",
                    accent = palette.batteryCharge,
                    modifier = tileModifier,
                )
                else -> HighlightTile(
                    label = "SELF-SUFFICIENCY",
                    value = "${((snapshot?.selfSufficiency ?: 0.0) * 100).toInt()}%",
                    caption = "of consumption covered",
                    accent = palette.green,
                    modifier = tileModifier,
                )
            }
        }

        snapshot?.kpi?.let { kpi ->
            FusionDivider()
            FusionGrid(itemCount = 4, minItemWidth = 130.dp, maxColumns = 4) { index, metricModifier ->
                when (index) {
                    0 -> FusionInlineMetric(
                        label = "Day",
                        value = parseNum(kpi.dailyEnergy)?.clean(1) ?: "—",
                        modifier = metricModifier,
                    )
                    1 -> FusionInlineMetric(
                        label = "Month",
                        value = parseNum(kpi.monthEnergy)?.clean(1) ?: "—",
                        modifier = metricModifier,
                    )
                    2 -> FusionInlineMetric(
                        label = "Year",
                        value = parseNum(kpi.yearEnergy)?.clean(1) ?: "—",
                        modifier = metricModifier,
                    )
                    else -> FusionInlineMetric(
                        label = "Lifetime",
                        value = parseNum(kpi.cumulativeEnergy)?.clean(1) ?: "—",
                        modifier = metricModifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun HighlightTile(
    label: String,
    value: String,
    caption: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val design = LocalFusionDesign.current
    val palette = LocalFusionPalette.current
    Column(
        modifier
            .clip(RoundedCornerShape(design.radiusMd))
            .background(accent.copy(alpha = 0.08f))
            .border(1.dp, accent.copy(alpha = 0.20f), RoundedCornerShape(design.radiusMd))
            .padding(design.spacingMd),
    ) {
        Text(
            label,
            style = design.type.labelMicro,
            color = accent,
        )
        Spacer(Modifier.height(3.dp))
        Text(value, style = design.type.numericSmall, color = palette.textPrimary)
        Text(
            caption,
            style = design.type.bodySmall,
            color = palette.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatsFooter(
    snapshot: StatisticsSnapshot?,
    lastUpdated: Long,
    diag: String?,
) {
    val design = LocalFusionDesign.current
    val palette = LocalFusionPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!snapshot?.warnings.isNullOrEmpty()) {
            Text(
                snapshot!!.warnings.joinToString(" · "),
                style = design.type.bodySmall,
                color = palette.danger,
            )
        }
        diag?.let {
            Text(it, style = design.type.bodySmall, color = palette.textTertiary)
        }
        Text(
            if (lastUpdated > 0) {
                "Updated ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(lastUpdated))}" +
                    " · ${snapshot?.rangeLabel ?: ""}"
            } else {
                "Waiting for first statistics update"
            },
            style = design.type.bodySmall,
            color = palette.textTertiary,
        )
    }
}

/**
 * Full-screen, landscape-oriented presentation of the production/power curve.
 * Entered from the expand glyph on [ChartCard]; the host activity forces sensor
 * landscape and hides the system bars while this composable is shown.
 */
@Composable
fun FullscreenChartScreen(
    snapshot: StatisticsSnapshot?,
    period: StatisticsPeriod,
    loading: Boolean,
    stationName: String,
    range: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalFusionPalette.current
    val design = LocalFusionDesign.current
    val production = remember(snapshot) { snapshot?.production().orEmpty() }
    val chartUnit = if (period == StatisticsPeriod.DAY) "kW" else "kWh"

    Column(
        modifier
            .fillMaxSize()
            .background(palette.surfaceBase)
            .displayCutoutPadding()
            .padding(horizontal = design.spacingXl, vertical = design.spacingLg),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (period == StatisticsPeriod.DAY) "POWER CURVE" else "PRODUCTION PROFILE",
                    style = design.type.labelMicro,
                    color = palette.cyan,
                )
                Text(
                    "$stationName · $range",
                    style = design.type.heading,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${production.size} buckets · $chartUnit",
                    style = design.type.bodySmall,
                    color = palette.textSecondary,
                )
            }
            FusionButton(
                text = "Close",
                onClick = onClose,
                variant = FusionButtonVariant.SECONDARY,
                icon = FusionIcon.CLOSE,
            )
        }

        Spacer(Modifier.height(design.spacingMd))
        ProductionAreaChart(
            points = production,
            accent = palette.gold,
            unit = chartUnit,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .semantics {
                    contentDescription = "Full screen production curve with ${production.size} buckets"
                },
            emptyLabel = if (loading) "Loading production…" else "No production recorded for this window",
        )
        Spacer(Modifier.height(design.spacingSm))
        Text(
            "Tap the curve to inspect a bucket · rotate back or tap Close to return",
            style = design.type.bodySmall,
            color = palette.textTertiary,
        )
    }
}
