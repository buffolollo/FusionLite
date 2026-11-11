package com.fusionlite.data

import android.content.Context

/**
 * Manual electricity tariffs entered by the user, kept as the raw text so the
 * field keeps exactly what was typed (including a comma decimal separator).
 * Values are in cents per kWh.
 *
 * Stored in SharedPreferences rather than the session database: the prices are
 * device preferences that must survive sign-out and need no migration.
 */
class TariffStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("fusionlite.tariffs", Context.MODE_PRIVATE)

    fun buyCents(): String = prefs.getString(KEY_BUY, "") ?: ""

    fun sellCents(): String = prefs.getString(KEY_SELL, "") ?: ""

    fun saveBuy(value: String) {
        prefs.edit().putString(KEY_BUY, value).apply()
    }

    fun saveSell(value: String) {
        prefs.edit().putString(KEY_SELL, value).apply()
    }

    private companion object {
        const val KEY_BUY = "buy_cents_per_kwh"
        const val KEY_SELL = "sell_cents_per_kwh"
    }
}

/** Accepts "12.5", "12,5" and rejects blanks/negatives. */
fun parseCentsPerKwh(text: String): Double? =
    text.trim()
        .replace(',', '.')
        .toDoubleOrNull()
        ?.takeIf { it.isFinite() && it >= 0.0 }

/**
 * Cost/revenue figures for one statistics window, in the station currency.
 * Energy totals come from the same energy-balance snapshot as the Analytics
 * page; prices are cents per kWh.
 */
data class EnergyCosts(
    val consumptionKwh: Double,
    val importKwh: Double,
    val exportKwh: Double,
    val selfUseKwh: Double,
    val buyCentsPerKwh: Double,
    val sellCentsPerKwh: Double,
    /** Total household consumption × purchase price. */
    val consumptionCost: Double,
    /** Energy actually bought from the grid × purchase price. */
    val importCost: Double,
    /** Energy exported to the grid × sell price. */
    val exportRevenue: Double,
    /** Value of self-consumed PV at the purchase price. */
    val selfUseSavings: Double,
    /** Actual bill: grid import cost minus export revenue. */
    val netBill: Double,
    val currency: String?,
)

fun StatisticsSnapshot?.energyCosts(
    buyCentsPerKwh: Double,
    sellCentsPerKwh: Double,
): EnergyCosts {
    val consumption = this?.periodConsumption ?: 0.0
    val imported = this?.periodImport ?: 0.0
    val exported = this?.periodExport ?: 0.0
    val selfUse = this?.periodSelfUse ?: 0.0
    val importCost = imported * buyCentsPerKwh / 100.0
    val exportRevenue = exported * sellCentsPerKwh / 100.0
    return EnergyCosts(
        consumptionKwh = consumption,
        importKwh = imported,
        exportKwh = exported,
        selfUseKwh = selfUse,
        buyCentsPerKwh = buyCentsPerKwh,
        sellCentsPerKwh = sellCentsPerKwh,
        consumptionCost = consumption * buyCentsPerKwh / 100.0,
        importCost = importCost,
        exportRevenue = exportRevenue,
        selfUseSavings = selfUse * buyCentsPerKwh / 100.0,
        netBill = importCost - exportRevenue,
        currency = this?.kpi?.currency,
    )
}
