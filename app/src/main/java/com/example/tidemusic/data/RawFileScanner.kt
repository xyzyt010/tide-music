package com.example.tidemusic.data

import android.media.MediaMetadataRetriever
import android.os.Environment
import android.util.Log
import com.example.tidemusic.data.db.StableIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A true physical filesystem scanner that walks accessible directories in external storage,
 * extracting real ID3/embedded tags so all albums and songs are correctly categorized.
 */
class RawFileScanner {

    suspend fun scan(): List<MediaStoreScanner.ScannedTrack> = withContext(Dispatchers.IO) {
        val root = Environment.getExternalStorageDirectory()
        val out = mutableListOf<MediaStoreScanner.ScannedTrack>()
        val supportedExtensions = setOf("mp3", "m4a", "flac", "wav", "aac", "ogg", "opus")

        val targetDirs = listOf(
            File(root, "Music"),
            File(root, "Download"),
            File(root, "Podcasts"),
            File(root, "Audiobooks"),
            File(root, "DCIM"),
            File(root, "TideMusic"),
            root
        )

        val scannedPaths = HashSet<String>()

        fun walk(dir: File, depth: Int = 0) {
            if (depth > 6) return // Prevent deep recursive stuck states
            val files = try {
                dir.listFiles()
            } catch (e: Exception) {
                null
            } ?: return

            for (file in files) {
                if (file.isDirectory) {
                    val name = file.name
                    if (!name.startsWith(".") && name != "Android") {
                        walk(file, depth + 1)
                    }
                } else {
                    val ext = file.extension.lowercase()
                    if (supportedExtensions.contains(ext) && scannedPaths.add(file.absolutePath)) {
                        val folderPath = file.parentFile?.absolutePath ?: ""
                        val folderName = file.parentFile?.name ?: "Unknown"
                        val folderId = if (folderPath.isBlank()) null else StableIds.folderId(folderPath)
                        val trackId = StableIds.pathId(file.absolutePath)

                        var title = file.nameWithoutExtension.ifBlank { "Unknown" }
                        var artist = "Unknown"
                        var album = folderName
                        var albumArtist: String? = null
                        var durationMs = 0L
                        var bitrate = 0
                        var sampleRate = 0
                        var trackNum = 0
                        var discNum = 0

                        var mmr: MediaMetadataRetriever? = null
                        try {
                            mmr = MediaMetadataRetriever()
                            mmr.setDataSource(file.absolutePath)
                            val t = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                            if (!t.isNullOrBlank()) title = t.trim()
                            val a = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                            if (!a.isNullOrBlank()) artist = a.trim()
                            val alb = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                            if (!alb.isNullOrBlank() && !alb.equals("Unknown", true)) {
                                album = alb.trim()
                            } else if (folderName.isNotBlank() && !folderName.equals("Music", true) && !folderName.equals("Download", true) && !folderName.equals("TideMusic", true)) {
                                album = folderName
                            } else {
                                album = title.ifBlank { "Unknown" }
                            }
                            val aa = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                            if (!aa.isNullOrBlank()) albumArtist = aa.trim()
                            val dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                            if (dur != null && dur > 0) durationMs = dur
                            val br = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()
                            if (br != null) bitrate = br / 1000
                            val tr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.toIntOrNull()
                            if (tr != null) trackNum = tr
                            val disc = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)?.toIntOrNull()
                            if (disc != null) discNum = disc
                        } catch (_: Exception) {
                        } finally {
                            try { mmr?.release() } catch (_: Exception) {}
                        }

                        out.add(
                            MediaStoreScanner.ScannedTrack(
                                id = trackId,
                                filePath = file.absolutePath,
                                uri = file.absolutePath,
                                title = title,
                                artist = artist,
                                album = album,
                                albumArtist = albumArtist,
                                durationMs = durationMs,
                                dateAdded = file.lastModified(),
                                dateModified = file.lastModified(),
                                size = file.length(),
                                mimeType = "audio/$ext",
                                bitrate = bitrate,
                                sampleRate = sampleRate,
                                trackNumber = trackNum,
                                discNumber = discNum,
                                folderId = folderId,
                                artworkUri = null
                            )
                        )
                    }
                }
            }
        }

        for (d in targetDirs) {
            if (d.exists() && d.isDirectory) {
                walk(d, 0)
            }
        }

        out
    }
}
