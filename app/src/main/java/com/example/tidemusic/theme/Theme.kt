package com.example.tidemusic.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.tidemusic.di.ServiceLocator

/**
 * App-wide palette exposed via [LocalTidePalette].
 * Components that need raw hex colors (accent fill, divider, accent-pressed)
 * read them here. The Material [darkColorScheme] is wired to the same source.
 */
data class TidePalette(
    val background: Color = Background,
    val surface: Color = Surface,
    val surfaceElevated: Color = SurfaceElevated,
    val outline: Color = Outline,
    val textPrimary: Color = TextPrimary,
    val textSecondary: Color = TextSecondary,
    val accent: Color = Accent,
    val accentPressed: Color = AccentVariant,
    val error: Color = Error,
)

val LocalTidePalette = staticCompositionLocalOf { TidePalette() }

private val DarkColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = AccentVariant,
    onPrimaryContainer = Color.White,
    secondary = Accent,
    onSecondary = Color.White,
    tertiary = Accent,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = Surface,
    surfaceContainerHigh = SurfaceElevated,
    surfaceContainerHighest = SurfaceElevated,
    surfaceContainerLow = Background,
    surfaceContainerLowest = Background,
    inverseSurface = Color.White,
    inverseOnSurface = Background,
    outline = Outline,
    outlineVariant = Outline,
    error = Error,
    onError = Background,
)

private val LightColorScheme = lightColorScheme(
    primary = LightAccent,
    onPrimary = Color.White,
    primaryContainer = LightAccentVariant,
    onPrimaryContainer = Color.White,
    secondary = LightAccent,
    onSecondary = Color.White,
    tertiary = LightAccent,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurfaceElevated,
    surfaceContainerHighest = LightSurfaceElevated,
    surfaceContainerLow = LightBackground,
    surfaceContainerLowest = LightBackground,
    inverseSurface = Color.Black,
    inverseOnSurface = Color.White,
    outline = LightOutline,
    outlineVariant = LightOutline,
    error = Error,
    onError = LightBackground,
)

@Composable
fun TideMusicTheme(
    content: @Composable () -> Unit,
) {
    val isDarkMode by ServiceLocator.settingsManager.isDarkMode.collectAsState()

    val palette = if (isDarkMode) TidePalette() else TidePalette(
        background = LightBackground,
        surface = LightSurface,
        surfaceElevated = LightSurfaceElevated,
        outline = LightOutline,
        textPrimary = LightTextPrimary,
        textSecondary = LightTextSecondary,
        accent = LightAccent,
        accentPressed = LightAccentVariant,
    )
    CompositionLocalProvider(LocalTidePalette provides palette) {
        MaterialTheme(
            colorScheme = if (isDarkMode) DarkColorScheme else LightColorScheme,
            typography = Typography,
            content = content,
        )
    }
}

/** Convenience accessor for components that want raw hex values instead of Material tokens. */
object TideColors {
    val accent: Color @Composable get() = LocalTidePalette.current.accent
    val accentPressed: Color @Composable get() = LocalTidePalette.current.accentPressed
    val background: Color @Composable get() = LocalTidePalette.current.background
    val surface: Color @Composable get() = LocalTidePalette.current.surface
    val surfaceElevated: Color @Composable get() = LocalTidePalette.current.surfaceElevated
    val outline: Color @Composable get() = LocalTidePalette.current.outline
    val textPrimary: Color @Composable get() = LocalTidePalette.current.textPrimary
    val textSecondary: Color @Composable get() = LocalTidePalette.current.textSecondary
    val error: Color @Composable get() = LocalTidePalette.current.error
}

@Suppress("unused")
private val BuildVersionStub = Build.VERSION.SDK_INT
