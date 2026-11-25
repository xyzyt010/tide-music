package com.example.tidemusic.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** DAO for the yt-dlp download task list (spec Section 3). */
@Dao
interface DownloadTaskDao {

    @Query("SELECT * FROM download_tasks ORDER BY added_at DESC")
    fun observeAll(): Flow<List<DownloadTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadTaskEntity): Long

    @Query("UPDATE download_tasks SET status = :status, progress = :progress WHERE id = :id")
    suspend fun updateProgress(id: Long, status: String, progress: Int)

    @Query("UPDATE download_tasks SET status = :status, destination_path = :path WHERE id = :id")
    suspend fun updateFinal(id: Long, status: String, path: String?)

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM download_tasks")
    suspend fun deleteAll()
}
