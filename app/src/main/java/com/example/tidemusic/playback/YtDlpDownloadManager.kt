package com.example.tidemusic.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import android.util.Log
import com.example.tidemusic.data.db.DownloadTaskDao
import com.example.tidemusic.data.db.DownloadTaskEntity
import com.example.tidemusic.domain.LibraryRepository
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Handles the actual yt-dlp downloads using youtubedl-android wrapper (spec Section 6.6 & 8.6).
 *
 * Enforces IPv4 mode for yt-dlp execution (--force-ipv4) while ensuring network status check
 * correctly recognizes active internet connectivity.
 */
class YtDlpDownloadManager(
    private val context: Context,
    private val downloadTaskDao: DownloadTaskDao,
    private val libraryRepository: LibraryRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _networkStatusMessage = MutableStateFlow<String?>(null)
    val networkStatusMessage: StateFlow<String?> = _networkStatusMessage.asStateFlow()

    init {
        checkNetworkAvailability()
    }

    /**
     * Checks network connectivity using system ConnectivityManager.
     * Returns null when connected to internet, or an error message if offline.
     */
    fun checkNetworkAvailability(): String {
        var isConnected = false
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                val activeNetwork = cm.activeNetwork
                val caps = cm.getNetworkCapabilities(activeNetwork)
                isConnected = caps != null && (
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                )
            }
        } catch (e: Exception) {
            Log.e("YtDlpDownload", "Error checking network availability", e)
            isConnected = true // fallback to allowing download attempt
        }

        val msg = if (!isConnected) "No internet connection" else null
        _networkStatusMessage.value = msg
        return msg ?: "Connected"
    }

    fun enqueueDownload(
        url: String,
        downloadWithLyrics: Boolean = true,
        qualityKbps: Int = 320,
        engine: String = "yt-dlp",
    ) {
        scope.launch {
            val networkStatus = checkNetworkAvailability()
            if (networkStatus == "No internet connection") {
                Log.w("YtDlpDownload", "Aborting download: $networkStatus")
                return@launch
            }

            val addedAt = System.currentTimeMillis()
            val entity = DownloadTaskEntity(
                sourceUrl = url,
                status = "Queued",
                progress = 0,
                destinationPath = null,
                addedAt = addedAt
            )
            val taskId = downloadTaskDao.insert(entity)

            val taskDir = File(context.cacheDir, "ytdlp_task_${taskId}_${System.currentTimeMillis()}")
            if (!taskDir.exists()) {
                taskDir.mkdirs()
            }

            try {
                downloadTaskDao.updateProgress(taskId, "Downloading", 0)

                fun buildRequest(withLyrics: Boolean): YoutubeDLRequest {
                    val req = YoutubeDLRequest(url)
                    req.addOption("-o", taskDir.absolutePath + "/%(title)s.%(ext)s")
                    req.addOption("-x") // Extract audio
                    req.addOption("--audio-format", "mp3")
                    val qualityOpt = when (qualityKbps) {
                        320 -> "0"
                        192 -> "2"
                        128 -> "5"
                        else -> "0"
                    }
                    req.addOption("--audio-quality", qualityOpt)
                    req.addOption("--embed-metadata")
                    req.addOption("--embed-thumbnail")
                    req.addOption("--no-playlist")
                    req.addOption("--no-mtime")
                    // Hardcoded IPv4-only networking — intentional and immutable (spec 6.6).
                    req.addOption("--force-ipv4")
                    req.addOption("--no-check-certificates")
                    req.addOption("--geo-bypass")

                    // NOTE: do NOT add `--extractor-args youtube:player_client=web,android;
                    // player_skip=configs` here. Forcing those clients is what broke subtitle
                    // downloads: the `android` client's metadata usually contains NO caption
                    // tracks at all, and skipping the web player config drops the rest, so
                    // --write-subs/--write-auto-subs found nothing to download even when the
                    // video clearly had subtitles. Default client selection returns captions
                    // reliably. `--no-warnings` was also removed so "no subtitles" warnings
                    // stay visible instead of silently masking the problem.

                    if (withLyrics) {
                        req.addOption("--write-subs")
                        req.addOption("--write-auto-subs")
                        // Manual subs first, then auto-generated; `*-orig` covers the video's
                        // original upload language when it isn't English. live_chat excluded.
                        req.addOption("--sub-langs", "en.*,en,eng,en-US,en-GB,.*-orig,-live_chat")
                        // vtt preferred (word-level timing for synced lyrics); ffmpeg converts to lrc.
                        req.addOption("--sub-format", "vtt/srt/best")
                        req.addOption("--convert-subs", "lrc")
                    }
                    return req
                }

                var downloadSuccess = false
                try {
                    val request = buildRequest(downloadWithLyrics)
                    YoutubeDL.getInstance().execute(request, taskId.toString()) { progress, _, _ ->
                        val mappedProgress = (progress * 0.90f).toInt().coerceIn(0, 90)
                        scope.launch {
                            downloadTaskDao.updateProgress(taskId, "Downloading", mappedProgress)
                        }
                    }
                    downloadSuccess = true
                } catch (firstAttemptError: Exception) {
                    Log.w("YtDlpDownload", "Primary download failed. Trying pure audio fallback...", firstAttemptError)
                    if (downloadWithLyrics) {
                        downloadTaskDao.updateProgress(taskId, "Retrying Audio...", 10)
                        val fallbackRequest = buildRequest(false)
                        YoutubeDL.getInstance().execute(fallbackRequest, taskId.toString()) { progress, _, _ ->
                            val mappedProgress = (progress * 0.90f).toInt().coerceIn(0, 90)
                            scope.launch {
                                downloadTaskDao.updateProgress(taskId, "Downloading Audio", mappedProgress)
                            }
                        }
                        downloadSuccess = true
                    } else {
                        throw firstAttemptError
                    }
                }

                downloadTaskDao.updateProgress(taskId, "Finalizing...", 95)

                val publicMusicDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                    "TideMusic"
                )
                if (!publicMusicDir.exists()) publicMusicDir.mkdirs()

                val allFiles = taskDir.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") } ?: emptyList()
                val audioFiles = allFiles.filter { it.extension.equals("mp3", true) || it.extension.equals("m4a", true) || it.extension.equals("opus", true) }
                val lrcFiles = allFiles.filter { it.extension.equals("lrc", true) }
                val subFiles = allFiles.filter { it.extension.equals("vtt", true) || it.extension.equals("srt", true) }

                var lyricsSynced = false
                val lyricsAttempted = downloadWithLyrics
                val scanPaths = mutableListOf<String>()

                for (audioFile in audioFiles) {
                    val publicAudio = File(publicMusicDir, audioFile.name)
                    try {
                        audioFile.copyTo(publicAudio, overwrite = true)
                        scanPaths.add(publicAudio.absolutePath)
                    } catch (e: Exception) {
                        Log.e("YtDlpDownload", "Failed to copy audio file", e)
                        scanPaths.add(audioFile.absolutePath)
                    }

                    // Process and convert subtitles to .lrc
                    if (downloadWithLyrics) {
                        val baseName = audioFile.nameWithoutExtension
                        val publicLrc = File(publicMusicDir, "$baseName.lrc")

                        // 1. Prefer an exact "<name>.lrc" (yt-dlp's --convert-subs output),
                        //    then the original-language track (<name>.<lang>-orig.lrc), then
                        //    any converted lrc belonging to this task dir (single-video task).
                        val matchingLrc = lrcFiles.firstOrNull { it.nameWithoutExtension.equals(baseName, true) }
                            ?: lrcFiles.firstOrNull {
                                it.name.startsWith(baseName, true) && it.nameWithoutExtension.endsWith("-orig", true)
                            }
                            ?: lrcFiles.firstOrNull { it.name.startsWith(baseName, true) || it.name.contains(baseName) }

                        if (matchingLrc != null && matchingLrc.length() > 0L) {
                            try {
                                matchingLrc.copyTo(publicLrc, overwrite = true)
                                if (publicLrc.exists() && publicLrc.length() > 0L) {
                                    lyricsSynced = true
                                }
                            } catch (e: Exception) {
                                Log.w("YtDlpDownload", "Failed copying native .lrc: ${matchingLrc.name}", e)
                            }
                        } else {
                            // 2. Check for .vtt or .srt and convert via SubtitleToLrcConverter
                            val matchingSub = subFiles.firstOrNull { it.name.startsWith(baseName, true) && it.nameWithoutExtension.endsWith("-orig", true) }
                                ?: subFiles.firstOrNull { it.name.startsWith(baseName, true) || it.name.contains(baseName) }
                            if (matchingSub != null) {
                                try {
                                    val success = com.example.tidemusic.util.SubtitleToLrcConverter.convertToLrc(matchingSub, publicLrc)
                                    if (success && publicLrc.exists() && publicLrc.length() > 0L) {
                                        lyricsSynced = true
                                    } else {
                                        try { if (publicLrc.exists()) publicLrc.delete() } catch (_: Exception) {}
                                    }
                                } catch (e: Exception) {
                                    Log.w("YtDlpDownload", "Failed converting subtitle: ${matchingSub.name}", e)
                                    try { if (publicLrc.exists()) publicLrc.delete() } catch (_: Exception) {}
                                }
                            } else {
                                Log.i("YtDlpDownload", "No subtitles found for track: ${audioFile.name}")
                                try { if (publicLrc.exists()) publicLrc.delete() } catch (_: Exception) {}
                            }
                        }
                    }
                }

                // Clean up task directory completely
                try { taskDir.deleteRecursively() } catch (_: Exception) {}

                // Ensure Download playlist exists, rescan library, and link new song(s) before marking Done
                try {
                    libraryRepository.ensureYtDlpPlaylist()
                    libraryRepository.rescanIncremental()
                    for (path in scanPaths) {
                        addPathToYtDlpPlaylist(path)
                    }
                } catch (e: Exception) {
                    Log.e("YtDlpDownload", "Failed direct rescanIncremental / Download playlist", e)
                }

                val finalStatus = when {
                    lyricsSynced -> "Done • Subtitles / Synced Lyrics"
                    lyricsAttempted -> "Done • No Subtitles or Lyrics Found"
                    else -> "Done • Audio Only"
                }

                val finalPath = scanPaths.firstOrNull() ?: publicMusicDir.absolutePath
                downloadTaskDao.updateFinal(taskId, finalStatus, finalPath)

                if (scanPaths.isNotEmpty()) {
                    android.media.MediaScannerConnection.scanFile(
                        context,
                        scanPaths.toTypedArray(),
                        null
                    ) { _, _ ->
                        scope.launch {
                            try {
                                libraryRepository.rescanIncremental()
                                for (path in scanPaths) {
                                    addPathToYtDlpPlaylist(path)
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e("YtDlpDownload", "Download failed for $url", e)
                downloadTaskDao.updateFinal(taskId, "Failed: ${e.message}", null)
            }
        }
    }

    private suspend fun addPathToYtDlpPlaylist(path: String) {
        try {
            val song = libraryRepository.findSongByFilePath(path)
            if (song != null) {
                libraryRepository.addSongToYtDlpPlaylist(song.id)
            } else {
                // Path may differ slightly after MediaStore reindex — try basename match via rescan path
                Log.w("YtDlpDownload", "Song not yet in library for path=$path")
            }
        } catch (e: Exception) {
            Log.e("YtDlpDownload", "Failed adding to yt-dlp playlist", e)
        }
    }
}
