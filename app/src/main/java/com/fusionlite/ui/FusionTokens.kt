package com.fusionlite.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * FusionLite design tokens.
 *
 * One immutable object per theme, exposed through [LocalFusionDesign] next to
 * [LocalFusionPalette]. Colours live in [FusionPalette] (they crossfade with
 * the theme blend); this layer carries the theme-independent geometry
 * (spacing, radii, elevation), motion timings and interaction-state recipes so
 * screens never hard-code dp values for rhythm or re-invent press/focus state.
 */

// ---------------------------------------------------------------------------
// Spacing — strict 4/8dp rhythm. Use these instead of literal dp paddings.
// ---------------------------------------------------------------------------

val FusionDesignSystem.spacingXs: Dp get() = 4.dp
val FusionDesignSystem.spacingSm: Dp get() = 8.dp
val FusionDesignSystem.spacingMd: Dp get() = 12.dp
val FusionDesignSystem.spacingLg: Dp get() = 16.dp
val FusionDesignSystem.spacingXl: Dp get() = 20.dp
val FusionDesignSystem.spacingXxl: Dp get() = 24.dp
val FusionDesignSystem.spacingSection: Dp get() = 32.dp

/** Minimum interactive target (Android 48dp guideline). */
val FusionDesignSystem.minTouchTarget: Dp get() = 48.dp

/** Maximum readable content width on tablets / landscape (avoids edge-to-edge text). */
val FusionDesignSystem.maxContentWidth: Dp get() = 680.dp

// ---------------------------------------------------------------------------
// Radii
// ---------------------------------------------------------------------------

val FusionDesignSystem.radiusSm: Dp get() = 12.dp
val FusionDesignSystem.radiusMd: Dp get() = 16.dp
val FusionDesignSystem.radiusLg: Dp get() = 20.dp
val FusionDesignSystem.radiusXl: Dp get() = 24.dp
val FusionDesignSystem.radiusHero: Dp get() = 26.dp
val FusionDesignSystem.radiusPill: Dp get() = 100.dp

fun FusionDesignSystem.shapeSm(): Shape = RoundedCornerShape(radiusSm)
fun FusionDesignSystem.shapeMd(): Shape = RoundedCornerShape(radiusMd)
fun FusionDesignSystem.shapeLg(): Shape = RoundedCornerShape(radiusLg)
fun FusionDesignSystem.shapeXl(): Shape = RoundedCornerShape(radiusXl)
fun FusionDesignSystem.shapeHero(): Shape = RoundedCornerShape(radiusHero)
fun FusionDesignSystem.shapePill(): Shape = RoundedCornerShape(radiusPill)

// ---------------------------------------------------------------------------
// Elevation / shadow recipes (drawn, because cards use transparent borders)
// ---------------------------------------------------------------------------

@Immutable
data class FusionElevation(
    val flat: Dp = 0.dp,
    val raised: Dp = 2.dp,
    val floating: Dp = 6.dp,
)

val FusionDesignSystem.elevation: FusionElevation get() = FusionElevation()

// ---------------------------------------------------------------------------
// Motion — one shared timing vocabulary.
// ---------------------------------------------------------------------------

@Immutable
data class FusionMotion(
    val fastMs: Int = 140,
    val baseMs: Int = 220,
    val slowMs: Int = 380,
    val themeBlendMs: Int = 520,
    val emphasis: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
    val standard: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f),
    val linear: Easing = LinearEasing,
)

val FusionDesignSystem.motion: FusionMotion get() = FusionMotion()

// ---------------------------------------------------------------------------
// Interaction states — press overlay + keyboard focus ring. The colours live
// in FusionStates (Theme.kt); the recipes live here.
// ---------------------------------------------------------------------------

/**
 * Adds a visible focus ring when this element gains keyboard/DPad focus and a
 * press overlay while touched. Both are drawn (not layout) so bounds never
 * shift during interaction.
 */
fun Modifier.fusionInteractive(
    design: FusionDesignSystem,
    shape: Shape,
    interactionSource: MutableInteractionSource,
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val pressed by interactionSource.collectIsPressedAsState()
    this
        .onFocusChanged { focused = it.isFocused }
        .drawWithContent {
            drawContent()
            if (pressed) {
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(design.states.pressedOverlay, design.states.pressedOverlay)
                    ),
                )
            }
            if (focused) {
                val stroke = 2.dp.toPx()
                val inset = stroke / 2
                drawRoundRect(
                    color = design.states.focus,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(width = stroke),
                )
            }
        }
}
