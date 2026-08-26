package com.example.tidemusic.ui.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tidemusic.data.db.DownloadTaskEntity
import com.example.tidemusic.domain.LibraryRepository
import com.example.tidemusic.domain.Song
import com.example.tidemusic.playback.PlaybackController
import com.example.tidemusic.playback.YtDlpDownloadManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Download screen ViewModel (spec Section 6.6 + 8.6).
 *
 * Active download tasks + songs from the auto-created "yt-dlp" playlist.
 * Removing a song from this screen removes it from the playlist; permanent
 * delete also removes the file from the library.
 */
class DownloadViewModel(
    private val repository: LibraryRepository,
    private val downloadManager: YtDlpDownloadManager,
    private val playbackController: PlaybackController,
) : ViewModel() {

    private val _url = MutableStateFlow("")
    val url: StateFlow<String> = _url

    val networkStatusMessage: StateFlow<String?> = downloadManager.networkStatusMessage

    private val _tasks = repository.observeDownloadTasks().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList(),
    )

    val tasks: StateFlow<List<DownloadTaskEntity>> = _tasks

    val activeTasks = _tasks.map { list ->
        list.filter { it.status != "Done" }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList(),
    )

    /** Songs in the yt-dlp playlist — primary list on the Download page. */
    val ytDlpSongs: StateFlow<List<Song>> = repository.observeYtDlpSongs().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList(),
    )

    private val _downloadWithLyrics = MutableStateFlow(true)
    val downloadWithLyrics: StateFlow<Boolean> = _downloadWithLyrics

    fun setUrl(v: String) { _url.value = v }

    fun setDownloadWithLyrics(value: Boolean) {
        _downloadWithLyrics.value = value
    }

    val isUrlValid: Boolean get() {
        val s = _url.value.trim()
        return s.startsWith("http://") || s.startsWith("https://")
    }

    val canDownload: Boolean
        get() = isUrlValid && networkStatusMessage.value == null

    fun enqueueDownload() {
        if (!canDownload) return
        // Re-ensure playlist exists in case user deleted it earlier
        viewModelScope.launch {
            try { repository.ensureYtDlpPlaylist() } catch (_: Exception) {}
        }
        downloadManager.enqueueDownload(_url.value.trim(), downloadWithLyrics = _downloadWithLyrics.value)
        _url.value = ""
    }

    fun clearAll() {
        viewModelScope.launch {
            try { repository.clearDownloadTasks() } catch (_: Exception) {}
        }
    }

    fun removeTask(taskId: Long) {
        viewModelScope.launch {
            try { repository.deleteDownloadTask(taskId) } catch (_: Exception) {}
        }
    }

    /** Remove song from yt-dlp playlist only (keeps file in library). */
    fun removeFromYtDlpPlaylist(songId: Long) {
        viewModelScope.launch {
            try { repository.removeSongFromYtDlpPlaylist(songId) } catch (_: Exception) {}
        }
    }

    /** Remove from playlist + delete file permanently. */
    fun deleteSongPermanently(songId: Long) {
        viewModelScope.launch {
            try {
                repository.removeSongFromYtDlpPlaylist(songId)
                repository.deletePermanently(listOf(songId))
            } catch (_: Exception) {}
        }
    }

    fun playSongAt(index: Int, songs: List<Song>) {
        try {
            playbackController.setQueue(songs, startIndex = index)
        } catch (_: Exception) {}
    }
}
