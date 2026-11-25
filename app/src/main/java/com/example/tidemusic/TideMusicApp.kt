package com.example.tidemusic

import android.app.Application
import com.example.tidemusic.di.ServiceLocator

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

        // yt-dlp / FFmpeg are deliberately NOT initialized here. Their payload is ~90 MB
        // per ABI of native binaries (libpython.zip.so + libffmpeg.zip.so): unzipping it,
        // spinning up Python and running a nightly network update on every cold start
        // used to keep RAM/CPU/disk busy all the time even when the user never downloads.
        // They are now loaded lazily by YtDlpInitializer.ensureInitialized() from
        // YtDlpDownloadManager.enqueueDownload(), i.e. only when a download is requested.
        // The "Download" playlist is likewise created on demand in DownloadViewModel.
    }
}
