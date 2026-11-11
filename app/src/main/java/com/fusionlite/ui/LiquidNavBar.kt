package com.fusionlite.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** Top-level destinations of the app shell. */
enum class AppTab(val label: String) {
    HOME("Home"),
    STATS("Analytics"),
    COSTS("Costs"),
    ACCOUNT("Account"),
}

/**
 * A gooey / liquid bottom navigation bar.
 *
 * The selected pill is an [Animatable] position; while it travels it stretches
 * horizontally and squashes vertically, and its bezier edges wobble with an
 * infinite phase so the highlight behaves like a drop of liquid. Two smaller
 * satellite droplets are thrown out behind it.
 */
@Composable
fun LiquidBottomBar(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = AppTab.entries
    val target = items.indexOf(selected).toFloat()
    val palette = LocalFusionPalette.current
    val position = remember { Animatable(target) }
    val transition = rememberInfiniteTransition(label = "liquid-nav")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing)),
        label = "phase",
    )
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(7800, easing = LinearEasing)),
        label = "drift",
    )

    LaunchedEffect(target) {
        position.animateTo(
            targetValue = target,
            animationSpec = spring(
                dampingRatio = 0.62f,
                stiffness = Spring.StiffnessLow,
            ),
        )
    }

    val shape = RoundedCornerShape(30.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(74.dp)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        palette.navTop.copy(alpha = 0.97f),
                        palette.navBottom.copy(alpha = 0.99f),
                    )
                )
            )
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        SolarCyan.copy(alpha = 0.30f),
                        SolarGold.copy(alpha = 0.16f),
                        SolarGreen.copy(alpha = 0.26f),
                    )
                ),
                shape,
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawLiquidLayer(position.value, phase, drift, items.size, palette)
        }

        Row(Modifier.fillMaxSize()) {
            items.forEachIndexed { index, tab ->
                LiquidNavItem(
                    tab = tab,
                    selected = tab == selected,
                    onClick = { if (tab != selected) onSelect(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun LiquidNavItem(
    tab: AppTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = when (tab) {
        AppTab.HOME -> LocalFusionPalette.current.gold
        AppTab.STATS -> LocalFusionPalette.current.cyan
        AppTab.COSTS -> LocalFusionPalette.current.amber
        AppTab.ACCOUNT -> LocalFusionPalette.current.green
    }
    val interaction = remember { MutableInteractionSource() }
    val palette = LocalFusionPalette.current
    val tint by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) accent else palette.navInactive,
        animationSpec = tween(320),
        label = "nav-tint",
    )
    Column(
        modifier
            .fillMaxSize()
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Canvas(Modifier.size(23.dp)) {
            drawNavGlyph(tab, tint)
        }
        Text(
            tab.label,
            fontSize = 9.sp,
            letterSpacing = 0.8.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = tint,
        )
    }
}

/**
 * Draws the travelling blob. All geometry is in pixels of the bar canvas.
 */
private fun DrawScope.drawLiquidLayer(
    pos: Float,
    phase: Float,
    drift: Float,
    count: Int,
    palette: FusionPalette,
) {
    val itemWidth = size.width / count
    val centerX = itemWidth * (pos + 0.5f)
    val centerY = size.height / 2f

    // 0 at rest, 1 at the midpoint between two items → the blob stretches.
    val travelled = pos - floor(pos)
    val stretch = sin(travelled * PI).toFloat().coerceIn(0f, 1f)

    val baseW = itemWidth * 0.86f
    val baseH = size.height * 0.70f
    val blobW = baseW * (1f + 0.30f * stretch)
    val blobH = baseH * (1f - 0.16f * stretch)
    val halfW = blobW / 2f
    val halfH = blobH / 2f

    val wobble = (sin(phase) * 0.10f + 0.04f) * halfH
    val wobble2 = (cos(phase * 1.3f) * 0.09f) * halfH

    val accents = listOf(palette.gold, palette.cyan, palette.amber, palette.green)
    val accentIndex = floor(pos).toInt().coerceIn(0, count - 1)
    val accent = accents[accentIndex.coerceIn(0, accents.lastIndex)]
    val nextAccent = accents[(accentIndex + 1).coerceAtMost(accents.lastIndex)]
    val blob = Path().apply {
        moveTo(centerX - halfW, centerY)
        cubicTo(
            centerX - halfW, centerY - halfH - wobble,
            centerX - halfW * 0.45f, centerY - halfH + wobble2,
            centerX, centerY - halfH,
        )
        cubicTo(
            centerX + halfW * 0.45f, centerY - halfH - wobble2,
            centerX + halfW, centerY - halfH + wobble,
            centerX + halfW, centerY,
        )
        cubicTo(
            centerX + halfW, centerY + halfH + wobble2,
            centerX + halfW * 0.40f, centerY + halfH - wobble,
            centerX, centerY + halfH,
        )
        cubicTo(
            centerX - halfW * 0.40f, centerY + halfH + wobble,
            centerX - halfW, centerY + halfH - wobble2,
            centerX - halfW, centerY,
        )
        close()
    }

    // Outer glow.
    drawPath(
        path = blob,
        color = accent.copy(alpha = 0.10f),
        style = Stroke(width = 10.dp.toPx()),
    )
    // Gooey body.
    drawPath(
        path = blob,
        brush = Brush.linearGradient(
            colors = listOf(
                accent.copy(alpha = 0.30f),
                nextAccent.copy(alpha = 0.22f),
                accent.copy(alpha = 0.14f),
            ),
            start = Offset(centerX - halfW, centerY - halfH),
            end = Offset(centerX + halfW, centerY + halfH),
        ),
    )
    // Glass rim.
    drawPath(
        path = blob,
        brush = Brush.linearGradient(
            listOf(
                accent.copy(alpha = 0.70f),
                palette.blobSpecular.copy(alpha = if (palette.dark) 0.10f else 0.35f),
                accent.copy(alpha = 0.35f),
            ),
            start = Offset(centerX - halfW, centerY - halfH),
            end = Offset(centerX + halfW, centerY + halfH),
        ),
        style = Stroke(width = 1.2.dp.toPx()),
    )
    // Specular highlight.
    rotate(degrees = -18f, pivot = Offset(centerX, centerY)) {
        drawArc(
            color = palette.blobSpecular,
            startAngle = 200f,
            sweepAngle = 120f,
            useCenter = false,
            topLeft = Offset(centerX - halfW * 0.62f, centerY - halfH * 0.72f),
            size = Size(halfW * 1.24f, halfH * 1.2f),
            style = Stroke(width = 2.dp.toPx()),
        )
    }

    // Two satellite droplets trailing the blob.
    val trailDir = if (travelled > 0.5f) -1f else 1f
    val dropFade = (0.45f - absf(travelled - 0.5f) * 0.9f).coerceAtLeast(0f)
    if (dropFade > 0f) {
        val d1 = 5.dp.toPx() * (0.6f + dropFade)
        val d2 = 3.dp.toPx() * (0.4f + dropFade)
        drawCircle(
            color = accent.copy(alpha = 0.30f * dropFade),
            radius = d1,
            center = Offset(
                centerX + trailDir * (halfW + 9.dp.toPx()) + sin(drift) * 2.dp.toPx(),
                centerY - halfH * 0.45f + cos(drift) * 2.dp.toPx(),
            ),
        )
        drawCircle(
            color = nextAccent.copy(alpha = 0.22f * dropFade),
            radius = d2,
            center = Offset(
                centerX + trailDir * (halfW + 17.dp.toPx()) + cos(drift * 1.4f) * 2.dp.toPx(),
                centerY + halfH * 0.30f + sin(drift * 1.2f) * 2.dp.toPx(),
            ),
        )
    }

    // Soft caustic line across the bar top.
    drawLine(
        brush = Brush.horizontalGradient(
            listOf(
                Color.Transparent,
                accent.copy(alpha = 0.25f),
                nextAccent.copy(alpha = 0.20f),
                Color.Transparent,
            ),
            startX = centerX - halfW * 1.6f,
            endX = centerX + halfW * 1.6f,
        ),
        start = Offset(centerX - halfW * 1.6f, 0.5.dp.toPx()),
        end = Offset(centerX + halfW * 1.6f, 0.5.dp.toPx()),
        strokeWidth = 1.dp.toPx(),
    )
}

private fun absf(v: Float): Float = if (v < 0f) -v else v

/** Hand-drawn vector glyphs keep the bar independent of icon fonts. */
private fun DrawScope.drawNavGlyph(tab: AppTab, color: Color) {
    val w = size.width
    val h = size.height
    val stroke = Stroke(width = w * 0.085f)
    when (tab) {
        AppTab.HOME -> {
            val roof = Path().apply {
                moveTo(w * 0.12f, h * 0.48f)
                lineTo(w * 0.5f, h * 0.16f)
                lineTo(w * 0.88f, h * 0.48f)
            }
            drawPath(roof, color, style = stroke)
            val body = Path().apply {
                moveTo(w * 0.22f, h * 0.44f)
                lineTo(w * 0.22f, h * 0.84f)
                lineTo(w * 0.78f, h * 0.84f)
                lineTo(w * 0.78f, h * 0.44f)
            }
            drawPath(body, color, style = stroke)
            drawLine(
                color,
                Offset(w * 0.44f, h * 0.84f),
                Offset(w * 0.44f, h * 0.62f),
                strokeWidth = stroke.width,
            )
            drawLine(
                color,
                Offset(w * 0.56f, h * 0.84f),
                Offset(w * 0.56f, h * 0.62f),
                strokeWidth = stroke.width,
            )
            drawLine(
                color,
                Offset(w * 0.44f, h * 0.62f),
                Offset(w * 0.56f, h * 0.62f),
                strokeWidth = stroke.width,
            )
        }

        AppTab.STATS -> {
            val bars = listOf(0.24f to 0.62f, 0.45f to 0.86f, 0.66f to 0.48f, 0.78f to 0.78f)
            bars.forEachIndexed { i, (x, top) ->
                val bw = w * 0.11f
                drawRoundRect(
                    color = color.copy(alpha = 0.55f + 0.12f * i),
                    topLeft = Offset(w * x - bw / 2f, h * top),
                    size = Size(bw, h * (0.90f - top)),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(bw / 2f),
                )
            }
            drawLine(
                color.copy(alpha = 0.7f),
                Offset(w * 0.10f, h * 0.90f),
                Offset(w * 0.90f, h * 0.90f),
                strokeWidth = stroke.width * 0.7f,
            )
        }

        AppTab.COSTS -> {
            // Coin with a cent stroke.
            drawCircle(
                color = color,
                radius = w * 0.31f,
                center = Offset(w * 0.5f, h * 0.5f),
                style = stroke,
            )
            drawCircle(
                color = color.copy(alpha = 0.55f),
                radius = w * 0.17f,
                center = Offset(w * 0.5f, h * 0.5f),
                style = Stroke(width = w * 0.06f),
            )
            drawLine(
                color,
                Offset(w * 0.5f, h * 0.16f),
                Offset(w * 0.5f, h * 0.84f),
                strokeWidth = stroke.width * 0.7f,
            )
        }

        AppTab.ACCOUNT -> {
            drawCircle(
                color = color,
                radius = w * 0.17f,
                center = Offset(w * 0.5f, h * 0.32f),
                style = stroke,
            )
            val shoulders = Path().apply {
                moveTo(w * 0.20f, h * 0.86f)
                cubicTo(
                    w * 0.22f, h * 0.60f,
                    w * 0.78f, h * 0.60f,
                    w * 0.80f, h * 0.86f,
                )
            }
            drawPath(shoulders, color, style = stroke)
        }
    }
}
