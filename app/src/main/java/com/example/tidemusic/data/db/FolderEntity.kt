package com.example.tidemusic.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Spec Section 3 — real folder hierarchy built from a filesystem walk (Section 8.5). */
@Entity(
    tableName = "folders",
    indices = [Index("parent_folder_id"), Index(value = ["path"], unique = true)],
)
data class FolderEntity(
    @PrimaryKey val id: Long,
    val path: String,
    @ColumnInfo(name = "parent_folder_id") val parentFolderId: Long? = null,
    val depth: Int,
    @ColumnInfo(name = "song_count") val songCount: Int = 0,
    @ColumnInfo(name = "subfolder_count") val subfolderCount: Int = 0,
    @ColumnInfo(name = "total_duration_ms") val totalDurationMs: Long = 0,
)
