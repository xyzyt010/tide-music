package com.example.tidemusic.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.media3.common.Player

/**
 * CompositionLocal exposing the active playback [Player] (or null until attached).
 *
 * Provided once from [com.example.tidemusic.MainActivity]'s composition scope from
 * [com.example.tidemusic.playback.PlaybackController.playerState].
 */
val LocalMediaController: ProvidableCompositionLocal<Player?> =
    compositionLocalOf { null }
