package com.example.tidemusic.playback

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.example.tidemusic.domain.LibraryRepository
import com.example.tidemusic.domain.Song
import com.example.tidemusic.util.orUnknown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Bridges the Room-backed queue with the ExoPlayer instance owned by [PlaybackService].
 *
 * All operations are crash-proof with boundary checking and safe try-catch blocks.
 * Continuously persists playback state (queue IDs, active song index, seek position) to
 * SharedPreferences so the queue & playback state remain preserved across app restarts and updates.
 */
class PlaybackController constructor(
    private val context: Context,
    private val repository: LibraryRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val secureRandom = java.security.SecureRandom()
    private var lastPlayedMediaId: Long? = null
    private var saveStateJob: kotlinx.coroutines.Job? = null
    private var player: Player? = null

    val audioSessionId: Int
        get() = (player as? androidx.media3.exoplayer.ExoPlayer)?.audioSessionId ?: 0

    val isPlaying: Boolean
        get() = player?.isPlaying == true

    val currentTitle: String?
        get() = player?.currentMediaItem?.mediaMetadata?.title?.toString()

    val currentArtist: String?
        get() = player?.currentMediaItem?.mediaMetadata?.artist?.toString()

    val currentPosition: Long
        get() = player?.currentPosition?.coerceAtLeast(0L) ?: 0L

    val duration: Long
        get() = player?.duration?.coerceAtLeast(0L) ?: 0L

    val currentMediaId: Long?
        get() = player?.currentMediaItem?.mediaId?.toLongOrNull()

    fun toggleFavoriteCurrentSong(onResult: ((Boolean) -> Unit)? = null) {
        val id = currentMediaId ?: return
        ioScope.launch {
            try {
                val next = repository.toggleFavorite(id)
                if (onResult != null) {
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        onResult(next)
                    }
                }
            } catch (e: Exception) {
                Log.e("PlaybackController", "Error toggling favorite", e)
            }
        }
    }

    fun checkIsFavorite(songId: Long, callback: (Boolean) -> Unit) {
        ioScope.launch {
            try {
                val isFav = repository.getSong(songId)?.isFavorite == true
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    callback(isFav)
                }
            } catch (e: Exception) {
                Log.e("PlaybackController", "Error checking favorite", e)
            }
        }
    }

    fun playPause() {
        val p = player ?: return
        try {
            if (p.isPlaying) {
                p.pause()
            } else {
                p.play()
            }
            savePlaybackState()
        } catch (e: Exception) {
            Log.e("PlaybackController", "Error toggling play/pause", e)
        }
    }

    suspend fun isCurrentSongFavorite(): Boolean {
        val id = currentMediaId ?: return false
        return repository.getSong(id)?.isFavorite == true
    }

    /** Bound during [PlaybackService.onCreate]; released during onDestroy. */
    fun attachPlayer(p: Player) {
        if (player === p) return
        try {
            player?.removeListener(listener)
        } catch (_: Exception) {}
        player = p
        try {
            p.addListener(listener)
        } catch (_: Exception) {}
    }

    /** Convert domain Song → MediaItem with full [MediaMetadata] for the notification. */
    fun mediaItemFor(song: Song): MediaItem {
        val extras = android.os.Bundle().apply {
            putString(EXTRA_FILE_PATH, song.filePath)
            putString(EXTRA_MIME_TYPE, song.mimeType)
            putLong(EXTRA_SIZE, song.size)
            putInt(EXTRA_BITRATE, song.bitrate)
            putInt(EXTRA_SAMPLE_RATE, song.sampleRate)
            putLong(EXTRA_DURATION_MS, song.durationMs)
            putLong(EXTRA_DATE_ADDED, song.dateAdded)
            putLong(EXTRA_DATE_MODIFIED, song.dateModified)
            putInt(EXTRA_PLAY_COUNT, song.playCount)
            song.lastPlayedTimestamp?.let { putLong(EXTRA_LAST_PLAYED, it) }
        }
        // ALWAYS set a per-song artwork URI (tide-art:// scheme). It is resolved by
        // TideArtworkBitmapLoader: embedded art when present, else a deterministic
        // placeholder gradient. NEVER fall back to the audio file's own content URI —
        // that decodes as an image never succeeds, and a failed bitmap load makes the
        // system media card keep showing the PREVIOUS song's artwork (the stale/wrong
        // lock-screen art bug).
        val artUri = ArtworkUri.forSong(
            songId = song.id,
            filePath = song.filePath,
            sourceUri = song.uri,
            dateModified = song.dateModified,
        )
        extras.putString(EXTRA_ARTWORK_URI, artUri.toString())

        val metadata = MediaMetadata.Builder()
            .setTitle(song.title.ifBlank { "Unknown" })
            .setArtist(song.artist.orUnknown())
            .setAlbumTitle(song.album.orUnknown())
            .setAlbumArtist(song.artist.orUnknown())
            .setTrackNumber(song.trackNumber.takeIf { it > 0 })
            .setDiscNumber(song.discNumber.takeIf { it > 0 })
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setArtworkUri(artUri)
            .setExtras(extras)
            .build()
        return MediaItem.Builder()
            .setMediaId(song.id.toString())
            .setUri(song.uri)
            .setMediaMetadata(metadata)
            .build()
    }

    companion object {
        const val EXTRA_FILE_PATH = "tide_file_path"
        const val EXTRA_MIME_TYPE = "tide_mime"
        const val EXTRA_SIZE = "tide_size"
        const val EXTRA_BITRATE = "tide_bitrate"
        const val EXTRA_SAMPLE_RATE = "tide_sample_rate"
        const val EXTRA_DURATION_MS = "tide_duration_ms"
        const val EXTRA_DATE_ADDED = "tide_date_added"
        const val EXTRA_DATE_MODIFIED = "tide_date_modified"
        const val EXTRA_PLAY_COUNT = "tide_play_count"
        const val EXTRA_LAST_PLAYED = "tide_last_played"
        const val EXTRA_ARTWORK_URI = "tide_artwork_uri"
    }

    fun setQueue(songs: List<Song>, startIndex: Int = 0) {
        val p = player ?: return
        try {
            if (songs.isEmpty()) {
                p.clearMediaItems()
                savePlaybackState()
                return
            }
            val mediaItems = songs.map(::mediaItemFor)
            val safeIndex = startIndex.coerceIn(0, mediaItems.lastIndex)
            p.clearMediaItems()
            p.setMediaItems(mediaItems, safeIndex, 0L)
            if (p.shuffleModeEnabled && mediaItems.size > 1) {
                (p as? androidx.media3.exoplayer.ExoPlayer)?.setShuffleOrder(
                    androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder(
                        mediaItems.size,
                        secureRandom.nextLong()
                    )
                )
            }
            p.prepare()
            p.playWhenReady = true
            savePlaybackState()
        } catch (e: Exception) {
            Log.e("PlaybackController", "Error setting queue", e)
        }
    }

    fun playNext(currentIndex: Int, song: Song) {
        val p = player ?: return
        try {
            val count = p.mediaItemCount
            val base = if (currentIndex >= 0) currentIndex else p.currentMediaItemIndex.coerceAtLeast(0)
            val insertPos = (base + 1).coerceIn(0, count)
            p.addMediaItem(insertPos, mediaItemFor(song))
            savePlaybackState()
        } catch (e: Exception) {
            Log.e("PlaybackController", "Error adding playNext", e)
        }
    }

    /** Insert [song] to play immediately after the currently playing item. */
    fun playNext(song: Song) {
        val p = player ?: return
        playNext(p.currentMediaItemIndex.coerceAtLeast(0), song)
    }

    fun addToQueue(song: Song) {
        val p = player ?: return
        try {
            p.addMediaItem(mediaItemFor(song))
            savePlaybackState()
        } catch (e: Exception) {
            Log.e("PlaybackController", "Error adding to queue", e)
        }
    }

    fun addToQueue(songs: List<Song>) {
        val p = player ?: return
        try {
            p.addMediaItems(songs.map(::mediaItemFor))
            savePlaybackState()
        } catch (e: Exception) {
            Log.e("PlaybackController", "Error adding multiple to queue", e)
        }
    }

    fun togglePlayPause() {
        val p = player ?: return
        try {
            if (p.isPlaying) {
                p.pause()
            } else {
                if (p.playbackState == Player.STATE_IDLE || p.playerError != null) {
                    p.prepare()
                } else if (p.playbackState == Player.STATE_ENDED) {
                    p.seekTo(0, 0L)
                    p.prepare()
                }
                p.play()
            }
            savePlaybackState()
        } catch (e: Exception) {
            Log.e("PlaybackController", "Error toggling play/pause", e)
        }
    }

    fun next() {
        val p = player ?: return
        try {
            if (p.mediaItemCount > 0) {
                if (p.hasNextMediaItem()) {
                    p.seekToNextMediaItem()
                } else if (p.shuffleModeEnabled && p.mediaItemCount > 1) {
                    (p as? androidx.media3.exoplayer.ExoPlayer)?.setShuffleOrder(
                        androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder(
                            p.mediaItemCount,
                            secureRandom.nextLong()
                        )
                    )
                    p.seekTo(0, 0L)
                } else {
                    p.seekTo(0, 0L)
                }
                if (!p.isPlaying) p.play()
            }
            savePlaybackState()
        } catch (e: Exception) {
            Log.e("PlaybackController", "Error seeking to next", e)
        }
    }

    fun previous() {
        val p = player ?: return
        try {
            if (p.mediaItemCount > 0) {
                if (p.currentPosition > 3000L) {
                    p.seekTo(0L)
                } else if (p.hasPreviousMediaItem()) {
                    p.seekToPreviousMediaItem()
                } else {
                    p.seekTo(0, 0L)
                }
                if (!p.isPlaying) p.play()
            }
            savePlaybackState()
        } catch (e: Exception) {
            Log.e("PlaybackController", "Error seeking to previous", e)
        }
    }

    fun seekTo(positionMs: Long) {
        val p = player ?: return
        try {
            if (p.mediaItemCount > 0) {
                val dur = p.duration.coerceAtLeast(0L)
                val target = if (dur > 0) positionMs.coerceIn(0L, dur) else positionMs.coerceAtLeast(0L)
                p.seekTo(target)
            }
            savePlaybackState()
        } catch (e: Exception) {
            Log.e("PlaybackController", "Error seeking position", e)
        }
    }

    fun stop() {
        val p = player ?: return
        try {
            p.stop()
            p.clearMediaItems()
            savePlaybackState()
        } catch (e: Exception) {
            Log.e("PlaybackController", "Error stopping playback", e)
        }
    }

    fun clearQueue() {
        val p = player ?: return
        try {
            p.stop()
            p.clearMediaItems()
            savePlaybackState()
        } catch (e: Exception) {
            Log.e("PlaybackController", "Error clearing queue", e)
        }
    }

    fun setShuffleMode(enabled: Boolean) {
        val p = player ?: return
        try {
            p.shuffleModeEnabled = enabled
            if (enabled && p.mediaItemCount > 1) {
                (p as? androidx.media3.exoplayer.ExoPlayer)?.setShuffleOrder(
                    androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder(
                        p.mediaItemCount,
                        secureRandom.nextLong()
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("PlaybackController", "Error setting shuffle", e)
        }
    }

    /** 3-state repeat: Off → Repeat Queue → Repeat One-Song → Off (Spec Section 7). */
    fun cycleRepeat(): Int {
        val p = player ?: return Player.REPEAT_MODE_OFF
        try {
            p.repeatMode = when (p.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            return p.repeatMode
        } catch (e: Exception) {
            Log.e("PlaybackController", "Error cycling repeat", e)
            return Player.REPEAT_MODE_OFF
        }
    }

    fun setPlaybackSpeed(speed: Float, pitch: Float) {
        val p = player ?: return
        try {
            p.playbackParameters = androidx.media3.common.PlaybackParameters(speed, pitch)
        } catch (e: Exception) {
            Log.e("PlaybackController", "Error setting playback speed", e)
        }
    }

    fun savePlaybackState() {
        val p = player ?: return
        try {
            val count = p.mediaItemCount
            val currentIndex = p.currentMediaItemIndex.coerceAtLeast(0)
            val currentPos = p.currentPosition.coerceAtLeast(0L)
            val ids = ArrayList<Long>(count)
            for (i in 0 until count) {
                val id = p.getMediaItemAt(i).mediaId.toLongOrNull()
                if (id != null) ids.add(id)
            }
            saveStateJob?.cancel()
            saveStateJob = ioScope.launch {
                try {
                    val prefs = context.getSharedPreferences("playback_state", Context.MODE_PRIVATE)
                    prefs.edit()
                        .putString("queue_ids", ids.joinToString(","))
                        .putInt("queue_index", currentIndex)
                        .putLong("queue_position", currentPos)
                        .apply()
                } catch (e: Exception) {
                    Log.e("PlaybackController", "Error saving playback state to prefs", e)
                }
            }
        } catch (e: Exception) {
            Log.e("PlaybackController", "Error saving playback state", e)
        }
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val id = mediaItem?.mediaId?.toLongOrNull()
            val p = player
            // Prevent back-to-back immediate repeat in shuffle mode if there are multiple songs
            if (id != null && p != null && p.shuffleModeEnabled && p.mediaItemCount > 1 && id == lastPlayedMediaId) {
                if (p.hasNextMediaItem()) {
                    p.seekToNextMediaItem()
                    return
                }
            }
            if (id != null) {
                lastPlayedMediaId = id
                scope.launch {
                    try {
                        val now = System.currentTimeMillis()
                        // 1. Immediately record last_played_timestamp so Recently Played is updated instantly
                        repository.recordPlayedTimestamp(id, now)

                        // 2. If auto transition (finished previous track), increment play count
                        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                            repository.markPlayed(id, now)
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            savePlaybackState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                val currentId = player?.currentMediaItem?.mediaId?.toLongOrNull()
                if (currentId != null) {
                    scope.launch {
                        try {
                            repository.markPlayed(currentId, System.currentTimeMillis())
                        } catch (_: Exception) {}
                    }
                }
            }
            savePlaybackState()
        }
    }
}
