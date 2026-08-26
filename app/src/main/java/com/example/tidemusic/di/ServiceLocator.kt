package com.example.tidemusic.di

import android.content.Context
import com.example.tidemusic.data.db.TideMusicDatabase
import com.example.tidemusic.domain.LibraryRepository
import com.example.tidemusic.playback.PlaybackController
import com.example.tidemusic.playback.YtDlpDownloadManager

/**
 * App-wide service-locator singletons (Dagger/Hilt-less DI).
 *
 * Hilt's Gradle plugin doesn't yet support AGP 9, so for the MVP we wire instances
 * manually from a single [ServiceLocator] held by [com.example.tidemusic.TideMusicApp].
 * All ViewModels reach into this locator for their dependencies via [ServiceLocator.get].
 *
 * Lifecycle: lazy-initialized on first access; Room database lives for the lifetime of the
 * application process. ([LibraryRepository] and [PlaybackController] are themselves
 * `@Singleton`-like in their own right.)
 */
object ServiceLocator {

    @Volatile private var repositoryRef: LibraryRepository? = null
    @Volatile private var playbackControllerRef: PlaybackController? = null
    @Volatile private var databaseRef: TideMusicDatabase? = null
    @Volatile private var downloadManagerRef: YtDlpDownloadManager? = null

    @Volatile private var settingsManagerRef: com.example.tidemusic.data.SettingsManager? = null

    fun init(context: Context) {
        if (settingsManagerRef == null) {
            settingsManagerRef = com.example.tidemusic.data.SettingsManager(context.applicationContext)
        }
        if (databaseRef == null) {
            databaseRef = DatabaseModule.provideDatabase(context.applicationContext)
        }
        if (repositoryRef == null) {
            val db = databaseRef!!
            repositoryRef = LibraryRepository(
                context = context.applicationContext,
                songDao = db.songDao(),
                albumDao = db.albumDao(),
                playlistDao = db.playlistDao(),
                queueDao = db.queueDao(),
                eqPresetDao = db.eqPresetDao(),
                bookmarkDao = db.bookmarkDao(),
                folderDao = db.folderDao(),
                downloadTaskDao = db.downloadTaskDao(),
            )
        }
        if (playbackControllerRef == null) {
            playbackControllerRef = PlaybackController(context.applicationContext, repositoryRef!!)
        }
        if (downloadManagerRef == null) {
            downloadManagerRef = YtDlpDownloadManager(
                context = context.applicationContext,
                downloadTaskDao = databaseRef!!.downloadTaskDao(),
                libraryRepository = repositoryRef!!,
            )
        }
    }

    fun get(): ServiceLocator = this

    val repository: LibraryRepository get() = repositoryRef ?: error("ServiceLocator.init must be called first")
    val playbackController: PlaybackController get() = playbackControllerRef ?: error("ServiceLocator.init must be called first")
    val downloadManager: YtDlpDownloadManager get() = downloadManagerRef ?: error("ServiceLocator.init must be called first")
    val settingsManager: com.example.tidemusic.data.SettingsManager get() = settingsManagerRef ?: error("ServiceLocator.init must be called first")
}
