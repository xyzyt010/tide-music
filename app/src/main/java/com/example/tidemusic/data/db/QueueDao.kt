package com.example.tidemusic.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface QueueDao {

    @Query("SELECT * FROM queue_items WHERE session_id = :sessionId ORDER BY position ASC")
    fun observeQueue(sessionId: String): Flow<List<QueueItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<QueueItem>)

    @Query("DELETE FROM queue_items WHERE session_id = :sessionId AND position = :position")
    suspend fun removeAt(sessionId: String, position: Int)

    @Query("DELETE FROM queue_items WHERE session_id = :sessionId")
    suspend fun clearSession(sessionId: String)

    @Query("DELETE FROM queue_items WHERE song_id = :songId")
    suspend fun removeSongFromAllSessions(songId: Long)

    @Query("SELECT MAX(position) FROM queue_items WHERE session_id = :sessionId")
    suspend fun maxPosition(sessionId: String): Int?
}
