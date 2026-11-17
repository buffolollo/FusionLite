package com.fusionlite.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
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

/** Vertical rhythm between cards on a page. */
val FusionDesignSystem.pageGap: Dp get() = 16.dp

/** Inner padding of a standard card. */
val FusionDesignSystem.cardPadding: Dp get() = 18.dp

/** Gap between the hero and the first content section. */
val FusionDesignSystem.sectionGap: Dp get() = 24.dp

/** Bottom inset that keeps scroll content clear of the floating nav bar. */
val FusionDesignSystem.navClearance: Dp get() = 96.dp

/** Minimum interactive target (Android 48dp guideline). */
val FusionDesignSystem.minTouchTarget: Dp get() = 48.dp

/** Maximum readable content width on tablets / landscape (avoids edge-to-edge text). */
val FusionDesignSystem.maxContentWidth: Dp get() = 760.dp

/** Adaptive horizontal gutter: 16dp phones, 24dp large phones, 32dp tablets. */
fun FusionDesignSystem.gutterFor(width: Dp): Dp = when {
    width >= 840.dp -> 32.dp
    width >= 600.dp -> 24.dp
    else -> 16.dp
}

// ---------------------------------------------------------------------------
// Radii
// ---------------------------------------------------------------------------

val FusionDesignSystem.radiusSm: Dp get() = 12.dp
val FusionDesignSystem.radiusMd: Dp get() = 16.dp
val FusionDesignSystem.radiusLg: Dp get() = 20.dp
val FusionDesignSystem.radiusXl: Dp get() = 24.dp
val FusionDesignSystem.radiusHero: Dp get() = 28.dp
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
// Motion — one shared timing vocabulary. Durations are in milliseconds; every
// consumer gates them through [LocalFusionReducedMotion].
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

/** Returns 0 when reduced motion is requested, so callers can pass it to tween(). */
@Composable
fun fusionDuration(millis: Int): Int = if (LocalFusionReducedMotion.current) 0 else millis

// ---------------------------------------------------------------------------
// Interaction states — press overlay + keyboard/DPad focus ring. The colours
// live in FusionStates (Theme.kt); the recipes live here.
// ---------------------------------------------------------------------------

/**
 * Adds a visible focus ring when this element gains keyboard/DPad focus and a
 * press/hover overlay while interacted with. Both are drawn (not layout) so
 * bounds never shift during interaction.
 */
fun Modifier.fusionInteractive(
    design: FusionDesignSystem,
    shape: Shape,
    interactionSource: MutableInteractionSource,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val corner = (shape as? RoundedCornerShape)?.topStart
    this
        .drawWithContent {
            val radius = CornerRadius(corner?.toPx(size, this) ?: 12.dp.toPx())
            drawContent()
            if (pressed || hovered) {
                drawRoundRect(
                    color = design.states.pressedOverlay,
                    cornerRadius = radius,
                )
            }
            if (focused) {
                val stroke = 2.dp.toPx()
                val inset = stroke / 2
                drawRoundRect(
                    color = design.states.focus,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = radius,
                    style = Stroke(width = stroke),
                )
            }
        }
}

/**
 * One-call interactive surface: press overlay, hover tint, focus ring, role,
 * ripple-free press feedback and the 48dp minimum target. Use this instead of
 * bare `Modifier.clickable` so every control behaves the same.
 */
fun Modifier.fusionClickable(
    design: FusionDesignSystem,
    shape: Shape,
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    role: Role = Role.Button,
    onClick: () -> Unit,
): Modifier = this
    .fusionInteractive(design, shape, interactionSource)
    .clickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
        role = role,
        onClick = onClick,
    )

/** Guarantees a 48x48dp hit area without changing the visual bounds. */
fun Modifier.minimumTouchTarget(): Modifier = sizeIn(
    minWidth = 48.dp,
    minHeight = 48.dp,
)

/** Convenience for a themed pill/box background + hairline border. */
fun Modifier.fusionContainer(
    shape: Shape,
    fill: Color,
    borderColor: Color? = null,
): Modifier {
    var modifier = this.background(fill, shape)
    if (borderColor != null) modifier = modifier.border(1.dp, borderColor, shape)
    return modifier
}

/** Shorthand used by components that need the shared pill shape. */
@Composable
fun rememberPillShape(): Shape = remember { RoundedCornerShape(100.dp) }

/** Tone vocabulary shared by banners, pills and stat tiles. */
enum class FusionTone { ACCENT, SUCCESS, WARNING, DANGER, INFO, NEUTRAL }

fun FusionTone.color(palette: FusionPalette): Color = when (this) {
    FusionTone.ACCENT -> palette.gold
    FusionTone.SUCCESS -> palette.success
    FusionTone.WARNING -> palette.warning
    FusionTone.DANGER -> palette.danger
    FusionTone.INFO -> palette.info
    FusionTone.NEUTRAL -> palette.textSecondary
}

/** Default accent for each bottom-bar destination. */
fun AppTab.accent(palette: FusionPalette): Color = when (this) {
    AppTab.HOME -> palette.gold
    AppTab.STATS -> palette.cyan
    AppTab.COSTS -> palette.amber
    AppTab.ACCOUNT -> palette.green
}
