package com.example.tidemusic.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

/**
 * App-wide Room database for the offline music player (spec Section 3).
 *
 * Entities mirror the spec's data model exactly. The cross-references and queue are
 * scoped by playlist/session; deletes go through [SongDao.purgeSongEverywhere] so no
 * dangles ever remain (spec Section 8.3).
 */
@Database(
    entities = [
        SongEntity::class,
        AlbumEntity::class,
        ArtistEntity::class,
        FolderEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        QueueItem::class,
        EqPresetEntity::class,
        BookmarkEntity::class,
        DownloadTaskEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(EqBandGainsConverter::class)
abstract class TideMusicDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun albumDao(): AlbumDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun queueDao(): QueueDao
    abstract fun eqPresetDao(): EqPresetDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun folderDao(): FolderDao
    abstract fun downloadTaskDao(): DownloadTaskDao

    companion object {
        const val NAME = "tide_music.db"
    }
}

/** Parses the EQ preset `band_gains` CSV column into a FloatArray and back. */
class EqBandGainsConverter {
    @TypeConverter
    fun fromFloatArray(value: FloatArray): String = value.joinToString(",")

    @TypeConverter
    fun toFloatArray(value: String): FloatArray =
        if (value.isBlank()) FloatArray(0)
        else value.split(",").map { it.trim().toFloat() }.toFloatArray()
}
