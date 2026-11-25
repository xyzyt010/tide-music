package com.example.tidemusic.util

import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Shader
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Placeholder Art Generator.
 *
 * Monochromatic solid black / dark charcoal presets (AMOLED-grade pure black).
 * Selection is deterministic per song id (spec Section 8.1) so the same track
 * always renders the same placeholder in every surface (notification, lock
 * screen, mini-player, player screen).
 */
object PlaceholderArt {

    /** Smooth preset gradient stops: (start color, end color). */
    private data class Preset(val start: Color, val end: Color)

    private val presets = listOf(
        Preset(Color(0xFF141414), Color(0xFF000000)),
        Preset(Color(0xFF121212), Color(0xFF000000)),
        Preset(Color(0xFF181818), Color(0xFF050505)),
        Preset(Color(0xFF101010), Color(0xFF000000)),
    )

    private fun presetIndexFor(id: Long): Int =
        ((id % presets.size + presets.size) % presets.size).toInt()

    /** Stable gradient for a given 64-bit id (e.g. Song.id). */
    fun gradientFor(id: Long): Brush {
        val preset = presets[presetIndexFor(id)]
        return Brush.linearGradient(listOf(preset.start, preset.end))
    }

    /** Stable gradient for any Long-compatible identifier. */
    fun gradientForHashCode(hash: Int): Brush {
        val preset = presets[((hash % presets.size + presets.size) % presets.size)]
        return Brush.linearGradient(listOf(preset.start, preset.end))
    }

    fun firstPresetColor(): Color = presets.first().start

    /**
     * Deterministic [Bitmap] placeholder for a song id, for surfaces that need a real
     * bitmap (media notification / lock-screen artwork when a track has no cover art).
     * Rendered on a bitmap so it is identical everywhere and never stale or random.
     */
    fun bitmapFor(id: Long, sizePx: Int = 512): Bitmap {
        val preset = presets[presetIndexFor(id)]
        val startColor = preset.start.toArgbInt()
        val endColor = preset.end.toArgbInt()
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply {
            shader = LinearGradient(
                0f, 0f, sizePx.toFloat(), sizePx.toFloat(),
                startColor, endColor,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), paint)
        return bitmap
    }

    private fun Color.toArgbInt(): Int =
        (alphaInt() shl 24) or (redInt() shl 16) or (greenInt() shl 8) or blueInt()

    private fun Color.alphaInt(): Int = (alpha * 255f).toInt().coerceIn(0, 255)
    private fun Color.redInt(): Int = (red * 255f).toInt().coerceIn(0, 255)
    private fun Color.greenInt(): Int = (green * 255f).toInt().coerceIn(0, 255)
    private fun Color.blueInt(): Int = (blue * 255f).toInt().coerceIn(0, 255)
}
