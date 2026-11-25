package com.example.tidemusic.playback

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.sync.Mutex

/**
 * JNI bridge to the Rust downloader (`rust/tide-ytdl`, `libtide_ytdl.so`).
 *
 * SAME offload/onload contract as the yt-dlp path:
 * - The `.so` (≈8.5 MB arm64, ≈5.7 MB armv7 — vs ~48 MB/ABI of Python payload)
 *   is loaded lazily via [ensureLoaded], i.e. only on the first Rust download.
 *   Idle playback / browsing never maps it.
 * - No Rust threads, runtime or sockets exist until [nativeDownload] is called;
 *   the Tokio runtime is created per download and dropped at the end.
 * - [nativeCancel] + task-id teardown mirror `destroyProcessById`: nothing
 *   Rust-related survives after the task ends (the small library mapping itself
 *   stays until process death — Android has no supported JNI unload — but no
 *   worker, thread or socket remains).
 */
object RustYtDlpBridge {

    private const val TAG = "RustYtDlp"
    private const val LIB = "tide_ytdl"

    private val loadMutex = Mutex()
    private val loaded = AtomicBoolean(false)
    @Volatile var loadError: String? = null
        private set

    /** Callback fed by Rust per chunk (progress 0..1). Implemented in Kotlin. */
    interface DownloadCallback {
        fun onProgress(progress01: Float)
    }

    /**
     * Loads `libtide_ytdl.so` on first use. Returns false when the ABI has no
     * bundled binary (e.g. x86 emulator) — callers must fall back to yt-dlp.
     */
    suspend fun ensureLoaded(): Boolean {
        if (loaded.get()) return true
        loadMutex.lock()
        try {
            if (loaded.get()) return true
            val ok = runCatching { System.loadLibrary(LIB) }
                .onFailure {
                    loadError = it.message
                    Log.w(TAG, "Rust engine unavailable, falling back to yt-dlp", it)
                }.isSuccess
            loaded.set(ok)
            if (ok) {
                Log.i(TAG, "Rust engine loaded: ${runCatching { nativeVersion() }.getOrDefault("?")}")
            }
            return ok
        } finally {
            loadMutex.unlock()
        }
    }

    val isAvailable: Boolean get() = loaded.get()

    /** `rusty_ytdl/<crate-version>` string for the Download UI. */
    external fun nativeVersion(): String

    /**
     * Downloads best-audio for [url] into [destPath].
     * @return 0 ok, 1 unsupported site (caller falls back to yt-dlp),
     *         2 cancelled, 3 error (details in thrown IOException).
     */
    external fun nativeDownload(
        url: String,
        destPath: String,
        taskId: String,
        qualityKbps: Int,
        withLyrics: Boolean,
        forceIpv4: Boolean,
        callback: DownloadCallback,
    ): Int

    external fun nativeCancel(taskId: String)
}
