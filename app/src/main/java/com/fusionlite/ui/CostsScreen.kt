package com.fusionlite.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
 *
 * Both fields validate inline (never with a blocking dialog) and keep the raw
 * text so a partially typed value such as "12," is not clobbered.
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
    val design = LocalFusionDesign.current

    FusionPage(modifier = modifier, bottomPadding = design.navClearance) {
        item {
            FusionScreenHeader(
                eyebrow = "Energy costs",
                title = station?.name ?: "Solar plant",
                accent = palette.amber,
                trailing = {
                    FusionIconButton(
                        icon = FusionIcon.REFRESH,
                        contentDescription = if (state.costsLoading) "Refreshing cost data" else "Refresh cost data",
                        onClick = onRefresh,
                        enabled = !state.costsLoading,
                        tint = palette.amber,
                        borderColor = palette.border,
                        spinning = state.costsLoading,
                    )
                },
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
            FusionCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Your tariff", style = design.type.heading, color = palette.textPrimary)
                    FusionStatusPill(
                        label = "Stored on device",
                        tone = FusionTone.NEUTRAL,
                        icon = FusionIcon.LOCK,
                    )
                }
                Text(
                    "Enter what one kilowatt-hour costs you. The app multiplies these prices by " +
                        "the selected window's energy; nothing is sent to the server.",
                    style = design.type.bodySmall,
                    color = palette.textSecondary,
                )
                FusionTextField(
                    value = state.buyPriceCents,
                    onValueChange = onBuyPrice,
                    label = "Purchase price",
                    supportingText = "What you pay for 1 kWh taken from the grid.",
                    placeholder = "e.g. 18.5",
                    leadingIcon = FusionIcon.GRID,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    error = if (state.buyPriceCents.isNotBlank() && buy == null) {
                        "Enter a number, for example 18.5"
                    } else {
                        null
                    },
                    success = if (state.buyPriceCents.isNotBlank() && buy != null) {
                        "Valid price · stored on this device"
                    } else {
                        null
                    },
                    accent = palette.gold,
                )
                FusionTextField(
                    value = state.sellPriceCents,
                    onValueChange = onSellPrice,
                    label = "Export sell price",
                    supportingText = "What the grid pays you for 1 kWh exported.",
                    placeholder = "e.g. 6.0",
                    leadingIcon = FusionIcon.ARROW_UP_RIGHT,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    error = if (state.sellPriceCents.isNotBlank() && sell == null) {
                        "Enter a number, for example 6.0"
                    } else {
                        null
                    },
                    success = if (state.sellPriceCents.isNotBlank() && sell != null) {
                        "Valid price · stored on this device"
                    } else {
                        null
                    },
                    accent = palette.green,
                )
            }
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
            FusionGrid(itemCount = 2, minItemWidth = 140.dp) { index, tileModifier ->
                when (index) {
                    0 -> MoneyStat(
                        label = "Export revenue",
                        value = if (sell != null) costs.exportRevenue else null,
                        caption = if (sell != null) {
                            "${kwh(costs.exportKwh)} × ${price(costs.sellCentsPerKwh)} ¢"
                        } else {
                            "Enter a sell price"
                        },
                        tone = FusionTone.SUCCESS,
                        currency = currency,
                        modifier = tileModifier,
                    )
                    else -> MoneyStat(
                        label = "Net bill",
                        value = if (buy != null) costs.netBill else null,
                        caption = if (buy != null) "Grid import − export" else "Enter a purchase price",
                        tone = FusionTone.INFO,
                        currency = currency,
                        modifier = tileModifier,
                    )
                }
            }
        }

        item { CostBreakdownCard(costs = costs, currency = currency, hasData = hasData) }

        item {
            Text(
                "Prices are stored on this device and are not sent to the server. " +
                    "Costs use the selected ${state.costsPeriod.title.lowercase()} window " +
                    "(${rangeLabel(state.costsPeriod, state.costsAnchor)}).",
                style = design.type.bodySmall,
                color = palette.textTertiary,
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
    val design = LocalFusionDesign.current
    val shape = RoundedCornerShape(design.radiusHero)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        palette.amber.copy(alpha = 0.22f),
                        palette.surfaceRaised,
                    )
                )
            )
            .border(1.dp, palette.amber.copy(alpha = 0.30f), shape)
            .padding(design.spacingXl),
        verticalArrangement = Arrangement.spacedBy(design.spacingXs),
    ) {
        Text(
            "CONSUMPTION COST · ${period.title.uppercase(Locale.getDefault())}",
            style = design.type.labelMicro,
            color = palette.amber,
        )
        if (loading && !hasData) {
            FusionSkeleton(Modifier.size(width = 160.dp, height = 42.dp))
        } else {
            Text(
                when {
                    !priceSet -> "—"
                    else -> money(costs.consumptionCost, currency)
                },
                style = design.type.displaySmall,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
        }
        Text(
            when {
                !priceSet -> "Enter your purchase price to calculate costs."
                !hasData && !loading -> "No energy data for this window."
                else -> "${kwh(costs.consumptionKwh)} × ${price(costs.buyCentsPerKwh)} ¢/kWh"
            },
            style = design.type.bodySmall,
            color = palette.textSecondary,
        )
    }
}

@Composable
private fun MoneyStat(
    label: String,
    value: Double?,
    caption: String,
    tone: FusionTone,
    currency: String?,
    modifier: Modifier = Modifier,
) {
    val palette = LocalFusionPalette.current
    val design = LocalFusionDesign.current
    val color = tone.color(palette)
    Column(
        modifier
            .clip(RoundedCornerShape(design.radiusLg))
            .background(palette.surfaceRaised)
            .border(1.dp, palette.border, RoundedCornerShape(design.radiusLg))
            .padding(design.spacingLg),
        verticalArrangement = Arrangement.spacedBy(design.spacingXs),
    ) {
        Text(
            label.uppercase(Locale.getDefault()),
            style = design.type.labelMicro,
            color = color,
            maxLines = 1,
        )
        Text(
            value?.let { money(it, currency) } ?: "—",
            style = design.type.numeric,
            color = if (value != null && value < 0) palette.success else palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            caption,
            style = design.type.bodySmall,
            color = palette.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CostBreakdownCard(costs: EnergyCosts, currency: String?, hasData: Boolean) {
    val palette = LocalFusionPalette.current
    val design = LocalFusionDesign.current
    FusionCard {
        Text("Breakdown", style = design.type.heading, color = palette.textPrimary)
        Text(
            "How each part of the bill is calculated",
            style = design.type.bodySmall,
            color = palette.textSecondary,
        )
        if (!hasData) {
            Text(
                "Load a window to see consumption, grid import and export totals.",
                style = design.type.bodySmall,
                color = palette.textSecondary,
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
        FusionDivider()
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Net bill", style = design.type.subheading, color = palette.textPrimary)
                Text(
                    "grid import − export revenue",
                    style = design.type.bodySmall,
                    color = palette.textSecondary,
                )
            }
            Text(
                money(costs.netBill, currency),
                style = design.type.numericSmall,
                color = if (costs.netBill < 0) palette.success else palette.gold,
            )
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
    val palette = LocalFusionPalette.current
    val design = LocalFusionDesign.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.10f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(label, style = design.type.label, color = accent, maxLines = 1)
            }
            Spacer(Modifier.width(design.spacingSm))
            Text(
                "$note · ${kwh(energy)}",
                style = design.type.bodySmall,
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(design.spacingSm))
        Text(
            money(amount, currency),
            style = design.type.label,
            fontWeight = FontWeight.SemiBold,
            color = palette.textPrimary,
            maxLines = 1,
        )
    }
}

private fun money(value: Double, currency: String?): String {
    val text = String.format(Locale.getDefault(), "%,.2f", value)
    return if (currency.isNullOrBlank()) text else "$text $currency"
}

private fun price(cents: Double): String = String.format(Locale.getDefault(), "%,.2f", cents)

private fun kwh(value: Double): String = String.format(Locale.getDefault(), "%,.1f kWh", value)
