package com.fusionlite.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fusionlite.UiState
import com.fusionlite.data.SeriesPoint
import com.fusionlite.data.StatisticsPeriod
import com.fusionlite.data.StatisticsSnapshot
import com.fusionlite.data.canNavigateForward
import com.fusionlite.data.clean
import com.fusionlite.data.parseNum
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
    modifier: Modifier = Modifier,
) {
    val snapshot = state.stats
    val station = state.stations.firstOrNull { it.dn == state.selectedDn } ?: state.stations.firstOrNull()

    val production = remember(snapshot) { snapshot?.production().orEmpty() }
    val consumption = remember(snapshot) { snapshot?.consumption().orEmpty() }
    val gridExport = remember(snapshot) { snapshot?.gridExport().orEmpty() }
    val gridImport = remember(snapshot) { snapshot?.gridImport().orEmpty() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 118.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            StatisticsHeader(
                stationName = station?.name ?: "Solar plant",
                loading = state.statsLoading,
                onRefresh = onRefresh,
            )
        }

        item { PeriodSelector(selected = state.statsPeriod, onSelect = onPeriod) }

        item {
            PeriodNavigator(
                period = state.statsPeriod,
                anchorMillis = state.statsAnchor,
                onShift = onShift,
            )
        }

        state.statsError?.let { error ->
            item { StatsErrorBanner(error, onRefresh) }
        }

        item { HeroProduction(snapshot, state.statsPeriod, state.statsLoading) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniStat(
                    label = "To grid",
                    value = snapshot?.periodExport,
                    unit = "kWh",
                    accent = SolarGreen,
                    modifier = Modifier.weight(1f),
                )
                MiniStat(
                    label = "Consumed",
                    value = snapshot?.periodConsumption,
                    unit = "kWh",
                    accent = SolarCyan,
                    modifier = Modifier.weight(1f),
                )
                MiniStat(
                    label = "Self-use",
                    value = snapshot?.periodSelfUse,
                    unit = "kWh",
                    accent = SolarGold,
                    modifier = Modifier.weight(1f),
                )
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

        item {
            MixCard(snapshot = snapshot, production = production)
        }

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
                onRefresh = onRefresh,
            )
        }
    }
}

@Composable
private fun StatisticsHeader(stationName: String, loading: Boolean, onRefresh: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                "PRODUCTION ANALYTICS",
                fontSize = 9.sp,
                letterSpacing = 2.2.sp,
                fontWeight = FontWeight.Bold,
                color = SolarCyan,
            )
            Text(
                stationName,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, SolarCyan.copy(alpha = 0.25f), CircleShape)
                .clickable(enabled = !loading, onClick = onRefresh)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                if (loading) "SYNCING" else "REFRESH",
                fontSize = 9.sp,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold,
                color = if (loading) SolarCyan.copy(alpha = 0.5f) else SolarCyan,
            )
        }
    }
}

@Composable
private fun PeriodSelector(selected: StatisticsPeriod, onSelect: (StatisticsPeriod) -> Unit) {
    val periods = StatisticsPeriod.entries
    val shape = RoundedCornerShape(50)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f), shape)
            .padding(4.dp),
    ) {
        val itemWidth = maxWidth / periods.size
        val indicatorOffset by animateDpAsState(
            targetValue = itemWidth * periods.indexOf(selected),
            animationSpec = tween(380),
            label = "period-pill",
        )
        Box(
            Modifier
                .offset(x = indicatorOffset)
                .width(itemWidth)
                .height(36.dp)
                .clip(shape)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            SolarCyan.copy(alpha = 0.22f),
                            SolarGreen.copy(alpha = 0.18f),
                            SolarGold.copy(alpha = 0.20f),
                        )
                    )
                )
                .border(1.dp, SolarCyan.copy(alpha = 0.45f), shape),
        )
        Row(Modifier.fillMaxWidth()) {
            periods.forEach { period ->
                val active = period == selected
                Box(
                    Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(shape)
                        .clickable { onSelect(period) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        period.chip,
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodNavigator(period: StatisticsPeriod, anchorMillis: Long, onShift: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            rangeLabel(period, anchorMillis),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (period != StatisticsPeriod.TOTAL) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NavArrow("‹") { onShift(-1) }
                Spacer(Modifier.width(6.dp))
                NavArrow("›", enabled = canNavigateForward(period, anchorMillis)) { onShift(1) }
            }
        } else {
            Text(
                "Lifecycle",
                fontSize = 10.sp,
                letterSpacing = 1.4.sp,
                fontWeight = FontWeight.Bold,
                color = SolarGold,
            )
        }
    }
}

@Composable
private fun NavArrow(glyph: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) 0.06f else 0.02f))
            .border(
                1.dp,
                if (enabled) SolarCyan.copy(alpha = 0.30f) else Color.Transparent,
                CircleShape,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.20f),
        )
    }
}

@Composable
private fun StatsErrorBanner(message: String, onRetry: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                message,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) {
                Text("RETRY", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HeroProduction(snapshot: StatisticsSnapshot?, period: StatisticsPeriod, loading: Boolean) {
    val value = snapshot?.headlineProduction
    val subtitle = when (period) {
        StatisticsPeriod.DAY -> "Energy generated today"
        StatisticsPeriod.MONTH -> "Energy generated this month"
        StatisticsPeriod.YEAR -> "Energy generated this year"
        StatisticsPeriod.TOTAL -> "Lifetime energy generated"
    }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        SolarCyan.copy(alpha = 0.16f),
                        NightSurfaceHigh.copy(alpha = 0.75f),
                        SolarGold.copy(alpha = 0.10f),
                    )
                )
            )
            .border(1.dp, SolarCyan.copy(alpha = 0.22f), RoundedCornerShape(26.dp))
            .padding(20.dp),
    ) {
        Column {
            Text(
                subtitle.uppercase(),
                fontSize = 9.sp,
                letterSpacing = 1.6.sp,
                fontWeight = FontWeight.SemiBold,
                color = SolarCyan,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value?.clean(1) ?: if (loading) "…" else "—",
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = (-1.5).sp,
                )
                Text(
                    " kWh",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = SolarGold,
                    modifier = Modifier.padding(bottom = 9.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val peak = snapshot?.peak
                Text(
                    if (peak != null) "Peak ${peak.value.clean(1)} kWh · ${peak.label}" else "No buckets yet",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.62f),
                )
                snapshot?.balance?.totalProductPower?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "·",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.35f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "server total ${parseNum(it)?.clean(1) ?: "—"}",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.42f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniStat(
    label: String,
    value: Double?,
    unit: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                label.uppercase(),
                fontSize = 8.5.sp,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value?.clean(1) ?: "—",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
                Text(
                    " $unit",
                    fontSize = 8.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
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
    var mode by remember { androidx.compose.runtime.mutableStateOf(ChartMode.CURVE) }
    // Evidence: PhotovoltaicStatisticsViewModel.getLeftUnit — the day series is
    // instantaneous power (kW); month/year buckets are energy (kWh).
    val chartUnit = if (period == StatisticsPeriod.DAY) "kW" else "kWh"
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        if (period == StatisticsPeriod.DAY) "Power curve" else "Production profile",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${production.size} buckets · $chartUnit",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ModeToggle(mode) { mode = it }
                    if (mode == ChartMode.CURVE) ExpandButton(onExpand)
                }
            }
            Spacer(Modifier.height(10.dp))

            when (mode) {
                ChartMode.CURVE -> ProductionAreaChart(
                    points = production,
                    accent = SolarGold,
                    unit = chartUnit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp),
                    emptyLabel = if (loading) "Loading production…" else "No production recorded for this window",
                )

                ChartMode.BARS -> {
                    GroupedEnergyBars(
                        primary = production,
                        secondary = consumption,
                        primaryColor = SolarGold,
                        secondaryColor = SolarCyan,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp),
                        emptyLabel = if (loading) "Loading production…" else "No series available",
                    )
                    Spacer(Modifier.height(8.dp))
                    LegendRow(
                        listOf(
                            "Production" to SolarGold,
                            "Consumption" to SolarCyan,
                        )
                    )
                }

                ChartMode.MIX -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EnergyDonut(
                            segments = donutSegments(snapshot),
                            modifier = Modifier.size(180.dp),
                            centerTitle = "Self-use",
                            centerValue = "${((snapshot?.selfConsumptionRate ?: 0.0) * 100).toInt()}%",
                            centerCaption = "of production",
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LegendRow(donutSegments(snapshot).map { it.label to it.color }, vertical = true)
                            Text(
                                "Self-sufficiency ${((snapshot?.selfSufficiency ?: 0.0) * 100).toInt()}%",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeToggle(mode: ChartMode, onChange: (ChartMode) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ChartMode.entries.forEach { item ->
            val active = item == mode
            val background by animateColorAsState(
                if (active) SolarCyan.copy(alpha = 0.20f) else Color.Transparent,
                label = "mode-bg",
            )
            val textColor by animateColorAsState(
                if (active) SolarCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "mode-fg",
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(background)
                    .clickable { onChange(item) }
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            ) {
                Text(item.label, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = textColor)
            }
        }
    }
}

/** Four corner brackets: opens the chart in full-screen landscape. */
@Composable
private fun ExpandButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, SolarCyan.copy(alpha = 0.25f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(14.dp)) {
            val stroke = 1.6.dp.toPx()
            val len = size.minDimension * 0.38f
            val w = size.width
            val h = size.height
            drawLine(SolarCyan, Offset(0f, len), Offset(0f, 0f), stroke)
            drawLine(SolarCyan, Offset(0f, 0f), Offset(len, 0f), stroke)
            drawLine(SolarCyan, Offset(w - len, 0f), Offset(w, 0f), stroke)
            drawLine(SolarCyan, Offset(w, 0f), Offset(w, len), stroke)
            drawLine(SolarCyan, Offset(0f, h - len), Offset(0f, h), stroke)
            drawLine(SolarCyan, Offset(0f, h), Offset(len, h), stroke)
            drawLine(SolarCyan, Offset(w - len, h), Offset(w, h), stroke)
            drawLine(SolarCyan, Offset(w, h), Offset(w, h - len), stroke)
        }
    }
}

@Composable
private fun LegendRow(items: List<Pair<String, Color>>, vertical: Boolean = false) {
    if (vertical) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEach { (label, color) -> LegendDot(label, color) }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items.forEach { (label, color) -> LegendDot(label, color) }
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun donutSegments(snapshot: StatisticsSnapshot?): List<DonutSegment> {
    if (snapshot == null) return emptyList()
    return listOf(
        DonutSegment("Self-consumed", snapshot.periodSelfUse, SolarGold),
        DonutSegment("Exported", snapshot.periodExport, SolarGreen),
        DonutSegment("Charged", snapshot.charge, SolarCyan),
        DonutSegment("Imported", snapshot.periodImport, SolarBlue),
    ).filter { it.value > 0.0 }
}

@Composable
private fun MixCard(snapshot: StatisticsSnapshot?, production: List<SeriesPoint>) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Energy balance", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Where the selected window's energy went",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                EnergyDonut(
                    segments = donutSegments(snapshot),
                    modifier = Modifier.size(150.dp),
                    centerTitle = "Self-use",
                    centerValue = "${((snapshot?.selfConsumptionRate ?: 0.0) * 100).toInt()}%",
                    centerCaption = "of production",
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RatioGauge(
                        ratio = (snapshot?.selfSufficiency ?: 0.0).toFloat(),
                        color = SolarGreen,
                        caption = "self-sufficiency",
                        modifier = Modifier.size(66.dp),
                    )
                    Text(
                        if (production.isEmpty()) {
                            "No production buckets in this window."
                        } else {
                            "${snapshot?.periodSelfUse?.clean(1) ?: "0"} kWh used on site, " +
                                "${snapshot?.periodExport?.clean(1) ?: "0"} kWh exported."
                        },
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
    val rows = listOf(
        Triple("PV production", snapshot?.periodProduction ?: 0.0, SolarGold),
        Triple("Consumption", snapshot?.periodConsumption ?: 0.0, SolarCyan),
        Triple("Exported to grid", snapshot?.periodExport ?: 0.0, SolarGreen),
        Triple("Imported from grid", snapshot?.periodImport ?: 0.0, SolarBlue),
        Triple("Battery charged", snapshot?.charge ?: 0.0, Color(0xFFB388FF)),
        Triple("Battery discharged", snapshot?.discharge ?: 0.0, Color(0xFFFF8A65)),
    )
    val maxValue = rows.maxOfOrNull { it.second } ?: 0.0
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Breakdown", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Totals for the selected window",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            rows.forEach { (label, value, color) ->
                MetricBar(
                    label = label,
                    value = value,
                    fraction = if (maxValue > 0.0) (value / maxValue).toFloat() else 0f,
                    color = color,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BucketChip("PV buckets", production.size)
                BucketChip("Load buckets", consumption.size)
                BucketChip("Export buckets", gridExport.count { it.value > 0.0 })
                BucketChip("Import buckets", gridImport.count { it.value > 0.0 })
            }
        }
    }
}

@Composable
private fun BucketChip(label: String, count: Int) {
    Column {
        Text(
            count.toString(),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            label,
            fontSize = 8.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HighlightsCard(snapshot: StatisticsSnapshot?, period: StatisticsPeriod) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Highlights", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HighlightTile(
                    label = if (period == StatisticsPeriod.DAY) "PEAK POWER" else "BEST BUCKET",
                    value = snapshot?.peak?.value?.clean(1) ?: "—",
                    caption = snapshot?.peak?.let {
                        if (period == StatisticsPeriod.DAY) "${it.label} · kW" else it.label
                    } ?: "no data",
                    accent = SolarGold,
                    modifier = Modifier.weight(1f),
                )
                HighlightTile(
                    label = "AVERAGE",
                    value = snapshot?.average?.clean(1) ?: "—",
                    caption = "per active bucket",
                    accent = SolarCyan,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HighlightTile(
                    label = "BATTERY FLOW",
                    value = snapshot?.batteryCycles?.clean(1) ?: "—",
                    caption = "kWh cycled",
                    accent = Color(0xFFB388FF),
                    modifier = Modifier.weight(1f),
                )
                HighlightTile(
                    label = "SELF-SUFFICIENCY",
                    value = "${((snapshot?.selfSufficiency ?: 0.0) * 100).toInt()}%",
                    caption = "of consumption covered",
                    accent = SolarGreen,
                    modifier = Modifier.weight(1f),
                )
            }

            snapshot?.kpi?.let { kpi ->
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.03f))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    KpiInline("Today", parseNum(kpi.dailyEnergy))
                    KpiInline("Month", parseNum(kpi.monthEnergy))
                    KpiInline("Year", parseNum(kpi.yearEnergy))
                    KpiInline("Lifetime", parseNum(kpi.cumulativeEnergy))
                }
            }
        }
    }
}

@Composable
private fun KpiInline(label: String, value: Double?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value?.clean(1) ?: "—",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            label,
            fontSize = 8.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.08f))
            .border(1.dp, accent.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
            .padding(12.dp),
    ) {
        Text(
            label,
            fontSize = 8.sp,
            letterSpacing = 1.sp,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
        Spacer(Modifier.height(3.dp))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(
            caption,
            fontSize = 8.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    onRefresh: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!snapshot?.warnings.isNullOrEmpty()) {
            Text(
                snapshot!!.warnings.joinToString(" · "),
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.75f),
            )
        }
        diag?.let {
            Text(it, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
        Text(
            if (lastUpdated > 0) {
                "Updated ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(lastUpdated))}" +
                    " · ${snapshot?.rangeLabel ?: ""}"
            } else {
                "Waiting for first statistics update"
            },
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
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
    val production = remember(snapshot) { snapshot?.production().orEmpty() }
    val chartUnit = if (period == StatisticsPeriod.DAY) "kW" else "kWh"

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 22.dp, vertical = 14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (period == StatisticsPeriod.DAY) "POWER CURVE" else "PRODUCTION PROFILE",
                    fontSize = 9.sp,
                    letterSpacing = 2.2.sp,
                    fontWeight = FontWeight.Bold,
                    color = SolarCyan,
                )
                Text(
                    "$stationName · $range",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${production.size} buckets · $chartUnit",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, SolarCyan.copy(alpha = 0.30f), CircleShape)
                    .clickable(onClick = onClose)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                Text(
                    "CLOSE",
                    fontSize = 9.sp,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold,
                    color = SolarCyan,
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        ProductionAreaChart(
            points = production,
            accent = SolarGold,
            unit = chartUnit,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            emptyLabel = if (loading) "Loading production…" else "No production recorded for this window",
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Tap the curve to inspect a bucket · rotate back or tap CLOSE to return",
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}
