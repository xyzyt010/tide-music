package com.example.tidemusic.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tidemusic.domain.LibraryRepository
import com.example.tidemusic.domain.Song
import com.example.tidemusic.playback.PlaybackController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class QueueViewModel(
    private val repository: LibraryRepository,
    private val playback: PlaybackController,
) : ViewModel() {

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val sessionId = "main"

    init {
        load()
    }

    /** Observe the persist-backed queue; resolve song ids to live Song domain objects. */
    private fun load() {
        viewModelScope.launch {
            repository.observeQueue(sessionId)
                .map { items -> items.sortedBy { it.position }.map { it.songId } }
                .combine(repository.observeAllSongs()) { ids, all ->
                    val byId = all.associateBy { it.id }
                    ids.mapNotNull { byId[it] }
                }
                .onEach { _songs.value = it }
                .launchIn(viewModelScope)
        }
    }

    /** Plays the row at [index] in the current queue. */
    fun playAt(index: Int) {
        playback.setQueue(_songs.value, startIndex = index)
        _currentIndex.value = index
    }

    fun removeFromQueue(position: Int) {
        viewModelScope.launch { repository.removeFromQueueAt(sessionId, position) }
    }

    fun saveQueueAsPlaylist(name: String) {
        viewModelScope.launch { repository.saveQueueAsPlaylist(name, _songs.value.map { it.id }) }
    }

    fun clearQueue() {
        viewModelScope.launch { repository.clearQueue(sessionId) }
    }

    fun saveSelectedAsPlaylist(name: String, songIds: List<Long>) {
        viewModelScope.launch { repository.saveQueueAsPlaylist(name, songIds) }
    }
}
