package com.example.tidemusic.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for a single indexed audio track (spec Section 3).
 *
 * `id` is a stable hash of the file's absolute path so rescans diff cleanly against
 * `dateModified` while keeping cross-references (PlaylistSongCrossRef, QueueItem,
 * Bookmark) stable across library rebuilds.
 */
@Entity(
    tableName = "songs",
    indices = [Index("album"), Index("artist"), Index("folder_id"), Index("is_favorite")],
)
data class SongEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "file_path") val filePath: String,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    @ColumnInfo(name = "album_artist_id") val albumArtistId: Long? = null,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "date_added") val dateAdded: Long,
    @ColumnInfo(name = "date_modified") val dateModified: Long,
    val size: Long,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    val bitrate: Int,
    @ColumnInfo(name = "sample_rate") val sampleRate: Int,
    @ColumnInfo(name = "track_number") val trackNumber: Int,
    @ColumnInfo(name = "disc_number") val discNumber: Int,
    @ColumnInfo(name = "play_count") val playCount: Int = 0,
    @ColumnInfo(name = "last_played_timestamp") val lastPlayedTimestamp: Long? = null,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "folder_id") val folderId: Long? = null,
    @ColumnInfo(name = "artwork_uri") val artworkUri: String? = null,
)
