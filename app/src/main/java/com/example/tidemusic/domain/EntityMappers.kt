package com.example.tidemusic.domain

import com.example.tidemusic.data.db.AlbumEntity
import com.example.tidemusic.data.db.ArtistEntity
import com.example.tidemusic.data.db.FolderEntity
import com.example.tidemusic.data.db.PlaylistEntity

fun AlbumEntity.toDomain(): Album = Album(
    id = id,
    name = name,
    artist = artist,
    artworkUri = artworkUri,
    trackCount = trackCount,
    totalDurationMs = totalDurationMs,
)

fun ArtistEntity.toDomain(): Artist = Artist(
    id = id,
    name = name,
    trackCount = trackCount,
    albumCount = albumCount,
    artworkUri = artworkUri,
)

fun FolderEntity.toDomain(): Folder = Folder(
    id = id,
    path = path,
    parentFolderId = parentFolderId,
    depth = depth,
    songCount = songCount,
    subfolderCount = subfolderCount,
    totalDurationMs = totalDurationMs,
)

fun PlaylistEntity.toDomain(): Playlist = Playlist(
    id = id,
    name = name,
    isBuiltIn = isBuiltIn,
    createdAt = createdAt,
    orderIndex = orderIndex,
)
