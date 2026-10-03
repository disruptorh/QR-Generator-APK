package com.disruptorh.qrgenerator.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF97CBFF),
    onPrimary = Color(0xFF003354),
    primaryContainer = Color(0xFF004A76),
    onPrimaryContainer = Color(0xFFCFE5FF),
    secondary = Color(0xFFB8C8DA),
    onSecondary = Color(0xFF233240),
    secondaryContainer = Color(0xFF394857),
    onSecondaryContainer = Color(0xFFD4E4F6),
    tertiary = Color(0xFFD6BEE6),
    onTertiary = Color(0xFF382A49),
    tertiaryContainer = Color(0xFF4F4061),
    onTertiaryContainer = Color(0xFFF1DBFF),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF101418),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF42474E),
    onSurfaceVariant = Color(0xFFC2C7CF),
    outline = Color(0xFF8C9199),
    outlineVariant = Color(0xFF42474E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00639A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCFE5FF),
    onPrimaryContainer = Color(0xFF001D31),
    secondary = Color(0xFF51606F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD4E4F6),
    onSecondaryContainer = Color(0xFF0D1D2A),
    tertiary = Color(0xFF68587A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEEDBFF),
    onTertiaryContainer = Color(0xFF251431),
    background = Color(0xFFFCFCFF),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFCFCFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFDEE3EB),
    onSurfaceVariant = Color(0xFF42474E),
    outline = Color(0xFF72777F),
    outlineVariant = Color(0xFFC2C7CF),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

/**
 * Material 3 colours.
 *
 * The palette is the app's own instead of the wallpaper's: the QR preview and
 * the export buttons have to look the same on every phone, and a wallpaper
 * palette would recolour them per device. Dark and light are the same blue ramp
 * as the desktop build.
 */
@Composable
fun QrTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}