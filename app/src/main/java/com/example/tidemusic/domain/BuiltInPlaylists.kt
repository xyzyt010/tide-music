package com.example.tidemusic.domain

/**
 * Built-in smart playlists.
 *
 * Pinned at the top of the Playlists screen: All Songs, Favorites, Recently Added,
 * Recently Played, Most Played, Not Played, and Download (yt-dlp downloaded tracks).
 */
data object BuiltInPlaylists {
    const val ALL_SONGS = "All Songs"
    const val FAVORITES = "Favorites"
    const val RECENTLY_ADDED = "Recently Added"
    const val RECENTLY_PLAYED = "Recently Played"
    const val MOST_PLAYED = "Most Played"
    const val NOT_PLAYED = "Not Played"
    const val DOWNLOAD = "Download"
    const val YT_DLP = "Download"
    const val FILE_FORMATS = "File Formats"

    /** Names in pinned/display order. */
    val ordered: List<String> = listOf(
        ALL_SONGS,
        FAVORITES,
        RECENTLY_ADDED,
        RECENTLY_PLAYED,
        MOST_PLAYED,
        NOT_PLAYED,
        FILE_FORMATS,
        DOWNLOAD,
    )

    fun isBuiltInName(name: String): Boolean =
        ordered.contains(name) || name.equals("yt-dlp", ignoreCase = true)
}

/** IDs given to built-in playlists are negative so they never clash with autogen user rows. */
object BuiltInPlaylistIds {
    const val ALL_SONGS: Long = -1L
    const val FAVORITES: Long = -2L
    const val RECENTLY_ADDED: Long = -3L
    const val RECENTLY_PLAYED: Long = -4L
    const val MOST_PLAYED: Long = -5L
    const val NOT_PLAYED: Long = -6L
    const val DOWNLOAD: Long = -7L
    const val FILE_FORMATS: Long = -10L

    fun isFormatPlaylist(id: Long): Boolean = id == FILE_FORMATS

    fun idForName(name: String): Long = when (name) {
        BuiltInPlaylists.ALL_SONGS -> ALL_SONGS
        BuiltInPlaylists.FAVORITES -> FAVORITES
        BuiltInPlaylists.RECENTLY_ADDED -> RECENTLY_ADDED
        BuiltInPlaylists.RECENTLY_PLAYED -> RECENTLY_PLAYED
        BuiltInPlaylists.MOST_PLAYED -> MOST_PLAYED
        BuiltInPlaylists.NOT_PLAYED -> NOT_PLAYED
        BuiltInPlaylists.DOWNLOAD, "yt-dlp", "Download" -> DOWNLOAD
        BuiltInPlaylists.FILE_FORMATS -> FILE_FORMATS
        else -> error("Not a built-in playlist: $name")
    }
}
