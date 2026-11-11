package com.fusionlite.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fusionlite.UiState
import com.fusionlite.data.EnergyCosts
import com.fusionlite.data.StatisticsPeriod
import com.fusionlite.data.energyCosts
import com.fusionlite.data.parseCentsPerKwh
import com.fusionlite.data.rangeLabel
import java.util.Locale

/**
 * Manual tariff calculator over the same energy-balance window used by the
 * Analytics page. Prices are entered in cents per kWh; costs appear in the
 * station's own currency (when the real-KPI payload reports one).
 */
@Composable
fun CostsScreen(
    state: UiState,
    onPeriod: (StatisticsPeriod) -> Unit,
    onShift: (Int) -> Unit,
    onRefresh: () -> Unit,
    onBuyPrice: (String) -> Unit,
    onSellPrice: (String) -> Unit,
    onToday: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val station = state.stations.firstOrNull { it.dn == state.selectedDn }
        ?: state.stations.firstOrNull()
    val buy = parseCentsPerKwh(state.buyPriceCents)
    val sell = parseCentsPerKwh(state.sellPriceCents)
    val costs = state.costs.energyCosts(buy ?: 0.0, sell ?: 0.0)
    // Numeric ids are filtered by energyCosts (see usableCurrency).
    val currency = costs.currency
    val hasData = state.costs != null
    val palette = LocalFusionPalette.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 118.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            CostsHeader(
                stationName = station?.name ?: "Solar plant",
                loading = state.costsLoading,
                onRefresh = onRefresh,
            )
        }

        item { PeriodSelector(selected = state.costsPeriod, onSelect = onPeriod) }

        item {
            PeriodNavigator(
                period = state.costsPeriod,
                anchorMillis = state.costsAnchor,
                onShift = onShift,
                onToday = onToday,
            )
        }

        state.costsError?.let { error ->
            item { StatsErrorBanner(error, onRefresh) }
        }

        item {
            TariffInputCard(
                title = "Purchase price",
                subtitle = "What you pay for 1 kWh taken from the grid.",
                value = state.buyPriceCents,
                accent = palette.gold,
                onValue = onBuyPrice,
            )
        }

        item {
            TariffInputCard(
                title = "Export sell price",
                subtitle = "What the grid pays you for 1 kWh exported.",
                value = state.sellPriceCents,
                accent = palette.green,
                onValue = onSellPrice,
            )
        }

        item {
            ConsumptionCostCard(
                costs = costs,
                period = state.costsPeriod,
                priceSet = buy != null,
                loading = state.costsLoading,
                hasData = hasData,
                currency = currency,
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MoneyStat(
                    label = "Export revenue",
                    value = if (sell != null) costs.exportRevenue else null,
                    caption = if (sell != null) {
                        "${kwh(costs.exportKwh)} × ${price(costs.sellCentsPerKwh)} ¢"
                    } else {
                        "Enter a sell price"
                    },
                    accent = palette.green,
                    currency = currency,
                    modifier = Modifier.weight(1f),
                )
                MoneyStat(
                    label = "Net bill",
                    value = if (buy != null) costs.netBill else null,
                    caption = if (buy != null) {
                        "Grid import − export"
                    } else {
                        "Enter a purchase price"
                    },
                    accent = palette.cyan,
                    currency = currency,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item { CostBreakdownCard(costs = costs, currency = currency, hasData = hasData) }

        item {
            Text(
                "Prices are stored on this device and are not sent to the server. " +
                    "Costs use the selected ${state.costsPeriod.title.lowercase()} window " +
                    "(${rangeLabel(state.costsPeriod, state.costsAnchor)}).",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun CostsHeader(stationName: String, loading: Boolean, onRefresh: () -> Unit) {
    val palette = LocalFusionPalette.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                "ENERGY COSTS",
                fontSize = 9.sp,
                letterSpacing = 2.2.sp,
                fontWeight = FontWeight.Bold,
                color = palette.amber,
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
                .background(palette.glass)
                .border(1.dp, palette.amber.copy(alpha = 0.25f), CircleShape)
                .clickable(enabled = !loading, onClick = onRefresh)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                if (loading) "SYNCING" else "REFRESH",
                fontSize = 9.sp,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold,
                color = if (loading) palette.amber.copy(alpha = 0.5f) else palette.amber,
            )
        }
    }
}

@Composable
private fun TariffInputCard(
    title: String,
    subtitle: String,
    value: String,
    accent: Color,
    onValue: (String) -> Unit,
) {
    val invalid = value.isNotBlank() && parseCentsPerKwh(value) == null
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.30f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title.uppercase(Locale.getDefault()),
                fontSize = 9.sp,
                letterSpacing = 1.8.sp,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
            Text(
                subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = value,
                onValueChange = onValue,
                singleLine = true,
                isError = invalid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                placeholder = { Text("e.g. 18.5") },
                suffix = { Text("¢/kWh") },
                supportingText = if (invalid) {
                    { Text("Enter a number, for example 18.5") }
                } else {
                    null
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ConsumptionCostCard(
    costs: EnergyCosts,
    period: StatisticsPeriod,
    priceSet: Boolean,
    loading: Boolean,
    hasData: Boolean,
    currency: String?,
) {
    val palette = LocalFusionPalette.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        SolarAmber.copy(alpha = 0.20f),
                        palette.surfaceHigh.copy(alpha = 0.85f),
                    )
                )
            ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "CONSUMPTION COST · ${period.title.uppercase(Locale.getDefault())}",
                fontSize = 9.sp,
                letterSpacing = 1.8.sp,
                fontWeight = FontWeight.Bold,
                color = palette.amber,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    !priceSet -> "—"
                    loading && !hasData -> "…"
                    else -> money(costs.consumptionCost, currency)
                },
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    !priceSet -> "Enter your purchase price to calculate costs."
                    !hasData && !loading -> "No energy data for this window."
                    else -> "${kwh(costs.consumptionKwh)} × ${price(costs.buyCentsPerKwh)} ¢/kWh"
                },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MoneyStat(
    label: String,
    value: Double?,
    caption: String,
    accent: Color,
    currency: String?,
    modifier: Modifier = Modifier,
) {
    val palette = LocalFusionPalette.current
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = modifier,
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                label.uppercase(Locale.getDefault()),
                fontSize = 8.5.sp,
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                value?.let { money(it, currency) } ?: "—",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (value != null && value < 0) palette.green
                else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                caption,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CostBreakdownCard(costs: EnergyCosts, currency: String?, hasData: Boolean) {
    val palette = LocalFusionPalette.current
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "BREAKDOWN",
                fontSize = 9.sp,
                letterSpacing = 1.8.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!hasData) {
                Text(
                    "Load a window to see consumption, grid import and export totals.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CostRow(
                label = "Consumption",
                energy = costs.consumptionKwh,
                amount = costs.consumptionCost,
                currency = currency,
                accent = palette.gold,
                note = "household load",
            )
            CostRow(
                label = "From grid",
                energy = costs.importKwh,
                amount = costs.importCost,
                currency = currency,
                accent = palette.cyan,
                note = "purchased",
            )
            CostRow(
                label = "Self-consumed PV",
                energy = costs.selfUseKwh,
                amount = costs.selfUseSavings,
                currency = currency,
                accent = palette.green,
                note = "avoided purchase",
            )
            CostRow(
                label = "Exported",
                energy = costs.exportKwh,
                amount = -costs.exportRevenue,
                currency = currency,
                accent = palette.green,
                note = "credited",
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Net bill", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "grid import − export revenue",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    money(costs.netBill, currency),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (costs.netBill < 0) palette.green else palette.gold,
                )
            }
        }
    }
}

@Composable
private fun CostRow(
    label: String,
    energy: Double,
    amount: Double,
    currency: String?,
    accent: Color,
    note: String,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = accent)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "$note · ${kwh(energy)}",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            money(amount, currency),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun money(value: Double, currency: String?): String {
    val text = String.format(Locale.getDefault(), "%,.2f", value)
    return if (currency.isNullOrBlank()) text else "$text $currency"
}

private fun price(cents: Double): String = String.format(Locale.getDefault(), "%,.2f", cents)

private fun kwh(value: Double): String = String.format(Locale.getDefault(), "%,.1f kWh", value)
