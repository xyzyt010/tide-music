package com.example.tidemusic.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Spec Section 3 — equalizer preset. 10-band parametric EQ (Section 8.4 Tier 2);
 * `bandGains` is stored as a CSV string for portability — the DAO converters parse it.
 */
@Entity(tableName = "eq_presets")
data class EqPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "is_built_in") val isBuiltIn: Boolean,
    /** CSV of 10 floats, ISO band centers: 31,62,125,250,500,1k,2k,4k,8k,16k Hz. */
    @ColumnInfo(name = "band_gains") val bandGains: String,
    @ColumnInfo(name = "preamp_gain") val preampGain: Float,
    @ColumnInfo(name = "is_active") val isActive: Boolean = false,
)

/** Spec Section 3 — timestamped note/bookmark on a song (player overflow menu). */
@Entity(
    tableName = "bookmarks",
    indices = [androidx.room.Index("song_id")],
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "song_id") val songId: Long,
    @ColumnInfo(name = "timestamp_ms") val timestampMs: Long,
    val note: String,
)

/** Spec Section 3 — yt-dlp download task row (Section 6.6 / 8.6). */
@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "source_url") val sourceUrl: String,
    val status: String, // Queued | Downloading | Extracting | Done | Failed
    val progress: Int, // 0..100
    @ColumnInfo(name = "destination_path") val destinationPath: String?,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)
