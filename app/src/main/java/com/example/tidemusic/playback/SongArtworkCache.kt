package com.example.tidemusic.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.collection.LruCache
import com.example.tidemusic.util.AudioArtworkFetcher
import com.example.tidemusic.util.PlaceholderArt

/**
 * Fast in-memory LRU cache for song artwork bitmaps.
 *
 * Ensures synchronous delivery of square cover art bitmaps to:
 * - Media3's [DefaultMediaNotificationProvider] largeIcon
 * - ColorOS / Realme UI Pantanal (Aqua Dynamics / Fluid Cloud) status bar punch-hole capsule
 * - Lockscreen and notification drawer media carousel
 */
object SongArtworkCache {

    private val cache = LruCache<Long, Bitmap>(30)

    fun get(songId: Long): Bitmap? = cache.get(songId)

    fun put(songId: Long, bitmap: Bitmap) {
        if (!bitmap.isRecycled) {
            cache.put(songId, bitmap)
        }
    }

    /**
     * Synchronously returns the artwork bitmap for [songId].
     * If cached, returns immediately; otherwise decodes embedded picture or generates
     * deterministic [PlaceholderArt.bitmapFor].
     */
    fun getOrDecode(
        context: Context,
        songId: Long,
        filePath: String,
        sourceUri: String
    ): Bitmap {
        val existing = cache.get(songId)
        if (existing != null && !existing.isRecycled) {
            return existing
        }

        val decoded = try {
            val bytes = AudioArtworkFetcher.extractEmbeddedPicture(filePath, sourceUri, context)
            if (bytes != null && bytes.isNotEmpty()) {
                val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (raw != null) cropToSquare(raw) else null
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }

        val result = decoded ?: PlaceholderArt.bitmapFor(songId, 320)
        cache.put(songId, result)
        return result
    }

    private fun cropToSquare(bitmap: Bitmap): Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
        val x = (bitmap.width - size) / 2
        val y = (bitmap.height - size) / 2
        val square = Bitmap.createBitmap(bitmap, x, y, size, size)
        val targetSize = minOf(size, 320)
        return if (size > targetSize) {
            Bitmap.createScaledBitmap(square, targetSize, targetSize, true)
        } else {
            square
        }
    }
}
