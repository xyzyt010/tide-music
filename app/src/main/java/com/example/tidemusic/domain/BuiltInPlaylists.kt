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

    const val FORMAT_MP3 = "MP3 Audio"
    const val FORMAT_FLAC = "FLAC Lossless"
    const val FORMAT_M4A = "M4A / AAC"
    const val FORMAT_WAV = "WAV Audio"
    const val FORMAT_OGG = "OGG / OPUS"

    val formatPlaylists: List<String> = listOf(
        FORMAT_MP3,
        FORMAT_FLAC,
        FORMAT_M4A,
        FORMAT_WAV,
        FORMAT_OGG,
    )

    /** Names in pinned/display order. */
    val ordered: List<String> = listOf(
        ALL_SONGS,
        FAVORITES,
        RECENTLY_ADDED,
        RECENTLY_PLAYED,
        MOST_PLAYED,
        NOT_PLAYED,
        DOWNLOAD,
    )

    fun isBuiltInName(name: String): Boolean =
        ordered.contains(name) || formatPlaylists.contains(name) || name.equals("yt-dlp", ignoreCase = true)
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

    const val FORMAT_MP3: Long = -10L
    const val FORMAT_FLAC: Long = -11L
    const val FORMAT_M4A: Long = -12L
    const val FORMAT_WAV: Long = -13L
    const val FORMAT_OGG: Long = -14L

    fun isFormatPlaylist(id: Long): Boolean = id in -14L..-10L

    fun idForName(name: String): Long = when (name) {
        BuiltInPlaylists.ALL_SONGS -> ALL_SONGS
        BuiltInPlaylists.FAVORITES -> FAVORITES
        BuiltInPlaylists.RECENTLY_ADDED -> RECENTLY_ADDED
        BuiltInPlaylists.RECENTLY_PLAYED -> RECENTLY_PLAYED
        BuiltInPlaylists.MOST_PLAYED -> MOST_PLAYED
        BuiltInPlaylists.NOT_PLAYED -> NOT_PLAYED
        BuiltInPlaylists.DOWNLOAD, "yt-dlp", "Download" -> DOWNLOAD
        BuiltInPlaylists.FORMAT_MP3 -> FORMAT_MP3
        BuiltInPlaylists.FORMAT_FLAC -> FORMAT_FLAC
        BuiltInPlaylists.FORMAT_M4A -> FORMAT_M4A
        BuiltInPlaylists.FORMAT_WAV -> FORMAT_WAV
        BuiltInPlaylists.FORMAT_OGG -> FORMAT_OGG
        else -> error("Not a built-in playlist: $name")
    }
}
