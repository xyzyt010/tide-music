package com.example.tidemusic.playback

import android.content.ComponentName
import android.content.Context
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.tidemusic.di.ServiceLocator
import com.example.tidemusic.domain.Song
import com.google.common.util.concurrent.ListenableFuture

/**
 * App-wide playback controller conforming to the Option A System Media Controls spec.
 * Call [MusicManager.get] once from the MAIN thread (e.g. MainActivity.onCreate),
 * then call the control methods from anywhere in the UI.
 */
@OptIn(UnstableApi::class)
class MusicManager private constructor() {

    @Volatile
    private var controller: MediaController? = null
    private var connecting = false

    companion object {
        @Volatile
        private var instance: MusicManager? = null

        @JvmStatic
        fun get(context: Context): MusicManager {
            val current = instance
            if (current != null) {
                current.connectOnce(context.applicationContext)
                return current
            }
            return synchronized(this) {
                val next = instance ?: MusicManager().also { instance = it }
                next.connectOnce(context.applicationContext)
                next
            }
        }
    }

    private fun connectOnce(appContext: Context) {
        if (controller != null || connecting) return
        connecting = true
        val token = SessionToken(
            appContext,
            ComponentName(appContext, PlaybackService::class.java)
        )
        val future: ListenableFuture<MediaController> =
            MediaController.Builder(appContext, token).buildAsync()

        future.addListener({
            try {
                controller = future.get()
            } catch (e: Exception) {
                connecting = false // allow retry on next get()
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    private fun ready(): Boolean = controller != null

    // ------------------------------------------------------------------ queue
    /** Play a queue starting at startIndex — this is what makes the pill appear. */
    fun play(queue: List<Song>, startIndex: Int = 0) {
        val ctrl = controller ?: return
        if (queue.isEmpty()) return

        val items = ArrayList<MediaItem>(queue.size)
        for (s in queue) {
            items.add(ServiceLocator.playbackController.mediaItemFor(s))
        }
        val safeIndex = startIndex.coerceIn(0, items.lastIndex)
        ctrl.setMediaItems(items, safeIndex, /* startPositionMs = */ 0L)
        ctrl.prepare()
        ctrl.play()
    }

    fun playOne(song: Song) {
        play(listOf(song), 0)
    }

    // --------------------------------------------------------------- controls
    fun togglePlayPause() {
        val ctrl = controller ?: return
        if (ctrl.isPlaying) ctrl.pause() else ctrl.play()
    }

    fun pause() {
        controller?.pause()
    }

    fun resume() {
        controller?.play()
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        controller?.seekToPreviousMediaItem()
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
    }

    // ------------------------------------------------------------------ state
    fun isPlaying(): Boolean = ready() && controller?.isPlaying == true

    fun positionMs(): Long = if (ready()) (controller?.currentPosition ?: 0L) else 0L

    fun durationMs(): Long = if (ready()) (controller?.duration ?: 0L) else 0L

    fun currentQueueIndex(): Int = if (ready()) (controller?.currentMediaItemIndex ?: -1) else -1

    fun currentMetadata(): MediaMetadata? = if (ready()) controller?.mediaMetadata else null

    /** For the Now-Playing screen. Register after playback has started. */
    fun addPlayerListener(listener: Player.Listener) {
        controller?.addListener(listener)
    }

    fun removePlayerListener(listener: Player.Listener) {
        controller?.removeListener(listener)
    }
}
