package com.fusionlite.data

import com.fusionlite.api.EnergyFlowData
import com.fusionlite.api.Station
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Loose number parsing for FusionSolar payloads. Values arrive as
 * "3.42", "3.42 kW", "3420 W" or "1,234.5" depending on host/version.
 * W values are normalised to kW.
 */
fun parseNum(raw: Any?): Double? {
    if (raw == null) return null
    if (raw is Number) return raw.toDouble()
    val text = raw.toString().trim()
    if (text.isEmpty() || text == "null" || text == "--") return null
    val match = Regex("-?\\d+(?:[.,]\\d+)?").find(text) ?: return null
    val value = match.value.replace(',', '.').toDoubleOrNull() ?: return null
    val lower = text.lowercase()
    return when {
        lower.contains("kw") -> value
        Regex("\\d\\s*w\\b").containsMatchIn(lower) -> value / 1000.0
        else -> value
    }
}

fun Double.clean(digits: Int = 2): String {
    if (this == 0.0) return "0"
    val absV = abs(this)
    val pattern = when {
        absV >= 1000 -> "%.0f"
        absV >= 100 -> "%.1f"
        else -> "%.${digits}f"
    }
    val formatted = String.format(java.util.Locale.US, pattern, this)
    return if (formatted.contains('.')) {
        formatted.trimEnd('0').trimEnd('.').ifEmpty { "0" }
    } else {
        formatted
    }
}

/** Live values extracted from the energy-flow graph for the animated diagram. */
data class FlowMetrics(
    val pv: Double? = null,
    val load: Double? = null,
    val grid: Double? = null,
    val battery: Double? = null,
    val soc: Double? = null,
    val inverter: Double? = null,
) {
    val hasData: Boolean
        get() = pv != null || load != null || grid != null || battery != null || soc != null

    /** Positive = exporting to the grid, negative = importing. */
    val gridExporting: Boolean
        get() = (grid ?: 0.0) >= 0

    companion object {
        private fun nodeKey(name: String?): String =
            name.orEmpty().lowercase().substringAfterLast('.')

        fun from(flow: EnergyFlowData?): FlowMetrics? {
            val nodes = flow?.flow?.nodes ?: return null
            if (nodes.isEmpty()) return null

            var pv: Double? = null
            var load: Double? = null
            var grid: Double? = null
            var battery: Double? = null
            var soc: Double? = null
            var inverter: Double? = null

            nodes.forEach { node ->
                val key = nodeKey(node.name ?: node.id)
                val value = parseNum(node.description?.value) ?: parseNum(node.value)
                val tips = node.deviceTips
                when {
                    key.contains("battery") || key.contains("energy_store") || key == "ess" -> {
                        soc = parseNum(tips?.get("SOC")) ?: soc
                        battery = parseNum(tips?.get("BATTERY_POWER")) ?: value ?: battery
                    }
                    key.contains("load") || key.contains("house") -> load = value ?: load
                    key.contains("pv") || key.contains("string") || key.contains("solar") ->
                        pv = value ?: pv
                    key.contains("inverter") ->
                        inverter = parseNum(tips?.get("ACTIVE_POWER")) ?: value ?: inverter
                    key.contains("grid") || key.contains("meter") ->
                        grid = parseNum(tips?.get("ACTIVE_POWER")) ?: value ?: grid
                }
            }
            if (pv == null) pv = inverter

            // Some hosts only expose the grid value on a buy/sell link label.
            if (grid == null) {
                flow.flow.links?.forEach { link ->
                    val label = link.description?.label.orEmpty().lowercase()
                    val value = parseNum(link.description?.value)
                    if (value != null && (label.contains("buy") || label.contains("sell") ||
                            label.contains("grid") || label.contains("feed"))
                    ) {
                        grid = value
                    }
                }
            }

            val result = FlowMetrics(pv, load, grid, battery, soc, inverter)
            return result.takeIf { it.hasData }
        }
    }
}

/** Where each kilowatt actually goes, derived from the live node values. */
enum class GridDirection { IMPORT, EXPORT, IDLE }

data class FlowAllocation(
    val pvToHome: Double = 0.0,
    val pvToBattery: Double = 0.0,
    val pvToGrid: Double = 0.0,
    val batteryToHome: Double = 0.0,
    val batteryToGrid: Double = 0.0,
    val gridToHome: Double = 0.0,
    val gridToBattery: Double = 0.0,
    val gridImport: Double = 0.0,
    val gridExport: Double = 0.0,
    val batteryCharge: Double = 0.0,
    val batteryDischarge: Double = 0.0,
    val selfConsumption: Double = 0.0,
) {
    val gridDirection: GridDirection
        get() = when {
            gridImport > gridExport + 0.02 -> GridDirection.IMPORT
            gridExport > gridImport + 0.02 -> GridDirection.EXPORT
            else -> GridDirection.IDLE
        }

    val isBalanced: Boolean
        get() = gridDirection != GridDirection.IDLE ||
            batteryCharge > 0.02 || batteryDischarge > 0.02
}

/**
 * Resolves direction/sign ambiguity in the Huawei payload.
 *
 * PV and load values are reliable, so the battery sign convention is detected
 * by testing which interpretation makes the grid magnitude match the balance
 * (some hosts report charge as positive, others as negative). The grid
 * direction itself always comes from the energy balance, which is what makes
 * "import vs export" unambiguous in the UI.
 */
fun FlowMetrics.allocation(): FlowAllocation {
    val pvV = pv ?: 0.0
    val loadV = load ?: 0.0
    val battRaw = battery ?: 0.0
    val gridAbs = grid?.let { abs(it) }

    var charge: Double
    var discharge: Double
    if (pv != null && load != null && gridAbs != null) {
        // export = pv - load - battery if positive means charging
        val exportIfPositiveCharges = pvV - loadV - battRaw
        val exportIfPositiveDischarges = pvV - loadV + battRaw
        val positiveCharges =
            abs(abs(exportIfPositiveCharges) - gridAbs) <=
                abs(abs(exportIfPositiveDischarges) - gridAbs)
        charge = if (positiveCharges) max(battRaw, 0.0) else max(-battRaw, 0.0)
        discharge = if (positiveCharges) max(-battRaw, 0.0) else max(battRaw, 0.0)
    } else {
        charge = max(battRaw, 0.0)
        discharge = max(-battRaw, 0.0)
    }

    val net = pvV + discharge - loadV - charge
    val import: Double
    val export: Double
    if (pv != null && load != null) {
        val magnitude = gridAbs ?: abs(net)
        if (net >= 0.0) {
            export = magnitude
            import = 0.0
        } else {
            import = magnitude
            export = 0.0
        }
    } else if (gridAbs != null) {
        // No balance available: fall back to the raw sign. A negative grid
        // value is treated as import, positive as export.
        val raw = grid ?: 0.0
        import = if (raw < 0) gridAbs else 0.0
        export = if (raw >= 0) gridAbs else 0.0
    } else {
        import = 0.0
        export = 0.0
    }

    val pvToHome = min(pvV, loadV)
    val pvLeft = max(pvV - pvToHome, 0.0)
    val pvToBattery = min(pvLeft, charge)
    val pvToGrid = min(max(pvLeft - pvToBattery, 0.0), export)

    var loadLeft = max(loadV - pvToHome, 0.0)
    val batteryToHome = min(discharge, loadLeft)
    loadLeft -= batteryToHome
    val gridToHome = min(import, loadLeft)

    val batteryToGrid = min(max(discharge - batteryToHome, 0.0), max(export - pvToGrid, 0.0))
    val gridToBattery = min(max(import - gridToHome, 0.0), max(charge - pvToBattery, 0.0))
    val selfConsumption = pvToHome + pvToBattery

    return FlowAllocation(
        pvToHome = pvToHome,
        pvToBattery = pvToBattery,
        pvToGrid = pvToGrid,
        batteryToHome = batteryToHome,
        batteryToGrid = batteryToGrid,
        gridToHome = gridToHome,
        gridToBattery = gridToBattery,
        gridImport = import,
        gridExport = export,
        batteryCharge = charge,
        batteryDischarge = discharge,
        selfConsumption = selfConsumption,
    )
}

/** Convenience view over a station list item for the dashboard. */
data class StationMetrics(
    val currentPower: Double?,
    val dailyEnergy: Double?,
    val cumulativeEnergy: Double?,
    val batterySoc: Double?,
    val capacity: Double?,
) {
    val powerFraction: Float
        get() {
            val cap = capacity ?: return 0f
            if (cap <= 0.0) return 0f
            return ((currentPower ?: 0.0) / cap).coerceIn(0.0, 1.0).toFloat()
        }

    companion object {
        fun from(station: Station?): StationMetrics {
            if (station == null) return StationMetrics(null, null, null, null, null)
            return StationMetrics(
                currentPower = parseNum(station.currentPower),
                dailyEnergy = parseNum(station.dailyEnergy),
                cumulativeEnergy = parseNum(station.cumulativeEnergy),
                batterySoc = parseNum(station.batterySoc),
                capacity = parseNum(station.installedCapacity),
            )
        }
    }
}
