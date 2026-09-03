package com.example.tidemusic.ui.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tidemusic.di.ServiceLocator
import com.example.tidemusic.domain.LibraryRepository
import com.example.tidemusic.domain.Playlist
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
import com.example.tidemusic.util.formatDuration
import com.example.tidemusic.util.formatPlaylistDuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class PlaylistDetailViewModel(
    private val repository: LibraryRepository,
    private val playback: PlaybackController,
) : ViewModel() {

    private val _rawSongs = MutableStateFlow<List<Song>>(emptyList())
    private val _sortCriteria = MutableStateFlow<List<SortCriteria>>(emptyList())
    val sortCriteria: StateFlow<List<SortCriteria>> = _sortCriteria.asStateFlow()

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private var currentPlaylist: Playlist? = null

    init {
        viewModelScope.launch {
            combine(_rawSongs, _sortCriteria) { list, criteria ->
                val filtered = list.filter { it.durationMs > 0L || it.size > 0L }
                val title = currentPlaylist?.name.orEmpty()
                val playlistSpecific = if (currentPlaylist?.isBuiltIn == true && title.equals("Not Played", ignoreCase = true)) {
                    filtered.filter { it.playCount == 0 }
                } else filtered
                playlistSpecific.applySortCriteria(criteria)
            }.collect { _songs.value = it }
        }
    }

    fun load(playlist: Playlist) {
        currentPlaylist = playlist
        val title = playlist.name
        val isLocked = playlist.isBuiltIn && (
            title.equals("Recently Added", ignoreCase = true) ||
            title.equals("Recently Played", ignoreCase = true) ||
            title.equals("Most Played", ignoreCase = true) ||
            title.equals("Not Played", ignoreCase = true)
        )

        if (isLocked) {
            _sortCriteria.value = when {
                title.equals("Recently Added", ignoreCase = true) -> listOf(SortCriteria.DATE_ADDED_DESC)
                title.equals("Recently Played", ignoreCase = true) -> listOf(SortCriteria.RECENTLY_PLAYED)
                title.equals("Most Played", ignoreCase = true) -> listOf(SortCriteria.MOST_PLAYED)
                title.equals("Not Played", ignoreCase = true) -> listOf(SortCriteria.DATE_ADDED_DESC)
                else -> emptyList()
            }
        } else {
            val saved = ServiceLocator.settingsManager.getSortCriteria("playlist_${playlist.id}")
            _sortCriteria.value = saved
        }

        viewModelScope.launch {
            repository.observeSongsForPlaylist(playlist).collect { _rawSongs.value = it }
        }
    }

    fun setSortCriteria(criteria: List<SortCriteria>) {
        _sortCriteria.value = criteria
        val pId = currentPlaylist?.id ?: -1L
        if (pId != -1L) {
            ServiceLocator.settingsManager.saveSortCriteria("playlist_$pId", criteria)
        }
    }

    fun playAt(index: Int) = playback.setQueue(_songs.value, startIndex = index)

    fun playAll() {
        if (_songs.value.isNotEmpty()) {
            playback.setQueue(_songs.value, startIndex = 0)
        }
    }

    fun shuffleAll() {
        if (_songs.value.isNotEmpty()) {
            val shuffled = _songs.value.shuffled(java.security.SecureRandom())
            playback.setQueue(shuffled, startIndex = 0)
            playback.setShuffleMode(true)
        }
    }
}

@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    title: String,
    isBuiltIn: Boolean,
    onBack: () -> Unit,
    viewModel: PlaylistDetailViewModel = rememberTideViewModel { PlaylistDetailViewModel(it.repository, it.playbackController) },
) {
    val playlist = Playlist(playlistId, title, isBuiltIn, 0L, 0)
    LaunchedEffect(playlistId) { viewModel.load(playlist) }
    val songs by viewModel.songs.collectAsState()
    val sortCriteria by viewModel.sortCriteria.collectAsState()
    val multiSelect = rememberMultiSelectState()
    val playlists by ServiceLocator.repository.observePlaylists().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showSortDropdown by remember { mutableStateOf(false) }

    val isSortLocked = isBuiltIn && (
        title.equals("Recently Added", ignoreCase = true) ||
        title.equals("Recently Played", ignoreCase = true) ||
        title.equals("Most Played", ignoreCase = true) ||
        title.equals("Not Played", ignoreCase = true)
    )

    Column(Modifier.fillMaxSize()) {
        ThinTopBar(
            title = title,
            onBack = onBack,
            trailing = if (!isSortLocked) {
                {
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
            } else null
        )
        Box(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        val totalDurationMs = remember(songs) { songs.sumOf { it.durationMs } }
                        val isMostPlayed = isBuiltIn && title.equals("Most Played", ignoreCase = true)
                        val totalPlays = remember(songs) { songs.sumOf { it.playCount } }
                        val headerText = if (isMostPlayed) {
                            "${songs.size} songs · $totalPlays total plays · ${formatPlaylistDuration(totalDurationMs)}"
                        } else {
                            "${songs.size} songs · ${formatPlaylistDuration(totalDurationMs)}"
                        }
                        Text(
                            text = headerText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TideColors.textSecondary,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        )

                        Spacer(Modifier.height(12.dp))

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
                                    contentColor = androidx.compose.ui.graphics.Color.Black,
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
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
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
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        androidx.compose.material3.HorizontalDivider(
                            modifier = Modifier.padding(top = 14.dp),
                            color = TideColors.outline,
                            thickness = 1.dp
                        )
                    }
                }
                itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                    val isMostPlayed = isBuiltIn && title.equals("Most Played", ignoreCase = true)
                    val playsText = if (song.playCount == 1) "1 play" else "${song.playCount} plays"
                    val customSubtitle = if (isMostPlayed) {
                        val artistStr = song.artist?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
                        "$artistStr • $playsText • ${formatDuration(song.durationMs)}"
                    } else null

                    SongRow(
                        song = song,
                        onClick = { viewModel.playAt(index) },
                        multiSelect = multiSelect,
                        subtitleOverride = customSubtitle,
                        trailing = if (!isBuiltIn) {
                            {
                                var menuOpen by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(onClick = { menuOpen = true }) {
                                        Icon(
                                            Icons.Rounded.MoreVert,
                                            contentDescription = "Options",
                                            tint = TideColors.textSecondary,
                                        )
                                    }
                                    com.example.tidemusic.ui.common.SongOverflowMenu(
                                        song = song,
                                        expanded = menuOpen,
                                        onDismiss = { menuOpen = false },
                                        showRemoveFromQueue = true,
                                        onRemoveFromQueue = {
                                            scope.launch {
                                                try {
                                                    ServiceLocator.repository.removeSongFromPlaylist(playlistId, song.id)
                                                } catch (_: Exception) {}
                                            }
                                        },
                                    )
                                }
                            }
                        } else null,
                    )
                }
            }
            MultiSelectActionBar(
                state = multiSelect,
                allIds = songs.map { it.id },
                playlists = playlists,
                onDelete = { ids ->
                    scope.launch {
                        try {
                            if (!isBuiltIn) {
                                ids.forEach { id ->
                                    ServiceLocator.repository.removeSongFromPlaylist(playlistId, id)
                                }
                            } else {
                                ServiceLocator.repository.deletePermanently(ids.toList())
                            }
                        } catch (_: Exception) {}
                    }
                },
                onAddToPlaylist = { pId, songIds ->
                    scope.launch {
                        try {
                            ServiceLocator.repository.addSongsToPlaylist(pId, songIds)
                        } catch (_: Exception) {}
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
