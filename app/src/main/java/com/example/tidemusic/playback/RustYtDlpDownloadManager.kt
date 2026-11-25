package com.example.tidemusic.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import android.util.Log
import com.example.tidemusic.data.db.DownloadTaskDao
import com.example.tidemusic.data.db.DownloadTaskEntity
import com.example.tidemusic.domain.LibraryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Rust downloader engine (`rusty_ytdl` via `libtide_ytdl.so`).
 *
 * SAME techniques + SAME fallback shape + SAME offload/onload contract as
 * [YtDlpDownloadManager], but in Rust:
 *
 * Techniques mirrored:
 * 1. Best-audio extraction (yt-dlp `--extract-audio --audio-format mp3
 *    --audio-quality`  <=> Rust best-audio stream pick + FFmpeg convert below).
 * 2. `--embed-metadata` / `--embed-thumbnail`: best-effort — the Rust stream URL
 *    usually resolves with metadata; thumbnail embedding is skipped (artwork is
 *    resolved at scan time via MediaStore/MMR like any other file).
 * 3. `--write-subs/--write-auto-subs --convert-subs lrc`: NOT available in
 *    `rusty_ytdl 0.7.4` (no caption API). When lyrics are requested the task is
 *    routed to the yt-dlp engine instead — same UX, no silent lyrics loss.
 * 4. `--force-ipv4`: forwarded to Rust; honored best-effort (system resolver +
 *    `java.net.preferIPv4Stack`, same as the rest of the app).
 * 5. `--no-playlist`, `--no-mtime`, `--geo-bypass`, `--no-check-certificates`:
 *    single-video semantics are inherent (rusty_ytdl has no playlist mode);
 *    TLS verification stays ON in Rust (strictly safer).
 *
 * Fallback mirrored:
 * - Inside Rust: full info -> basic info retry (see `rust/tide-ytdl/src/lib.rs`).
 * - Across engines: unsupported site (non-YouTube) or Rust failure ->
 *   [YtDlpDownloadManager.enqueueDownload] with the same arguments.
 *
 * Offload/onload mirrored:
 * - Nothing loads at startup. [RustYtDlpBridge.ensureLoaded] maps the `.so`
 *   (≈8.5 MB arm64 vs ~48 MB/ABI of Python payload) only on the first Rust
 *   download; [YtDlpInitializer.ensureFFmpeg] prepares the FFmpeg binary
 *   WITHOUT touching Python. Idle/background playback never pays for either.
 * - Per task the Tokio runtime lives only for the download; [RustYtDlpBridge.nativeCancel]
 *   is invoked in `finally`, exactly like `destroyProcessById` on the yt-dlp path.
 */
class RustYtDlpDownloadManager(
    private val context: Context,
    private val downloadTaskDao: DownloadTaskDao,
    private val libraryRepository: LibraryRepository,
    private val ytdlpFallback: YtDlpDownloadManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _engineStatusMessage = MutableStateFlow<String?>(null)
    val engineStatusMessage: StateFlow<String?> = _engineStatusMessage.asStateFlow()

    private val activeTasksCount = java.util.concurrent.atomic.AtomicInteger(0)

    fun checkNetworkAvailability(): String = ytdlpFallback.checkNetworkAvailability()

    fun enqueueDownload(
        url: String,
        downloadWithLyrics: Boolean = true,
        qualityKbps: Int = 320,
        engine: String = "rust",
    ) {
        scope.launch {
            val networkStatus = checkNetworkAvailability()
            if (networkStatus == "No internet connection") {
                Log.w(TAG, "Aborting Rust download: $networkStatus")
                return@launch
            }

            // Lyrics need captions => yt-dlp engine owns that path (honest routing).
            if (downloadWithLyrics) {
                Log.i(TAG, "Lyrics requested, routing to yt-dlp engine")
                ytdlpFallback.enqueueDownload(url, downloadWithLyrics, qualityKbps, "yt-dlp")
                return@launch
            }

            _engineStatusMessage.value = null

            // Lazy load: Rust .so + FFmpeg binary only. Python is never touched here.
            val rustOk = RustYtDlpBridge.ensureLoaded()
            if (!rustOk) {
                Log.i(TAG, "Rust engine unavailable, falling back to yt-dlp")
                ytdlpFallback.enqueueDownload(url, downloadWithLyrics, qualityKbps, "yt-dlp")
                return@launch
            }
            if (!YtDlpInitializer.ensureFFmpeg(context)) {
                Log.w(TAG, "FFmpeg unavailable; Rust download continues without MP3 convert")
            }

            val addedAt = System.currentTimeMillis()
            val entity = DownloadTaskEntity(
                sourceUrl = url,
                status = "Queued (Rust)",
                progress = 0,
                destinationPath = null,
                addedAt = addedAt,
            )
            val taskId = downloadTaskDao.insert(entity)
            val taskIdStr = taskId.toString()

            val taskDir = File(context.cacheDir, "rustytdl_task_${taskId}_${System.currentTimeMillis()}")
            if (!taskDir.exists()) taskDir.mkdirs()

            val active = activeTasksCount.incrementAndGet()
            DownloadService.update(context, active, 0, url)

            try {
                downloadTaskDao.updateProgress(taskId, "Downloading (Rust)", 0)

                val rawName = "audio_$taskId"
                val destRaw = File(taskDir, "$rawName.bin").absolutePath

                val callback = object : RustYtDlpBridge.DownloadCallback {
                    override fun onProgress(progress01: Float) {
                        val mapped = (progress01 * 90f).toInt().coerceIn(0, 90)
                        DownloadService.update(context, activeTasksCount.get(), mapped, url)
                        scope.launch {
                            downloadTaskDao.updateProgress(taskId, "Downloading (Rust)", mapped)
                        }
                    }
                }

                val rc = withContext(Dispatchers.IO) {
                    RustYtDlpBridge.nativeDownload(
                        url, destRaw, taskIdStr, qualityKbps, false, true, callback,
                    )
                }

                when (rc) {
                    1 -> {
                        // Unsupported site => same fallback contract as yt-dlp's own retry.
                        Log.i(TAG, "Rust engine: unsupported site, falling back to yt-dlp")
                        downloadTaskDao.updateProgress(taskId, "Switching engine...", 5)
                        ytdlpFallback.enqueueDownload(url, downloadWithLyrics, qualityKbps, "yt-dlp")
                        downloadTaskDao.updateFinal(taskId, "Done • Handed to yt-dlp", null)
                        return@launch
                    }
                    2 -> {
                        downloadTaskDao.updateFinal(taskId, "Failed: cancelled", null)
                        return@launch
                    }
                    3 -> throw java.io.IOException("Rust engine failed (see logcat $TAG)")
                }

                downloadTaskDao.updateProgress(taskId, "Finalizing...", 95)

                val rawFile = File(destRaw)
                if (!rawFile.exists() || rawFile.length() == 0L) {
                    throw java.io.IOException("Rust engine produced no audio")
                }

                val publicMusicDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                    "TideMusic",
                )
                if (!publicMusicDir.exists()) publicMusicDir.mkdirs()

                val baseTitle = sanitizeFileName(url)
                // Same convert step yt-dlp does via --extract-audio: try FFmpeg binary,
                // fall back to the original container (still playable/indexed).
                val (publicAudio, converted) = convertToMp3OrKeep(rawFile, publicMusicDir, baseTitle, qualityKbps)

                val scanPaths = mutableListOf(publicAudio.absolutePath)
                try { taskDir.deleteRecursively() } catch (_: Exception) {}

                try {
                    libraryRepository.ensureYtDlpPlaylist()
                    libraryRepository.rescanIncremental()
                    addPathToYtDlpPlaylist(publicAudio.absolutePath)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed rescan/playlist (Rust)", e)
                }

                val finalStatus = if (converted) "Done • Audio (Rust)" else "Done • Audio (Rust, original format)"
                downloadTaskDao.updateFinal(taskId, finalStatus, publicAudio.absolutePath)

                if (scanPaths.isNotEmpty()) {
                    android.media.MediaScannerConnection.scanFile(context, scanPaths.toTypedArray(), null) { _, _ ->
                        scope.launch {
                            try {
                                libraryRepository.rescanIncremental()
                                for (path in scanPaths) addPathToYtDlpPlaylist(path)
                            } catch (_: Exception) {}
                        }
                    }
                }
            } catch (e: Exception) {
                // Cross-engine fallback on Rust failure (mirrors yt-dlp's own retry).
                if ((e.message ?: "").contains("Rust engine failed")) {
                    Log.w(TAG, "Rust download failed, falling back to yt-dlp for $url", e)
                    try {
                        ytdlpFallback.enqueueDownload(url, downloadWithLyrics, qualityKbps, "yt-dlp")
                        downloadTaskDao.updateFinal(taskId, "Done • Handed to yt-dlp", null)
                    } catch (fe: Exception) {
                        Log.e(TAG, "Fallback also failed", fe)
                        downloadTaskDao.updateFinal(taskId, "Failed: ${e.message}", null)
                    }
                } else {
                    Log.e(TAG, "Rust download failed for $url", e)
                    downloadTaskDao.updateFinal(taskId, "Failed: ${e.message}", null)
                }
            } finally {
                // Same unload contract: no Rust worker survives the task.
                runCatching { RustYtDlpBridge.nativeCancel(taskIdStr) }
                val remaining = activeTasksCount.decrementAndGet()
                if (remaining <= 0) DownloadService.stop(context)
                else DownloadService.update(context, remaining, 100, "Processing queue...")
            }
        }
    }

    fun cancelTask(taskId: Long) {
        scope.launch {
            runCatching { RustYtDlpBridge.nativeCancel(taskId.toString()) }
        }
    }

    private suspend fun addPathToYtDlpPlaylist(path: String) {
        try {
            val song = libraryRepository.findSongByFilePath(path)
            if (song != null) libraryRepository.addSongToYtDlpPlaylist(song.id)
            else Log.w(TAG, "Song not yet in library for path=$path")
        } catch (e: Exception) {
            Log.e(TAG, "Failed adding to yt-dlp playlist", e)
        }
    }

    private fun sanitizeFileName(url: String): String {
        val id = Regex("(v=|youtu\\.be/|shorts/)([A-Za-z0-9_-]{6,})").find(url)
            ?.groupValues?.getOrNull(2) ?: System.currentTimeMillis().toString()
        return "rust_$id"
    }

    /**
     * Best-effort MP3 convert using the already-bundled FFmpeg binary
     * (extracted by [YtDlpInitializer.ensureFFmpeg], no Python involved).
     * Returns (file, converted=true/false). Never throws: falls back to [raw].
     */
    private fun convertToMp3OrKeep(raw: File, outDir: File, base: String, qualityKbps: Int): Pair<File, Boolean> {
        val target = File(outDir, "$base.mp3")
        val ffmpegBin = findFfmpegBinary()
        if (ffmpegBin == null) {
            val keep = File(outDir, "$base.m4a")
            return try {
                raw.copyTo(keep, overwrite = true); Pair(keep, false)
            } catch (_: Exception) { Pair(raw, false) }
        }
        val q = when (qualityKbps) {
            320 -> "0"; 192 -> "2"; 128 -> "5"; else -> "0"
        }
        return try {
            // Equivalent of yt-dlp `-x --audio-format mp3 --audio-quality <q>`.
            val proc = ProcessBuilder(
                ffmpegBin.absolutePath, "-y", "-i", raw.absolutePath,
                "-vn", "-c:a", "libmp3lame", "-q:a", q, target.absolutePath,
            ).redirectErrorStream(true).start()
            val done = proc.waitFor(5, TimeUnit.MINUTES)
            if (done && proc.exitValue() == 0 && target.exists() && target.length() > 0L) {
                Pair(target, true)
            } else {
                try { if (target.exists()) target.delete() } catch (_: Exception) {}
                val keep = File(outDir, "$base.m4a")
                raw.copyTo(keep, overwrite = true); Pair(keep, false)
            }
        } catch (_: Exception) {
            try {
                val keep = File(outDir, "$base.m4a")
                raw.copyTo(keep, overwrite = true); Pair(keep, false)
            } catch (_: Exception) { Pair(raw, false) }
        }
    }

    private fun findFfmpegBinary(): File? = try {
        val root = File(context.noBackupFilesDir, "youtubedl-android/packages/ffmpeg")
        if (!root.exists()) return null
        root.walkTopDown().firstOrNull { it.isFile && (it.name == "ffmpeg" || it.name.startsWith("ffmpeg")) && it.canExecute() }
            ?: root.walkTopDown().firstOrNull { it.isFile && it.name == "ffmpeg" }
    } catch (_: Exception) { null }

    companion object {
        private const val TAG = "RustYtDlp"
    }
}
