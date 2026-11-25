package com.example.tidemusic.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY is_built_in DESC, order_index ASC, created_at ASC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observePlaylist(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): PlaylistEntity?

    @Query(
        """
        SELECT s.* FROM songs s
        INNER JOIN playlist_song_cross_ref r ON r.song_id = s.id
        WHERE r.playlist_id = :playlistId
        ORDER BY r.position ASC
        """,
    )
    fun observeSongsInPlaylist(playlistId: Long): Flow<List<SongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(playlists: List<PlaylistEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(playlist: PlaylistEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRefs(refs: List<PlaylistSongCrossRef>)

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE playlists SET order_index = :order WHERE id = :id")
    suspend fun updateOrder(id: Long, order: Int)

    @Query("DELETE FROM playlists WHERE id = :id AND is_built_in = 0")
    suspend fun delete(id: Long)

    @Query("DELETE FROM playlist_song_cross_ref WHERE playlist_id = :playlistId AND song_id = :songId")
    suspend fun removeSong(playlistId: Long, songId: Long)

    @Query("DELETE FROM playlist_song_cross_ref WHERE song_id = :songId")
    suspend fun removeAllSongReferences(songId: Long)

    @Transaction
    suspend fun replaceSongs(playlistId: Long, songs: List<Long>) {
        clearPlaylist(playlistId)
        songs.forEachIndexed { index, songId ->
            insertCrossRefs(listOf(PlaylistSongCrossRef(playlistId, songId, index)))
        }
    }

    @Query("DELETE FROM playlist_song_cross_ref WHERE playlist_id = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)
}
