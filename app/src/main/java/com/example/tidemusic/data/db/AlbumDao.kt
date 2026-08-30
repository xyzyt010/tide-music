package com.example.tidemusic.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {

    @Query("SELECT * FROM albums WHERE name != '' AND LOWER(name) NOT IN ('unknown', '<unknown>', 'download', 'downloads', 'music', 'tidemusic', 'audio', 'podcasts', 'audiobooks', 'dcim', '0', 'sdcard', 'emulated', 'storage', 'internal storage') AND track_count > 0 ORDER BY name COLLATE NOCASE ASC")
    fun observeAlbums(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE id = :id")
    fun observeAlbum(id: Long): Flow<AlbumEntity?>

    @Query("SELECT * FROM songs WHERE album = (SELECT name FROM albums WHERE id = :albumId) ORDER BY track_number ASC, title COLLATE NOCASE ASC")
    fun observeTracksForAlbum(albumId: Long): Flow<List<SongEntity>>

    @Query("SELECT * FROM artists WHERE name != '' AND LOWER(name) NOT IN ('unknown', '<unknown>') AND track_count > 0 ORDER BY name COLLATE NOCASE ASC")
    fun observeArtists(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM songs WHERE album_artist_id = :artistId OR artist = :artistName ORDER BY title COLLATE NOCASE ASC")
    fun observeTracksForArtist(artistId: Long, artistName: String): Flow<List<SongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAllAlbums(albums: List<AlbumEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAllArtists(artists: List<ArtistEntity>)

    @Query("DELETE FROM albums")
    suspend fun deleteAllAlbums()

    @Query("DELETE FROM artists")
    suspend fun deleteAllArtists()
}

/** Tracks for an album — relation populated by AlbumDao.observeAlbumWithTracks. */
data class AlbumWithTracks(
    @Embedded val album: AlbumEntity,
    @Relation(parentColumn = "id", entityColumn = "album")
    val tracks: List<SongEntity>,
)
