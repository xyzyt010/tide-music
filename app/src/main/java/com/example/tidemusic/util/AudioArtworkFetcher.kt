package com.example.tidemusic.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import okio.Buffer
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Model representing a song artwork request.
 * Allows Coil to uniquely key and fetch embedded artwork per song without cross-song collisions.
 */
data class SongArtworkRequest(
    val songId: Long,
    val filePath: String,
    val uriString: String = "",
    val dateModified: Long = 0L,
)

/**
 * Stable keyer ensuring each song has its own independent cache key:
 * "song_art_{songId}_{dateModified}"
 */
class SongArtworkKeyer : Keyer<SongArtworkRequest> {
    override fun key(data: SongArtworkRequest, options: Options): String {
        return "song_art_${data.songId}_${data.dateModified}_${data.filePath.hashCode()}"
    }
}

/**
 * Custom Coil 3 Fetcher that extracts embedded APIC / MP4 / Vorbis cover art
 * directly from audio files using MediaMetadataRetriever and ContentResolver thumbnail fallback.
 */
class AudioArtworkFetcher(
    private val request: SongArtworkRequest,
    private val context: Context,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val bytes = extractEmbeddedPicture(request.filePath, request.uriString, context)
            ?: return null

        val buffer = Buffer().write(bytes)
        return SourceFetchResult(
            source = ImageSource(source = buffer, fileSystem = okio.FileSystem.SYSTEM),
            mimeType = "image/jpeg",
            dataSource = DataSource.DISK,
        )
    }

    class Factory(private val context: Context) : Fetcher.Factory<SongArtworkRequest> {
        override fun create(
            data: SongArtworkRequest,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher {
            return AudioArtworkFetcher(data, context)
        }
    }

    companion object {
        /**
         * Safely extracts embedded picture bytes from a file path or content URI.
         */
        fun extractEmbeddedPicture(filePath: String, uriString: String, context: Context): ByteArray? {
            // 1. Try file path via MediaMetadataRetriever
            if (filePath.isNotBlank()) {
                val file = File(filePath)
                if (file.exists() && file.length() > 0) {
                    var mmr: MediaMetadataRetriever? = null
                    try {
                        mmr = MediaMetadataRetriever()
                        mmr.setDataSource(filePath)
                        val pic = mmr.embeddedPicture
                        if (pic != null && pic.isNotEmpty()) {
                            return pic
                        }
                    } catch (_: Exception) {
                        // Fallback to stream / Uri
                    } finally {
                        try { mmr?.release() } catch (_: Exception) {}
                    }
                }
            }

            // 2. Try content / file URI via MediaMetadataRetriever
            if (uriString.isNotBlank()) {
                var mmr: MediaMetadataRetriever? = null
                try {
                    val parsedUri = Uri.parse(uriString)
                    mmr = MediaMetadataRetriever()
                    mmr.setDataSource(context, parsedUri)
                    val pic = mmr.embeddedPicture
                    if (pic != null && pic.isNotEmpty()) {
                        return pic
                    }
                } catch (_: Exception) {
                    // Fallback to ContentResolver thumbnail
                } finally {
                    try { mmr?.release() } catch (_: Exception) {}
                }

                // 3. On Android 10+ (API 29+), try ContentResolver.loadThumbnail for content:// URIs
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uriString.startsWith("content://")) {
                    try {
                        val parsedUri = Uri.parse(uriString)
                        val bitmap = context.contentResolver.loadThumbnail(parsedUri, Size(512, 512), null)
                        val stream = ByteArrayOutputStream()
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, stream)
                        val bytes = stream.toByteArray()
                        if (bytes.isNotEmpty()) {
                            return bytes
                        }
                    } catch (_: Exception) {}
                }
            }

            return null
        }
    }
}
