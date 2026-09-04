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

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .build().also { exo ->
                playbackController.attachPlayer(exo)
                exo.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        val item = exo.currentMediaItem
                        val filePath = item?.mediaMetadata?.extras?.getString(PlaybackController.EXTRA_FILE_PATH)
                        val uriStr = item?.localConfiguration?.uri?.toString()
                        FloatingPillService.showOrUpdate(this@PlaybackService, filePath, uriStr, isPlaying)
                    }
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        val filePath = mediaItem?.mediaMetadata?.extras?.getString(PlaybackController.EXTRA_FILE_PATH)
                        val uriStr = mediaItem?.localConfiguration?.uri?.toString()
                        FloatingPillService.showOrUpdate(this@PlaybackService, filePath, uriStr, exo.isPlaying)
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
                            FloatingPillService.hide(this@PlaybackService)
                        }
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

        val closeCommandButton = androidx.media3.session.CommandButton.Builder()
            .setDisplayName("Close")
            .setIconResId(com.example.tidemusic.R.drawable.ic_close_notification)
            .setSessionCommand(androidx.media3.session.SessionCommand("ACTION_CLOSE", android.os.Bundle.EMPTY))
            .build()

        mediaSession = MediaSession.Builder(this, player!!)
            .setSessionActivity(sessionActivity)
            // Custom artwork loader: resolves the tide-art:// artwork URIs set on every
            // MediaItem (embedded art, else deterministic per-song placeholder). Guarantees
            // the notification / lock screen always receive fresh, per-song bitmaps —
            // fixes the stale "one song's image everywhere" artwork bug.
            .setBitmapLoader(
                androidx.media3.session.CacheBitmapLoader(TideArtworkBitmapLoader(this))
            )
            .setCustomLayout(com.google.common.collect.ImmutableList.of(closeCommandButton))
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val connectionResult = super.onConnect(session, controller)
                    val availableSessionCommands = connectionResult.availableSessionCommands.buildUpon()
                    availableSessionCommands.add(androidx.media3.session.SessionCommand("ACTION_CLOSE", android.os.Bundle.EMPTY))
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
                        FloatingPillService.hide(this@PlaybackService)
                        player?.stop()
                        player?.clearMediaItems()
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                        stopSelf()
                        return com.google.common.util.concurrent.Futures.immediateFuture(
                            androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS)
                        )
                    }
                    return super.onCustomCommand(session, controller, customCommand, args)
                }
            })
            .build()

        // Full-bleed custom icons so prev / play-pause / next read larger in the notification drawer.
        setMediaNotificationProvider(
            object : androidx.media3.session.DefaultMediaNotificationProvider(this@PlaybackService) {
                override fun getMediaButtons(
                    session: MediaSession,
                    playerCommands: Player.Commands,
                    customLayout: com.google.common.collect.ImmutableList<androidx.media3.session.CommandButton>,
                    showPauseButton: Boolean,
                ): com.google.common.collect.ImmutableList<androidx.media3.session.CommandButton> {
                    val buttons = ArrayList<androidx.media3.session.CommandButton>()

                    if (playerCommands.contains(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) ||
                        playerCommands.contains(Player.COMMAND_SEEK_TO_PREVIOUS)
                    ) {
                        buttons.add(
                            androidx.media3.session.CommandButton.Builder()
                                .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                                .setIconResId(com.example.tidemusic.R.drawable.ic_notif_prev)
                                .setDisplayName("Previous")
                                .build(),
                        )
                    }

                    if (playerCommands.contains(Player.COMMAND_PLAY_PAUSE)) {
                        buttons.add(
                            androidx.media3.session.CommandButton.Builder()
                                .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                                .setIconResId(
                                    if (showPauseButton) com.example.tidemusic.R.drawable.ic_notif_pause
                                    else com.example.tidemusic.R.drawable.ic_notif_play,
                                )
                                .setDisplayName(if (showPauseButton) "Pause" else "Play")
                                .build(),
                        )
                    }

                    if (playerCommands.contains(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) ||
                        playerCommands.contains(Player.COMMAND_SEEK_TO_NEXT)
                    ) {
                        buttons.add(
                            androidx.media3.session.CommandButton.Builder()
                                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                                .setIconResId(com.example.tidemusic.R.drawable.ic_notif_next)
                                .setDisplayName("Next")
                                .build(),
                        )
                    }

                    for (custom in customLayout) {
                        if (buttons.none { it.sessionCommand?.customAction == custom.sessionCommand?.customAction }) {
                            buttons.add(custom)
                        }
                    }
                    if (buttons.none { it.sessionCommand?.customAction == "ACTION_CLOSE" }) {
                        buttons.add(closeCommandButton)
                    }
                    return com.google.common.collect.ImmutableList.copyOf(buttons)
                }
            },
        )
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
            FloatingPillService.hide(this)
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
        // Never stop playback when app is swiped away from recent apps.
        // Playback only stops when user taps the Close (X) button in the notification drawer.
    }

    override fun onDestroy() {
        saveState()
        FloatingPillService.hide(this)
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
