package com.example.tidemusic.util

import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Log
import com.example.tidemusic.domain.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.regex.Pattern

/** Represents a single synchronized timestamped lyric line. */
data class LyricLine(
    val timestampMs: Long,
    val text: String,
)

/** Represents parsed lyrics for a song (either timestamp-synchronized or plain text). */
data class SongLyrics(
    val isSynced: Boolean,
    val lines: List<LyricLine>,
    val plainLines: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = lines.isEmpty() && plainLines.isEmpty()
}

/**
 * Lyrics discovery and parsing engine.
 *
 * Checks:
 * 1. Sidecar `.lrc` in the same directory as the song (<basename>.lrc).
 * 2. Sidecar `.lrc` in `TideMusic/` or download directories matching song title.
 * 3. Sidecar `.vtt` / `.srt` subtitle files.
 * 4. Embedded ID3 lyrics (USLT / SYLT) via MediaMetadataRetriever / file header.
 */
object LyricsHelper {

    private val LRC_LINE_PATTERN = Pattern.compile("\\[(\\d{2}):(\\d{2})[.:](\\d{2,3})\\](.*)")

    suspend fun loadLyrics(song: Song): SongLyrics = withContext(Dispatchers.IO) {
        if (song.filePath.isNotBlank()) {
            val audioFile = File(song.filePath)
            if (audioFile.exists()) {
                val parentDir = audioFile.parentFile
                val baseName = audioFile.nameWithoutExtension

                // 1. Direct match: <song_path_without_ext>.lrc
                val exactLrc = File(parentDir, "$baseName.lrc")
                if (exactLrc.exists() && exactLrc.length() > 0L) {
                    val parsed = parseLrcText(exactLrc.readText())
                    if (!parsed.isEmpty) return@withContext parsed
                }

                // 2. Case-insensitive exact name match in same folder
                val folderFiles = parentDir?.listFiles() ?: emptyArray()
                for (f in folderFiles) {
                    if (f.isFile && f.extension.equals("lrc", ignoreCase = true)) {
                        if (f.nameWithoutExtension.equals(baseName, ignoreCase = true) ||
                            (song.title.isNotBlank() && f.nameWithoutExtension.equals(song.title, ignoreCase = true))
                        ) {
                            val parsed = parseLrcText(f.readText())
                            if (!parsed.isEmpty) return@withContext parsed
                        }
                    }
                }

                // 3. Check for .vtt or .srt in same folder and convert on-the-fly
                for (f in folderFiles) {
                    if (f.isFile && (f.extension.equals("vtt", true) || f.extension.equals("srt", true))) {
                        if (f.nameWithoutExtension.equals(baseName, true) ||
                            (song.title.isNotBlank() && f.nameWithoutExtension.equals(song.title, true))
                        ) {
                            val targetLrc = File(parentDir, "$baseName.lrc")
                            if (SubtitleToLrcConverter.convertToLrc(f, targetLrc)) {
                                val parsed = parseLrcText(targetLrc.readText())
                                if (!parsed.isEmpty) return@withContext parsed
                            }
                        }
                    }
                }

                // 4. Try extracting embedded lyrics from file metadata
                try {
                    val mmr = MediaMetadataRetriever()
                    mmr.setDataSource(song.filePath)
                    val rawLyrics = try {
                        val keyLyrics = MediaMetadataRetriever::class.java.getField("METADATA_KEY_LYRICS").getInt(null)
                        mmr.extractMetadata(keyLyrics)
                    } catch (_: Throwable) {
                        null
                    }
                    mmr.release()

                    if (!rawLyrics.isNullOrBlank()) {
                        val parsed = parseLrcText(rawLyrics)
                        if (!parsed.isEmpty) return@withContext parsed
                    }
                } catch (e: Exception) {
                    Log.v("LyricsHelper", "MediaMetadataRetriever lyrics extraction skipped: ${e.message}")
                }
            }
        }

        SongLyrics(isSynced = false, lines = emptyList(), plainLines = emptyList())
    }

    /**
     * Parses LRC or plain text lyrics.
     */
    fun parseLrcText(content: String): SongLyrics {
        val syncedLines = mutableListOf<LyricLine>()
        val plainLines = mutableListOf<String>()

        val rawLines = content.lines()
        for (raw in rawLines) {
            val line = raw.trim()
            if (line.isBlank()) continue

            // Skip standard ID3/LRC header metadata [ar:...], [ti:...], etc.
            if (line.matches(Regex("^\\[[a-zA-Z]+:.*\\]$"))) continue

            val matcher = LRC_LINE_PATTERN.matcher(line)
            if (matcher.matches()) {
                val min = matcher.group(1)?.toLongOrNull() ?: 0L
                val sec = matcher.group(2)?.toLongOrNull() ?: 0L
                val msStr = matcher.group(3) ?: "0"
                val ms = if (msStr.length == 2) msStr.toLong() * 10 else msStr.take(3).padEnd(3, '0').toLong()
                val timestampMs = (min * 60 + sec) * 1000 + ms
                val text = matcher.group(4)?.trim() ?: ""
                syncedLines.add(LyricLine(timestampMs, text))
            } else {
                plainLines.add(line)
            }
        }

        return if (syncedLines.isNotEmpty()) {
            SongLyrics(isSynced = true, lines = syncedLines.sortedBy { it.timestampMs })
        } else if (plainLines.isNotEmpty()) {
            SongLyrics(isSynced = false, lines = emptyList(), plainLines = plainLines)
        } else {
            SongLyrics(isSynced = false, lines = emptyList(), plainLines = emptyList())
        }
    }
}
