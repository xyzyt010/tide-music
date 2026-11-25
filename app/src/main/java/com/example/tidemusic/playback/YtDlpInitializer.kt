package com.example.tidemusic.playback

import android.content.Context
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/**
 * Lazy, process-lifetime runtime for yt-dlp + FFmpeg.
 *
 * IMPORTANT: nothing here runs at Application.onCreate. The bundled payload is large
 * (`libpython.zip.so` is unzipped into ~90 MB of files on first init, and `execute()`
 * spawns Python child processes), so loading it on every cold start wasted RAM, CPU,
 * disk I/O and battery even when the user never opened the Download screen.
 *
 * [ensureInitialized] is called only from [YtDlpDownloadManager.enqueueDownload], i.e.
 * only when the user actually requests a download. Concurrent enqueues are serialized
 * by a mutex so the heavy unzip/init happens exactly once per process. After each task
 * finishes, [destroyTask] kills the Python process spawned for that task so no yt-dlp
 * worker keeps living in the background once downloading is over.
 *
 * The nightly update check used to run on every app start; it is now a rate-limited
 * (once per 7 days) best-effort background job that only starts after a successful
 * initialization.
 */
object YtDlpInitializer {

    enum class Status { IDLE, INITIALIZING, READY, FAILED }

    private const val TAG = "YtDlpRuntime"
    private const val PREFS = "ytdlp_runtime"
    private const val KEY_LAST_UPDATE = "last_update_ms"
    private const val UPDATE_INTERVAL_MS = 7L * 24L * 60L * 60L * 1000L

    private val initMutex = Mutex()
    private val _status = MutableStateFlow(Status.IDLE)
    val status: StateFlow<Status> = _status.asStateFlow()

    /** True only after a successful lazy init in this process. */
    val isAvailable: Boolean get() = _status.value == Status.READY

    /** True while the first download is waiting for the payload to be prepared. */
    val isInitializing: Boolean get() = _status.value == Status.INITIALIZING

    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val ffmpegMutex = Mutex()
    @Volatile private var ffmpegReady = false

    /**
     * Ensures only the FFmpeg binary is ready (no Python, no yt-dlp payload).
     * Used by the Rust engine, which downloads with `rusty_ytdl` and only needs
     * FFmpeg for the final audio extraction — Python is never loaded on this path.
     */
    suspend fun ensureFFmpeg(context: Context): Boolean {
        if (ffmpegReady) return true
        ffmpegMutex.lock()
        try {
            if (ffmpegReady) return true
            val ok = withContext(Dispatchers.IO) {
                runCatching { FFmpeg.getInstance().init(context.applicationContext) }
                    .onFailure { Log.w(TAG, "FFmpeg lazy init failed", it) }
                    .isSuccess
            }
            ffmpegReady = ok
            return ok
        } finally {
            ffmpegMutex.unlock()
        }
    }

    /**
     * Ensures YoutubeDL + FFmpeg natives are ready. Safe to call concurrently; returns
     * false (and resets to [Status.IDLE] so a later download can retry) if init fails.
     */
    suspend fun ensureInitialized(context: Context): Boolean {
        if (_status.value == Status.READY) return true
        initMutex.lock()
        try {
            if (_status.value == Status.READY) return true
            _status.value = Status.INITIALIZING
            val app = context.applicationContext
            val ok = withContext(Dispatchers.IO) {
                val ytdlpOk = runCatching { YoutubeDL.getInstance().init(app) }
                    .onFailure { Log.w(TAG, "yt-dlp lazy init failed", it) }
                    .isSuccess
                // Reuse the FFmpeg-only path so both engines share one init.
                val ffmpegOk = ensureFFmpeg(app)
                ytdlpOk && ffmpegOk
            }
            _status.value = if (ok) Status.READY else Status.IDLE
            if (ok) maybeUpdateYoutubeDL(app)
            return ok
        } finally {
            initMutex.unlock()
        }
    }

    /** Best-effort, rate-limited extractor refresh — never on the cold-start path. */
    private fun maybeUpdateYoutubeDL(app: Context) {
        runtimeScope.launch {
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            if (now - prefs.getLong(KEY_LAST_UPDATE, 0L) < UPDATE_INTERVAL_MS) return@launch
            val updated = runCatching {
                YoutubeDL.getInstance().updateYoutubeDL(app, YoutubeDL.UpdateChannel.NIGHTLY)
            }.onFailure { Log.w(TAG, "yt-dlp update skipped", it) }.isSuccess
            if (updated) {
                prefs.edit().putLong(KEY_LAST_UPDATE, System.currentTimeMillis()).apply()
            }
        }
    }

    /**
     * Kills the Python process (and its children) that yt-dlp spawned for this task id.
     * Called in the `finally` block of every download so nothing yt-dlp-related survives
     * after the download completes, fails or is cancelled.
     */
    fun destroyTask(taskId: String) {
        runCatching { YoutubeDL.getInstance().destroyProcessById(taskId) }
            .onFailure { Log.w(TAG, "Failed to stop yt-dlp process $taskId", it) }
    }

    /** Sanity-check the runtime instance directly. */
    val isInstanceAlive: Boolean
        get() = runCatching { YoutubeDL.getInstance(); true }.getOrDefault(false)

    /** Re-exported so callers have a single place to look up YoutubeDL exceptions if needed. */
    @Suppress("UNUSED")
    val exceptionType: Class<out Throwable> = YoutubeDLException::class.java
}
