package com.example.tidemusic

import android.app.Application
import android.util.Log
import com.example.tidemusic.di.ServiceLocator
import com.example.tidemusic.playback.YtDlpInitializer
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.example.tidemusic.util.AudioArtworkFetcher
import com.example.tidemusic.util.SongArtworkKeyer

/**
 * TideMusicApp — manually wires the [ServiceLocator] at process start.
 */
class TideMusicApp : Application(), SingletonImageLoader.Factory {
    companion object {
        lateinit var instance: TideMusicApp
            private set
        private const val TAG = "TideMusicApp"
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(SongArtworkKeyer())
                add(AudioArtworkFetcher.Factory(this@TideMusicApp))
            }
            .memoryCache {
                coil3.memory.MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .build()
    }

    override fun onCreate() {
        instance = this
        super.onCreate()
        ServiceLocator.init(this)

        // IPv4 hardcoded network preference — set early, at app startup, so any auxiliary
        // networking (eg. artwork fetch, metadata revive) stays on IPv4 alongside the
        // yt-dlp downloader's --force-ipv4 flag. SEE YtDlpDownloadManager.kt.
        System.setProperty("java.net.preferIPv4Stack", "true")

        var ytdlpInitOk = false
        try {
            YoutubeDL.getInstance().init(this)
            ytdlpInitOk = true
            // Update yt-dlp to latest NIGHTLY in the background to fix YouTube breaking changes
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    YoutubeDL.getInstance().updateYoutubeDL(this@TideMusicApp, YoutubeDL.UpdateChannel.NIGHTLY)
                } catch (e: Exception) {
                    Log.e(TAG, "yt-dlp update failed", e)
                }
            }
        } catch (e: Exception) {
             Log.e(TAG, "yt-dlp init failed", e)
        }

        try {
            FFmpeg.getInstance().init(this)
        } catch (e: Exception) {
            Log.e(TAG, "ffmpeg init failed", e)
        }

        // Reflect the real initialization outcome so the Download screen can degrade
        // gracefully when the bundled yt-dlp payload failed to load on this device.
        YtDlpInitializer.markInitialized(success = ytdlpInitOk)

        // Auto-create the "yt-dlp" playlist on first launch (user-deletable).
        GlobalScope.launch(Dispatchers.IO) {
            try {
                ServiceLocator.repository.ensureYtDlpPlaylist()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to ensure yt-dlp playlist", e)
            }
        }
    }
}
