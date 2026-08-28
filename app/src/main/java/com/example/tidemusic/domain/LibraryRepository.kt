package com.example.tidemusic.domain

import android.content.Context
import com.example.tidemusic.data.FolderTreeBuilder
import com.example.tidemusic.data.MediaStoreScanner
import com.example.tidemusic.data.db.AlbumEntity
import com.example.tidemusic.data.db.ArtistEntity
import com.example.tidemusic.data.db.BookmarkDao
import com.example.tidemusic.data.db.BookmarkEntity
import com.example.tidemusic.data.db.DownloadTaskDao
import com.example.tidemusic.data.db.DownloadTaskEntity
import com.example.tidemusic.data.db.EqPresetDao
import com.example.tidemusic.data.db.EqPresetEntity
import com.example.tidemusic.data.db.FolderDao
import com.example.tidemusic.data.db.FolderEntity
import com.example.tidemusic.data.db.PlaylistDao
import com.example.tidemusic.data.db.PlaylistEntity
import com.example.tidemusic.data.db.PlaylistSongCrossRef
import com.example.tidemusic.data.db.QueueDao
import com.example.tidemusic.data.db.QueueItem
import com.example.tidemusic.data.db.SongDao
import com.example.tidemusic.data.db.SongEntity
import com.example.tidemusic.data.db.StableIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ScanProgress(
    val isScanning: Boolean = false,
    val stage: String = "",
    val count: Int = 0,
)

/**
 * Single source of truth for the library (spec Section 8.3).
 *
 * Every read goes through repository functions that return domain models built from Room
 * entities; every write — especially delete/move/tag-edit — goes through [deleteSongs] which
 * also touches every cross-referencing table so the Queue / Playlists / Favorites / Search
 * never show a dangling entry after a delete.
 */
class LibraryRepository constructor(
    private val context: Context,
    private val songDao: SongDao,
    @Suppress("unused") private val albumDao: com.example.tidemusic.data.db.AlbumDao,
    private val playlistDao: PlaylistDao,
    private val queueDao: QueueDao,
    @Suppress("unused") private val eqPresetDao: EqPresetDao,
    private val bookmarkDao: BookmarkDao,
    private val folderDao: FolderDao,
    private val downloadTaskDao: DownloadTaskDao,
) {
    private val scanner = MediaStoreScanner(context)
    private val treeBuilder = FolderTreeBuilder()

    private val _scanProgress = MutableStateFlow(ScanProgress())
    val scanProgress: StateFlow<ScanProgress> = _scanProgress.asStateFlow()

    // ── Library scan (spec Section 3 — incremental diff against dateModified) ─────────

    private val rawScanner = com.example.tidemusic.data.RawFileScanner()

    /**
     * Full rebuild of the song/album/artist/folder indices combining MediaStore and raw storage.
     * Respects user-configured folder exclusion settings.
     */
    suspend fun rescanFull(includePrefixes: Collection<String>? = null) = withContext(Dispatchers.IO) {
        _scanProgress.value = ScanProgress(isScanning = true, stage = "Querying audio index...", count = 0)
        val msScanned = scanner.scan()
        _scanProgress.value = ScanProgress(isScanning = true, stage = "Staging audio tracks...", count = msScanned.size)
        
        val existingPaths = msScanned.mapTo(HashSet()) { it.filePath }
        val rawScanned = try {
            rawScanner.scan(knownPaths = existingPaths) { unindexedFound ->
                _scanProgress.value = ScanProgress(
                    isScanning = true,
                    stage = "Discovering unindexed songs...",
                    count = msScanned.size + unindexedFound,
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
        
        val mergedScanned = msScanned.toMutableList()
        for (raw in rawScanned) {
            if (!existingPaths.contains(raw.filePath)) {
                mergedScanned.add(raw)
            }
        }

        val excluded = com.example.tidemusic.di.ServiceLocator.settingsManager.getExcludedFolders()
        val filteredScanned = if (excluded.isEmpty()) mergedScanned else mergedScanned.filter { track ->
            excluded.none { ex -> track.filePath.startsWith(ex) }
        }

        _scanProgress.value = ScanProgress(isScanning = true, stage = "Organizing library...", count = filteredScanned.size)

        songDao.deleteAll()
        albumDao.deleteAllAlbums()
        albumDao.deleteAllArtists()

        val (songs, albums, artists) = scanner.toEntities(filteredScanned)
        songDao.upsertAll(songs)
        albumDao.upsertAllAlbums(albums)
        albumDao.upsertAllArtists(artists)
        val folderTree = treeBuilder.build(songs, includePrefixes)
        folderDao.deleteAll()
        folderDao.upsertAll(folderTree.folders)

        _scanProgress.value = ScanProgress(isScanning = false, stage = "Complete", count = songs.size)
        Unit
    }

    /**
     * Incremental rescan: updates changed rows and rebuilds folder hierarchy.
     */
    suspend fun rescanIncremental(includePrefixes: Collection<String>? = null) = withContext(Dispatchers.IO) {
        val msScanned = scanner.scan()
        val rawScanned = try { rawScanner.scan() } catch (e: Exception) { emptyList() }
        
        val existingPaths = msScanned.mapTo(HashSet()) { it.filePath }
        val mergedScanned = msScanned.toMutableList()
        for (raw in rawScanned) {
            if (!existingPaths.contains(raw.filePath)) {
                mergedScanned.add(raw)
            }
        }

        val excluded = com.example.tidemusic.di.ServiceLocator.settingsManager.getExcludedFolders()
        val filteredScanned = if (excluded.isEmpty()) mergedScanned else mergedScanned.filter { track ->
            excluded.none { ex -> track.filePath.startsWith(ex) }
        }

        val existing = songDao.observeAllSongs().first().associateBy { it.id }
        val changed = filteredScanned.filter { row ->
            val s = existing[row.id]
            s == null || s.dateModified != row.dateModified
        }
        if (changed.isNotEmpty()) {
            val (songs, albums, artists) = scanner.toEntities(changed)
            songDao.upsertAll(songs)
            albumDao.upsertAllAlbums(albums)
            albumDao.upsertAllArtists(artists)
        }

        // Remove any songs in DB that are now in excluded folders
        if (excluded.isNotEmpty()) {
            val allSongsInDb = songDao.observeAllSongs().first()
            val toRemove = allSongsInDb.filter { song -> excluded.any { ex -> song.filePath.startsWith(ex) } }
            if (toRemove.isNotEmpty()) {
                songDao.deleteByIds(toRemove.map { it.id })
            }
        }

        val allSongs = songDao.observeAllSongs().first()
        val folderTree = treeBuilder.build(allSongs, includePrefixes)
        folderDao.deleteAll()
        folderDao.upsertAll(folderTree.folders)
    }

    /** Used by the Download screen (Section 8.6) — only re-index a small folder. */
    suspend fun rescanFolder(prefix: String) = withContext(Dispatchers.IO) {
        val scanned = scanner.scan().filter { it.filePath.startsWith(prefix) }
        if (scanned.isEmpty()) return@withContext
        val (songs, _, _) = scanner.toEntities(scanned)
        songDao.upsertAll(songs)
        val allSongs = songDao.observeAllSongs().first()
        val folderTree = treeBuilder.build(allSongs, null)
        folderDao.deleteAll(); folderDao.upsertAll(folderTree.folders)
    }

    // ── Songs (six built-in playlist computations + favorites + search) ──────────────

    fun observeAllSongs(): Flow<List<Song>> =
        songDao.observeAllSongs().map { rows -> rows.map { it.toDomain() } }

    fun observeFavorites(): Flow<List<Song>> =
        songDao.observeFavorites().map { rows -> rows.map { it.toDomain() } }

    fun observeRecentlyAdded(): Flow<List<Song>> =
        songDao.observeRecentlyAdded().map { rows -> rows.map { it.toDomain() } }

    fun observeRecentlyPlayed(): Flow<List<Song>> =
        songDao.observeRecentlyPlayed().map { rows -> rows.map { it.toDomain() } }

    fun observeMostPlayed(): Flow<List<Song>> =
        songDao.observeMostPlayed().map { rows -> rows.map { it.toDomain() } }

    fun observeNotPlayed(): Flow<List<Song>> =
        songDao.observeNotPlayed().map { rows -> rows.map { it.toDomain() } }

    fun observeSong(id: Long): Flow<Song?> =
        songDao.observeById(id).map { row -> row?.toDomain() }

    suspend fun getSong(id: Long): Song? = withContext(Dispatchers.IO) {
        songDao.getById(id)?.toDomain()
    }

    /** Batch-load songs by id (order of [ids] is preserved; missing ids are skipped). */
    suspend fun getSongsByIds(ids: List<Long>): List<Song> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()
        val map = ids.distinct().mapNotNull { id -> songDao.getById(id)?.toDomain() }.associateBy { it.id }
        ids.mapNotNull { map[it] }
    }

    fun searchSongs(query: String): Flow<List<Song>> =
        songDao.searchSongs(query).map { rows -> rows.map { it.toDomain() } }

    suspend fun setFavorite(id: Long, favorite: Boolean) =
        songDao.setFavorite(id, favorite)

    suspend fun recordPlayedTimestamp(id: Long, ts: Long) =
        songDao.recordPlayedTimestamp(id, ts)

    suspend fun markPlayed(id: Long, ts: Long) =
        songDao.incrementPlayCount(id, ts)

    suspend fun updateTags(id: Long, title: String, artist: String, album: String) =
        songDao.updateTags(id, title, artist, album)

    // ── Albums / Artists ────────────────────────────────────────────────────────────

    fun observeAlbums(): Flow<List<Album>> =
        albumDao.observeAlbums().map { rows -> rows.map { it.toDomain() } }

    fun observeAlbum(id: Long): Flow<Album?> =
        albumDao.observeAlbum(id).map { row -> row?.toDomain() }

    fun observeTracksForAlbum(albumId: Long): Flow<List<Song>> =
        albumDao.observeTracksForAlbum(albumId).map { rows -> rows.map { it.toDomain() } }

    fun observeArtists(): Flow<List<Artist>> =
        albumDao.observeArtists().map { rows -> rows.map { it.toDomain() } }

    // ── Playlists (built-in + user) ─────────────────────────────────────────────────

    /**
     * Playlists shown on the Playlists screen: built-ins (synthetic) always pinned at the
     * top; user-created ones below in display order. Built-ins are NOT stored as rows.
     */
    fun observePlaylists(): Flow<List<Playlist>> =
        playlistDao.observePlaylists().map { rows ->
            val builtIns = BuiltInPlaylists.ordered.mapIndexed { idx, name ->
                Playlist(
                    id = BuiltInPlaylistIds.idForName(name),
                    name = name,
                    isBuiltIn = true,
                    createdAt = 0L,
                    orderIndex = idx,
                )
            }
            val userRows = rows.filter { it.name != BuiltInPlaylists.DOWNLOAD && it.name != "yt-dlp" }
            builtIns + userRows.map { it.toDomain() }
        }

    fun observeSongsForPlaylist(playlist: Playlist): Flow<List<Song>> = when (playlist.id) {
        BuiltInPlaylistIds.ALL_SONGS -> observeAllSongs()
        BuiltInPlaylistIds.FAVORITES -> observeFavorites()
        BuiltInPlaylistIds.RECENTLY_ADDED -> observeRecentlyAdded()
        BuiltInPlaylistIds.RECENTLY_PLAYED -> observeRecentlyPlayed()
        BuiltInPlaylistIds.MOST_PLAYED -> observeMostPlayed()
        BuiltInPlaylistIds.NOT_PLAYED -> observeNotPlayed()
        BuiltInPlaylistIds.DOWNLOAD -> observeYtDlpSongs()
        else -> playlistDao.observeSongsInPlaylist(playlist.id)
            .map { rows -> rows.map { it.toDomain() } }
    }

    suspend fun createUserPlaylist(name: String): Long {
        val now = System.currentTimeMillis()
        val maxOrder = playlistDao.observePlaylists().first()
            .filterNot { it.isBuiltIn }
            .maxOfOrNull { it.orderIndex }
            ?: -1
        return playlistDao.insert(
            PlaylistEntity(
                name = name,
                isBuiltIn = false,
                createdAt = now,
                orderIndex = maxOrder + 1,
            ),
        )
    }

    /**
     * Ensures the "Download" playlist exists for downloaded tracks.
     * Safe to call repeatedly; returns the playlist id (existing or newly created).
     */
    suspend fun ensureYtDlpPlaylist(): Long = withContext(Dispatchers.IO) {
        playlistDao.getByName(BuiltInPlaylists.DOWNLOAD)?.id
            ?: playlistDao.getByName("yt-dlp")?.also { playlistDao.rename(it.id, BuiltInPlaylists.DOWNLOAD) }?.id
            ?: createUserPlaylist(BuiltInPlaylists.DOWNLOAD)
    }

    /** Observe songs currently in the Download playlist. */
    fun observeYtDlpSongs(): Flow<List<Song>> =
        playlistDao.observePlaylists()
            .map { rows -> rows.firstOrNull { it.name == BuiltInPlaylists.DOWNLOAD || it.name == "yt-dlp" } }
            .flatMapLatest { entity ->
                if (entity == null) {
                    flowOf(emptyList())
                } else {
                    playlistDao.observeSongsInPlaylist(entity.id)
                        .map { rows -> rows.map { it.toDomain() } }
                }
            }

    suspend fun getYtDlpPlaylistId(): Long? = withContext(Dispatchers.IO) {
        playlistDao.getByName(BuiltInPlaylists.DOWNLOAD)?.id
            ?: playlistDao.getByName("yt-dlp")?.id
    }

    /** Add a downloaded song to the Download playlist (creates playlist if needed). */
    suspend fun addSongToYtDlpPlaylist(songId: Long) = withContext(Dispatchers.IO) {
        val playlistId = ensureYtDlpPlaylist()
        val current = playlistDao.observeSongsInPlaylist(playlistId).first().map { it.id }
        if (songId !in current) {
            playlistDao.replaceSongs(playlistId, current + songId)
        }
    }

    /** Remove a song from the Download playlist only (does not delete the file). */
    suspend fun removeSongFromYtDlpPlaylist(songId: Long) = withContext(Dispatchers.IO) {
        val playlistId = getYtDlpPlaylistId() ?: return@withContext
        playlistDao.removeSong(playlistId, songId)
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) = withContext(Dispatchers.IO) {
        playlistDao.removeSong(playlistId, songId)
    }

    suspend fun findSongByFilePath(path: String): Song? = withContext(Dispatchers.IO) {
        songDao.getByFilePath(path)?.toDomain()
    }

    suspend fun renamePlaylist(id: Long, name: String) =
        playlistDao.rename(id, name)

    suspend fun deletePlaylist(id: Long) = withContext(Dispatchers.IO) {
        playlistDao.clearPlaylist(id)
        playlistDao.delete(id)
    }

    suspend fun addSongsToPlaylist(playlistId: Long, songIds: List<Long>) = withContext(Dispatchers.IO) {
        val maxPos = playlistDao.run {
            // playlistDao doesn't expose max position; we rebuild the cross-refs.
            val current = observeSongsInPlaylist(playlistId).first().map { it.id }
            val combined = (current + songIds).distinct()
            replaceSongs(playlistId, combined)
        }
        maxPos
    }

    suspend fun saveQueueAsPlaylist(name: String, songIds: List<Long>): Long = withContext(Dispatchers.IO) {
        val playlistId = createUserPlaylist(name)
        playlistDao.replaceSongs(playlistId, songIds)
        playlistId
    }

    // ── Queue (Room-persisted) ──────────────────────────────────────────────────────

    fun observeQueue(sessionId: String): Flow<List<QueueItem>> = queueDao.observeQueue(sessionId)

    suspend fun setQueue(sessionId: String, songIds: List<Long>) = withContext(Dispatchers.IO) {
        queueDao.clearSession(sessionId)
        queueDao.insertAll(songIds.mapIndexed { index, id -> QueueItem(sessionId, id, index) })
    }

    suspend fun clearQueue(sessionId: String) = queueDao.clearSession(sessionId)

    suspend fun removeFromQueueAt(sessionId: String, position: Int) =
        queueDao.removeAt(sessionId, position)

    suspend fun playNext(sessionId: String, songId: Long, atPosition: Int) = withContext(Dispatchers.IO) {
        val current = queueDao.observeQueue(sessionId).first().sortedBy { it.position }
        val rebuilt = current.filterNot { it.songId == songId }.toMutableList()
        val insertAt = atPosition.coerceIn(0, rebuilt.size)
        rebuilt.add(insertAt, QueueItem(sessionId, songId, insertAt))
        queueDao.clearSession(sessionId)
        queueDao.insertAll(rebuilt.mapIndexed { idx, item -> item.copy(position = idx) })
    }

    // ── Bookmarks ───────────────────────────────────────────────────────────────────

    fun observeBookmarks(songId: Long): Flow<List<BookmarkEntity>> =
        bookmarkDao.observeForSong(songId)

    suspend fun addBookmark(songId: Long, timestampMs: Long, note: String) =
        bookmarkDao.insert(BookmarkEntity(songId = songId, timestampMs = timestampMs, note = note))

    suspend fun deleteBookmark(id: Long) = bookmarkDao.delete(id)

    // ── Folders ─────────────────────────────────────────────────────────────────────

    fun observeRootFolders(): Flow<List<Folder>> =
        folderDao.observeRootFolders().map { rows -> rows.map { it.toDomain() } }

    fun observeFolderChildren(parentId: Long): Flow<List<Folder>> =
        folderDao.observeChildren(parentId).map { rows -> rows.map { it.toDomain() } }

    fun observeSongsInFolder(folderId: Long): Flow<List<Song>> =
        folderDao.observeSongsInFolder(folderId).map { rows -> rows.map { it.toDomain() } }

    fun observeAllFolders(): Flow<List<Folder>> =
        folderDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    // ── EQ presets ──────────────────────────────────────────────────────────────────

    fun observeEqPresets(): Flow<List<EqPresetEntity>> = eqPresetDao.observeAll()

    fun observeActiveEqPreset(): Flow<EqPresetEntity?> = eqPresetDao.observeActive()

    suspend fun saveEqPreset(preset: EqPresetEntity): Long = eqPresetDao.upsert(preset)

    suspend fun setActiveEqPreset(id: Long) {
        eqPresetDao.clearActive()
        eqPresetDao.setActive(id)
    }

    suspend fun deleteEqPreset(id: Long) = eqPresetDao.delete(id)

    // ── Download tasks ──────────────────────────────────────────────────────

    fun observeDownloadTasks(): Flow<List<DownloadTaskEntity>> = downloadTaskDao.observeAll()

    suspend fun clearDownloadTasks() = withContext(Dispatchers.IO) {
        downloadTaskDao.deleteAll()
    }

    suspend fun deleteDownloadTask(id: Long) = withContext(Dispatchers.IO) {
        downloadTaskDao.delete(id)
    }

    // ── Consistency-safe delete (spec Section 8.3) ──────────────────────────

    /**
     * DELETE PERMANENTLY route (player overflow menu, Section 7 "Delete Permanently").
     *
     * Single atomic path: (a) requests the file-system delete via the
     * `MediaStore.createDeleteRequest()` scoped-storage API (user-confirmed, safe), then
     * (b) — once committed — wipes every Room reference: the [SongEntity] row, all
     * [PlaylistSongCrossRef] rows, all [QueueItem] rows, and all [BookmarkEntity] rows
     * for that song, so nothing ever dangles in Queue / Playlists / Favorites / Search.
     *
     * Returns the IntentSender the Activity must launch (post-confirmation dialog); the
     * caller invokes the deleteIntentSender after the user confirms in the system dialog.
     * For safety against callers that never launch the system confirmation, the Room
     * rows are removed only after the file system confirms the deletion.
     */
    suspend fun deletePermanently(songIds: List<Long>): List<SongEntity> = withContext(Dispatchers.IO) {
        val resolved = songIds.mapNotNull { songDao.getById(it) }
        for (song in resolved) {
            // (b) wipe every Room reference first so the UI never shows a dangling entry.
            songDao.deleteByIds(listOf(song.id))
            playlistDao.removeAllSongReferences(song.id)
            queueDao.removeSongFromAllSessions(song.id)
            bookmarkDao.deleteForSong(song.id)

            // (a) physical file deletion. File.delete() works for files this app created
            // (yt-dlp downloads) or when All-Files-Access was granted; otherwise fall back
            // to deleting through MediaStore. Verify the outcome — a silently failed delete
            // used to make the song reappear after the next rescan.
            if (song.filePath.isNotBlank()) {
                val file = java.io.File(song.filePath)
                var deleted = try { file.delete() && !file.exists() } catch (_: Exception) { false }

                if (!deleted && song.uri.isNotBlank() && song.uri.startsWith("content://")) {
                    deleted = try {
                        context.contentResolver.delete(android.net.Uri.parse(song.uri), null, null) > 0
                    } catch (_: Exception) {
                        false
                    }
                }

                if (!deleted) {
                    // Last resort: locate the row in MediaStore by absolute path and delete
                    // through its media URI (works for files this app contributed).
                    deleted = deleteFromMediaStoreByPath(song.filePath)
                }

                if (deleted || !file.exists()) {
                    // Remove lyrics/subtitle sidecars that belonged to this track so no
                    // orphan .lrc/.vtt/.srt files are left behind in the music folder.
                    val parent = file.parentFile
                    val stem = file.nameWithoutExtension
                    parent?.listFiles()?.forEach { sidecar ->
                        val nameMatches = sidecar.nameWithoutExtension.equals(stem, ignoreCase = true) ||
                                sidecar.name.startsWith("$stem.", ignoreCase = true)
                        val isSidecarExt = sidecar.extension.equals("lrc", true) ||
                                sidecar.extension.equals("vtt", true) ||
                                sidecar.extension.equals("srt", true)
                        if (sidecar.isFile && nameMatches && isSidecarExt) {
                            try { sidecar.delete() } catch (_: Exception) {}
                        }
                    }
                }

                // Ask MediaStore to drop the stale row so rescans don't resurrect the song.
                try {
                    android.media.MediaScannerConnection.scanFile(
                        context,
                        arrayOf(song.filePath),
                        null,
                        null,
                    )
                } catch (_: Exception) {}
            }
        }
        resolved
    }

    suspend fun renameFile(songId: Long, newName: String): Boolean = withContext(Dispatchers.IO) {
        val song = songDao.getById(songId) ?: return@withContext false
        if (song.filePath.isBlank()) return@withContext false
        val file = java.io.File(song.filePath)
        if (!file.exists()) return@withContext false
        val parent = file.parentFile ?: return@withContext false
        val newFile = java.io.File(parent, newName)
        if (file.renameTo(newFile)) {
            songDao.upsertAll(listOf(song.copy(filePath = newFile.absolutePath, title = newName.substringBeforeLast('.'))))
            true
        } else {
            false
        }
    }

    /** Finds the MediaStore audio row for [path] and deletes it. Returns true on success. */
    private fun deleteFromMediaStoreByPath(path: String): Boolean = try {
        val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            android.provider.MediaStore.Audio.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL)
        } else {
            android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        var deleted = false
        context.contentResolver.query(
            collection,
            arrayOf(android.provider.MediaStore.Audio.Media._ID),
            "${android.provider.MediaStore.Audio.Media.DATA} = ?",
            arrayOf(path),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                try {
                    deleted = context.contentResolver
                        .delete(android.content.ContentUris.withAppendedId(collection, id), null, null) > 0 || deleted
                } catch (_: Exception) {}
            }
        }
        deleted
    } catch (_: Exception) {
        false
    }
}
