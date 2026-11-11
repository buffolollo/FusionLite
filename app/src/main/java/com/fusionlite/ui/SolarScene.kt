package com.fusionlite.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fusionlite.data.FlowAllocation
import com.fusionlite.data.FlowMetrics
import com.fusionlite.data.GridDirection
import com.fusionlite.data.allocation
import com.fusionlite.data.clean
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

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

/** A directed energy connection between two nodes. */
private data class QuadLink(
    val from: Offset,
    val to: Offset,
    val control: Offset,
    val power: Double,
    val color: Color,
    val labelT: Float = 0.5f,
)

/**
 * Diamond topology (solar top, battery right, home bottom, grid left) so every
 * connection has a clear direction. Active links animate particles from source
 * to destination, carry an arrow head and show the kW travelling on that edge.
 */
@Composable
fun EnergyFlowDiagram(
    metrics: FlowMetrics?,
    modifier: Modifier = Modifier,
    allocation: FlowAllocation? = metrics?.allocation(),
) {
    val infinite = rememberInfiniteTransition(label = "flow-diagram")
    val progress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "particles",
    )
    val labelPaint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    val labelBackground = MaterialTheme.colorScheme.surface
    val labelText = MaterialTheme.colorScheme.onSurface
    val skeletonColor = MaterialTheme.colorScheme.onSurfaceVariant

    BoxWithConstraints(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val pv = Offset(w * 0.50f, h * 0.31f)
            val battery = Offset(w * 0.72f, h * 0.50f)
            val home = Offset(w * 0.50f, h * 0.69f)
            val grid = Offset(w * 0.28f, h * 0.50f)

            val pvHome = Offset(w * 0.30f, h * 0.53f)
            val pvBatt = Offset(w * 0.80f, h * 0.28f)
            val pvGrid = Offset(w * 0.20f, h * 0.28f)
            val gridHome = Offset(w * 0.20f, h * 0.76f)
            val battHome = Offset(w * 0.80f, h * 0.76f)
            val battGrid = Offset(w * 0.50f, h * 1.02f)
            val gridBatt = Offset(w * 0.50f, h * -0.02f)

            // Faint topology so the system structure is always readable.
            val skeleton = PathEffect.dashPathEffect(floatArrayOf(7f, 9f))
            fun skeletonLink(a: Offset, c: Offset, b: Offset) {
                drawPath(
                    quadPath(a, c, b),
                    color = skeletonColor.copy(alpha = 0.16f),
                    style = Stroke(width = 1.2f, pathEffect = skeleton),
                )
            }
            skeletonLink(pv, pvHome, home)
            skeletonLink(pv, pvBatt, battery)
            skeletonLink(pv, pvGrid, grid)
            skeletonLink(grid, gridHome, home)
            skeletonLink(battery, battHome, home)

            val links = mutableListOf<QuadLink>()
            val alloc = allocation
            if (alloc != null) {
                fun add(from: Offset, to: Offset, control: Offset, power: Double, color: Color) {
                    if (power > 0.02) links += QuadLink(from, to, control, power, color)
                }
                add(pv, home, pvHome, alloc.pvToHome, SolarGold)
                add(pv, battery, pvBatt, alloc.pvToBattery, SolarCyan)
                add(pv, grid, pvGrid, alloc.pvToGrid, SolarGold)
                add(grid, home, gridHome, alloc.gridToHome, SolarAmber)
                add(battery, home, battHome, alloc.batteryToHome, SolarGreen)
                add(battery, grid, battGrid, alloc.batteryToGrid, SolarGreen)
                add(grid, battery, gridBatt, alloc.gridToBattery, SolarAmber)
            }

            links.forEach { link ->
                val path = quadPath(link.from, link.control, link.to)
                drawPath(
                    path,
                    color = link.color.copy(alpha = 0.12f),
                    style = Stroke(width = 9f, cap = StrokeCap.Round),
                )
                drawPath(
                    path,
                    color = link.color.copy(alpha = 0.72f),
                    style = Stroke(width = 2.5f, cap = StrokeCap.Round),
                )
                drawParticles(path, progress, count = 3 + (link.power.coerceAtMost(6.0) * 0.8).toInt(), color = link.color)
                drawArrowHead(
                    point = quadPoint(link.from, link.control, link.to, 0.64f),
                    tangent = quadTangent(link.from, link.control, link.to, 0.64f),
                    color = link.color,
                )
                drawFlowLabel(
                    text = "${link.power.clean(1)} kW",
                    point = quadPoint(link.from, link.control, link.to, link.labelT),
                    paint = labelPaint,
                    background = labelBackground,
                    foreground = labelText,
                    accent = link.color,
                )
            }

            listOf(
                pv to SolarGold,
                battery to SolarGreen,
                home to SolarCyan,
                grid to SolarBlue,
            ).forEach { (center, color) ->
                drawCircle(color.copy(alpha = 0.10f), radius = 18f, center = center)
                drawCircle(color.copy(alpha = 0.65f), radius = 3.5f, center = center)
            }
        }

        val alloc = allocation
        val gridDirection = alloc?.gridDirection ?: GridDirection.IDLE
        val gridValue = when (gridDirection) {
            GridDirection.IMPORT -> alloc?.gridImport
            GridDirection.EXPORT -> alloc?.gridExport
            GridDirection.IDLE -> metrics?.grid?.let { abs(it) }
        }
        val gridAccent = when (gridDirection) {
            GridDirection.IMPORT -> SolarAmber
            GridDirection.EXPORT -> SolarGreen
            GridDirection.IDLE -> SolarBlue
        }
        val gridState = when (gridDirection) {
            GridDirection.IMPORT -> "importing"
            GridDirection.EXPORT -> "exporting"
            GridDirection.IDLE -> "idle"
        }
        val batteryState = when {
            (alloc?.batteryCharge ?: 0.0) > 0.05 -> "charging"
            (alloc?.batteryDischarge ?: 0.0) > 0.05 -> "supplying"
            else -> "idle"
        }

        FlowNodeChip(
            "☀", "Solar", metrics?.pv, "kW", SolarGold, "producing",
            Modifier.align(Alignment.TopCenter),
        )
        FlowNodeChip(
            "🔋", "Battery", metrics?.soc, "%", SolarGreen, batteryState,
            Modifier.align(Alignment.CenterEnd),
        )
        FlowNodeChip(
            "🏠", "Home", metrics?.load, "kW", SolarCyan, "consuming",
            Modifier.align(Alignment.BottomCenter),
        )
        FlowNodeChip(
            "⚡", "Grid", gridValue, "kW", gridAccent, gridState,
            Modifier.align(Alignment.CenterStart),
        )
    }
}

private fun quadPath(a: Offset, c: Offset, b: Offset): Path = Path().apply {
    moveTo(a.x, a.y)
    quadraticTo(c.x, c.y, b.x, b.y)
}

private fun quadPoint(a: Offset, c: Offset, b: Offset, t: Float): Offset {
    val u = 1f - t
    return Offset(
        u * u * a.x + 2f * u * t * c.x + t * t * b.x,
        u * u * a.y + 2f * u * t * c.y + t * t * b.y,
    )
}

private fun quadTangent(a: Offset, c: Offset, b: Offset, t: Float): Offset {
    val x = 2f * (1f - t) * (c.x - a.x) + 2f * t * (b.x - c.x)
    val y = 2f * (1f - t) * (c.y - a.y) + 2f * t * (b.y - c.y)
    val length = sqrt(x * x + y * y).coerceAtLeast(0.0001f)
    return Offset(x / length, y / length)
}

private fun DrawScope.drawArrowHead(point: Offset, tangent: Offset, color: Color) {
    val size = 7f
    val normal = Offset(-tangent.y, tangent.x)
    val tip = Offset(point.x + tangent.x * size, point.y + tangent.y * size)
    val base = Offset(point.x - tangent.x * size * 0.5f, point.y - tangent.y * size * 0.5f)
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(base.x + normal.x * size * 0.8f, base.y + normal.y * size * 0.8f)
        lineTo(base.x - normal.x * size * 0.8f, base.y - normal.y * size * 0.8f)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawFlowLabel(
    text: String,
    point: Offset,
    paint: android.graphics.Paint,
    background: Color,
    foreground: Color,
    accent: Color,
) {
    paint.textSize = 10.sp.toPx()
    paint.textAlign = android.graphics.Paint.Align.CENTER
    paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
    paint.color = foreground.toArgb()
    val padH = 6.dp.toPx()
    val padV = 3.dp.toPx()
    val width = paint.measureText(text) + padH * 2
    val height = paint.textSize + padV * 2
    val topLeft = Offset(point.x - width / 2, point.y - height / 2)
    drawRoundRect(
        color = background.copy(alpha = 0.95f),
        topLeft = topLeft,
        size = Size(width, height),
        cornerRadius = CornerRadius(height / 2),
    )
    drawRoundRect(
        color = accent.copy(alpha = 0.55f),
        topLeft = topLeft,
        size = Size(width, height),
        cornerRadius = CornerRadius(height / 2),
        style = Stroke(width = 1f),
    )
    drawContext.canvas.nativeCanvas.drawText(
        text,
        point.x,
        point.y - (paint.ascent() + paint.descent()) / 2f,
        paint,
    )
}

@Composable
private fun FlowNodeChip(
    icon: String,
    label: String,
    value: Double?,
    unit: String,
    accent: Color,
    state: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .widthIn(min = 86.dp)
            .background(LocalFusionPalette.current.surfaceHigh.copy(alpha = 0.94f), RoundedCornerShape(16.dp))
            .border(1.dp, accent.copy(alpha = 0.40f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 14.sp, textAlign = TextAlign.Center)
            Text(
                " $label",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.8.sp,
            )
        }
        Text(
            text = value?.let { "${it.clean()} $unit" } ?: "—",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
        Text(
            state,
            fontSize = 9.sp,
            color = accent.copy(alpha = 0.75f),
        )
    }
}
