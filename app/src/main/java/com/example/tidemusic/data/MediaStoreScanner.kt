package com.example.tidemusic.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.tidemusic.data.db.AlbumEntity
import com.example.tidemusic.data.db.ArtistEntity
import com.example.tidemusic.data.db.SongEntity
import com.example.tidemusic.data.db.StableIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Reads `MediaStore.Audio.Media` as the primary index for library scanning.
 *
 * Fast, non-blocking query using contentResolver.
 */
class MediaStoreScanner(private val context: Context) {

    data class ScannedTrack(
        val id: Long,
        val filePath: String,
        val uri: String,
        val title: String,
        val artist: String,
        val album: String,
        val albumArtist: String?,
        val durationMs: Long,
        val dateAdded: Long,
        val dateModified: Long,
        val size: Long,
        val mimeType: String,
        val bitrate: Int,
        val sampleRate: Int,
        val trackNumber: Int,
        val discNumber: Int,
        val folderId: Long?,
        val artworkUri: String?,
    )

    /** Queries the OS media index for all audio files and returns them as ScannedTracks. */
    suspend fun scan(): List<ScannedTrack> = withContext(Dispatchers.IO) {
        val collection = audioCollection()
        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.ALBUM_ID
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            projection.add(MediaStore.Audio.Media.ALBUM_ARTIST)
            projection.add(MediaStore.Audio.Media.BITRATE)
            projection.add(MediaStore.Audio.Media.TRACK)
            projection.add(MediaStore.Audio.Media.DISC_NUMBER)
        }

        val selection: String? = null
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        val out = ArrayList<ScannedTrack>()
        try {
            context.contentResolver.query(collection, projection.toTypedArray(), selection, null, sortOrder)?.use { c ->
                val idCol = c.getColumnIndex(MediaStore.Audio.Media._ID)
                val dataCol = c.getColumnIndex(MediaStore.Audio.Media.DATA)
                val nameCol = c.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                val titleCol = c.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val albumCol = c.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val durCol = c.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val addedCol = c.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
                val modCol = c.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
                val sizeCol = c.getColumnIndex(MediaStore.Audio.Media.SIZE)
                val mimeCol = c.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                val albumIdCol = c.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)

                val albumArtistCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) c.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST) else -1
                val bitrateCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) c.getColumnIndex(MediaStore.Audio.Media.BITRATE) else -1
                val sampleCol = c.getColumnIndex("sample_rate")
                val trackCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) c.getColumnIndex(MediaStore.Audio.Media.TRACK) else -1
                val discCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) c.getColumnIndex(MediaStore.Audio.Media.DISC_NUMBER) else -1

                while (c.moveToNext()) {
                    val id = if (idCol != -1) c.getLong(idCol) else 0L
                    val data = if (dataCol != -1) c.getString(dataCol) ?: "" else ""
                    val displayName = if (nameCol != -1) c.getString(nameCol) ?: "" else ""
                    val title = (if (titleCol != -1) c.getString(titleCol) else null)?.takeIf { it.isNotBlank() } ?: displayName.ifBlank { "Unknown" }
                    val artist = (if (artistCol != -1) c.getString(artistCol) else null)?.takeIf { it.isNotBlank() } ?: "Unknown"
                    val parentFolder = if (data.isNotBlank()) File(data).parentFile?.name else null
                    val album = (if (albumCol != -1) c.getString(albumCol) else null)?.takeIf { it.isNotBlank() && !it.equals("Unknown", true) }
                        ?: (if (!parentFolder.isNullOrBlank() && !parentFolder.equals("Music", true) && !parentFolder.equals("Download", true) && !parentFolder.equals("TideMusic", true)) parentFolder else title.ifBlank { "Unknown" })
                    val albumArtist = if (albumArtistCol != -1) c.getString(albumArtistCol)?.takeIf { it.isNotBlank() } else null
                    val folderPath = data.substringBeforeLast('/', "")
                    val folderId = if (folderPath.isBlank()) null else StableIds.folderId(folderPath)
                    val contentUri = ContentUris.withAppendedId(collection, id)
                    val msAlbumId = if (albumIdCol != -1) c.getLong(albumIdCol) else -1L
                    val artworkUri = if (msAlbumId != -1L) "content://media/external/audio/albumart/$msAlbumId" else null
                    val durMs = if (durCol != -1) c.getLong(durCol) else 0L
                    val sizeBytes = if (sizeCol != -1) c.getLong(sizeCol) else 0L

                    // Filter out corrupt 0s dummy files / non-audio entries
                    if (durMs <= 0L && sizeBytes <= 0L) continue
                    if (durMs <= 0L && data.isBlank()) continue

                    out += ScannedTrack(
                        id = stableSongId(data, id),
                        filePath = data,
                        uri = contentUri.toString(),
                        title = title,
                        artist = artist,
                        album = album,
                        albumArtist = albumArtist,
                        durationMs = durMs,
                        dateAdded = (if (addedCol != -1) c.getLong(addedCol) else 0L) * 1000L,
                        dateModified = (if (modCol != -1) c.getLong(modCol) else 0L) * 1000L,
                        size = sizeBytes,
                        mimeType = (if (mimeCol != -1) c.getString(mimeCol) else null) ?: "audio/*",
                        bitrate = if (bitrateCol >= 0) c.getInt(bitrateCol).coerceAtLeast(0) else 0,
                        sampleRate = if (sampleCol >= 0) c.getInt(sampleCol).coerceAtLeast(0) else 0,
                        trackNumber = if (trackCol >= 0) c.getInt(trackCol).coerceAtLeast(0) else 0,
                        discNumber = if (discCol >= 0) c.getInt(discCol).coerceAtLeast(0) else 0,
                        folderId = folderId,
                        artworkUri = artworkUri
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MediaStoreScanner", "Error querying MediaStore", e)
        }
        out
    }

    private fun stableSongId(filePath: String, mediaStoreId: Long): Long =
        if (filePath.isNotBlank()) StableIds.pathId(filePath) else mediaStoreId

    private fun audioCollection(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

    /** Convenience: build Room entities from scanned tracks (used by LibraryRepository). */
    fun toEntities(tracks: List<ScannedTrack>): Triple<List<SongEntity>, List<AlbumEntity>, List<ArtistEntity>> {
        val songs = ArrayList<SongEntity>(tracks.size)
        val albums = HashMap<Long, AlbumEntity>()
        val artists = HashMap<Long, ArtistEntity>()

        for (t in tracks) {
            val artistId = StableIds.artistId(t.artist)
            artists.merge(
                artistId,
                ArtistEntity(artistId, t.artist, trackCount = 1, albumCount = 0),
            ) { a, b -> a.copy(trackCount = a.trackCount + b.trackCount) }

            val albumId = StableIds.albumId(t.album, t.albumArtist ?: t.artist)
            albums.merge(
                albumId,
                AlbumEntity(
                    id = albumId,
                    name = t.album,
                    artist = t.albumArtist ?: t.artist,
                    artworkUri = t.artworkUri,
                    trackCount = 1,
                    totalDurationMs = t.durationMs,
                ),
            ) { a, b ->
                a.copy(
                    trackCount = a.trackCount + 1,
                    totalDurationMs = a.totalDurationMs + b.totalDurationMs,
                    artworkUri = a.artworkUri ?: b.artworkUri
                )
            }

            songs += SongEntity(
                id = t.id,
                filePath = t.filePath,
                uri = t.uri,
                title = t.title,
                artist = t.artist,
                album = t.album,
                albumArtistId = artistId,
                durationMs = t.durationMs,
                dateAdded = t.dateAdded,
                dateModified = t.dateModified,
                size = t.size,
                mimeType = t.mimeType,
                bitrate = t.bitrate,
                sampleRate = t.sampleRate,
                trackNumber = t.trackNumber,
                discNumber = t.discNumber,
                playCount = 0,
                lastPlayedTimestamp = null,
                isFavorite = false,
                folderId = t.folderId,
                artworkUri = t.artworkUri,
            )
        }

        return Triple(songs, albums.values.toList(), artists.values.toList())
    }
}
