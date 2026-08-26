package com.example.tidemusic.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE ASC")
    fun observeAllSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getById(id: Long): SongEntity?

    @Query("SELECT * FROM songs WHERE file_path = :path LIMIT 1")
    suspend fun getByFilePath(path: String): SongEntity?

    @Query("SELECT * FROM songs WHERE id = :id")
    fun observeById(id: Long): Flow<SongEntity?>

    @Query("SELECT * FROM songs WHERE is_favorite = 1 ORDER BY title COLLATE NOCASE ASC")
    fun observeFavorites(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs ORDER BY date_added DESC, date_modified DESC")
    fun observeRecentlyAdded(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE last_played_timestamp IS NOT NULL AND last_played_timestamp > 0 ORDER BY last_played_timestamp DESC")
    fun observeRecentlyPlayed(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE play_count > 0 ORDER BY play_count DESC, last_played_timestamp DESC")
    fun observeMostPlayed(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE play_count = 0 OR last_played_timestamp IS NULL ORDER BY date_added DESC, title COLLATE NOCASE ASC")
    fun observeNotPlayed(): Flow<List<SongEntity>>

    @Query(
        """
        SELECT * FROM songs
        WHERE title LIKE '%' || :query || '%'
           OR artist LIKE '%' || :query || '%'
           OR album LIKE '%' || :query || '%'
        ORDER BY title COLLATE NOCASE ASC
        """,
    )
    fun searchSongs(query: String): Flow<List<SongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(songs: List<SongEntity>)

    @Query("DELETE FROM songs WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM songs")
    suspend fun deleteAll()

    @Query("UPDATE songs SET is_favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE songs SET last_played_timestamp = :ts WHERE id = :id")
    suspend fun recordPlayedTimestamp(id: Long, ts: Long)

    @Query("UPDATE songs SET play_count = play_count + 1, last_played_timestamp = :ts WHERE id = :id")
    suspend fun incrementPlayCount(id: Long, ts: Long)

    @Query("UPDATE songs SET title = :title, artist = :artist, album = :album WHERE id = :id")
    suspend fun updateTags(id: Long, title: String, artist: String, album: String)
}
