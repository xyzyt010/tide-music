package com.example.tidemusic.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.tidemusic.MainActivity
import com.example.tidemusic.di.ServiceLocator
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The MediaSessionService that owns ExoPlayer + MediaSession (spec Section 1 architecture).
 *
 * - Single source of truth for playback; survives navigation/backgrounding because it runs
 *   as a bound + started foreground service of type `mediaPlayback`.
 * - The UI layer talks to it ONLY via a Media3 [androidx.media3.session.MediaController],
 *   never touches the ExoPlayer directly. [ConnectionHelper] handles the controller wiring.
 * - Lock-screen + notification-drawer controls (Section 4) come "for free" from Media3's
 *   DefaultMediaNotificationProvider once [MediaMetadata] is populated on each MediaItem.
 * - The collapsed/expanded layouts in Sections 4.1/4.2 map onto the standard MediaStyle
 *   template; we don't hand-build a RemoteViews layout.
 */
class PlaybackService : MediaSessionService() {

    private val playbackController: PlaybackController get() = ServiceLocator.playbackController

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        // Set JVM-level IPv4 preference here too so any auxiliary lookup (eg. artwork fetch
        // in MediaMetadata) stays on IPv4 alongside the yt-dlp downloader (spec Section 6.6).
        // NOTE: the downloader hardcodes --force-ipv4 unconditionally. This is intentional.
        System.setProperty("java.net.preferIPv4Stack", "true")

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        var isCurrentSongFavorite = false
        val closeCommandButton = androidx.media3.session.CommandButton.Builder()
            .setDisplayName("Close")
            .setIconResId(com.example.tidemusic.R.drawable.ic_close_notification)
            .setSessionCommand(androidx.media3.session.SessionCommand("ACTION_CLOSE", android.os.Bundle.EMPTY))
            .build()

        fun buildFavoriteCommandButton(isFav: Boolean): androidx.media3.session.CommandButton =
            androidx.media3.session.CommandButton.Builder()
                .setDisplayName(if (isFav) "Favorited" else "Favorite")
                .setIconResId(
                    if (isFav) com.example.tidemusic.R.drawable.ic_notif_favorite_filled
                    else com.example.tidemusic.R.drawable.ic_notif_favorite_border
                )
                .setSessionCommand(androidx.media3.session.SessionCommand("ACTION_TOGGLE_FAVORITE", android.os.Bundle.EMPTY))
                .build()

        fun buildShuffleCommandButton(shuffleOn: Boolean): androidx.media3.session.CommandButton =
            androidx.media3.session.CommandButton.Builder()
                .setDisplayName(if (shuffleOn) "Shuffle On" else "Shuffle Off")
                .setIconResId(
                    if (shuffleOn) com.example.tidemusic.R.drawable.ic_notif_shuffle_on
                    else com.example.tidemusic.R.drawable.ic_notif_shuffle_off
                )
                .setSessionCommand(androidx.media3.session.SessionCommand("ACTION_TOGGLE_SHUFFLE", android.os.Bundle.EMPTY))
                .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(androidx.media3.common.C.WAKE_MODE_LOCAL)
            .build().also { exo ->
                playbackController.attachPlayer(exo)
                exo.addListener(object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        val mediaId = mediaItem?.mediaId?.toLongOrNull() ?: -1L
                        if (mediaId > 0L) {
                            GlobalScope.launch(Dispatchers.IO) {
                                val fav = ServiceLocator.repository.getSong(mediaId)?.isFavorite == true
                                isCurrentSongFavorite = fav
                                withContext(Dispatchers.Main) {
                                    mediaSession?.setCustomLayout(
                                        com.google.common.collect.ImmutableList.of(
                                            buildFavoriteCommandButton(fav),
                                            buildShuffleCommandButton(exo.shuffleModeEnabled),
                                            closeCommandButton
                                        )
                                    )
                                }
                            }
                        }
                    }
                    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                        mediaSession?.setCustomLayout(
                            com.google.common.collect.ImmutableList.of(
                                buildFavoriteCommandButton(isCurrentSongFavorite),
                                buildShuffleCommandButton(shuffleModeEnabled),
                                closeCommandButton
                            )
                        )
                    }
                })
            }
            
        restoreState()

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                data = "tidemusic://player".toUri()
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        mediaSession = MediaSession.Builder(this, player!!)
            .setSessionActivity(sessionActivity)
            .setBitmapLoader(
                androidx.media3.session.CacheBitmapLoader(TideArtworkBitmapLoader(this))
            )
            .setCustomLayout(com.google.common.collect.ImmutableList.of(
                buildFavoriteCommandButton(false),
                buildShuffleCommandButton(player!!.shuffleModeEnabled),
                closeCommandButton
            ))
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val connectionResult = super.onConnect(session, controller)
                    val availableSessionCommands = connectionResult.availableSessionCommands.buildUpon()
                    availableSessionCommands.add(androidx.media3.session.SessionCommand("ACTION_CLOSE", android.os.Bundle.EMPTY))
                    availableSessionCommands.add(androidx.media3.session.SessionCommand("ACTION_TOGGLE_FAVORITE", android.os.Bundle.EMPTY))
                    availableSessionCommands.add(androidx.media3.session.SessionCommand("ACTION_TOGGLE_SHUFFLE", android.os.Bundle.EMPTY))
                    return MediaSession.ConnectionResult.accept(
                        availableSessionCommands.build(),
                        connectionResult.availablePlayerCommands
                    )
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: androidx.media3.session.SessionCommand,
                    args: android.os.Bundle
                ): com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.SessionResult> {
                    if (customCommand.customAction == "ACTION_CLOSE") {
                        saveState()
                        player?.stop()
                        player?.clearMediaItems()
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                        stopSelf()
                        return com.google.common.util.concurrent.Futures.immediateFuture(
                            androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS)
                        )
                    } else if (customCommand.customAction == "ACTION_TOGGLE_FAVORITE") {
                        val currentItem = player?.currentMediaItem
                        val songId = currentItem?.mediaId?.toLongOrNull()
                        if (songId != null) {
                            GlobalScope.launch(Dispatchers.IO) {
                                try {
                                    val nextFav = ServiceLocator.repository.toggleFavorite(songId)
                                    isCurrentSongFavorite = nextFav
                                    withContext(Dispatchers.Main) {
                                        mediaSession?.setCustomLayout(
                                            com.google.common.collect.ImmutableList.of(
                                                buildFavoriteCommandButton(nextFav),
                                                buildShuffleCommandButton(player?.shuffleModeEnabled == true),
                                                closeCommandButton
                                            )
                                        )
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("PlaybackService", "Error toggling favorite", e)
                                }
                            }
                        }
                        return com.google.common.util.concurrent.Futures.immediateFuture(
                            androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS)
                        )
                    } else if (customCommand.customAction == "ACTION_TOGGLE_SHUFFLE") {
                        val p = player
                        if (p != null) {
                            val nextShuffle = !p.shuffleModeEnabled
                            playbackController.setShuffleMode(nextShuffle)
                            mediaSession?.setCustomLayout(
                                com.google.common.collect.ImmutableList.of(
                                    buildFavoriteCommandButton(isCurrentSongFavorite),
                                    buildShuffleCommandButton(nextShuffle),
                                    closeCommandButton
                                )
                            )
                        }
                        return com.google.common.util.concurrent.Futures.immediateFuture(
                            androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS)
                        )
                    }
                    return super.onCustomCommand(session, controller, customCommand, args)
                }
            })
            .build()

        // Explicitly create playback notification channel for Android O+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val nm = getSystemService(android.app.NotificationManager::class.java)
            val channel = android.app.NotificationChannel(
                androidx.media3.session.DefaultMediaNotificationProvider.DEFAULT_CHANNEL_ID,
                getString(com.example.tidemusic.R.string.media_notification_channel),
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music playback controls"
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            nm?.createNotificationChannel(channel)
        }

        val notificationProvider = androidx.media3.session.DefaultMediaNotificationProvider.Builder(this@PlaybackService)
            .setChannelName(com.example.tidemusic.R.string.media_notification_channel)
            .build()
        notificationProvider.setSmallIcon(com.example.tidemusic.R.drawable.ic_music_note)
        setMediaNotificationProvider(notificationProvider)
    }
    
    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    private fun restoreState() {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val prefs = getSharedPreferences("playback_state", android.content.Context.MODE_PRIVATE)
                val savedIds = prefs.getString("queue_ids", "") ?: ""
                if (savedIds.isNotBlank()) {
                    val ids = savedIds.split(",").mapNotNull { it.toLongOrNull() }
                    val allSongs = ServiceLocator.repository.observeAllSongs().first().associateBy { it.id }
                    val queueSongs = ids.mapNotNull { allSongs[it] }
                    val savedIndex = prefs.getInt("queue_index", 0)
                    val savedPosition = prefs.getLong("queue_position", 0L)
                    
                    withContext(Dispatchers.Main) {
                        val mediaItems = queueSongs.map { playbackController.mediaItemFor(it) }
                        if (mediaItems.isNotEmpty()) {
                            player?.setMediaItems(mediaItems, savedIndex.coerceIn(0, mediaItems.lastIndex), savedPosition)
                            player?.prepare()
                        }
                    }
                }
            } catch (e: Throwable) {
                android.util.Log.e("PlaybackService", "Error restoring state", e)
            }
        }
    }

    private fun saveState() {
        try {
            val p = player ?: return
            val count = p.mediaItemCount
            val ids = mutableListOf<Long>()
            for (i in 0 until count) {
                val id = p.getMediaItemAt(i).mediaId.toLongOrNull()
                if (id != null) ids.add(id)
            }
            val prefs = getSharedPreferences("playback_state", android.content.Context.MODE_PRIVATE)
            prefs.edit()
                .putString("queue_ids", ids.joinToString(","))
                .putInt("queue_index", p.currentMediaItemIndex)
                .putLong("queue_position", p.currentPosition)
                .apply()
        } catch (e: Throwable) {
            android.util.Log.e("PlaybackService", "Error saving state", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_CLOSE") {
            saveState()
            player?.stop()
            player?.clearMediaItems()
            @Suppress("DEPRECATION")
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        saveState()
        val p = player
        // If music is actively playing, keep running in the foreground seamlessly even if swiped from recents!
        // If paused, ended, or empty, release foreground and stop service cleanly so it doesn't drain battery or memory.
        if (p == null || !p.playWhenReady || !p.isPlaying || p.mediaItemCount == 0) {
            @Suppress("DEPRECATION")
            stopForeground(true)
            stopSelf()
        }
    }

    override fun onDestroy() {
        saveState()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        super.onDestroy()
    }
}

/** Sentinel MediaItem used to signal "open full-screen player" intent on notification tap. */
internal object DeepLinks {
    const val PLAYER = "tidemusic://player"
    const val EQUALIZER = "tidemusic://equalizer"
}

/** Marker for the mediaItem metadata the UI also reads from MediaController. */
@Suppress("unused")
internal fun MediaItem.tideMetadata(): MediaMetadata = mediaMetadata ?: MediaMetadata.Builder().build()
