package com.example.tidemusic.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EqPresetDao {

    @Query("SELECT * FROM eq_presets ORDER BY is_built_in DESC, name ASC")
    fun observeAll(): Flow<List<EqPresetEntity>>

    @Query("SELECT * FROM eq_presets WHERE is_active = 1 LIMIT 1")
    fun observeActive(): Flow<EqPresetEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(presets: List<EqPresetEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preset: EqPresetEntity): Long

    @Query("UPDATE eq_presets SET is_active = 0")
    suspend fun clearActive()

    @Query("UPDATE eq_presets SET is_active = 1 WHERE id = :id")
    suspend fun setActive(id: Long)

    @Query("DELETE FROM eq_presets WHERE id = :id AND is_built_in = 0")
    suspend fun delete(id: Long)
}

@Dao
interface BookmarkDao {

    @Query("SELECT * FROM bookmarks WHERE song_id = :songId ORDER BY timestamp_ms ASC")
    fun observeForSong(songId: Long): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM bookmarks WHERE song_id = :songId")
    suspend fun deleteForSong(songId: Long)
}

@Dao
interface FolderDao {

    @Query("SELECT * FROM folders ORDER BY path ASC")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE parent_folder_id IS NULL AND song_count > 0 ORDER BY path ASC")
    fun observeRootFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE parent_folder_id = :parentId ORDER BY path ASC")
    fun observeChildren(parentId: Long): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getById(id: Long): FolderEntity?

    @Query("SELECT * FROM folders WHERE id = :id")
    fun observeById(id: Long): Flow<FolderEntity?>

    @Query("SELECT * FROM songs WHERE folder_id = :folderId ORDER BY title COLLATE NOCASE ASC")
    fun observeSongsInFolder(folderId: Long): Flow<List<SongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(folders: List<FolderEntity>)

    @Query("DELETE FROM folders")
    suspend fun deleteAll()
} 
