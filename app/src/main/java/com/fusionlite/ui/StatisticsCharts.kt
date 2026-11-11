package com.fusionlite.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fusionlite.data.SeriesPoint
import com.fusionlite.data.clean
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

private const val LABEL_ALPHA = 0.55f

private fun compact(value: Double): String = when {
    abs(value) >= 1_000_000 -> "${(value / 1_000_000.0).clean(1)}M"
    abs(value) >= 10_000 -> "${(value / 1000.0).clean(1)}k"
    abs(value) >= 1000 -> value.clean(0)
    abs(value) >= 10 -> value.clean(0)
    else -> value.clean(1)
}

private fun DrawScope.text(
    value: String,
    x: Float,
    y: Float,
    color: Color,
    sizePx: Float,
    align: android.graphics.Paint.Align = android.graphics.Paint.Align.CENTER,
    bold: Boolean = false,
) {
    val paint = android.graphics.Paint().apply {
        this.color = color.toArgb()
        textSize = sizePx
        textAlign = align
        isAntiAlias = true
        typeface = android.graphics.Typeface.create(
            android.graphics.Typeface.DEFAULT,
            if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL,
        )
    }
    drawContext.canvas.nativeCanvas.drawText(value, x, y, paint)
}

/**
 * Custom production curve: gradient area, glowing stroke, grid, and tap-to-
 * inspect buckets. Completely hand-drawn — no chart library.
 */
@Composable
fun ProductionAreaChart(
    points: List<SeriesPoint>,
    accent: Color,
    modifier: Modifier = Modifier,
    unit: String = "kWh",
    emptyLabel: String = "No production recorded for this window",
) {
    var selected by remember(points) { mutableIntStateOf(-1) }
    val palette = LocalFusionPalette.current
    val reveal = remember(points) { Animatable(0f) }
    LaunchedEffect(points) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(720))
    }

    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(points) {
                    detectTapGestures { tap ->
                        if (points.isEmpty()) return@detectTapGestures
                        val left = 38.dp.toPx()
                        val right = size.width - 8.dp.toPx()
                        val step = if (points.size <= 1) 0f else (right - left) / (points.size - 1)
                        if (step <= 0f) {
                            selected = if (selected == 0) -1 else 0
                            return@detectTapGestures
                        }
                        val index = ((tap.x - left) / step).roundToInt().coerceIn(0, points.lastIndex)
                        selected = if (selected == index) -1 else index
                    }
                },
        ) {
            if (points.isEmpty()) return@Canvas
            val left = 38.dp.toPx()
            val right = size.width - 8.dp.toPx()
            val top = 16.dp.toPx()
            val bottom = size.height - 24.dp.toPx()
            val chartH = bottom - top
            val maxValue = max(points.maxOf { it.value }, 0.01) * 1.18
            val stepX = if (points.size <= 1) 0f else (right - left) / (points.size - 1)

            // Grid + y labels
            val gridColor = palette.chartGrid
            val labelColor = palette.chartLabel
            for (i in 0..4) {
                val y = bottom - chartH * i / 4f
                drawLine(
                    gridColor,
                    Offset(left, y),
                    Offset(right, y),
                    strokeWidth = if (i == 0) 1.4f else 1f,
                )
                text(
                    compact(maxValue * i / 4.0),
                    left - 6.dp.toPx(),
                    y + 4.dp.toPx(),
                    labelColor,
                    9.sp.toPx(),
                    align = android.graphics.Paint.Align.RIGHT,
                )
            }

            fun pointAt(index: Int): Offset {
                val x = left + stepX * index
                val fraction = (points[index].value / maxValue).coerceIn(0.0, 1.0)
                val y = bottom - chartH * fraction.toFloat() * reveal.value
                return Offset(x, y)
            }

            val line = Path()
            points.indices.forEach { index ->
                val p = pointAt(index)
                if (index == 0) line.moveTo(p.x, p.y) else line.lineTo(p.x, p.y)
            }
            val area = Path().apply {
                addPath(line)
                lineTo(left + stepX * points.lastIndex, bottom)
                lineTo(left, bottom)
                close()
            }
            drawPath(
                area,
                Brush.verticalGradient(
                    listOf(
                        accent.copy(alpha = 0.34f),
                        accent.copy(alpha = 0.10f),
                        Color.Transparent,
                    ),
                    startY = top,
                    endY = bottom,
                ),
            )
            drawPath(
                line,
                Brush.horizontalGradient(listOf(SolarCyan, accent, SolarGold)),
                style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round),
            )

            // Bucket dots for sparse series.
            if (points.size in 2..14) {
                points.indices.forEach { index ->
                    val p = pointAt(index)
                    drawCircle(accent.copy(alpha = 0.20f), radius = 5.dp.toPx(), center = p)
                    drawCircle(accent, radius = 2.dp.toPx(), center = p)
                }
            }

            // X axis labels (about six, never crowded).
            val labelEvery = max(1, ceil(points.size / 6.0).toInt())
            points.indices.forEach { index ->
                if (index % labelEvery == 0 || index == points.lastIndex) {
                    val p = pointAt(index)
                    text(
                        points[index].label,
                        p.x.coerceIn(left, right),
                        size.height - 6.dp.toPx(),
                        labelColor,
                        8.5.sp.toPx(),
                    )
                }
            }

            // Selection marker.
            if (selected in points.indices) {
                val p = pointAt(selected)
                drawLine(
                    accent.copy(alpha = 0.55f),
                    Offset(p.x, top),
                    Offset(p.x, bottom),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)),
                )
                drawCircle(Color.White.copy(alpha = 0.95f), radius = 4.5.dp.toPx(), center = p)
                drawCircle(accent, radius = 7.5.dp.toPx(), center = p, style = Stroke(1.5.dp.toPx()))

                val tag = "${points[selected].label} · ${points[selected].value.clean(1)} $unit"
                val tagWidth = tag.length * 5.2.dp.toPx() + 18.dp.toPx()
                val tagX = (p.x - tagWidth / 2f).coerceIn(2.dp.toPx(), size.width - tagWidth - 2.dp.toPx())
                val tagY = (p.y - 30.dp.toPx()).coerceAtLeast(0f)
                drawRoundRect(
                    color = palette.surfaceHigh.copy(alpha = 0.96f),
                    topLeft = Offset(tagX, tagY),
                    size = Size(tagWidth, 20.dp.toPx()),
                    cornerRadius = CornerRadius(10.dp.toPx()),
                )
                drawRoundRect(
                    color = accent.copy(alpha = 0.55f),
                    topLeft = Offset(tagX, tagY),
                    size = Size(tagWidth, 20.dp.toPx()),
                    cornerRadius = CornerRadius(10.dp.toPx()),
                    style = Stroke(1.dp.toPx()),
                )
                text(
                    tag,
                    tagX + tagWidth / 2f,
                    tagY + 14.dp.toPx(),
                    palette.chartTagText,
                    9.sp.toPx(),
                )
            }
        }

        if (points.isEmpty()) {
            Text(
                emptyLabel,
                modifier = Modifier.align(Alignment.Center),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Paired bars for production vs. consumption (or any two aligned series).
 */
@Composable
fun GroupedEnergyBars(
    primary: List<SeriesPoint>,
    secondary: List<SeriesPoint>,
    primaryColor: Color,
    secondaryColor: Color,
    modifier: Modifier = Modifier,
    emptyLabel: String = "No series available",
) {
    val reveal = remember(primary, secondary) { Animatable(0f) }
    val palette = LocalFusionPalette.current
    LaunchedEffect(primary, secondary) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(650))
    }

    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            if (primary.isEmpty()) return@Canvas
            val left = 34.dp.toPx()
            val right = size.width - 8.dp.toPx()
            val top = 14.dp.toPx()
            val bottom = size.height - 22.dp.toPx()
            val chartH = bottom - top
            val maxValue = max(
                max(primary.maxOfOrNull { it.value } ?: 0.0, secondary.maxOfOrNull { it.value } ?: 0.0),
                0.01,
            ) * 1.15

            for (i in 0..3) {
                val y = bottom - chartH * i / 3f
                drawLine(
                    palette.chartGrid,
                    Offset(left, y),
                    Offset(right, y),
                    strokeWidth = if (i == 0) 1.4f else 1f,
                )
                text(
                    compact(maxValue * i / 3.0),
                    left - 6.dp.toPx(),
                    y + 4.dp.toPx(),
                    palette.chartLabel,
                    9.sp.toPx(),
                    align = android.graphics.Paint.Align.RIGHT,
                )
            }

            val slot = (right - left) / primary.size
            val barW = (slot * 0.28f).coerceAtMost(9.dp.toPx())
            primary.indices.forEach { index ->
                val center = left + slot * (index + 0.5f)
                val pValue = primary[index].value
                val sValue = secondary.getOrNull(index)?.value ?: 0.0
                val pH = (pValue / maxValue).toFloat().coerceIn(0f, 1f) * chartH * reveal.value
                val sH = (sValue / maxValue).toFloat().coerceIn(0f, 1f) * chartH * reveal.value
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(primaryColor, primaryColor.copy(alpha = 0.45f))
                    ),
                    topLeft = Offset(center - barW - 1.5.dp.toPx(), bottom - pH),
                    size = Size(barW, pH),
                    cornerRadius = CornerRadius(barW / 2f),
                )
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(secondaryColor.copy(alpha = 0.85f), secondaryColor.copy(alpha = 0.30f))
                    ),
                    topLeft = Offset(center + 1.5.dp.toPx(), bottom - sH),
                    size = Size(barW, sH),
                    cornerRadius = CornerRadius(barW / 2f),
                )
            }

            val labelEvery = max(1, ceil(primary.size / 6.0).toInt())
            primary.indices.forEach { index ->
                if (index % labelEvery == 0 || index == primary.lastIndex) {
                    val center = left + slot * (index + 0.5f)
                    text(
                        primary[index].label,
                        center.coerceIn(left, right),
                        size.height - 5.dp.toPx(),
                        palette.chartLabel,
                        8.5.sp.toPx(),
                    )
                }
            }
        }
        if (primary.isEmpty()) {
            Text(
                emptyLabel,
                modifier = Modifier.align(Alignment.Center),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

data class DonutSegment(val label: String, val value: Double, val color: Color)

/**
 * Energy-mix ring with animated sweep and a rotating highlight.
 */
@Composable
fun EnergyDonut(
    segments: List<DonutSegment>,
    modifier: Modifier = Modifier,
    centerTitle: String = "Self-use",
    centerValue: String = "—",
    centerCaption: String = "",
) {
    val positive = segments.filter { it.value > 0.0 }
    val total = positive.sumOf { it.value }
    val palette = LocalFusionPalette.current
    val sweep = remember(segments) { Animatable(0f) }
    LaunchedEffect(segments) {
        sweep.snapTo(0f)
        sweep.animateTo(1f, tween(900))
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val ring = 18.dp.toPx()
            val diameter = minOf(size.width, size.height) - ring
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)

            drawArc(
                color = palette.glassStrong,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = ring, cap = StrokeCap.Round),
            )

            if (total > 0.0) {
                var start = -90f
                positive.forEach { segment ->
                    val fraction = (segment.value / total).toFloat()
                    val angle = fraction * 360f * sweep.value
                    val gap = if (positive.size > 1) 2.5f else 0f
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(
                                segment.color.copy(alpha = 0.75f),
                                segment.color,
                                segment.color.copy(alpha = 0.75f),
                            )
                        ),
                        startAngle = start + gap / 2f,
                        sweepAngle = (angle - gap).coerceAtLeast(0.1f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = ring, cap = StrokeCap.Round),
                    )
                    start += angle
                }
            }

            // Inner glow ring.
            drawArc(
                color = SolarCyan.copy(alpha = 0.10f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft + Offset(ring * 0.75f, ring * 0.75f),
                size = Size(diameter - ring * 1.5f, diameter - ring * 1.5f),
                style = Stroke(width = 1.dp.toPx()),
            )
        }

        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                centerTitle.uppercase(),
                fontSize = 8.5.sp,
                letterSpacing = 1.4.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                centerValue,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (centerCaption.isNotEmpty()) {
                Text(
                    centerCaption,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Thin animated bar used in the metric breakdown list. */
@Composable
fun MetricBar(
    label: String,
    value: Double,
    fraction: Float,
    color: Color,
    unit: String = "kWh",
    modifier: Modifier = Modifier,
) {
    val palette = LocalFusionPalette.current
    val animated = androidx.compose.animation.core.animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(720),
        label = "metric-bar-$label",
    )
    androidx.compose.foundation.layout.Column(
        modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
    ) {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        ) {
            Text(
                label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${value.clean(1)} $unit",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(palette.glassStrong, androidx.compose.foundation.shape.RoundedCornerShape(50)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animated.value)
                    .height(6.dp)
                    .background(
                        Brush.horizontalGradient(listOf(color.copy(alpha = 0.55f), color)),
                        androidx.compose.foundation.shape.RoundedCornerShape(50),
                    ),
            )
        }
    }
}

/** Small radial gauge for a ratio (0..1). */
@Composable
fun RatioGauge(
    ratio: Float,
    color: Color,
    caption: String,
    modifier: Modifier = Modifier,
) {
    val palette = LocalFusionPalette.current
    val onSurface = MaterialTheme.colorScheme.onSurface
    val animated = androidx.compose.animation.core.animateFloatAsState(
        targetValue = ratio.coerceIn(0f, 1f),
        animationSpec = tween(850),
        label = "gauge",
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 7.dp.toPx()
            val d = minOf(size.width, size.height) - stroke
            val tl = Offset((size.width - d) / 2f, (size.height - d) / 2f)
            val arcSize = Size(d, d)
            drawArc(
                palette.glassStrong,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = tl,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(color.copy(alpha = 0.5f), color, color.copy(alpha = 0.9f))
                ),
                startAngle = 135f,
                sweepAngle = 270f * animated.value,
                useCenter = false,
                topLeft = tl,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            val angle = (135f + 270f * animated.value) * PI.toFloat() / 180f
            val r = d / 2f
            drawCircle(
                color = onSurface,
                radius = 2.2.dp.toPx(),
                center = Offset(
                    size.width / 2f + cos(angle) * r,
                    size.height / 2f + sin(angle) * r,
                ),
            )
        }
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${(animated.value * 100).roundToInt()}%",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(
                caption,
                fontSize = 8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
