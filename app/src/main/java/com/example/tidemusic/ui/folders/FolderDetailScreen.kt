package com.example.tidemusic.ui.folders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.basicMarquee
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tidemusic.di.ServiceLocator
import com.example.tidemusic.domain.Folder
import com.example.tidemusic.domain.LibraryRepository
import com.example.tidemusic.domain.Song
import com.example.tidemusic.playback.PlaybackController
import com.example.tidemusic.theme.TideColors
import com.example.tidemusic.ui.common.MultiSelectActionBar
import com.example.tidemusic.ui.common.SongRow
import com.example.tidemusic.ui.common.ThinTopBar
import com.example.tidemusic.ui.common.rememberMultiSelectState
import com.example.tidemusic.ui.rememberTideViewModel
import com.example.tidemusic.util.formatAggregateDuration
import com.example.tidemusic.util.formatCount
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

class FolderDetailViewModel(
    private val repository: LibraryRepository,
    private val playback: PlaybackController,
) : ViewModel() {

    private val _folder = MutableStateFlow<Folder?>(null)
    val folder: StateFlow<Folder?> = _folder.asStateFlow()

    private val _children = MutableStateFlow<List<Folder>>(emptyList())
    val children: StateFlow<List<Folder>> = _children

    private val _rawSongs = MutableStateFlow<List<Song>>(emptyList())
    private val _sortCriteria = MutableStateFlow<List<SortCriteria>>(emptyList())
    val sortCriteria: StateFlow<List<SortCriteria>> = _sortCriteria.asStateFlow()

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    init {
        viewModelScope.launch {
            combine(_rawSongs, _sortCriteria) { list, criteria ->
                list.applySortCriteria(criteria)
            }.collect { _songs.value = it }
        }
    }

    fun load(folderId: Long) {
        viewModelScope.launch {
            launch { repository.observeFolderChildren(folderId).collect { _children.value = it } }
            launch { repository.observeSongsInFolder(folderId).collect { _rawSongs.value = it } }
            launch {
                repository.observeAllFolders().collect { all ->
                    _folder.value = all.firstOrNull { it.id == folderId }
                }
            }
        }
    }

    fun setSortCriteria(criteria: List<SortCriteria>) {
        _sortCriteria.value = criteria
    }

    fun playAllFromHere(index: Int) = playback.setQueue(_songs.value, startIndex = index)
}

@Composable
fun FolderDetailScreen(
    folderId: Long,
    path: String,
    onBack: () -> Unit,
    onFolderClick: (Folder) -> Unit,
    viewModel: FolderDetailViewModel = rememberTideViewModel { FolderDetailViewModel(it.repository, it.playbackController) },
) {
    LaunchedEffect(folderId) { viewModel.load(folderId) }
    val folder by viewModel.folder.collectAsState()
    val children by viewModel.children.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val sortCriteria by viewModel.sortCriteria.collectAsState()
    val multiSelect = rememberMultiSelectState()
    val playlists by ServiceLocator.repository.observePlaylists().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showSortDropdown by remember { mutableStateOf(false) }

    val folderName = folder?.path?.substringAfterLast('/') ?: path.substringAfterLast('/')
    Column(Modifier.fillMaxSize()) {
        ThinTopBar(
            title = folderName.ifBlank { "Folder" },
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
        val listState = com.example.tidemusic.ui.common.rememberScrollMemoryState("folder_$folderId", children.size + songs.size)
        Box(Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                // Header — sticky-style top section.
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            text = folder?.path ?: path,
                            style = MaterialTheme.typography.bodySmall,
                            color = TideColors.textSecondary,
                            modifier = Modifier.basicMarquee(),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${formatCount(folder?.subfolderCount ?: 0)} folders · " +
                                "${formatCount(folder?.songCount ?: 0)} songs · " +
                                formatAggregateDuration(folder?.totalDurationMs ?: 0L),
                            style = MaterialTheme.typography.bodySmall,
                            color = TideColors.textSecondary,
                            fontWeight = FontWeight.Bold,
                        )
                        androidx.compose.material3.HorizontalDivider(
                            modifier = Modifier.padding(top = 12.dp),
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.08f),
                            thickness = 1.dp
                        )
                    }
                }
                items(children, key = { it.id }) { child ->
                    FolderRow(folder = child, onClick = { onFolderClick(child) })
                }
                items(songs, key = { it.id }) { song ->
                    val index = songs.indexOf(song)
                    SongRow(
                        song = song,
                        onClick = { viewModel.playAllFromHere(index) },
                        multiSelect = multiSelect,
                        titleOverride = song.filePath.substringAfterLast('/'),
                    )
                }
            }
            MultiSelectActionBar(
                state = multiSelect,
                allIds = songs.map { it.id },
                playlists = playlists,
                onDelete = { ids -> scope.launch { ServiceLocator.repository.deletePermanently(ids.toList()) } },
                onAddToPlaylist = { playlistId, songIds -> scope.launch { ServiceLocator.repository.addSongsToPlaylist(playlistId, songIds) } },
                modifier = Modifier.align(Alignment.BottomCenter),
                onRename = { id, newName -> scope.launch { ServiceLocator.repository.renameFile(id, newName) } }
            )
        }
    }
}
