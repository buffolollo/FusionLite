package com.fusionlite.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SolarGold = Color(0xFFFFC94D)
val SolarAmber = Color(0xFFFF9F1C)
val SolarCyan = Color(0xFF4DD8FF)
val SolarGreen = Color(0xFF5BE49B)
val SolarBlue = Color(0xFF2E7CF6)
val NightSky = Color(0xFF070D1B)
val NightSurface = Color(0xFF101A2E)
val NightSurfaceHigh = Color(0xFF182640)
val NightOutline = Color(0xFF26385A)

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
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFFB26A00),
    onPrimary = Color.White,
    secondary = Color(0xFF00697F),
    tertiary = Color(0xFF1B7A4B),
    background = Color(0xFFF4F7FC),
    surface = Color.White,
    surfaceVariant = Color(0xFFE8EEF8),
    onSurfaceVariant = Color(0xFF455267),
    outline = Color(0xFFC2CCDC),
)

@Composable
fun FusionTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content,
    )
}
