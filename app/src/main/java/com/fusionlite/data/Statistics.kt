package com.fusionlite.data

import com.fusionlite.api.EnergyBalanceData
import com.fusionlite.api.EnergyBalanceRequest
import com.fusionlite.api.StationRealKpi
import com.fusionlite.api.StationRealKpiRequest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * Historical production window. The codes are the dex values of
 * `StationKpiChartArgBean.StatDim` (evidence: `<clinit>`):
 * DAY="2", MONTH="4", YEAR="5", ALL="6".
 *
 * The same code is sent as `timeDim` by
 * `NormalPowerFragment.queryKpiManage` and as `statDim` by
 * `PhotovoltaicStatisticsViewModel.generateStationArgs`.
 */
enum class StatisticsPeriod(val dim: String, val chip: String, val title: String) {
    DAY("2", "Day", "Today"),
    MONTH("4", "Month", "This month"),
    YEAR("5", "Year", "This year"),
    TOTAL("6", "Total", "Lifetime"),
    ;

    companion object {
        fun fromDim(dim: String?): StatisticsPeriod =
            entries.firstOrNull { it.dim == dim } ?: DAY
    }
}

/** One aligned chart bucket: a label from `xAxis` and a numeric value. */
data class SeriesPoint(val label: String, val value: Double)

/**
 * Everything the statistics dashboard needs for one selected window.
 * `balance` is the raw energy-balance payload; `kpi` the station-real-kpi one.
 */
data class StatisticsSnapshot(
    val stationDn: String,
    val period: StatisticsPeriod,
    val anchorMillis: Long,
    val rangeLabel: String,
    val kpi: StationRealKpi? = null,
    val balance: EnergyBalanceData? = null,
    val error: String? = null,
    val warnings: List<String> = emptyList(),
) {
    val hasChart: Boolean get() = seriesPoints().isNotEmpty()

    private fun series(raw: List<Any?>?): List<SeriesPoint> {
        val labels = balance?.xAxis.orEmpty()
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.mapIndexed { index, value ->
            SeriesPoint(
                label = labels.getOrNull(index)?.toString().orEmpty().ifBlank { "${index + 1}" },
                value = parseNum(value) ?: 0.0,
            )
        }
    }

    // The member names mirror StationEnergyManageBean exactly.
    fun seriesPoints(): List<SeriesPoint> = series(balance?.productPower)
    fun production(): List<SeriesPoint> = series(balance?.productPower)
    fun consumption(): List<SeriesPoint> = series(balance?.usePower)
    fun gridExport(): List<SeriesPoint> = series(balance?.onGridPower)
    fun gridImport(): List<SeriesPoint> = series(balance?.buyPower)
    fun selfUse(): List<SeriesPoint> = series(balance?.selfUsePower)
    fun batteryCharge(): List<SeriesPoint> = series(balance?.chargePower)
    fun batteryDischarge(): List<SeriesPoint> = series(balance?.dischargePower)

    fun totalOf(points: List<SeriesPoint>): Double = points.sumOf { it.value }

    /** Sum of the visible buckets, falling back to the server's own total. */
    val periodProduction: Double
        get() = parseNum(balance?.totalProductPower) ?: totalOf(production())

    val periodConsumption: Double
        get() = parseNum(balance?.totalUsePower) ?: totalOf(consumption())

    val periodExport: Double
        get() = parseNum(balance?.totalOnGridPower) ?: totalOf(gridExport())

    val periodImport: Double
        get() = parseNum(balance?.totalBuyPower) ?: totalOf(gridImport())

    val periodSelfUse: Double
        get() = parseNum(balance?.totalSelfUsePower) ?: totalOf(selfUse())

    val charge: Double get() = totalOf(batteryCharge())
    val discharge: Double get() = totalOf(batteryDischarge())

    /**
     * Headline number for the selected window. Prefers the energy balance
     * (server total or bucket sum) and falls back to station-real-kpi when the
     * chart endpoint returned nothing.
     */
    val headlineProduction: Double?
        get() {
            // For the daily window the energy-balance series is instantaneous
            // power (kW); the day's kWh total is authoritative in real-kpi.
            if (period == StatisticsPeriod.DAY) {
                parseNum(kpi?.dailyEnergy)?.let { return it }
            }
            val fromBalance = if (hasChart || balance?.totalProductPower != null) {
                periodProduction
            } else {
                null
            }
            if (fromBalance != null && fromBalance > 0.0) return fromBalance
            val fallback = when (period) {
                StatisticsPeriod.DAY -> kpi?.dailyEnergy
                StatisticsPeriod.MONTH -> kpi?.monthEnergy
                StatisticsPeriod.YEAR -> kpi?.yearEnergy
                StatisticsPeriod.TOTAL -> kpi?.cumulativeEnergy
            }
            return parseNum(fallback) ?: fromBalance
        }

    val peak: SeriesPoint?
        get() = production().maxByOrNull { it.value }?.takeIf { it.value > 0.0 }

    val average: Double
        get() {
            val points = production().filter { it.value > 0.0 }
            return if (points.isEmpty()) 0.0 else points.sumOf { it.value } / points.size
        }

    val selfSufficiency: Double
        get() {
            val productionTotal = periodProduction
            val consumptionTotal = periodConsumption
            if (consumptionTotal <= 0.0) return 0.0
            return (periodSelfUse / consumptionTotal).coerceIn(0.0, 1.0)
        }

    val selfConsumptionRate: Double
        get() {
            val productionTotal = periodProduction
            if (productionTotal <= 0.0) return 0.0
            return (periodSelfUse / productionTotal).coerceIn(0.0, 1.0)
        }

    val batteryCycles: Double get() = (charge + discharge) / 2.0
}

// ---------------------------------------------------------------------------
// Request builders — replicate the decompiled callers exactly.
// ---------------------------------------------------------------------------

private fun zone(): ZoneId = ZoneId.systemDefault()

/** `brn.c(now, defaultZone)` → "+8" / "-5" / "+5.5" (no leading plus sign). */
fun timeZoneValue(instantMillis: Long = System.currentTimeMillis()): String {
    val offsetSeconds = zone().rules.getOffset(Instant.ofEpochMilli(instantMillis)).totalSeconds
    val sign = if (offsetSeconds < 0) "-" else ""
    val hours = kotlin.math.abs(offsetSeconds) / 3600.0
    return if (hours == kotlin.math.floor(hours)) {
        "$sign${hours.toInt()}"
    } else {
        String.format(Locale.US, "%s%.2f", sign, hours).trimEnd('0').trimEnd('.')
    }
}

fun timeZoneId(): String = java.util.TimeZone.getDefault().id

/**
 * `jqn.a(timeDim, selectedTime)` — dateStr formatting from the shipped app:
 *  - DAY   → "yyyy-MM-dd 00:00:00"
 *  - MONTH → "yyyy-MM-01 00:00:00"
 *  - YEAR/ALL → "yyyy-01-01 00:00:00"
 */
fun dateStrFor(period: StatisticsPeriod, anchorMillis: Long): String {
    val date = Instant.ofEpochMilli(anchorMillis).atZone(zone()).toLocalDate()
    return when (period) {
        StatisticsPeriod.DAY -> "$date 00:00:00"
        StatisticsPeriod.MONTH -> String.format(Locale.US, "%04d-%02d-01 00:00:00", date.year, date.monthValue)
        StatisticsPeriod.YEAR, StatisticsPeriod.TOTAL ->
            String.format(Locale.US, "%04d-01-01 00:00:00", date.year)
    }
}

/**
 * `qv2.j(bean)` / `PhotovoltaicStatisticsViewModel`: bucket start as epoch
 * millis (midnight local). DAY → that day, MONTH → first day of month,
 * YEAR/ALL → first day of the year.
 */
fun queryTimeFor(period: StatisticsPeriod, anchorMillis: Long): Long {
    val date = Instant.ofEpochMilli(anchorMillis).atZone(zone()).toLocalDate()
    val start = when (period) {
        StatisticsPeriod.DAY -> date
        StatisticsPeriod.MONTH -> date.withDayOfMonth(1)
        StatisticsPeriod.YEAR, StatisticsPeriod.TOTAL -> date.withDayOfYear(1)
    }
    return start.atStartOfDay(zone()).toInstant().toEpochMilli()
}

/**
 * `NormalPowerFragment.queryKpiManage` / `qv2.k` request body:
 * stationDn, timeDim, dateStr, queryTime, timeZone, timeZoneStr, "_".
 */
fun energyBalanceRequest(
    stationDn: String,
    period: StatisticsPeriod,
    anchorMillis: Long,
): EnergyBalanceRequest {
    val queryTime = queryTimeFor(period, anchorMillis)
    return EnergyBalanceRequest(
        stationDn = stationDn,
        timeDim = period.dim,
        dateStr = dateStrFor(period, anchorMillis),
        queryTime = queryTime,
        timeZone = timeZoneValue(queryTime),
        timeZoneStr = timeZoneId(),
        timestamp = System.currentTimeMillis(),
    )
}

/** Flattens [StationRealKpiRequest] for the GET fallback (HTTP 405 hosts). */
fun StationRealKpiRequest.asQueryParams(): Map<String, String> = mapOf(
    "stationDn" to stationDn,
    "queryTime" to queryTime.toString(),
    "timeZone" to timeZone,
)

/**
 * Same fields as [energyBalanceRequest], flattened for the GET fallback used
 * when a regional gateway answers the POST form with HTTP 405.
 */
fun EnergyBalanceRequest.asQueryParams(): Map<String, String> = mapOf(
    "stationDn" to stationDn,
    "timeDim" to timeDim,
    "dateStr" to dateStr,
    "queryTime" to queryTime.toString(),
    "timeZone" to timeZone,
    "timeZoneStr" to timeZoneStr,
    "_" to timestamp.toString(),
)

/**
 * `ChartModeKpiHeaderViewModel.querySingleRealKpi` request body:
 * stationDn, queryTime (midnight today), timeZone.
 */
fun stationRealKpiRequest(
    stationDn: String,
    nowMillis: Long = System.currentTimeMillis(),
): StationRealKpiRequest {
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone()).toLocalDate()
    val midnight = today.atStartOfDay(zone()).toInstant().toEpochMilli()
    return StationRealKpiRequest(
        stationDn = stationDn,
        queryTime = midnight,
        timeZone = timeZoneValue(midnight),
    )
}

// ---------------------------------------------------------------------------
// Window navigation / labels
// ---------------------------------------------------------------------------

fun rangeLabel(period: StatisticsPeriod, anchorMillis: Long): String {
    val date = Instant.ofEpochMilli(anchorMillis).atZone(zone()).toLocalDate()
    val month = date.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())
    return when (period) {
        StatisticsPeriod.DAY -> "${date.dayOfMonth} $month ${date.year}"
        StatisticsPeriod.MONTH -> "$month ${date.year}"
        StatisticsPeriod.YEAR, StatisticsPeriod.TOTAL -> "${date.year}"
    }
}

/** Move the window by [delta] units (day / month / year / year for Total). */
fun shiftAnchor(period: StatisticsPeriod, anchorMillis: Long, delta: Int): Long {
    val date = Instant.ofEpochMilli(anchorMillis).atZone(zone()).toLocalDate()
    val moved: LocalDate = when (period) {
        StatisticsPeriod.DAY -> date.plusDays(delta.toLong())
        StatisticsPeriod.MONTH -> date.plusMonths(delta.toLong())
        StatisticsPeriod.YEAR, StatisticsPeriod.TOTAL -> date.plusYears(delta.toLong())
    }
    return moved.atStartOfDay(zone()).toInstant().toEpochMilli()
}

fun canNavigateForward(period: StatisticsPeriod, anchorMillis: Long): Boolean {
    if (period == StatisticsPeriod.TOTAL) return false
    val today = LocalDate.now(zone())
    val date = Instant.ofEpochMilli(anchorMillis).atZone(zone()).toLocalDate()
    val next = when (period) {
        StatisticsPeriod.DAY -> date.plusDays(1)
        StatisticsPeriod.MONTH -> date.plusMonths(1).withDayOfMonth(1)
        StatisticsPeriod.YEAR -> date.plusYears(1).withDayOfYear(1)
        StatisticsPeriod.TOTAL -> return false
    }
    return !next.isAfter(today.plusDays(1))
}
