package com.example.tidemusic.di

import android.content.Context
import androidx.room.Room
import com.example.tidemusic.data.db.AlbumDao
import com.example.tidemusic.data.db.BookmarkDao
import com.example.tidemusic.data.db.DownloadTaskDao
import com.example.tidemusic.data.db.EqPresetDao
import com.example.tidemusic.data.db.FolderDao
import com.example.tidemusic.data.db.PlaylistDao
import com.example.tidemusic.data.db.QueueDao
import com.example.tidemusic.data.db.SongDao
import com.example.tidemusic.data.db.TideMusicDatabase

/** Originally a Hilt module; with the ServiceLocator pattern this is a small helper so
 *  the database + DAOs are constructable in one place, callable from [ServiceLocator.init]. */
internal object DatabaseModule {

    fun provideDatabase(context: Context): TideMusicDatabase =
        Room.databaseBuilder(context, TideMusicDatabase::class.java, TideMusicDatabase.NAME)
            .fallbackToDestructiveMigration(false)
            .build()

    fun songDao(db: TideMusicDatabase): SongDao = db.songDao()
    fun albumDao(db: TideMusicDatabase): AlbumDao = db.albumDao()
    fun playlistDao(db: TideMusicDatabase): PlaylistDao = db.playlistDao()
    fun queueDao(db: TideMusicDatabase): QueueDao = db.queueDao()
    fun eqPresetDao(db: TideMusicDatabase): EqPresetDao = db.eqPresetDao()
    fun downloadTaskDao(db: TideMusicDatabase): DownloadTaskDao = db.downloadTaskDao()
    fun bookmarkDao(db: TideMusicDatabase): BookmarkDao = db.bookmarkDao()
    fun folderDao(db: TideMusicDatabase): FolderDao = db.folderDao()
}
