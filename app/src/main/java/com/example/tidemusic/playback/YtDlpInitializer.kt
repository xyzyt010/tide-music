package com.example.tidemusic.playback

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lifecycle-independent flag set after `YoutubeDL.getInstance().init()` is attempted at
 * app startup. Screens read [isAvailable] so they can downgrade gracefully if the bundled
 * yt-dlp/payload failed to initialize on a particular device.
 */
@Suppress("unused")
object YtDlpInitializer {

    private val initialized = AtomicBoolean(false)
    /** True after TideMusicApp finished attempting to load YoutubeDL + FFmpeg natives. */
    val isAvailable: Boolean get() = initialized.get() && initializedOk

    private var initializedOk: Boolean by mutableStateOf(false)

    fun markInitialized(success: Boolean = true) {
        initialized.set(true)
        initializedOk = success
    }

    /** Sanity-check the runtime instance directly. */
    val isInstanceAlive: Boolean
        get() = runCatching {
            YoutubeDL.getInstance() != null
        }.getOrDefault(false)

    /** Re-exported so callers have a single place to look up YoutubeDL exceptions if needed. */
    @Suppress("UNUSED")
    val exceptionType: Class<out Throwable> = YoutubeDLException::class.java
}
