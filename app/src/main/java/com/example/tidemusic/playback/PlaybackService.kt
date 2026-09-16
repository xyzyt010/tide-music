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
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.media.MediaBrowserServiceCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.tidemusic.MainActivity
import com.example.tidemusic.R
import com.example.tidemusic.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 1:1 VLC for Android Architecture PlaybackService.
 *
 * Extends [MediaBrowserServiceCompat] (the standard Android media service used by VLC, Spotify,
 * and Pocket Casts). Completely eliminates Media3 hybrid session conflicts and provides 100%
 * native integration with ColorOS 14 / Realme UI 5.0 Aqua Dynamics (Fluid Cloud) status bar
 * punch-hole capsule (pill with album art thumbnail and dancing equalizer bars):
 *
 * 1. Exactly ONE [MediaSessionCompat] instance (tag "TideMusic").
 * 2. Session published directly to [sessionToken] on the [MediaBrowserServiceCompat].
 * 3. Flags: FLAG_HANDLES_MEDIA_BUTTONS or FLAG_HANDLES_TRANSPORT_CONTROLS.
 * 4. isActive = true maintained throughout playback.
 * 5. MediaButtonReceiver handles hardware, Bluetooth, and ColorOS SystemUI intents.
 * 6. PlaybackStateCompat synchronously published on every ExoPlayer state change (STATE_PLAYING,
 *    position, speed 1.0f, transport actions bitmask) to trigger the dancing 4-bar equalizer.
 * 7. MediaMetadataCompat synchronously updated with 1:1 square Bitmap cover art for the capsule thumbnail.
 * 8. NotificationCompat.MediaStyle bound to sessionToken with compact actions (0, 1, 2).
 * 9. Direct foreground lifecycle via startForeground when playing and stopForeground(STOP_FOREGROUND_DETACH)
 *    when paused.
 */
@UnstableApi
class PlaybackService : MediaBrowserServiceCompat() {

    private val playbackController: PlaybackController get() = ServiceLocator.playbackController
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var player: ExoPlayer? = null
    lateinit var mediaSession: MediaSessionCompat
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
                })
            }

        restoreState()

        // Initialize MediaSessionCompat exactly mirroring VLC for Android
        initMediaSession()

        // Initial state synchronization
        syncPlaybackAndNotification()
    }

    private fun initMediaSession() {
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

        mediaSession = MediaSessionCompat(
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

        // Publish sessionToken to MediaBrowserServiceCompat
        sessionToken = mediaSession.sessionToken
        Log.i(TAG, "MediaSessionCompat initialized, activated, and bound to MediaBrowserServiceCompat (isActive=${mediaSession.isActive})")
    }

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

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        return BrowserRoot("tide_media_root", null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        result.sendResult(mutableListOf())
    }

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

            mediaSession.setPlaybackState(stateBuilder.build())
            mediaSession.isActive = true
        } catch (e: Throwable) {
            Log.e(TAG, "Error publishing playback state", e)
        }
    }

    private fun updateMetadata() {
        try {
            val p = player ?: return
            val currentItem = p.currentMediaItem ?: run {
                mediaSession.setMetadata(null)
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

            mediaSession.setMetadata(metaBuilder.build())
        } catch (e: Throwable) {
            Log.e(TAG, "Error updating metadata", e)
        }
    }

    fun buildNotification(): Notification {
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
            .setMediaSession(mediaSession.sessionToken)
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
            MediaButtonReceiver.handleIntent(mediaSession, intent)

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
            mediaSession.isActive = false
            mediaSession.release()
        } catch (e: Throwable) {
            Log.e(TAG, "Error releasing mediaSession", e)
        }
        playbackController.detachPlayer()
        player?.release()
        player = null
        super.onDestroy()
    }
}
