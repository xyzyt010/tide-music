package com.example.tidemusic.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
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
 * Native MediaSession & Playback Service mirroring the VLC for Android architecture.
 *
 * Provides full end-to-end integration for ColorOS 14 / Realme UI 5.0 Aqua Dynamics (Fluid Cloud)
 * status bar punch-hole capsule (pill with album art thumbnail and dancing equalizer bars):
 *
 * 1. Owns a dedicated, standalone [MediaSessionCompat] with FLAG_HANDLES_MEDIA_BUTTONS and
 *    FLAG_HANDLES_TRANSPORT_CONTROLS, explicitly maintained with isActive = true.
 * 2. MediaButtonReceiver registered in Manifest and wired to MediaSessionCompat for hardware,
 *    Bluetooth, and ColorOS SystemUI transport routing.
 * 3. Builds notifications using [androidx.media.app.NotificationCompat.MediaStyle] linked
 *    directly to [MediaSessionCompat.sessionToken]. This injects both the platform MediaSession token
 *    and the NotificationCompat.EXTRA_MEDIA_SESSION ("android.media.session") parcelable.
 * 4. Synchronously updates [PlaybackStateCompat] (STATE_PLAYING / STATE_PAUSED, position, speed,
 *    actions bitmask) on all ExoPlayer events to drive the dancing equalizer animation.
 * 5. Synchronously updates [MediaMetadataCompat] with Title, Artist, Album, Duration, and 1:1 square
 *    cover art Bitmap to render the status bar capsule thumbnail and lockscreen card.
 * 6. Posts directly to [startForeground] when playing and detaches foreground when paused.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private val playbackController: PlaybackController get() = ServiceLocator.playbackController
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    lateinit var mediaSessionCompat: MediaSessionCompat
        private set

    @Volatile
    var isCurrentSongFavorite: Boolean = false
        private set

    companion object {
        const val TAG = "PlaybackService"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "tide_media_playback_v5"

        const val ACTION_PLAY_PAUSE = "com.example.tidemusic.ACTION_PLAY_PAUSE"
        const val ACTION_PREVIOUS = "com.example.tidemusic.ACTION_PREVIOUS"
        const val ACTION_NEXT = "com.example.tidemusic.ACTION_NEXT"
        const val ACTION_TOGGLE_FAVORITE = "com.example.tidemusic.ACTION_TOGGLE_FAVORITE"
        const val ACTION_TOGGLE_SHUFFLE = "com.example.tidemusic.ACTION_TOGGLE_SHUFFLE"
        const val ACTION_CLOSE = "com.example.tidemusic.ACTION_CLOSE"

        private const val STANDARD_ACTIONS: Long =
            PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_SEEK_TO or
            PlaybackStateCompat.ACTION_STOP or
            PlaybackStateCompat.ACTION_SET_REPEAT_MODE or
            PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE
    }

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        System.setProperty("java.net.preferIPv4Stack", "true")

        setupNotificationChannel()

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
                        syncPlaybackAndNotification()
                        val mediaId = mediaItem?.mediaId?.toLongOrNull() ?: -1L
                        if (mediaId > 0L) {
                            serviceScope.launch(Dispatchers.IO) {
                                val fav = ServiceLocator.repository.getSong(mediaId)?.isFavorite == true
                                isCurrentSongFavorite = fav
                                withContext(Dispatchers.Main) {
                                    syncPlaybackAndNotification()
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

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        syncPlaybackAndNotification()
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        syncPlaybackAndNotification()
                    }

                    override fun onPositionDiscontinuity(
                        oldPosition: Player.PositionInfo,
                        newPosition: Player.PositionInfo,
                        reason: Int
                    ) {
                        syncPlaybackAndNotification()
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

        // 1. Initialize dedicated MediaSessionCompat matching VLC for Android
        initMediaSessionCompat()

        // 2. Initialize Media3 MediaSession so internal UI controllers (ConnectionHolder) stay connected
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
                            stopForegroundCompat(true)
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
                                            syncPlaybackAndNotification()
                                            mediaSession?.setCustomLayout(
                                                ImmutableList.of(
                                                    buildFavoriteCommandButton(nextFav),
                                                    buildShuffleCommandButton(player?.shuffleModeEnabled == true),
                                                    closeCommandButton
                                                )
                                            )
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error toggling favorite", e)
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

        addSession(mediaSession!!)
        setShowNotificationForIdlePlayer(SHOW_NOTIFICATION_FOR_IDLE_PLAYER_AFTER_STOP_OR_ERROR)

        // Delegate Media3's internal notification manager to return our VLC-style MediaStyle notification
        setMediaNotificationProvider(TideMediaNotificationProvider(this))

        // Initial synchronization
        syncPlaybackAndNotification()
    }

    /**
     * Initializes MediaSessionCompat exactly mirroring VLC for Android:
     * - MediaButtonReceiver intent & pending intent
     * - MediaSessionCompat with ComponentName
     * - FLAG_HANDLES_MEDIA_BUTTONS and FLAG_HANDLES_TRANSPORT_CONTROLS
     * - Session Activity PendingIntent
     * - MediaSessionCallback handling transport commands
     * - isActive = true
     */
    private fun initMediaSessionCompat() {
        val mbrIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            component = ComponentName(this@PlaybackService, MediaButtonReceiver::class.java)
        }
        val mbrPendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            mbrIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            data = "tidemusic://player".toUri()
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSessionCompat = MediaSessionCompat(
            this,
            "TideMusic",
            ComponentName(this, MediaButtonReceiver::class.java),
            mbrPendingIntent
        ).apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setSessionActivity(sessionActivityPendingIntent)
            setCallback(MediaSessionCallback())
            isActive = true
        }
        Log.i(TAG, "MediaSessionCompat initialized and activated (isActive=${mediaSessionCompat.isActive})")
    }

    /**
     * MediaSessionCompat.Callback routing system/hardware/Bluetooth commands.
     */
    private inner class MediaSessionCallback : MediaSessionCompat.Callback() {
        override fun onPlay() {
            player?.play()
        }

        override fun onPause() {
            player?.pause()
        }

        override fun onSkipToNext() {
            playbackController.next()
        }

        override fun onSkipToPrevious() {
            playbackController.previous()
        }

        override fun onSeekTo(pos: Long) {
            player?.seekTo(pos)
        }

        override fun onStop() {
            player?.pause()
            saveState()
            stopForegroundCompat(true)
            stopSelf()
        }

        override fun onCustomAction(action: String?, extras: Bundle?) {
            when (action) {
                ACTION_TOGGLE_FAVORITE -> playbackController.toggleFavoriteCurrentSong()
                ACTION_TOGGLE_SHUFFLE -> playbackController.toggleShuffle()
                ACTION_CLOSE -> {
                    saveState()
                    player?.stop()
                    player?.clearMediaItems()
                    stopForegroundCompat(true)
                    stopSelf()
                }
            }
        }
    }

    /**
     * Publishes PlaybackStateCompat to [mediaSessionCompat] synchronously.
     * ColorOS Pantanal checks for STATE_PLAYING and transport actions to trigger the
     * dancing equalizer animation in the punch-hole capsule.
     */
    private fun publishPlaybackState() {
        try {
            val p = player ?: return
            val isPlaying = p.isPlaying
            val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
            val speed = if (isPlaying) p.playbackParameters.speed else 0f
            val position = p.currentPosition.coerceAtLeast(0L)

            val stateBuilder = PlaybackStateCompat.Builder()
                .setActions(STANDARD_ACTIONS)
                .setState(state, position, speed, SystemClock.elapsedRealtime())

            mediaSessionCompat.setPlaybackState(stateBuilder.build())
            mediaSessionCompat.isActive = true
        } catch (e: Throwable) {
            Log.e(TAG, "Error publishing playback state", e)
        }
    }

    /**
     * Publishes MediaMetadataCompat containing Title, Artist, Album, Duration, and 1:1 square
     * cover art Bitmap to [mediaSessionCompat]. ColorOS Pantanal uses this bitmap to render
     * the status bar capsule thumbnail and lockscreen player card.
     */
    private fun updateMetadata() {
        try {
            val p = player ?: return
            val currentItem = p.currentMediaItem ?: run {
                mediaSessionCompat.setMetadata(null)
                return
            }

            val songId = currentItem.mediaId.toLongOrNull() ?: 0L
            val filePath = currentItem.mediaMetadata.extras?.getString(PlaybackController.EXTRA_FILE_PATH) ?: ""
            val sourceUri = currentItem.requestMetadata?.mediaUri?.toString() ?: ""
            val title = currentItem.mediaMetadata.title?.toString()
                ?: currentItem.mediaMetadata.displayTitle?.toString()
                ?: "Unknown Track"
            val artist = currentItem.mediaMetadata.artist?.toString() ?: "Unknown Artist"
            val album = currentItem.mediaMetadata.albumTitle?.toString() ?: ""
            val duration = p.duration.coerceAtLeast(0L)

            val metaBuilder = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, artist)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)

            val artBitmap = SongArtworkCache.getOrDecode(this, songId, filePath, sourceUri)
            if (!artBitmap.isRecycled) {
                metaBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artBitmap)
                metaBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, artBitmap)
                metaBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, artBitmap)
            }

            mediaSessionCompat.setMetadata(metaBuilder.build())
        } catch (e: Throwable) {
            Log.e(TAG, "Error updating metadata", e)
        }
    }

    /**
     * Builds the standard MediaStyle notification matching VLC for Android:
     * - Uses [androidx.media.app.NotificationCompat.MediaStyle]
     * - Calls [MediaStyle.setMediaSession] with [mediaSessionCompat.sessionToken]
     * - Configures compact view actions: 0 (Prev), 1 (Play/Pause), 2 (Next)
     * - Injects square largeIcon Bitmap
     * - Sets CATEGORY_TRANSPORT and VISIBILITY_PUBLIC
     */
    internal fun buildNotification(): Notification {
        val p = player
        val isPlaying = p?.isPlaying == true
        val currentItem = p?.currentMediaItem

        val songId = currentItem?.mediaId?.toLongOrNull() ?: 0L
        val filePath = currentItem?.mediaMetadata?.extras?.getString(PlaybackController.EXTRA_FILE_PATH) ?: ""
        val sourceUri = currentItem?.requestMetadata?.mediaUri?.toString() ?: ""
        val title = currentItem?.mediaMetadata?.title?.toString()
            ?: currentItem?.mediaMetadata?.displayTitle?.toString()
            ?: "Unknown Track"
        val artist = currentItem?.mediaMetadata?.artist?.toString() ?: "Unknown Artist"
        val album = currentItem?.mediaMetadata?.albumTitle?.toString() ?: ""

        val artBitmap = SongArtworkCache.getOrDecode(this, songId, filePath, sourceUri)

        val prevIntent = buildActionIntent(ACTION_PREVIOUS)
        val playPauseIntent = buildActionIntent(ACTION_PLAY_PAUSE)
        val nextIntent = buildActionIntent(ACTION_NEXT)
        val closeIntent = buildActionIntent(ACTION_CLOSE)
        val favIntent = buildActionIntent(ACTION_TOGGLE_FAVORITE)

        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            data = "tidemusic://player".toUri()
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            sessionActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlaying) R.drawable.ic_notif_pause else R.drawable.ic_notif_play
        val favIcon = if (isCurrentSongFavorite) R.drawable.ic_notif_favorite_filled else R.drawable.ic_notif_favorite_border

        val mediaStyle = MediaStyle()
            .setMediaSession(mediaSessionCompat.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)
            .setShowCancelButton(true)
            .setCancelButtonIntent(closeIntent)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(title)
            .setContentText(artist)
            .setSubText(album)
            .setLargeIcon(artBitmap)
            .setContentIntent(contentIntent)
            .setDeleteIntent(closeIntent)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(mediaStyle)
            .addAction(R.drawable.ic_notif_prev, "Previous", prevIntent)
            .addAction(playPauseIcon, if (isPlaying) "Pause" else "Play", playPauseIntent)
            .addAction(R.drawable.ic_notif_next, "Next", nextIntent)
            .addAction(favIcon, if (isCurrentSongFavorite) "Favorited" else "Favorite", favIntent)
            .addAction(R.drawable.ic_close_notification, "Close", closeIntent)

        return builder.build()
    }

    private fun buildActionIntent(action: String): PendingIntent {
        val intent = Intent(this, PlaybackService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Synchronously synchronizes session playback state, metadata, and posts the notification.
     * Starts foreground when playing, and updates detached notification when paused.
     */
    private fun syncPlaybackAndNotification() {
        try {
            publishPlaybackState()
            updateMetadata()
            val notif = buildNotification()
            val isPlaying = player?.isPlaying == true
            if (isPlaying) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                } else {
                    startForeground(NOTIFICATION_ID, notif)
                }
            } else {
                stopForegroundCompat(false)
                val nm = NotificationManagerCompat.from(this)
                nm.notify(NOTIFICATION_ID, notif)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error in syncPlaybackAndNotification", e)
        }
    }

    private fun stopForegroundCompat(removeNotification: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(if (removeNotification) STOP_FOREGROUND_REMOVE else STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(removeNotification)
        }
    }

    private fun setupNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            try {
                nm?.deleteNotificationChannel("tide_fluid_dynamics_live")
                nm?.deleteNotificationChannel("tide_playback_v1")
                nm?.deleteNotificationChannel("tide_media_playback_v2")
                nm?.deleteNotificationChannel("tide_media_playback_v3")
                nm?.deleteNotificationChannel("tide_media_playback_v4")
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
                Log.e(TAG, "Error restoring state", e)
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
            Log.e(TAG, "Error saving state", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            MediaButtonReceiver.handleIntent(mediaSessionCompat, intent)

            when (intent.action) {
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
                    stopForegroundCompat(true)
                    stopSelf()
                    return START_NOT_STICKY
                }
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
            stopForegroundCompat(true)
            stopSelf()
        }
    }

    override fun onDestroy() {
        saveState()
        try {
            mediaSessionCompat.isActive = false
            mediaSessionCompat.release()
        } catch (e: Throwable) {
            Log.e(TAG, "Error releasing mediaSessionCompat", e)
        }
        mediaSession?.run {
            removeSession(this)
            player.release()
            release()
        }
        mediaSession = null
        player = null
        super.onDestroy()
    }
}

/**
 * Custom notification provider delegating directly to VLC-style MediaStyle notification.
 */
@UnstableApi
private class TideMediaNotificationProvider(
    private val service: PlaybackService
) : MediaNotification.Provider {
    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback
    ): MediaNotification {
        return MediaNotification(PlaybackService.NOTIFICATION_ID, service.buildNotification())
    }

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle
    ): Boolean = false

    override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo {
        return MediaNotification.Provider.NotificationChannelInfo(
            PlaybackService.CHANNEL_ID,
            service.getString(R.string.media_notification_channel)
        )
    }
}
