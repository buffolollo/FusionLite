package com.fusionlite.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.fusionlite.data.ThemeMode

// ---------------------------------------------------------------------------
// Raw brand constants — artwork only. For anything on a themed surface use
// FusionPalette (LocalFusionPalette.current), whose light variants are
// darkened for contrast.
// ---------------------------------------------------------------------------

val SolarGold = Color(0xFFFFC94D)
val SolarAmber = Color(0xFFFF9F1C)
val SolarCyan = Color(0xFF4DD8FF)
val SolarGreen = Color(0xFF5BE49B)
val SolarBlue = Color(0xFF2E7CF6)
val NightSky = Color(0xFF070D1B)
val NightSurface = Color(0xFF101A2E)
val NightSurfaceHigh = Color(0xFF182640)
val NightOutline = Color(0xFF26385A)

// ---------------------------------------------------------------------------
// Extended palette
//
// MaterialTheme only exposes a handful of slots, while this UI also needs
// glass fills, chart grid lines, the nav-bar gradient, the hero scrim,
// battery roles and interaction-state colours. Keeping those in one immutable
// palette means every surface, chart and ornament animates together when the
// theme changes. All text/accent pairs below were chosen to clear WCAG AA
// (4.5:1 body text, 3:1 large text and non-text UI) on their own containers.
// ---------------------------------------------------------------------------

@Immutable
data class FusionPalette(
    val dark: Boolean,
    // Accents (light variants are darkened ≥4.5:1 on white cards).
    val gold: Color,
    val amber: Color,
    val cyan: Color,
    val green: Color,
    val blue: Color,
    // Status roles.
    val danger: Color,
    val batteryCharge: Color,
    val batteryDischarge: Color,
    // Surfaces & ornaments.
    val surfaceHigh: Color,
    val glass: Color,
    val glassStrong: Color,
    val chartGrid: Color,
    val chartLabel: Color,
    val chartTagText: Color,
    val heroScrim: Color,
    val navTop: Color,
    val navBottom: Color,
    val navInactive: Color,
    val blobSpecular: Color,
    val loginGradient: List<Color>,
    val loginTitle: Color,
    val loginSubtitle: Color,
)

private val DarkPalette = FusionPalette(
    dark = true,
    gold = SolarGold,
    amber = SolarAmber,
    cyan = SolarCyan,
    green = SolarGreen,
    blue = Color(0xFF7FB3FF),
    danger = Color(0xFFFF7A7A),
    batteryCharge = Color(0xFFC9A6FF),
    batteryDischarge = Color(0xFFFFA588),
    surfaceHigh = NightSurfaceHigh,
    glass = Color.White.copy(alpha = 0.05f),
    glassStrong = Color.White.copy(alpha = 0.09f),
    chartGrid = Color.White.copy(alpha = 0.07f),
    chartLabel = Color.White.copy(alpha = 0.58f),
    chartTagText = Color.White,
    heroScrim = NightSky,
    navTop = NightSurfaceHigh,
    navBottom = NightSurface,
    navInactive = Color.White.copy(alpha = 0.48f),
    blobSpecular = Color.White.copy(alpha = 0.22f),
    loginGradient = listOf(Color(0xFF081226), Color(0xFF0D1D36), Color(0xFF07101F)),
    loginTitle = Color.White,
    loginSubtitle = Color.White.copy(alpha = 0.72f),
)

private val LightPalette = FusionPalette(
    dark = false,
    // Deeper accents so labels and values stay readable on white cards (≥4.5:1).
    gold = Color(0xFF8F6100),
    amber = Color(0xFFA85800),
    cyan = Color(0xFF00738C),
    green = Color(0xFF157A4C),
    blue = Color(0xFF2563C9),
    danger = Color(0xFFB3261E),
    batteryCharge = Color(0xFF6D3FC4),
    batteryDischarge = Color(0xFFC2411F),
    surfaceHigh = Color(0xFFEDF2FA),
    glass = Color(0xFF0B1B33).copy(alpha = 0.045f),
    glassStrong = Color(0xFF0B1B33).copy(alpha = 0.08f),
    chartGrid = Color(0xFF15243B).copy(alpha = 0.10f),
    chartLabel = Color(0xFF455267),
    chartTagText = Color(0xFF101C2E),
    heroScrim = NightSky,
    navTop = Color.White,
    navBottom = Color(0xFFF3F7FD),
    navInactive = Color(0xFF5A6779),
    blobSpecular = Color.White.copy(alpha = 0.55f),
    loginGradient = listOf(Color(0xFFDBEAFB), Color(0xFFEFF6FF), Color(0xFFFBFDFF)),
    loginTitle = Color(0xFF101C2E),
    loginSubtitle = Color(0xFF455267),
)

private fun lerpPalette(from: FusionPalette, to: FusionPalette, t: Float): FusionPalette {
    fun c(a: Color, b: Color) = lerp(a, b, t)
    return FusionPalette(
        dark = to.dark,
        gold = c(from.gold, to.gold),
        amber = c(from.amber, to.amber),
        cyan = c(from.cyan, to.cyan),
        green = c(from.green, to.green),
        blue = c(from.blue, to.blue),
        danger = c(from.danger, to.danger),
        batteryCharge = c(from.batteryCharge, to.batteryCharge),
        batteryDischarge = c(from.batteryDischarge, to.batteryDischarge),
        surfaceHigh = c(from.surfaceHigh, to.surfaceHigh),
        glass = c(from.glass, to.glass),
        glassStrong = c(from.glassStrong, to.glassStrong),
        chartGrid = c(from.chartGrid, to.chartGrid),
        chartLabel = c(from.chartLabel, to.chartLabel),
        chartTagText = c(from.chartTagText, to.chartTagText),
        heroScrim = c(from.heroScrim, to.heroScrim),
        navTop = c(from.navTop, to.navTop),
        navBottom = c(from.navBottom, to.navBottom),
        navInactive = c(from.navInactive, to.navInactive),
        blobSpecular = c(from.blobSpecular, to.blobSpecular),
        loginGradient = from.loginGradient.zip(to.loginGradient) { a, b -> c(a, b) },
        loginTitle = c(from.loginTitle, to.loginTitle),
        loginSubtitle = c(from.loginSubtitle, to.loginSubtitle),
    )
}

val LocalFusionPalette = staticCompositionLocalOf { DarkPalette }

// ---------------------------------------------------------------------------
// Interaction-state tokens (theme dependent)
// ---------------------------------------------------------------------------

@Immutable
data class FusionStates(
    val pressedOverlay: Color,
    val focus: Color,
    val disabledContent: Color,
    val disabledContainer: Color,
    val hairline: Color,
)

private val DarkStates = FusionStates(
    pressedOverlay = Color.White.copy(alpha = 0.10f),
    focus = SolarCyan,
    disabledContent = Color.White.copy(alpha = 0.38f),
    disabledContainer = Color.White.copy(alpha = 0.12f),
    hairline = Color.White.copy(alpha = 0.08f),
)

private val LightStates = FusionStates(
    pressedOverlay = Color(0xFF0B1B33).copy(alpha = 0.08f),
    focus = Color(0xFF2563C9),
    disabledContent = Color(0xFF455267).copy(alpha = 0.38f),
    disabledContainer = Color(0xFF0B1B33).copy(alpha = 0.08f),
    hairline = Color(0xFF15243B).copy(alpha = 0.10f),
)

/**
 * Geometry + motion tokens (theme independent) bundled with the theme's
 * interaction-state colours. Provided through [LocalFusionDesign].
 */
@Immutable
class FusionDesignSystem(val states: FusionStates)

val LocalFusionDesign = staticCompositionLocalOf { FusionDesignSystem(DarkStates) }

// ---------------------------------------------------------------------------
// Typography scale — one vocabulary for every screen. Sizes are exact values
// the screens already use, promoted to tokens.
// ---------------------------------------------------------------------------

@Immutable
data class FusionType(
    val display: TextStyle = TextStyle(fontSize = 46.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.5).sp),
    val title: TextStyle = TextStyle(fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    val heading: TextStyle = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    val body: TextStyle = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Normal),
    val bodySmall: TextStyle = TextStyle(fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Normal),
    /** All-caps micro label (section eyebrows, chips). */
    val labelMicro: TextStyle = TextStyle(fontSize = 9.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Bold),
)

val FusionDesignSystem.type: FusionType get() = FusionType()

private fun materialTypography(): Typography = Typography(
    displayLarge = FusionType().display,
    titleLarge = FusionType().title,
    titleMedium = FusionType().heading,
    bodyMedium = FusionType().body,
    bodySmall = FusionType().bodySmall,
    labelSmall = FusionType().labelMicro,
)

// ---------------------------------------------------------------------------
// Colour schemes
// ---------------------------------------------------------------------------

private val DarkScheme = darkColorScheme(
    primary = SolarGold,
    onPrimary = Color(0xFF241800),
    primaryContainer = Color(0xFF4A3600),
    onPrimaryContainer = SolarGold,
    secondary = SolarCyan,
    onSecondary = Color(0xFF00202B),
    secondaryContainer = Color(0xFF073A4C),
    onSecondaryContainer = SolarCyan,
    tertiary = SolarGreen,
    background = NightSky,
    onBackground = Color(0xFFE9F1FF),
    surface = NightSurface,
    onSurface = Color(0xFFE9F1FF),
    surfaceVariant = NightSurfaceHigh,
    onSurfaceVariant = Color(0xFF9FB0CC),
    outline = NightOutline,
    error = Color(0xFFFF7A7A),
    onError = Color(0xFF2A0505),
    surfaceContainerLowest = Color(0xFF060B16),
    surfaceContainerLow = NightSurface,
    surfaceContainer = Color(0xFF0D1728),
    surfaceContainerHigh = Color(0xFF141F34),
    surfaceContainerHighest = NightSurfaceHigh,
    surfaceBright = Color(0xFF1E2D4A),
    surfaceDim = Color(0xFF060B16),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFFA35F00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDFA8),
    onPrimaryContainer = Color(0xFF3A2600),
    secondary = Color(0xFF00697F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB8ECFF),
    onSecondaryContainer = Color(0xFF001E26),
    tertiary = Color(0xFF1B7A4B),
    onTertiary = Color.White,
    background = Color(0xFFF4F7FC),
    onBackground = Color(0xFF101C2E),
    surface = Color.White,
    onSurface = Color(0xFF101C2E),
    surfaceVariant = Color(0xFFE8EEF8),
    onSurfaceVariant = Color(0xFF455267),
    outline = Color(0xFFC2CCDC),
    outlineVariant = Color(0xFFDCE5F2),
    error = Color(0xFFB3261E),
    onError = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8FAFE),
    surfaceContainer = Color(0xFFF1F5FB),
    surfaceContainerHigh = Color(0xFFEAF0F8),
    surfaceContainerHighest = Color(0xFFE3EAF4),
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFDDE5F0),
)

/** Blends every Material slot so a theme change crossfades the whole UI. */
private fun blendSchemes(from: ColorScheme, to: ColorScheme, t: Float): ColorScheme {
    fun c(a: Color, b: Color) = lerp(a, b, t)
    return from.copy(
        primary = c(from.primary, to.primary),
        onPrimary = c(from.onPrimary, to.onPrimary),
        primaryContainer = c(from.primaryContainer, to.primaryContainer),
        onPrimaryContainer = c(from.onPrimaryContainer, to.onPrimaryContainer),
        inversePrimary = c(from.inversePrimary, to.inversePrimary),
        secondary = c(from.secondary, to.secondary),
        onSecondary = c(from.onSecondary, to.onSecondary),
        secondaryContainer = c(from.secondaryContainer, to.secondaryContainer),
        onSecondaryContainer = c(from.onSecondaryContainer, to.onSecondaryContainer),
        tertiary = c(from.tertiary, to.tertiary),
        onTertiary = c(from.onTertiary, to.onTertiary),
        tertiaryContainer = c(from.tertiaryContainer, to.tertiaryContainer),
        onTertiaryContainer = c(from.onTertiaryContainer, to.onTertiaryContainer),
        background = c(from.background, to.background),
        onBackground = c(from.onBackground, to.onBackground),
        surface = c(from.surface, to.surface),
        onSurface = c(from.onSurface, to.onSurface),
        surfaceVariant = c(from.surfaceVariant, to.surfaceVariant),
        onSurfaceVariant = c(from.onSurfaceVariant, to.onSurfaceVariant),
        surfaceTint = c(from.surfaceTint, to.surfaceTint),
        inverseSurface = c(from.inverseSurface, to.inverseSurface),
        inverseOnSurface = c(from.inverseOnSurface, to.inverseOnSurface),
        error = c(from.error, to.error),
        onError = c(from.onError, to.onError),
        errorContainer = c(from.errorContainer, to.errorContainer),
        onErrorContainer = c(from.onErrorContainer, to.onErrorContainer),
        outline = c(from.outline, to.outline),
        outlineVariant = c(from.outlineVariant, to.outlineVariant),
        scrim = c(from.scrim, to.scrim),
        surfaceBright = c(from.surfaceBright, to.surfaceBright),
        surfaceDim = c(from.surfaceDim, to.surfaceDim),
        surfaceContainer = c(from.surfaceContainer, to.surfaceContainer),
        surfaceContainerHigh = c(from.surfaceContainerHigh, to.surfaceContainerHigh),
        surfaceContainerHighest = c(from.surfaceContainerHighest, to.surfaceContainerHighest),
        surfaceContainerLow = c(from.surfaceContainerLow, to.surfaceContainerLow),
        surfaceContainerLowest = c(from.surfaceContainerLowest, to.surfaceContainerLowest),
    )
}

/** Resolves [ThemeMode.SYSTEM] against the phone's current setting. */
@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun FusionTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = themeMode.isDark()
    // One shared progress value drives every colour slot, so the entire UI
    // (including hand-drawn canvases) fades from dark to white in lockstep.
    val progress by animateFloatAsState(
        targetValue = if (dark) 1f else 0f,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "theme-blend",
    )
    val palette = lerpPalette(LightPalette, DarkPalette, progress)
    val scheme = blendSchemes(LightScheme, DarkScheme, progress)
    val states = FusionStates(
        pressedOverlay = lerp(LightStates.pressedOverlay, DarkStates.pressedOverlay, progress),
        focus = lerp(LightStates.focus, DarkStates.focus, progress),
        disabledContent = lerp(LightStates.disabledContent, DarkStates.disabledContent, progress),
        disabledContainer = lerp(LightStates.disabledContainer, DarkStates.disabledContainer, progress),
        hairline = lerp(LightStates.hairline, DarkStates.hairline, progress),
    )
    CompositionLocalProvider(
        LocalFusionPalette provides palette,
        LocalFusionDesign provides FusionDesignSystem(states),
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = materialTypography(),
            content = content,
        )
    }
}
