package com.fusionlite.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fusionlite.data.FlowAllocation
import com.fusionlite.data.FlowMetrics
import com.fusionlite.data.GridDirection
import com.fusionlite.data.allocation
import com.fusionlite.data.clean
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Animated hero artwork: a sun, a tilted PV array and current pulses flowing
 * from the panels to the house. Animation speed/particle count follow the live
 * production fraction so the scene "breathes" with the plant.
 */
@Composable
fun SolarPanelScene(
    powerFraction: Float,
    modifier: Modifier = Modifier,
    showHouse: Boolean = true,
) {
    val energy = powerFraction.coerceIn(0f, 1f)
    val fraction = max(energy, 0.02f)
    val dark = LocalFusionPalette.current.dark

    // Real-time production drives the scene tempo: ~0.35x at night/zero output
    // up to 2x at full capacity. The phases are integrated with
    // withFrameNanos instead of fixed infinite tweens so a refresh changing
    // power (every 15-60 s) changes the speed without snapping the animation.
    val speed = 0.35f + 1.65f * energy
    val currentSpeed = rememberUpdatedState(speed)

    var sweep by remember { mutableFloatStateOf(0f) }
    var pulse by remember { mutableFloatStateOf(0f) }
    var flow by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        val cycle = (2 * PI).toFloat()
        var lastFrame = 0L
        while (true) {
            withFrameNanos { now ->
                if (lastFrame != 0L) {
                    // Clamp after a pause/background jump so the phase does not leap.
                    val dt = ((now - lastFrame) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.05f)
                    val k = currentSpeed.value
                    sweep = (sweep + dt / 4.2f * k) % 1f
                    pulse = (pulse + dt / 5.2f * k * cycle) % cycle
                    flow = (flow + dt / 2.6f * k) % 1f
                }
                lastFrame = now
            }
        }
    }

    Canvas(modifier) {
        drawRect(
            Brush.verticalGradient(
                if (dark) {
                    listOf(Color(0xFF0A1730), Color(0xFF0C2243), Color(0xFF07101F))
                } else {
                    // Daylight sky for the white theme.
                    listOf(Color(0xFF8FC4F2), Color(0xFFBFDFFA), Color(0xFFEAF4FF))
                }
            )
        )
        if (dark) drawStars(pulse)
        drawSun(Offset(size.width * 0.80f, size.height * 0.24f), size.minDimension * 0.075f, pulse, fraction)
        drawPanels(Offset(size.width * 0.04f, size.height * 0.40f), size.width * 0.52f, size.height * 0.52f, fraction, sweep)
        if (showHouse) {
            val houseCenter = Offset(size.width * 0.76f, size.height * 0.72f)
            drawHouse(houseCenter, size.minDimension * 0.11f, fraction, dark)
            drawPowerFlow(
                from = Offset(size.width * 0.46f, size.height * 0.50f),
                to = Offset(houseCenter.x - size.minDimension * 0.09f, houseCenter.y - size.minDimension * 0.02f),
                progress = flow,
                intensity = fraction,
            )
        }
    }
}

private fun DrawScope.drawStars(phase: Float) {
    val seed = 17
    var value = seed
    repeat(26) { i ->
        value = (value * 1103515245 + 12345) and 0x7fffffff
        val x = (value % 1000) / 1000f * size.width
        value = (value * 1103515245 + 12345) and 0x7fffffff
        val y = (value % 1000) / 1000f * size.height * 0.62f
        val twinkle = 0.25f + 0.55f * (((sin(phase + i) + 1f) / 2f))
        drawCircle(Color.White.copy(alpha = twinkle * 0.55f), radius = 1.1f + (i % 3) * 0.5f, center = Offset(x, y))
    }
}

private fun DrawScope.drawSun(center: Offset, radius: Float, phase: Float, fraction: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                SolarGold.copy(alpha = 0.32f + 0.18f * fraction),
                SolarAmber.copy(alpha = 0.10f),
                Color.Transparent,
            ),
            center = center,
            radius = radius * 4.2f,
        ),
        radius = radius * 4.2f,
        center = center,
    )
    rotate(degrees = phase * 180f / PI.toFloat(), pivot = center) {
        repeat(12) { i ->
            val angle = i * (2 * PI / 12)
            val inner = radius * 1.35f
            val outer = radius * (1.75f + 0.18f * sin(phase + i))
            drawLine(
                color = SolarGold.copy(alpha = 0.30f + 0.25f * fraction),
                start = Offset(center.x + cos(angle).toFloat() * inner, center.y + sin(angle).toFloat() * inner),
                end = Offset(center.x + cos(angle).toFloat() * outer, center.y + sin(angle).toFloat() * outer),
                strokeWidth = 2.2f,
                cap = StrokeCap.Round,
            )
        }
    }
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFFF2C4), SolarGold, SolarAmber),
            center = center,
            radius = radius * 1.1f,
        ),
        radius = radius,
        center = center,
    )
}

/** Tilted 3x2 array with cyan cell grid and a moving specular sweep. */
private fun DrawScope.drawPanels(origin: Offset, width: Float, height: Float, fraction: Float, sweep: Float) {
    val topInset = width * 0.13f
    val topY = 0f
    val bottomY = height
    val topLeft = origin.x + topInset
    val topRight = origin.x + width - topInset
    val bottomLeft = origin.x
    val bottomRight = origin.x + width
    val cols = 3
    val rows = 2
    val panelGap = width * 0.014f

    val group = Path()
    group.moveTo(topLeft, origin.y + topY)
    group.lineTo(topRight, origin.y + topY)
    group.lineTo(bottomRight, origin.y + bottomY)
    group.lineTo(bottomLeft, origin.y + bottomY)
    group.close()

    // Soft glow under the array, brighter with production.
    drawOval(
        brush = Brush.radialGradient(
            listOf(SolarCyan.copy(alpha = 0.16f * fraction + 0.05f), Color.Transparent),
            center = Offset(origin.x + width / 2, origin.y + height),
            radius = width * 0.75f,
        ),
        topLeft = Offset(origin.x - width * 0.1f, origin.y + height * 0.72f),
        size = Size(width * 1.2f, height * 0.5f),
    )

    for (row in 0 until rows) {
        for (col in 0 until cols) {
            val c0 = col.toFloat() / cols
            val c1 = (col + 1f) / cols
            val r0 = row.toFloat() / rows
            val r1 = (row + 1f) / rows

            fun xAt(t: Float, xTop: Float, xBottom: Float) = xTop + (xBottom - xTop) * t
            fun point(c: Float, r: Float): Offset {
                val y = origin.y + (topY + (bottomY - topY) * r)
                val left = xAt(r, topLeft, bottomLeft)
                val right = xAt(r, topRight, bottomRight)
                return Offset(left + (right - left) * c, y)
            }

            val padC = panelGap / width
            val padR = panelGap / height
            val p0 = point(c0 + padC, r0 + padR)
            val p1 = point(c1 - padC, r0 + padR)
            val p2 = point(c1 - padC, r1 - padR)
            val p3 = point(c0 + padC, r1 - padR)
            val panel = Path().apply {
                moveTo(p0.x, p0.y)
                lineTo(p1.x, p1.y)
                lineTo(p2.x, p2.y)
                lineTo(p3.x, p3.y)
                close()
            }
            drawPath(
                panel,
                brush = Brush.linearGradient(
                    listOf(Color(0xFF123A66), Color(0xFF0A2748), Color(0xFF071C35)),
                    start = p0,
                    end = p2,
                ),
            )
            drawPath(
                panel,
                color = SolarCyan.copy(alpha = 0.30f + 0.45f * fraction),
                style = Stroke(width = 1.6f),
            )
            // Cell grid
            for (k in 1 until 4) {
                val t = k / 4f
                val a = Offset(p0.x + (p3.x - p0.x) * t, p0.y + (p3.y - p0.y) * t)
                val b = Offset(p1.x + (p2.x - p1.x) * t, p1.y + (p2.y - p1.y) * t)
                drawLine(Color(0xFF2E6FA8).copy(alpha = 0.55f), a, b, strokeWidth = 0.9f)
            }
            for (k in 1 until 3) {
                val t = k / 3f
                val a = Offset(p0.x + (p1.x - p0.x) * t, p0.y + (p1.y - p0.y) * t)
                val b = Offset(p3.x + (p2.x - p3.x) * t, p3.y + (p2.y - p3.y) * t)
                drawLine(Color(0xFF2E6FA8).copy(alpha = 0.45f), a, b, strokeWidth = 0.9f)
            }
        }
    }

    // Sweep highlight clipped to the whole array.
    clipPath(group) {
        val bandX = origin.x - width * 0.3f + (width * 1.6f) * sweep
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.10f + 0.16f * fraction),
                    Color.Transparent,
                ),
                start = Offset(bandX - width * 0.12f, origin.y),
                end = Offset(bandX + width * 0.12f, origin.y + height),
            ),
            topLeft = Offset(bandX - width * 0.12f, origin.y),
            size = Size(width * 0.24f, height),
        )
    }

    // Support legs + ground line.
    drawLine(
        Color(0xFF8FA9C9).copy(alpha = 0.55f),
        Offset(origin.x + width * 0.24f, origin.y + height),
        Offset(origin.x + width * 0.22f, origin.y + height + height * 0.10f),
        strokeWidth = 3f,
    )
    drawLine(
        Color(0xFF8FA9C9).copy(alpha = 0.55f),
        Offset(origin.x + width * 0.74f, origin.y + height),
        Offset(origin.x + width * 0.78f, origin.y + height + height * 0.10f),
        strokeWidth = 3f,
    )
}

private fun DrawScope.drawHouse(center: Offset, r: Float, fraction: Float, dark: Boolean) {
    val bodyTop = center.y - r * 0.10f
    val bodyBottom = center.y + r * 0.85f
    drawRoundRect(
        color = if (dark) Color(0xFF132743) else Color(0xFFF7FBFF),
        topLeft = Offset(center.x - r * 0.72f, bodyTop),
        size = Size(r * 1.44f, bodyBottom - bodyTop),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f),
    )
    drawRoundRect(
        color = SolarCyan.copy(alpha = if (dark) 0.42f else 0.75f),
        topLeft = Offset(center.x - r * 0.72f, bodyTop),
        size = Size(r * 1.44f, bodyBottom - bodyTop),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f),
        style = Stroke(width = 1.6f),
    )
    val roof = Path().apply {
        moveTo(center.x - r * 0.94f, bodyTop)
        lineTo(center.x, bodyTop - r * 0.72f)
        lineTo(center.x + r * 0.94f, bodyTop)
        close()
    }
    drawPath(roof, color = if (dark) Color(0xFF1B3558) else Color(0xFFE3EEFB))
    drawPath(roof, color = SolarGold.copy(alpha = 0.55f), style = Stroke(width = 1.8f))
    // Windows
    val windowGlow = 0.35f + 0.55f * fraction
    drawRoundRect(
        color = SolarGold.copy(alpha = windowGlow),
        topLeft = Offset(center.x - r * 0.42f, bodyTop + r * 0.24f),
        size = Size(r * 0.34f, r * 0.30f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f),
    )
    drawRoundRect(
        color = SolarGold.copy(alpha = windowGlow * 0.8f),
        topLeft = Offset(center.x + r * 0.08f, bodyTop + r * 0.24f),
        size = Size(r * 0.34f, r * 0.30f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f),
    )
    drawRoundRect(
        color = SolarCyan.copy(alpha = 0.6f),
        topLeft = Offset(center.x - r * 0.12f, bodyBottom - r * 0.34f),
        size = Size(r * 0.24f, r * 0.34f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f),
    )
}

private fun DrawScope.drawPowerFlow(from: Offset, to: Offset, progress: Float, intensity: Float) {
    val path = Path().apply {
        moveTo(from.x, from.y)
        cubicTo(
            from.x + size.width * 0.09f, from.y - size.height * 0.06f,
            to.x - size.width * 0.10f, to.y - size.height * 0.10f,
            to.x, to.y,
        )
    }
    drawPath(
        path,
        color = SolarCyan.copy(alpha = 0.18f + 0.25f * intensity),
        style = Stroke(width = 2.4f, cap = StrokeCap.Round),
    )
    drawParticles(path, progress, count = 3 + (intensity * 5).toInt(), color = SolarCyan)
}

private fun DrawScope.drawParticles(path: Path, progress: Float, count: Int, color: Color) {
    val measure = PathMeasure().apply { setPath(path, false) }
    val length = measure.length
    if (length <= 0f) return
    repeat(count.coerceAtLeast(1)) { i ->
        val fraction = ((progress + i.toFloat() / count.coerceAtLeast(1)) % 1f)
        val position = measure.getPosition(length * fraction)
        val fade = sin(fraction * PI).toFloat()
        drawCircle(color.copy(alpha = 0.25f + 0.65f * fade), radius = 2.6f + 1.3f * fade, center = position)
    }
}


private enum class FlowIcon { SUN, HOME, GRID, BATTERY }

/** One active connection, already oriented from source to destination. */
private data class FlowEdge(
    val start: Offset,
    val end: Offset,
    val power: Double,
    val color: Color,
)

/**
 * Hub-and-spoke energy flow.
 *
 * All four nodes connect to a central inverter hub, which is how a real PV
 * system is wired: every kilowatt (PV, grid, battery) passes through the hub
 * before reaching the house. Straight spokes mean **no crossing lines**, and
 * each node carries its own value, so the diagram stays readable at a glance.
 * Active spokes animate particles in the actual direction of travel.
 */
@Composable
fun EnergyFlowDiagram(
    metrics: FlowMetrics?,
    modifier: Modifier = Modifier,
    allocation: FlowAllocation? = metrics?.allocation(),
) {
    val palette = LocalFusionPalette.current
    val outline = MaterialTheme.colorScheme.outline
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val textPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }

    val alloc = allocation
    val pv = metrics?.pv ?: 0.0
    val load = metrics?.load ?: 0.0
    val gridImport = alloc?.gridImport ?: 0.0
    val gridExport = alloc?.gridExport ?: 0.0
    val charge = alloc?.batteryCharge ?: 0.0
    val discharge = alloc?.batteryDischarge ?: 0.0
    val soc = metrics?.soc
    val hasBattery = metrics?.battery != null || soc != null

    // One clock drives every particle so the flows stay in sync and the speed
    // can follow live power without snapping (same trick as SolarPanelScene).
    var phase by remember { mutableFloatStateOf(0f) }
    val activity = (pv + discharge + gridImport).coerceIn(0.0, 8.0)
    val activityState = rememberUpdatedState(activity)
    LaunchedEffect(Unit) {
        var lastFrame = 0L
        while (true) {
            withFrameNanos { now ->
                if (lastFrame != 0L) {
                    val dt = ((now - lastFrame) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.05f)
                    val speed = 0.08f + 0.16f * (activityState.value / 6.0).toFloat().coerceIn(0f, 1f)
                    phase = (phase + dt * speed) % 1f
                }
                lastFrame = now
            }
        }
    }

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val cardW = min(w * 0.30f, 96.dp.toPx())
        val cardH = min(54.dp.toPx(), h * 0.22f)
        val margin = 2.dp.toPx()
        val hubR = min(24.dp.toPx(), w * 0.08f)
        val hub = Offset(w / 2f, h / 2f)

        val pvC = Offset(w / 2f, margin + cardH / 2f)
        val homeC = Offset(w / 2f, h - margin - cardH / 2f)
        val gridC = Offset(margin + cardW / 2f, h / 2f)
        val battC = Offset(w - margin - cardW / 2f, h / 2f)

        val pvAnchor = Offset(pvC.x, pvC.y + cardH / 2f)
        val homeAnchor = Offset(homeC.x, homeC.y - cardH / 2f)
        val gridAnchor = Offset(gridC.x + cardW / 2f, gridC.y)
        val battAnchor = Offset(battC.x - cardW / 2f, battC.y)
        val hubTop = Offset(hub.x, hub.y - hubR)
        val hubBottom = Offset(hub.x, hub.y + hubR)
        val hubLeft = Offset(hub.x - hubR, hub.y)
        val hubRight = Offset(hub.x + hubR, hub.y)

        // Faint topology, always visible even at night.
        val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx()))
        listOf(
            pvAnchor to hubTop,
            homeAnchor to hubBottom,
            gridAnchor to hubLeft,
            battAnchor to hubRight,
        ).forEach { (a, b) ->
            drawLine(
                color = outline.copy(alpha = 0.30f),
                start = a,
                end = b,
                strokeWidth = 1.1.dp.toPx(),
                pathEffect = dash,
            )
        }

        val edges = buildList {
            if (pv > 0.02) add(FlowEdge(pvAnchor, hubTop, pv, palette.gold))
            if (load > 0.02) add(FlowEdge(hubBottom, homeAnchor, load, palette.cyan))
            if (gridImport > 0.02) {
                add(FlowEdge(gridAnchor, hubLeft, gridImport, palette.amber))
            } else if (gridExport > 0.02) {
                add(FlowEdge(hubLeft, gridAnchor, gridExport, palette.green))
            }
            if (hasBattery) {
                if (charge > 0.02) {
                    add(FlowEdge(hubRight, battAnchor, charge, palette.green))
                } else if (discharge > 0.02) {
                    add(FlowEdge(battAnchor, hubRight, discharge, palette.green))
                }
            }
        }
        edges.forEach { edge ->
            drawLine(
                color = edge.color.copy(alpha = 0.16f),
                start = edge.start,
                end = edge.end,
                strokeWidth = 9.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = edge.color.copy(alpha = 0.82f),
                start = edge.start,
                end = edge.end,
                strokeWidth = 2.2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawEdgeParticles(edge, phase)
            drawEdgeArrow(edge)
        }

        drawHub(hub, hubR, phase, palette, activity > 0.02)

        val pvValue = if (metrics?.pv == null) "—" else "${pv.clean(1)} kW"
        val loadValue = if (metrics?.load == null) "—" else "${load.clean(1)} kW"
        val gridValue =
            if (metrics?.grid == null) "—" else "${max(gridImport, gridExport).clean(1)} kW"
        val batteryValue = if (!hasBattery) "—" else "${max(charge, discharge).clean(1)} kW"
        val gridState = when {
            gridImport > 0.02 -> "importing"
            gridExport > 0.02 -> "exporting"
            metrics?.grid == null -> "no meter"
            else -> "idle"
        }
        val gridColor = when {
            gridImport > 0.02 -> palette.amber
            gridExport > 0.02 -> palette.green
            else -> palette.blue
        }
        val batteryState = when {
            !hasBattery -> "no battery"
            charge > 0.02 -> "charging"
            discharge > 0.02 -> "supplying"
            else -> "idle"
        }

        drawFlowCard(
            pvC, cardW, cardH, palette.gold, FlowIcon.SUN, "Solar",
            pvValue, if (pv > 0.02) "producing" else "idle",
            pv > 0.02, palette, muted, textPaint,
        )
        drawFlowCard(
            homeC, cardW, cardH, palette.cyan, FlowIcon.HOME, "Home",
            loadValue, if (load > 0.02) "consuming" else "idle",
            load > 0.02, palette, muted, textPaint,
        )
        drawFlowCard(
            gridC, cardW, cardH, gridColor, FlowIcon.GRID, "Grid",
            gridValue, gridState,
            max(gridImport, gridExport) > 0.02, palette, muted, textPaint,
        )
        drawFlowCard(
            battC, cardW, cardH, palette.green, FlowIcon.BATTERY, "Battery",
            batteryValue, batteryState,
            charge > 0.02 || discharge > 0.02, palette, muted, textPaint,
        )
    }
}

private fun DrawScope.drawEdgeParticles(edge: FlowEdge, phase: Float) {
    val delta = edge.end - edge.start
    val length = delta.getDistance()
    if (length <= 1f) return
    val count = (2 + (edge.power.coerceIn(0.0, 6.0) * 0.6).toInt()).coerceIn(2, 5)
    repeat(count) { index ->
        val fraction = (phase + index.toFloat() / count) % 1f
        val position = edge.start + delta * fraction
        val fade = sin(fraction * PI).toFloat()
        drawCircle(
            color = edge.color.copy(alpha = 0.20f + 0.70f * fade),
            radius = 1.3.dp.toPx() + 1.4.dp.toPx() * fade,
            center = position,
        )
    }
}

private fun DrawScope.drawEdgeArrow(edge: FlowEdge) {
    val delta = edge.end - edge.start
    val length = delta.getDistance()
    if (length <= 1f) return
    val direction = delta / length
    val normal = Offset(-direction.y, direction.x)
    val arrow = 4.5.dp.toPx()
    val tip = edge.end - direction * 2.dp.toPx()
    val base = tip - direction * arrow
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(base.x + normal.x * arrow * 0.62f, base.y + normal.y * arrow * 0.62f)
        lineTo(base.x - normal.x * arrow * 0.62f, base.y - normal.y * arrow * 0.62f)
        close()
    }
    drawPath(path, edge.color.copy(alpha = 0.95f))
}

private fun DrawScope.drawHub(
    center: Offset,
    radius: Float,
    phase: Float,
    palette: FusionPalette,
    active: Boolean,
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                palette.gold.copy(alpha = if (active) 0.20f else 0.08f),
                palette.gold.copy(alpha = 0f),
            ),
            center = center,
            radius = radius * 2.0f,
        ),
        radius = radius * 2.0f,
        center = center,
    )
    rotate(degrees = phase * 360f, pivot = center) {
        drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(palette.gold, palette.green, palette.cyan, palette.gold),
                center = center,
            ),
            radius = radius,
            center = center,
            style = Stroke(width = 2.dp.toPx()),
            alpha = if (active) 0.95f else 0.45f,
        )
    }
    drawCircle(palette.surfaceHigh, radius = radius * 0.86f, center = center)
    drawCircle(
        palette.gold.copy(alpha = if (active) 0.16f else 0.05f),
        radius = radius * 0.86f,
        center = center,
    )

    val s = radius * 0.48f
    val bolt = Path().apply {
        moveTo(center.x + s * 0.10f, center.y - s)
        lineTo(center.x - s * 0.55f, center.y + s * 0.12f)
        lineTo(center.x - s * 0.08f, center.y + s * 0.12f)
        lineTo(center.x - s * 0.18f, center.y + s)
        lineTo(center.x + s * 0.55f, center.y - s * 0.14f)
        lineTo(center.x + s * 0.08f, center.y - s * 0.14f)
        close()
    }
    drawPath(bolt, palette.gold.copy(alpha = if (active) 1f else 0.55f))

    repeat(3) { index ->
        val angle = (phase * 2f * PI + index * 2f * PI / 3f).toFloat()
        val dot = Offset(
            center.x + cos(angle) * radius * 1.30f,
            center.y + sin(angle) * radius * 1.30f,
        )
        drawCircle(palette.gold.copy(alpha = 0.40f), radius = 1.5.dp.toPx(), center = dot)
    }
}

private fun DrawScope.drawFlowCard(
    center: Offset,
    w: Float,
    h: Float,
    accent: Color,
    icon: FlowIcon,
    label: String,
    value: String,
    state: String,
    active: Boolean,
    palette: FusionPalette,
    muted: Color,
    paint: Paint,
) {
    val left = center.x - w / 2f
    val top = center.y - h / 2f
    val corner = 14.dp.toPx()
    drawRoundRect(
        color = palette.surfaceHigh.copy(alpha = if (active) 1f else 0.72f),
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(corner),
    )
    drawRoundRect(
        color = accent.copy(alpha = if (active) 0.55f else 0.22f),
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(corner),
        style = Stroke(width = 1.dp.toPx()),
    )
    drawFlowIcon(
        icon = icon,
        center = Offset(left + 16.dp.toPx(), center.y),
        radius = 7.dp.toPx(),
        color = accent.copy(alpha = if (active) 0.95f else 0.50f),
    )
    val textX = left + 31.dp.toPx()
    drawTextLine(paint, label.uppercase(), textX, center.y - 9.dp.toPx(), 8f, muted, true)
    drawTextLine(paint, value, textX, center.y + 5.dp.toPx(), 14f, accent, true)
    drawTextLine(paint, state, textX, center.y + 17.dp.toPx(), 8f, muted, false)
}

private fun DrawScope.drawTextLine(
    paint: Paint,
    text: String,
    x: Float,
    baselineY: Float,
    sizeSp: Float,
    color: Color,
    bold: Boolean,
) {
    paint.textSize = sizeSp.sp.toPx()
    paint.color = color.toArgb()
    paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    paint.textAlign = Paint.Align.LEFT
    drawContext.canvas.nativeCanvas.drawText(text, x, baselineY, paint)
}

private fun DrawScope.drawFlowIcon(icon: FlowIcon, center: Offset, radius: Float, color: Color) {
    when (icon) {
        FlowIcon.SUN -> {
            drawCircle(color, radius = radius * 0.38f, center = center)
            repeat(8) { index ->
                val angle = index * (PI / 4)
                val dx = cos(angle).toFloat()
                val dy = sin(angle).toFloat()
                drawLine(
                    color = color,
                    start = Offset(center.x + dx * radius * 0.62f, center.y + dy * radius * 0.62f),
                    end = Offset(center.x + dx * radius, center.y + dy * radius),
                    strokeWidth = 1.2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }

        FlowIcon.HOME -> {
            val roof = Path().apply {
                moveTo(center.x - radius, center.y)
                lineTo(center.x, center.y - radius * 0.85f)
                lineTo(center.x + radius, center.y)
            }
            drawPath(
                roof,
                color,
                style = Stroke(
                    width = 1.4.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
            drawRoundRect(
                color = color,
                topLeft = Offset(center.x - radius * 0.62f, center.y + radius * 0.02f),
                size = Size(radius * 1.24f, radius * 0.9f),
                cornerRadius = CornerRadius(1.dp.toPx()),
                style = Stroke(width = 1.4.dp.toPx()),
            )
            drawRect(
                color = color.copy(alpha = 0.65f),
                topLeft = Offset(center.x - radius * 0.16f, center.y + radius * 0.38f),
                size = Size(radius * 0.32f, radius * 0.54f),
            )
        }

        FlowIcon.GRID -> {
            val stroke = 1.3.dp.toPx()
            drawLine(
                color,
                Offset(center.x - radius * 0.62f, center.y + radius),
                Offset(center.x - radius * 0.20f, center.y - radius),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color,
                Offset(center.x + radius * 0.62f, center.y + radius),
                Offset(center.x + radius * 0.20f, center.y - radius),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            listOf(0.26f, -0.14f, -0.54f).forEach { level ->
                drawLine(
                    color.copy(alpha = 0.8f),
                    Offset(center.x - radius * 0.72f, center.y + radius * level),
                    Offset(center.x + radius * 0.72f, center.y + radius * level),
                    strokeWidth = stroke,
                )
            }
            drawLine(
                color,
                Offset(center.x - radius * 0.28f, center.y - radius),
                Offset(center.x + radius * 0.28f, center.y - radius),
                strokeWidth = stroke,
            )
        }

        FlowIcon.BATTERY -> {
            val bodyWidth = radius * 1.5f
            val bodyHeight = radius
            drawRoundRect(
                color = color,
                topLeft = Offset(center.x - bodyWidth / 2f, center.y - bodyHeight / 2f),
                size = Size(bodyWidth, bodyHeight),
                cornerRadius = CornerRadius(1.6.dp.toPx()),
                style = Stroke(width = 1.4.dp.toPx()),
            )
            drawRoundRect(
                color = color,
                topLeft = Offset(center.x + bodyWidth / 2f + 1.dp.toPx(), center.y - bodyHeight * 0.22f),
                size = Size(2.dp.toPx(), bodyHeight * 0.44f),
                cornerRadius = CornerRadius(1.dp.toPx()),
            )
            drawRoundRect(
                color = color.copy(alpha = 0.55f),
                topLeft = Offset(center.x - bodyWidth / 2f + 2.dp.toPx(), center.y - bodyHeight / 2f + 2.dp.toPx()),
                size = Size(bodyWidth * 0.42f, bodyHeight - 4.dp.toPx()),
                cornerRadius = CornerRadius(1.dp.toPx()),
            )
        }
    }
}

/**
 * Destination (or source) breakdown under the diagram.
 *
 * While the sun is up it answers "where does the solar go?"; at night it
 * flips to "where does the house energy come from?". Three segments with
 * matching legends make the split readable without a table.
 */
@Composable
fun FlowAllocationBar(metrics: FlowMetrics, allocation: FlowAllocation?) {
    val palette = LocalFusionPalette.current
    val alloc = allocation ?: return
    val pv = metrics.pv ?: 0.0
    val solarMode = pv > 0.05
    val items: List<Triple<String, Double, Color>> = if (solarMode) {
        listOf(
            Triple("Home", alloc.pvToHome, palette.cyan),
            Triple("Battery", alloc.pvToBattery, palette.green),
            Triple("Grid", alloc.pvToGrid, palette.blue),
        )
    } else {
        listOf(
            Triple("Solar", alloc.pvToHome, palette.gold),
            Triple("Battery", alloc.batteryToHome, palette.green),
            Triple("Grid", alloc.gridToHome, palette.amber),
        )
    }
    val animated = items.map { item ->
        animateFloatAsState(
            targetValue = item.second.toFloat().coerceAtLeast(0f),
            animationSpec = tween(650),
            label = "alloc-${item.first}",
        ).value
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(Modifier.padding(top = 2.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (solarMode) "WHERE THE SOLAR GOES" else "WHERE THE HOME ENERGY COMES FROM",
                fontSize = 9.sp,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.SemiBold,
                color = muted,
            )
            Text(
                if (solarMode) {
                    "${((alloc.selfConsumption / pv) * 100).coerceIn(0.0, 100.0).toInt()}% self-used"
                } else {
                    "${(metrics.load ?: 0.0).clean(1)} kW load"
                },
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = palette.gold,
            )
        }
        Spacer(Modifier.height(7.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            items.forEachIndexed { index, item ->
                val weight = animated[index]
                if (weight > 0.0001f) {
                    Box(Modifier.weight(weight).fillMaxHeight().background(item.third))
                    if (index < items.lastIndex) {
                        Spacer(
                            Modifier
                                .width(1.5.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surface),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { item ->
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).background(item.third, CircleShape))
                        Spacer(Modifier.width(4.dp))
                        Text(item.first, fontSize = 9.sp, color = muted, maxLines = 1)
                    }
                    Text(
                        "${item.second.clean(1)} kW",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (item.second > 0.02) item.third else muted.copy(alpha = 0.55f),
                    )
                }
            }
        }
    }
}
