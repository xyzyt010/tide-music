package com.example.tidemusic

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** All Compose destinations in the navigation graph (Compose Navigation 3 keys). */

@Serializable data object Main : NavKey
@Serializable data object Queue : NavKey
@Serializable data object Albums : NavKey
@Serializable data object Playlists : NavKey
@Serializable data object Folders : NavKey
@Serializable data object Search : NavKey
@Serializable data object Download : NavKey
@Serializable data object Artists : NavKey
@Serializable data object Player : NavKey
@Serializable data object Equalizer : NavKey
@Serializable data object Settings : NavKey
@Serializable data object HelpInfo : NavKey
@Serializable data object SleepTimer : NavKey
@Serializable data class AlbumDetail(val albumId: Long) : NavKey
@Serializable data class PlaylistDetail(val playlistId: Long, val playlistName: String, val isBuiltIn: Boolean) : NavKey
@Serializable data class FolderDetail(val folderId: Long, val folderPath: String) : NavKey
@Serializable data class ArtistDetail(val artistId: Long, val artistName: String) : NavKey
