package com.example.tidemusic.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Spec Section 3 — Playlist (built-in or user-created) + cross-ref to songs. */
@Entity(
    tableName = "playlists",
    indices = [Index("order_index")],
)
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "is_built_in") val isBuiltIn: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "order_index") val orderIndex: Int,
)

/** Cross-reference table for songs-in-playlists; composite primary key on (playlistId, position). */
@Entity(
    tableName = "playlist_song_cross_ref",
    primaryKeys = ["playlist_id", "position"],
    indices = [Index("song_id")],
)
data class PlaylistSongCrossRef(
    @ColumnInfo(name = "playlist_id") val playlistId: Long,
    @ColumnInfo(name = "song_id") val songId: Long,
    val position: Int,
)

/** Queue item — ordered playback queue scoped to a session. */
@Entity(
    tableName = "queue_items",
    primaryKeys = ["session_id", "position"],
    indices = [Index("song_id")],
)
data class QueueItem(
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "song_id") val songId: Long,
    val position: Int,
)
