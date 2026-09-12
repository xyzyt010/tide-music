package com.example.tidemusic.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.tidemusic.MainActivity
import com.example.tidemusic.R
import com.example.tidemusic.di.ServiceLocator
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Native MediaSessionService powering playback and deep OS integration.
 *
 * Integrates directly with ColorOS / Realme UI SystemUI Media Carousel and
 * native Aqua Dynamics (Fluid Cloud) status bar punch-hole capsule:
 * - NO "Display over other apps" permission required (pure OS feature).
 * - Standard MediaStyle notification on an IMPORTANCE_LOW silent channel.
 * - Enriched with CATEGORY_TRANSPORT, VISIBILITY_PUBLIC, and isOngoing while playing.
 * - SystemUI renders the prominent circular Play/Pause button, seekbar, and status capsule.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private val playbackController: PlaybackController get() = ServiceLocator.playbackController
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    @Volatile
    var isCurrentSongFavorite: Boolean = false
        private set

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "tide_media_playback_v2"

        const val ACTION_PLAY_PAUSE = "com.example.tidemusic.ACTION_PLAY_PAUSE"
        const val ACTION_PREVIOUS = "com.example.tidemusic.ACTION_PREVIOUS"
        const val ACTION_NEXT = "com.example.tidemusic.ACTION_NEXT"
        const val ACTION_TOGGLE_FAVORITE = "com.example.tidemusic.ACTION_TOGGLE_FAVORITE"
        const val ACTION_TOGGLE_SHUFFLE = "com.example.tidemusic.ACTION_TOGGLE_SHUFFLE"
        const val ACTION_CLOSE = "com.example.tidemusic.ACTION_CLOSE"
    }

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        System.setProperty("java.net.preferIPv4Stack", "true")

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val closeCommandButton = CommandButton.Builder()
            .setDisplayName("Close")
            .setIconResId(R.drawable.ic_close_notification)
            .setSessionCommand(SessionCommand(ACTION_CLOSE, Bundle.EMPTY))
            .build()

        fun buildFavoriteCommandButton(isFav: Boolean): CommandButton =
            CommandButton.Builder()
                .setDisplayName(if (isFav) "Favorited" else "Favorite")
                .setIconResId(
                    if (isFav) R.drawable.ic_notif_favorite_filled
                    else R.drawable.ic_notif_favorite_border
                )
                .setSessionCommand(SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY))
                .build()

        fun buildShuffleCommandButton(shuffleOn: Boolean): CommandButton =
            CommandButton.Builder()
                .setDisplayName(if (shuffleOn) "Shuffle On" else "Shuffle Off")
                .setIconResId(
                    if (shuffleOn) R.drawable.ic_notif_shuffle_on
                    else R.drawable.ic_notif_shuffle_off
                )
                .setSessionCommand(SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY))
                .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build().also { exo ->
                playbackController.attachPlayer(exo)
                exo.addListener(object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        val mediaId = mediaItem?.mediaId?.toLongOrNull() ?: -1L
                        if (mediaId > 0L) {
                            serviceScope.launch(Dispatchers.IO) {
                                val fav = ServiceLocator.repository.getSong(mediaId)?.isFavorite == true
                                isCurrentSongFavorite = fav
                                withContext(Dispatchers.Main) {
                                    mediaSession?.setCustomLayout(
                                        ImmutableList.of(
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
                            ImmutableList.of(
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
            .setCustomLayout(ImmutableList.of(
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
                    availableSessionCommands.add(SessionCommand(ACTION_CLOSE, Bundle.EMPTY))
                    availableSessionCommands.add(SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY))
                    availableSessionCommands.add(SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY))
                    return MediaSession.ConnectionResult.accept(
                        availableSessionCommands.build(),
                        connectionResult.availablePlayerCommands
                    )
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    when (customCommand.customAction) {
                        ACTION_CLOSE -> {
                            saveState()
                            player?.stop()
                            player?.clearMediaItems()
                            @Suppress("DEPRECATION")
                            stopForeground(true)
                            stopSelf()
                            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }
                        ACTION_TOGGLE_FAVORITE -> {
                            val currentItem = player?.currentMediaItem
                            val songId = currentItem?.mediaId?.toLongOrNull()
                            if (songId != null) {
                                serviceScope.launch(Dispatchers.IO) {
                                    try {
                                        val nextFav = ServiceLocator.repository.toggleFavorite(songId)
                                        isCurrentSongFavorite = nextFav
                                        withContext(Dispatchers.Main) {
                                            mediaSession?.setCustomLayout(
                                                ImmutableList.of(
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
                            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }
                        ACTION_TOGGLE_SHUFFLE -> {
                            val p = player
                            if (p != null) {
                                val nextShuffle = !p.shuffleModeEnabled
                                playbackController.setShuffleMode(nextShuffle)
                                mediaSession?.setCustomLayout(
                                    ImmutableList.of(
                                        buildFavoriteCommandButton(isCurrentSongFavorite),
                                        buildShuffleCommandButton(nextShuffle),
                                        closeCommandButton
                                    )
                                )
                            }
                            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }
                    }
                    return super.onCustomCommand(session, controller, customCommand, args)
                }
            })
            .build()

        setupNotificationChannel()
        val notificationProvider = TideMediaNotificationProvider(this)
        notificationProvider.setSmallIcon(R.drawable.ic_music_note)
        setMediaNotificationProvider(notificationProvider)
    }

    private fun setupNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            // Clean up legacy channels so ColorOS / Realme UI SystemUI routes cleanly to Media Carousel
            try {
                nm?.deleteNotificationChannel("tide_fluid_dynamics_live")
                nm?.deleteNotificationChannel("tide_playback_v1")
            } catch (_: Exception) {}

            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.media_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music playback controls and status bar capsule"
                setShowBadge(false)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
            }
            nm?.createNotificationChannel(channel)
        }
    }

    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    private fun restoreState() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val prefs = getSharedPreferences("playback_state", Context.MODE_PRIVATE)
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
            val prefs = getSharedPreferences("playback_state", Context.MODE_PRIVATE)
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
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> {
                playbackController.playPause()
                return START_NOT_STICKY
            }
            ACTION_PREVIOUS -> {
                playbackController.previous()
                return START_NOT_STICKY
            }
            ACTION_NEXT -> {
                playbackController.next()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_FAVORITE -> {
                playbackController.toggleFavoriteCurrentSong()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_SHUFFLE -> {
                playbackController.toggleShuffle()
                return START_NOT_STICKY
            }
            ACTION_CLOSE -> {
                saveState()
                player?.stop()
                player?.clearMediaItems()
                @Suppress("DEPRECATION")
                stopForeground(true)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        saveState()
        val p = player
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

/**
 * Enriches Media3's DefaultMediaNotificationProvider with:
 * - CATEGORY_TRANSPORT (required by Android SystemUI and ColorOS Pantanal for Media Carousel)
 * - VISIBILITY_PUBLIC (shows controls on lock screen)
 * - isOngoing = true while playing
 */
@UnstableApi
private class TideMediaNotificationProvider(
    context: Context
) : DefaultMediaNotificationProvider(
    context,
    { PlaybackService.NOTIFICATION_ID },
    PlaybackService.CHANNEL_ID,
    R.string.media_notification_channel
) {
    override fun addNotificationActions(
        mediaSession: MediaSession,
        mediaButtons: ImmutableList<CommandButton>,
        builder: NotificationCompat.Builder,
        actionFactory: MediaNotification.ActionFactory
    ): IntArray {
        val compactIndices = super.addNotificationActions(mediaSession, mediaButtons, builder, actionFactory)
        builder.setCategory(NotificationCompat.CATEGORY_TRANSPORT)
        builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        builder.setOngoing(mediaSession.player.isPlaying)
        return compactIndices
    }
}
