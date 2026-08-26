package com.example.tidemusic.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.BitmapLoader
import com.example.tidemusic.util.AudioArtworkFetcher
import com.example.tidemusic.util.PlaceholderArt
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Custom [BitmapLoader] used by the MediaSession (and therefore by the media notification,
 * lock screen, Android Auto / Wear surfaces).
 *
 * WHY THIS EXISTS (the "always shows one wrong song's image" bug):
 * The stock loader only understands plain image URIs. When a track had no albumart URI we
 * used to fall back to the AUDIO file's content URI as the artwork URI; decoding that as an
 * image always failed, and when a bitmap load fails the system media card keeps displaying
 * the LAST successfully-loaded artwork for the session — so whatever song previously had
 * art got stuck on the lock screen / notification drawer. Intermittency came from which
 * songs had resolvable `content://media/external/audio/albumart/…` URIs.
 *
 * FIX: every MediaItem now carries a deterministic `tide-art://` artwork URI built from the
 * song id + file path (see [ArtworkUri.forSong]). This loader resolves it:
 *   1. embedded APIC/MP4/Vorbis cover art via [AudioArtworkFetcher],
 *   2. else a deterministic per-song placeholder gradient ([PlaceholderArt.bitmapFor]).
 * A real bitmap is ALWAYS returned, so every track transition delivers fresh, correct art
 * to the framework MediaSession — no stale/wrong artwork can survive a transition.
 */
@UnstableApi
class TideArtworkBitmapLoader(
    private val context: Context,
) : BitmapLoader {

    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "tide-art-loader") }

    override fun supportsMimeType(mimeType: String): Boolean = true

    /** Only used when [androidx.media3.common.MediaMetadata.artworkData] is set (we never set it). */
    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val bitmap = try {
            val raw = BitmapFactory.decodeByteArray(data, 0, data.size)
            if (raw != null) cropToSquare(raw) else null
        } catch (_: Exception) {
            null
        }
        return if (bitmap != null) Futures.immediateFuture(bitmap)
        else Futures.immediateFailedFuture(IllegalArgumentException("Not a decodable image"))
    }

    /**
     * Resolves a tide-art:// artwork URI. Never returns null and never throws: worst case
     * it yields the song's deterministic placeholder gradient, which guarantees the media
     * notification / lock screen always receives fresh per-song artwork.
     */
    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        Futures.submit(Callable { resolveBitmap(uri) }, executor)

    private fun resolveBitmap(uri: Uri): Bitmap {
        if (uri.scheme != ArtworkUri.SCHEME) {
            decodeExternalImage(uri)?.let { return cropToSquare(it) }
            return PlaceholderArt.bitmapFor(0L)
        }

        val songId = uri.lastPathSegment?.toLongOrNull() ?: 0L
        val filePath = uri.getQueryParameter(ArtworkUri.QP_PATH) ?: ""
        val sourceUri = uri.getQueryParameter(ArtworkUri.QP_SOURCE) ?: ""

        // 1. Real embedded / associated artwork for this exact song.
        val bytes = AudioArtworkFetcher.extractEmbeddedPicture(filePath, sourceUri, context)
        if (bytes != null) {
            try {
                val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (raw != null) {
                    return cropToSquare(raw)
                }
            } catch (_: Exception) {
            }
        }

        // 2. Deterministic per-song placeholder — never random, never another song's art.
        return PlaceholderArt.bitmapFor(songId)
    }

    /** Best-effort decode of an ordinary image URI through the ContentResolver. */
    private fun decodeExternalImage(uri: Uri): Bitmap? = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val raw = BitmapFactory.decodeStream(stream)
            if (raw != null) cropToSquare(raw) else null
        }
    } catch (_: Exception) {
        null
    }

    /**
     * Crops any rectangular / non-square bitmap to a centered 1:1 square.
     * Prevents black bars / pillarboxing in Android's notification drawer & lock screen.
     */
    private fun cropToSquare(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        if (width <= 0 || height <= 0) return src
        if (width == height) return src
        val size = minOf(width, height)
        val x = (width - size) / 2
        val y = (height - size) / 2
        return try {
            Bitmap.createBitmap(src, x, y, size, size)
        } catch (_: Exception) {
            src
        }
    }
}

/** Central definition of the tide-art artwork URI scheme shared by controller & loader. */
object ArtworkUri {
    const val SCHEME = "tide-art"
    const val AUTHORITY = "song"
    const val QP_PATH = "path"
    const val QP_SOURCE = "src"
    const val QP_DATE_MODIFIED = "dm"

    /** Deterministic artwork URI for a song; consumed by [TideArtworkBitmapLoader]. */
    fun forSong(songId: Long, filePath: String?, sourceUri: String?, dateModified: Long): Uri =
        Uri.Builder()
            .scheme(SCHEME)
            .authority(AUTHORITY)
            .appendPath(songId.toString())
            .appendQueryParameter(QP_PATH, filePath ?: "")
            .appendQueryParameter(QP_SOURCE, sourceUri ?: "")
            .appendQueryParameter(QP_DATE_MODIFIED, dateModified.toString())
            .build()
}
