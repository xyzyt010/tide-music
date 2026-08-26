package com.example.tidemusic.ui.albums

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tidemusic.di.ServiceLocator
import com.example.tidemusic.domain.LibraryRepository
import com.example.tidemusic.domain.Song
import com.example.tidemusic.playback.PlaybackController
import com.example.tidemusic.ui.common.MultiSelectActionBar
import com.example.tidemusic.ui.common.SongRow
import com.example.tidemusic.ui.common.ThinTopBar
import com.example.tidemusic.ui.common.rememberMultiSelectState
import com.example.tidemusic.ui.rememberTideViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.tidemusic.ui.common.SortCriteria
import com.example.tidemusic.ui.common.SortDropdown
import com.example.tidemusic.ui.common.applySortCriteria
import kotlinx.coroutines.flow.combine
import com.example.tidemusic.theme.TideColors
import com.example.tidemusic.util.formatPlaylistDuration
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp

class AlbumDetailViewModel(
    private val repository: LibraryRepository,
    private val playback: PlaybackController,
) : ViewModel() {

    private val _album = MutableStateFlow<com.example.tidemusic.domain.Album?>(null)
    val album: StateFlow<com.example.tidemusic.domain.Album?> = _album.asStateFlow()

    private val _rawTracks = MutableStateFlow<List<Song>>(emptyList())
    private val _sortCriteria = MutableStateFlow<List<SortCriteria>>(emptyList())
    val sortCriteria: StateFlow<List<SortCriteria>> = _sortCriteria.asStateFlow()

    private val _tracks = MutableStateFlow<List<Song>>(emptyList())
    val tracks: StateFlow<List<Song>> = _tracks.asStateFlow()

    init {
        viewModelScope.launch {
            combine(_rawTracks, _sortCriteria) { list, criteria ->
                list.applySortCriteria(criteria)
            }.collect { _tracks.value = it }
        }
    }

    fun load(albumId: Long) {
        viewModelScope.launch {
            launch { repository.observeTracksForAlbum(albumId).collect { _rawTracks.value = it } }
            launch { repository.observeAlbum(albumId).collect { _album.value = it } }
        }
    }

    fun setSortCriteria(criteria: List<SortCriteria>) {
        _sortCriteria.value = criteria
    }

    fun playAt(index: Int) { playback.setQueue(_tracks.value, startIndex = index) }
}

@Composable
fun AlbumDetailScreen(
    albumId: Long,
    onBack: () -> Unit,
    viewModel: AlbumDetailViewModel = rememberTideViewModel { AlbumDetailViewModel(it.repository, it.playbackController) },
) {
    androidx.compose.runtime.LaunchedEffect(albumId) { viewModel.load(albumId) }
    val tracks by viewModel.tracks.collectAsState()
    val album by viewModel.album.collectAsState()
    val sortCriteria by viewModel.sortCriteria.collectAsState()
    val multiSelect = rememberMultiSelectState()
    val playlists by ServiceLocator.repository.observePlaylists().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showSortDropdown by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ThinTopBar(
            title = album?.name ?: "Album",
            onBack = onBack,
            trailing = {
                Box {
                    IconButton(onClick = { showSortDropdown = true }) {
                        Icon(Icons.Rounded.Sort, contentDescription = "Sort")
                    }
                    SortDropdown(
                        expanded = showSortDropdown,
                        onDismissRequest = { showSortDropdown = false },
                        activeCriteria = sortCriteria,
                        onCriteriaChanged = { viewModel.setSortCriteria(it) }
                    )
                }
            }
        )
        Box(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        val totalDurationMs = remember(tracks) { tracks.sumOf { it.durationMs } }
                        Text(
                            text = "${tracks.size} songs · ${formatPlaylistDuration(totalDurationMs)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TideColors.textSecondary,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        )
                        androidx.compose.material3.HorizontalDivider(
                            modifier = Modifier.padding(top = 10.dp),
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.08f),
                            thickness = 1.dp
                        )
                    }
                }
                itemsIndexed(tracks, key = { _, s -> s.id }) { index, song ->
                    SongRow(
                        song = song,
                        onClick = { viewModel.playAt(index) },
                        multiSelect = multiSelect,
                    )
                }
            }
            MultiSelectActionBar(
                state = multiSelect,
                allIds = tracks.map { it.id },
                playlists = playlists,
                onDelete = { ids -> scope.launch { ServiceLocator.repository.deletePermanently(ids.toList()) } },
                onAddToPlaylist = { playlistId, songIds -> scope.launch { ServiceLocator.repository.addSongsToPlaylist(playlistId, songIds) } },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
