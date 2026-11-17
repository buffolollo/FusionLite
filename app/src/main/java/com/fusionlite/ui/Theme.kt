package com.fusionlite.ui

import android.provider.Settings
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
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
// theme changes.
//
// Contrast (WCAG 2.1, verified with the token audit in
// design-system/fusionlite/ACCESSIBILITY.md):
//   light  textPrimary 17.5:1 · textSecondary 9.0:1 · textTertiary 5.8:1
//   dark   textPrimary 15.3:1 · textSecondary 9.9:1 · textTertiary 6.8:1
//   accents on their card surfaces ≥ 5.1:1 (light) / ≥ 8.1:1 (dark)
//   borderStrong ≥ 4.2:1 so interactive boundaries clear the 3:1 UI rule.
// ---------------------------------------------------------------------------

@Immutable
data class FusionPalette(
    val dark: Boolean,
    /** 0 = daylight artwork, 1 = night artwork; animates with the theme blend. */
    val night: Float,
    // Semantic text roles — never hard-code onSurface for body copy.
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    // Surfaces, borders and separators.
    val surfaceBase: Color,
    val surfaceRaised: Color,
    val surfaceSunken: Color,
    val surfaceHigh: Color,
    val border: Color,
    val borderStrong: Color,
    // Accents (light variants are darkened ≥4.5:1 on white cards).
    val gold: Color,
    val amber: Color,
    val cyan: Color,
    val green: Color,
    val blue: Color,
    // Status roles.
    val danger: Color,
    val success: Color,
    val warning: Color,
    val info: Color,
    val batteryCharge: Color,
    val batteryDischarge: Color,
    // Ornaments and feedback.
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
    val skeleton: Color,
    val focusRing: Color,
    val scrim: Color,
    val shadow: Color,
    val loginGradient: List<Color>,
    val loginTitle: Color,
    val loginSubtitle: Color,
)

private val DarkPalette = FusionPalette(
    dark = true,
    night = 1f,
    textPrimary = Color(0xFFEAF1FB),
    textSecondary = Color(0xFFB7C5DA),
    textTertiary = Color(0xFF93A3BC),
    surfaceBase = NightSky,
    surfaceRaised = NightSurface,
    surfaceSunken = Color(0xFF060B16),
    surfaceHigh = NightSurfaceHigh,
    border = Color(0xFF26385A),
    borderStrong = Color(0xFF55688A),
    gold = SolarGold,
    amber = SolarAmber,
    cyan = SolarCyan,
    green = SolarGreen,
    blue = Color(0xFF7FB3FF),
    danger = Color(0xFFFF7A7A),
    success = SolarGreen,
    warning = SolarAmber,
    info = Color(0xFF7FB3FF),
    batteryCharge = Color(0xFFC9A6FF),
    batteryDischarge = Color(0xFFFFA588),
    glass = Color.White.copy(alpha = 0.05f),
    glassStrong = Color.White.copy(alpha = 0.09f),
    chartGrid = Color.White.copy(alpha = 0.08f),
    chartLabel = Color(0xFFB7C5DA),
    chartTagText = Color.White,
    heroScrim = NightSky,
    navTop = NightSurfaceHigh,
    navBottom = NightSurface,
    navInactive = Color(0xFFA8B6CC),
    blobSpecular = Color.White.copy(alpha = 0.22f),
    skeleton = Color.White.copy(alpha = 0.07f),
    focusRing = SolarCyan,
    scrim = Color(0xFF03070F).copy(alpha = 0.74f),
    shadow = Color.Black.copy(alpha = 0.45f),
    loginGradient = listOf(Color(0xFF081226), Color(0xFF0D1D36), Color(0xFF07101F)),
    loginTitle = Color.White,
    loginSubtitle = Color(0xFFB7C5DA),
)

private val LightPalette = FusionPalette(
    dark = false,
    night = 0f,
    textPrimary = Color(0xFF0E1A2B),
    textSecondary = Color(0xFF3C4A5E),
    textTertiary = Color(0xFF5A6779),
    surfaceBase = Color(0xFFF4F7FC),
    surfaceRaised = Color.White,
    surfaceSunken = Color(0xFFE9EFF8),
    surfaceHigh = Color(0xFFEDF2FA),
    border = Color(0xFFC2CCDC),
    borderStrong = Color(0xFF6E7C90),
    // Deeper accents so labels and values stay readable on white cards (≥4.5:1).
    gold = Color(0xFF8F6100),
    amber = Color(0xFFA85800),
    cyan = Color(0xFF00738C),
    green = Color(0xFF157A4C),
    blue = Color(0xFF2563C9),
    danger = Color(0xFFB3261E),
    success = Color(0xFF157A4C),
    warning = Color(0xFFA85800),
    info = Color(0xFF2563C9),
    batteryCharge = Color(0xFF6D3FC4),
    batteryDischarge = Color(0xFFC2411F),
    glass = Color(0xFF0B1B33).copy(alpha = 0.045f),
    glassStrong = Color(0xFF0B1B33).copy(alpha = 0.08f),
    chartGrid = Color(0xFF15243B).copy(alpha = 0.10f),
    chartLabel = Color(0xFF455267),
    chartTagText = Color(0xFF101C2E),
    heroScrim = NightSky,
    navTop = Color.White,
    navBottom = Color(0xFFF3F7FD),
    navInactive = Color(0xFF4F5D70),
    blobSpecular = Color.White.copy(alpha = 0.55f),
    skeleton = Color(0xFF0B1B33).copy(alpha = 0.12f),
    focusRing = Color(0xFF00738C),
    scrim = Color(0xFF0B1B33).copy(alpha = 0.55f),
    shadow = Color(0xFF0B1B33).copy(alpha = 0.12f),
    loginGradient = listOf(Color(0xFFDBEAFB), Color(0xFFEFF6FF), Color(0xFFFBFDFF)),
    loginTitle = Color(0xFF101C2E),
    loginSubtitle = Color(0xFF455267),
)

private fun lerpPalette(from: FusionPalette, to: FusionPalette, t: Float): FusionPalette {
    fun c(a: Color, b: Color) = lerp(a, b, t)
    return FusionPalette(
        // `dark` drives discrete artwork choices (house trim, specular alpha),
        // while `night` smoothly crossfades the sky during a theme change.
        dark = if (t < 0.5f) from.dark else to.dark,
        night = from.night + (to.night - from.night) * t,
        textPrimary = c(from.textPrimary, to.textPrimary),
        textSecondary = c(from.textSecondary, to.textSecondary),
        textTertiary = c(from.textTertiary, to.textTertiary),
        surfaceBase = c(from.surfaceBase, to.surfaceBase),
        surfaceRaised = c(from.surfaceRaised, to.surfaceRaised),
        surfaceSunken = c(from.surfaceSunken, to.surfaceSunken),
        surfaceHigh = c(from.surfaceHigh, to.surfaceHigh),
        border = c(from.border, to.border),
        borderStrong = c(from.borderStrong, to.borderStrong),
        gold = c(from.gold, to.gold),
        amber = c(from.amber, to.amber),
        cyan = c(from.cyan, to.cyan),
        green = c(from.green, to.green),
        blue = c(from.blue, to.blue),
        danger = c(from.danger, to.danger),
        success = c(from.success, to.success),
        warning = c(from.warning, to.warning),
        info = c(from.info, to.info),
        batteryCharge = c(from.batteryCharge, to.batteryCharge),
        batteryDischarge = c(from.batteryDischarge, to.batteryDischarge),
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
        skeleton = c(from.skeleton, to.skeleton),
        focusRing = c(from.focusRing, to.focusRing),
        scrim = c(from.scrim, to.scrim),
        shadow = c(from.shadow, to.shadow),
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
    disabledContent = Color(0xFF93A3BC).copy(alpha = 0.55f),
    disabledContainer = Color.White.copy(alpha = 0.10f),
    hairline = Color.White.copy(alpha = 0.08f),
)

private val LightStates = FusionStates(
    pressedOverlay = Color(0xFF0B1B33).copy(alpha = 0.08f),
    focus = Color(0xFF00738C),
    disabledContent = Color(0xFF5A6779).copy(alpha = 0.60f),
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

/**
 * True when the platform asks for reduced motion (`animator_duration_scale`
 * = 0, the switch behind "Remove animations"). Every non-essential animation
 * in the app reads this local and renders its final state instead.
 */
val LocalFusionReducedMotion = staticCompositionLocalOf { false }

@Composable
private fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
}

// ---------------------------------------------------------------------------
// Typography scale — one vocabulary for every screen. Sizes are deliberately
// larger than the previous pass (8–10sp captions were unreadable on phones).
// Numeric styles enable tabular figures so values do not jitter while live
// data updates.
// ---------------------------------------------------------------------------

@Immutable
data class FusionType(
    val display: TextStyle = TextStyle(
        fontSize = 52.sp, lineHeight = 56.sp, fontWeight = FontWeight.Bold,
        letterSpacing = (-1.5).sp, fontFeatureSettings = "tnum",
    ),
    val displaySmall: TextStyle = TextStyle(
        fontSize = 38.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold,
        letterSpacing = (-0.8).sp, fontFeatureSettings = "tnum",
    ),
    val title: TextStyle = TextStyle(
        fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp,
    ),
    val heading: TextStyle = TextStyle(
        fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.1).sp,
    ),
    val subheading: TextStyle = TextStyle(
        fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold,
    ),
    val body: TextStyle = TextStyle(
        fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal,
    ),
    val bodySmall: TextStyle = TextStyle(
        fontSize = 12.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal,
    ),
    val label: TextStyle = TextStyle(
        fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium,
    ),
    /** All-caps micro label (section eyebrows, chips). */
    val labelMicro: TextStyle = TextStyle(
        fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 1.3.sp,
        fontWeight = FontWeight.Bold,
    ),
    val numeric: TextStyle = TextStyle(
        fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp, fontFeatureSettings = "tnum",
    ),
    val numericSmall: TextStyle = TextStyle(
        fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold,
        fontFeatureSettings = "tnum",
    ),
)

private val FusionTypes = FusionType()

val FusionDesignSystem.type: FusionType get() = FusionTypes

private fun materialTypography(): Typography = Typography(
    displayLarge = FusionTypes.display,
    displayMedium = FusionTypes.displaySmall,
    headlineLarge = FusionTypes.title,
    headlineMedium = FusionTypes.title,
    headlineSmall = FusionTypes.heading,
    titleLarge = FusionTypes.title,
    titleMedium = FusionTypes.heading,
    titleSmall = FusionTypes.subheading,
    bodyLarge = FusionTypes.body,
    bodyMedium = FusionTypes.body,
    bodySmall = FusionTypes.bodySmall,
    labelLarge = FusionTypes.label,
    labelMedium = FusionTypes.label,
    labelSmall = FusionTypes.labelMicro,
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
    onTertiary = Color(0xFF00281A),
    background = NightSky,
    onBackground = Color(0xFFEAF1FB),
    surface = NightSurface,
    onSurface = Color(0xFFEAF1FB),
    surfaceVariant = NightSurfaceHigh,
    onSurfaceVariant = Color(0xFFB7C5DA),
    outline = NightOutline,
    outlineVariant = Color(0xFF1E2D4A),
    error = Color(0xFFFF7A7A),
    onError = Color(0xFF2A0505),
    errorContainer = Color(0xFF4A1111),
    onErrorContainer = Color(0xFFFFD9D9),
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
    onBackground = Color(0xFF0E1A2B),
    surface = Color.White,
    onSurface = Color(0xFF0E1A2B),
    surfaceVariant = Color(0xFFE8EEF8),
    onSurfaceVariant = Color(0xFF3C4A5E),
    outline = Color(0xFFC2CCDC),
    outlineVariant = Color(0xFFDCE5F2),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
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
    val reducedMotion = rememberReducedMotion()
    // One shared progress value drives every colour slot, so the entire UI
    // (including hand-drawn canvases) fades from dark to white in lockstep.
    val progress by animateFloatAsState(
        targetValue = if (dark) 1f else 0f,
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 520, easing = FastOutSlowInEasing),
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
        LocalFusionReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = materialTypography(),
            content = content,
        )
    }
}
