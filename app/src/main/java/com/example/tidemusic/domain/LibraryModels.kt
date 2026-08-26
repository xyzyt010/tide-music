package com.example.tidemusic.domain

import com.example.tidemusic.data.db.SongEntity

/**
 * Domain song model — a stable, read-only view consumed directly by the UI. Persisted state
 * lives in [SongEntity] (Room). Conversion happens centrally in [com.example.tidemusic.domain.LibraryRepository].
 */
data class Song(
    val id: Long,
    val filePath: String,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val dateAdded: Long,
    val dateModified: Long,
    val size: Long,
    val mimeType: String,
    val bitrate: Int,
    val sampleRate: Int,
    val trackNumber: Int,
    val discNumber: Int,
    val playCount: Int,
    val lastPlayedTimestamp: Long?,
    val isFavorite: Boolean,
    val folderId: Long?,
    val artworkUri: String?,
)

fun SongEntity.toDomain(): Song = Song(
    id = id,
    filePath = filePath,
    uri = uri,
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    dateAdded = dateAdded,
    dateModified = dateModified,
    size = size,
    mimeType = mimeType,
    bitrate = bitrate,
    sampleRate = sampleRate,
    trackNumber = trackNumber,
    discNumber = discNumber,
    playCount = playCount,
    lastPlayedTimestamp = lastPlayedTimestamp,
    isFavorite = isFavorite,
    folderId = folderId,
    artworkUri = artworkUri,
)

data class Album(
    val id: Long,
    val name: String,
    val artist: String,
    val artworkUri: String?,
    val trackCount: Int,
    val totalDurationMs: Long,
)

data class Artist(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val albumCount: Int,
)

data class Folder(
    val id: Long,
    val path: String,
    val parentFolderId: Long?,
    val depth: Int,
    val songCount: Int,
    val subfolderCount: Int,
    val totalDurationMs: Long,
)

data class Playlist(
    val id: Long,
    val name: String,
    val isBuiltIn: Boolean,
    val createdAt: Long,
    val orderIndex: Int,
)
