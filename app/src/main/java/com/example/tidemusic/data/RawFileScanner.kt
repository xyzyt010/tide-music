package com.example.tidemusic.data

import android.media.MediaMetadataRetriever
import android.os.Environment
import android.util.Log
import com.example.tidemusic.data.db.StableIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A fast physical filesystem scanner that walks accessible directories in external storage,
 * extracting real ID3/embedded tags for any unindexed music files.
 */
class RawFileScanner {

    private val ignoredDirNames = setOf(
        "android", "cache", "thumbnails", "obb", "system", ".trash", "lost.dir",
        ".cache", ".temp", ".thumbnails", ".data", "dcim", "pictures", "movies", "camera"
    )

    suspend fun scan(
        knownPaths: Set<String> = emptySet(),
        onTrackFound: ((Int) -> Unit)? = null,
    ): List<MediaStoreScanner.ScannedTrack> = withContext(Dispatchers.IO) {
        val root = Environment.getExternalStorageDirectory()
        val out = mutableListOf<MediaStoreScanner.ScannedTrack>()
        val supportedExtensions = setOf("mp3", "m4a", "flac", "wav", "aac", "ogg", "opus")

        // Priority audio folders only
        val candidateDirs = mutableListOf<File>()
        listOf("Music", "Download", "TideMusic", "Audiobooks", "Podcasts").forEach {
            val f = File(root, it)
            if (f.exists() && f.isDirectory) candidateDirs.add(f)
        }

        val visitedDirs = HashSet<String>()
        val scannedPaths = HashSet<String>(knownPaths)
        var mmr: MediaMetadataRetriever? = null
        try {
            mmr = MediaMetadataRetriever()
        } catch (_: Exception) {}

        try {
            fun walk(dir: File, depth: Int = 0) {
                if (depth > 4) return
                val canonicalPath = try { dir.canonicalPath } catch (_: Exception) { dir.absolutePath }
                if (!visitedDirs.add(canonicalPath)) return

                val files = try {
                    dir.listFiles()
                } catch (_: Exception) {
                    null
                } ?: return

                for (file in files) {
                    if (file.isDirectory) {
                        val name = file.name.lowercase()
                        if (!name.startsWith(".") && name !in ignoredDirNames) {
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
                            var album = ""
                            var albumArtist: String? = null
                            var durationMs = 0L
                            var bitrate = 0
                            var sampleRate = 0
                            var trackNum = 0
                            var discNum = 0

                            if (mmr != null) {
                                try {
                                    mmr.setDataSource(file.absolutePath)
                                    val t = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                                    if (!t.isNullOrBlank()) title = t.trim()
                                    val a = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                                    if (!a.isNullOrBlank()) artist = a.trim()
                                    val alb = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                                    val isGenAlb = isGenericOrInvalidAlbum(alb)
                                    if (!alb.isNullOrBlank() && !isGenAlb) {
                                        album = alb.trim()
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
                                } catch (_: Exception) {}
                            }

                            val (parsedArtist, parsedTitle) = MediaStoreScanner.parseArtistAndTitle(title, artist, file.name)
                            title = parsedTitle
                            artist = parsedArtist

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
                                    artworkUri = null,
                                )
                            )
                            onTrackFound?.invoke(out.size)
                        }
                    }
                }
            }

            for (d in candidateDirs) {
                walk(d, 0)
            }
        } finally {
            try { mmr?.release() } catch (_: Exception) {}
        }

        out
    }

    private fun isGenericOrInvalidAlbum(name: String?): Boolean {
        if (name.isNullOrBlank()) return true
        val lower = name.trim().lowercase()
        return lower in setOf(
            "unknown", "download", "downloads", "music", "tidemusic", "audio",
            "podcasts", "audiobooks", "dcim", "0", "sdcard", "emulated", "storage",
            "internal storage", "sounds", "recordings", "voice"
        )
    }
}
