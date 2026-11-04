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
    val customThemeInt by ServiceLocator.settingsManager.customThemeColor.collectAsState()
    val customHighlightInt by ServiceLocator.settingsManager.customHighlightColor.collectAsState()
    val customTextInt by ServiceLocator.settingsManager.customTextColor.collectAsState()

    val baseBackground = customThemeInt?.let { Color(it) }
        ?: if (isDarkMode) Background else LightBackground

    val isDark = customThemeInt?.let {
        val c = Color(it)
        (0.299f * c.red + 0.587f * c.green + 0.114f * c.blue) < 0.5f
    } ?: isDarkMode

    val baseSurface = customThemeInt?.let {
        val c = Color(it)
        if (isDark) {
            Color(
                red = (c.red + 0.05f).coerceAtMost(1f),
                green = (c.green + 0.05f).coerceAtMost(1f),
                blue = (c.blue + 0.05f).coerceAtMost(1f),
                alpha = 1f
            )
        } else {
            Color(
                red = (c.red - 0.04f).coerceAtLeast(0f),
                green = (c.green - 0.04f).coerceAtLeast(0f),
                blue = (c.blue - 0.04f).coerceAtLeast(0f),
                alpha = 1f
            )
        }
    } ?: if (isDarkMode) Surface else LightSurface

    val baseSurfaceElevated = customThemeInt?.let {
        val c = Color(it)
        if (isDark) {
            Color(
                red = (c.red + 0.09f).coerceAtMost(1f),
                green = (c.green + 0.09f).coerceAtMost(1f),
                blue = (c.blue + 0.09f).coerceAtMost(1f),
                alpha = 1f
            )
        } else {
            Color(
                red = (c.red - 0.08f).coerceAtLeast(0f),
                green = (c.green - 0.08f).coerceAtLeast(0f),
                blue = (c.blue - 0.08f).coerceAtLeast(0f),
                alpha = 1f
            )
        }
    } ?: if (isDarkMode) SurfaceElevated else LightSurfaceElevated

    val baseOutline = customThemeInt?.let {
        val c = Color(it)
        if (isDark) {
            Color(
                red = (c.red + 0.14f).coerceAtMost(1f),
                green = (c.green + 0.14f).coerceAtMost(1f),
                blue = (c.blue + 0.14f).coerceAtMost(1f),
                alpha = 1f
            )
        } else {
            Color(
                red = (c.red - 0.14f).coerceAtLeast(0f),
                green = (c.green - 0.14f).coerceAtLeast(0f),
                blue = (c.blue - 0.14f).coerceAtLeast(0f),
                alpha = 1f
            )
        }
    } ?: if (isDarkMode) Outline else LightOutline

    val baseTextPrimary = customTextInt?.let { Color(it) }
        ?: if (isDark) TextPrimary else LightTextPrimary

    val baseTextSecondary = customTextInt?.let {
        Color(it).copy(alpha = 0.65f)
    } ?: if (isDark) TextSecondary else LightTextSecondary

    val baseAccent = customHighlightInt?.let { Color(it) }
        ?: if (isDarkMode) Accent else LightAccent

    val baseAccentPressed = customHighlightInt?.let {
        val c = Color(it)
        Color(
            red = (c.red * 0.82f).coerceIn(0f, 1f),
            green = (c.green * 0.82f).coerceIn(0f, 1f),
            blue = (c.blue * 0.82f).coerceIn(0f, 1f),
            alpha = 1f
        )
    } ?: if (isDarkMode) AccentVariant else LightAccentVariant

    val palette = TidePalette(
        background = baseBackground,
        surface = baseSurface,
        surfaceElevated = baseSurfaceElevated,
        outline = baseOutline,
        textPrimary = baseTextPrimary,
        textSecondary = baseTextSecondary,
        accent = baseAccent,
        accentPressed = baseAccentPressed,
        error = Error,
    )

    val colorScheme = if (isDark) {
        DarkColorScheme.copy(
            primary = baseAccent,
            secondary = baseAccent,
            tertiary = baseAccent,
            background = baseBackground,
            onBackground = baseTextPrimary,
            surface = baseSurface,
            onSurface = baseTextPrimary,
            surfaceVariant = baseSurfaceElevated,
            onSurfaceVariant = baseTextSecondary,
            surfaceContainer = baseSurface,
            surfaceContainerHigh = baseSurfaceElevated,
            surfaceContainerHighest = baseSurfaceElevated,
            surfaceContainerLow = baseBackground,
            surfaceContainerLowest = baseBackground,
            outline = baseOutline,
            outlineVariant = baseOutline,
        )
    } else {
        LightColorScheme.copy(
            primary = baseAccent,
            secondary = baseAccent,
            tertiary = baseAccent,
            background = baseBackground,
            onBackground = baseTextPrimary,
            surface = baseSurface,
            onSurface = baseTextPrimary,
            surfaceVariant = baseSurfaceElevated,
            onSurfaceVariant = baseTextSecondary,
            surfaceContainer = baseSurface,
            surfaceContainerHigh = baseSurfaceElevated,
            surfaceContainerHighest = baseSurfaceElevated,
            surfaceContainerLow = baseBackground,
            surfaceContainerLowest = baseBackground,
            outline = baseOutline,
            outlineVariant = baseOutline,
        )
    }

    CompositionLocalProvider(LocalTidePalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
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
