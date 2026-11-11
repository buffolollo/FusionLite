package com.fusionlite.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.fusionlite.data.ThemeMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A small animated day/night switch.
 *
 * The track crossfades from a warm sunrise gradient to a starry night, the knob
 * springs across and morphs between a sun and a crescent moon. Flipping the
 * switch pins an explicit [ThemeMode] (light or dark); long-pressing is not
 * needed because [ThemeMode.SYSTEM] is restored from the Account page.
 */
@Composable
fun ThemeToggle(
    mode: ThemeMode,
    onMode: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = mode.isDark()
    val night = if (dark) 1f else 0f
    val knob by animateFloatAsState(
        targetValue = night,
        animationSpec = spring(dampingRatio = 0.66f, stiffness = Spring.StiffnessMediumLow),
        label = "theme-knob",
    )
    val sun by animateFloatAsState(
        targetValue = 1f - night,
        animationSpec = tween(300),
        label = "theme-sun",
    )
    val trackStart by animateColorAsState(
        targetValue = if (dark) Color(0xFF142238) else Color(0xFFFFE29B),
        animationSpec = tween(520),
        label = "track-start",
    )
    val trackEnd by animateColorAsState(
        targetValue = if (dark) Color(0xFF070E1C) else Color(0xFFFFB347),
        animationSpec = tween(520),
        label = "track-end",
    )
    val trackBorder by animateColorAsState(
        targetValue = if (dark) Color(0xFF3A5273) else Color(0xFFFFC94D),
        animationSpec = tween(520),
        label = "track-border",
    )

    Box(
        modifier
            .size(width = 66.dp, height = 36.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(trackStart, trackEnd)))
            .border(1.dp, trackBorder.copy(alpha = 0.55f), CircleShape)
            .clickable(role = Role.Switch) {
                onMode(if (dark) ThemeMode.LIGHT else ThemeMode.DARK)
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val pad = 4.dp.toPx()
            val knobR = (size.height - pad * 2f) / 2f
            val travel = size.width - pad * 2f - knobR * 2f
            val knobCenter = Offset(pad + knobR + travel * knob, size.height / 2f)

            // Stars fade in on the night side of the track.
            if (night > 0.01f) {
                val stars = listOf(0.24f to 0.33f, 0.32f to 0.66f, 0.40f to 0.44f, 0.16f to 0.55f)
                stars.forEachIndexed { index, (fx, fy) ->
                    val twinkle = if (index % 2 == 0) 1f else 0.6f
                    drawCircle(
                        color = Color.White.copy(alpha = 0.7f * night * twinkle),
                        radius = 0.9.dp.toPx(),
                        center = Offset(size.width * fx, size.height * fy),
                    )
                }
            }

            // Knob: warm disc with a soft rim.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFF6DA), Color(0xFFFFD166), Color(0xFFFFAF3A)),
                    center = knobCenter - Offset(knobR * 0.3f, knobR * 0.3f),
                    radius = knobR * 1.5f,
                ),
                radius = knobR,
                center = knobCenter,
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.35f),
                radius = knobR,
                center = knobCenter,
                style = Stroke(0.8.dp.toPx()),
            )

            // Sun rays fade away as the moon takes over.
            if (sun > 0.01f) {
                repeat(8) { i ->
                    val angle = i * PI / 4.0
                    val inner = knobR * 0.62f
                    val outer = knobR * 0.92f
                    drawLine(
                        color = Color(0xFFFFF3C4).copy(alpha = sun),
                        start = Offset(
                            knobCenter.x + cos(angle).toFloat() * inner,
                            knobCenter.y + sin(angle).toFloat() * inner,
                        ),
                        end = Offset(
                            knobCenter.x + cos(angle).toFloat() * outer,
                            knobCenter.y + sin(angle).toFloat() * outer,
                        ),
                        strokeWidth = 1.4.dp.toPx(),
                    )
                }
            }

            // Crescent moon carved out of the same warm disc.
            if (night > 0.01f) {
                val moonR = knobR * 0.74f
                val outer = Path().apply {
                    addOval(
                        Rect(
                            center = knobCenter,
                            radius = moonR,
                        )
                    )
                }
                val bite = Path().apply {
                    addOval(
                        Rect(
                            center = knobCenter + Offset(moonR * 0.62f, -moonR * 0.30f),
                            radius = moonR * 0.86f,
                        )
                    )
                }
                val crescent = Path().apply {
                    op(outer, bite, PathOperation.Difference)
                }
                drawPath(
                    crescent,
                    Color(0xFF0B1526).copy(alpha = night),
                )
                drawCircle(
                    color = Color(0xFFFFE6A8).copy(alpha = 0.65f * night),
                    radius = moonR * 0.14f,
                    center = knobCenter + Offset(-moonR * 0.34f, moonR * 0.28f),
                )
            }
        }
    }
}

/** Human-readable current selection, used by the Account page. */
val ThemeMode.label: String
    get() = when (this) {
        ThemeMode.SYSTEM -> "Follow system"
        ThemeMode.LIGHT -> "White"
        ThemeMode.DARK -> "Dark"
    }

/** Muted caption describing what the switch will do next. */
@Composable
fun ThemeMode.caption(): String = when (this) {
    ThemeMode.SYSTEM ->
        if (isDark()) "Following the phone · dark right now" else "Following the phone · white right now"
    ThemeMode.LIGHT -> "White theme pinned"
    ThemeMode.DARK -> "Dark theme pinned"
}
