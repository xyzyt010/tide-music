package com.example.tidemusic.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Holds a UI-side [MediaController] connected to [PlaybackService]. Lifecycle-owned at the
 * [MainActivity] scope so every Composable screen can read live playback state without
 * re-establishing the session every navigation.
 *
 * The UI NEVER touches ExoPlayer directly — `toc()` actions on the player are observed
 * here and converted to [StateFlow]s for Compose (spec Section 1 architecture).
 */
@UnstableApi
object ConnectionHolder {

    private var future: ListenableFuture<MediaController>? = null
    private val _connected = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _connected

    fun connect(context: Context) {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        future = MediaController.Builder(context, token).buildAsync().also { f ->
            f.addListener({
                try {
                    val mc = f.get()
                    _connected.value = mc
                    mc.addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(playing: Boolean) {
                            (_isPlaying as MutableStateFlow).value = playing
                        }
                    })
                    (_isPlaying as MutableStateFlow).value = mc.isPlaying
                } catch (e: Throwable) {
                    android.util.Log.e("ConnectionHolder", "Failed to get MediaController", e)
                }
            }, androidx.core.content.ContextCompat.getMainExecutor(context))
        }
    }

    fun disconnect() {
        future?.let { MediaController.releaseFuture(it) }
        future = null
        _connected.value = null
    }

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    fun Player?.orNull(): Player? = this
}
