package com.example.tidemusic.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.media3.session.MediaController

/**
 * CompositionLocal exposing the connected [MediaController] (or null until connected).
 *
 * Provided once from [com.example.tidemusic.MainActivity]'s composition scope. Screens that
 * need playback actions read this; screens that need live state poll the controller via the
 * helpers in controller.observe package. Never touch ExoPlayer directly from Compose.
 */
val LocalMediaController: ProvidableCompositionLocal<MediaController?> =
    compositionLocalOf { null }
