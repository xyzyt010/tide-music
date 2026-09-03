package com.example.tidemusic.ui.artists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tidemusic.di.ServiceLocator
import com.example.tidemusic.domain.LibraryRepository
import com.example.tidemusic.domain.Song
import com.example.tidemusic.playback.PlaybackController
import com.example.tidemusic.theme.TideColors
import com.example.tidemusic.ui.common.MultiSelectActionBar
import com.example.tidemusic.ui.common.SongRow
import com.example.tidemusic.ui.common.SortCriteria
import com.example.tidemusic.ui.common.SortDropdown
import com.example.tidemusic.ui.common.ThinTopBar
import com.example.tidemusic.ui.common.applySortCriteria
import com.example.tidemusic.ui.common.rememberMultiSelectState
import com.example.tidemusic.ui.rememberTideViewModel
import com.example.tidemusic.util.formatPlaylistDuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ArtistDetailViewModel(
    private val repository: LibraryRepository,
    private val playback: PlaybackController,
) : ViewModel() {

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

    fun load(artistId: Long, artistName: String) {
        viewModelScope.launch {
            repository.observeTracksForArtist(artistId, artistName).collect { _rawTracks.value = it }
        }
    }

    fun setSortCriteria(criteria: List<SortCriteria>) {
        _sortCriteria.value = criteria
    }

    fun playAt(index: Int) {
        playback.setQueue(_tracks.value, startIndex = index)
    }

    fun playAll() {
        if (_tracks.value.isNotEmpty()) {
            playback.setQueue(_tracks.value, startIndex = 0)
        }
    }

    fun shuffleAll() {
        if (_tracks.value.isNotEmpty()) {
            val shuffled = _tracks.value.shuffled(java.security.SecureRandom())
            playback.setQueue(shuffled, startIndex = 0)
            playback.setShuffleMode(true)
        }
    }
}

@Composable
fun ArtistDetailScreen(
    artistId: Long,
    artistName: String,
    onBack: () -> Unit,
    viewModel: ArtistDetailViewModel = rememberTideViewModel { ArtistDetailViewModel(it.repository, it.playbackController) },
) {
    androidx.compose.runtime.LaunchedEffect(artistId, artistName) {
        viewModel.load(artistId, artistName)
    }
    val tracks by viewModel.tracks.collectAsState()
    val sortCriteria by viewModel.sortCriteria.collectAsState()
    val multiSelect = rememberMultiSelectState()
    val playlists by ServiceLocator.repository.observePlaylists().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showSortDropdown by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ThinTopBar(
            title = artistName.takeIf { it.isNotBlank() } ?: "Artist",
            onBack = onBack,
            trailing = {
                Box {
                    IconButton(onClick = { showSortDropdown = true }) {
                        Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort")
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
                            text = "${tracks.size} ${if (tracks.size == 1) "song" else "songs"} · ${formatPlaylistDuration(totalDurationMs)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TideColors.textSecondary,
                            fontWeight = FontWeight.Medium,
                        )

                        Spacer(Modifier.height(12.dp))

                        // Playlist action header: Play All and Shuffle All
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.Button(
                                onClick = { viewModel.playAll() },
                                modifier = Modifier.weight(1f),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = TideColors.accent,
                                    contentColor = Color.Black,
                                ),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Play All",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }

                            androidx.compose.material3.FilledTonalButton(
                                onClick = { viewModel.shuffleAll() },
                                modifier = Modifier.weight(1f),
                                colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                                    containerColor = TideColors.surfaceElevated,
                                    contentColor = TideColors.textPrimary,
                                ),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Shuffle,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = TideColors.accent,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Shuffle",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(top = 14.dp),
                            color = Color.White.copy(alpha = 0.08f),
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
