package com.example.tidemusic.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Spec Section 3 — Album row. Art is nullable; UI falls back to PlaceholderArt gradient. */
@Entity(
    tableName = "albums",
    indices = [Index("artist", unique = false)],
)
data class AlbumEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val artist: String,
    @ColumnInfo(name = "artwork_uri") val artworkUri: String?,
    @ColumnInfo(name = "track_count") val trackCount: Int,
    @ColumnInfo(name = "total_duration_ms") val totalDurationMs: Long,
)

@Entity(
    tableName = "artists",
    indices = [Index("name")],
)
data class ArtistEntity(
    @PrimaryKey val id: Long,
    val name: String,
    @ColumnInfo(name = "track_count") val trackCount: Int,
    @ColumnInfo(name = "album_count") val albumCount: Int,
    @ColumnInfo(name = "artwork_uri") val artworkUri: String? = null,
)
