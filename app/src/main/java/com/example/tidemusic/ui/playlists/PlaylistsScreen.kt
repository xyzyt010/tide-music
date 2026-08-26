package com.example.tidemusic.ui.playlists

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FiberNew
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tidemusic.domain.BuiltInPlaylists
import com.example.tidemusic.domain.LibraryRepository
import com.example.tidemusic.domain.Playlist
import com.example.tidemusic.theme.TideColors
import com.example.tidemusic.ui.rememberTideViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlaylistsViewModel(
    private val repository: LibraryRepository,
) : ViewModel() {
    val playlists: StateFlow<List<Playlist>> = repository.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            try { repository.createUserPlaylist(name) } catch (_: Exception) {}
        }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch {
            try { repository.deletePlaylist(id) } catch (_: Exception) {}
        }
    }

    fun renamePlaylist(id: Long, name: String) {
        viewModelScope.launch {
            try { repository.renamePlaylist(id, name) } catch (_: Exception) {}
        }
    }
}

private fun builtInIcon(name: String): ImageVector = when (name) {
    "All Songs" -> Icons.Rounded.MusicNote
    "Favorites" -> Icons.Rounded.Favorite
    "Recently Added" -> Icons.Rounded.FiberNew
    "Recently Played" -> Icons.Rounded.PlayCircle
    "Most Played" -> Icons.Rounded.Star
    "Not Played" -> Icons.Rounded.Notifications
    BuiltInPlaylists.YT_DLP -> Icons.Rounded.Download
    else -> Icons.Rounded.List
}

private fun builtInDescription(name: String): String = when (name) {
    "All Songs" -> "Every indexed track"
    "Favorites" -> "Songs you've favorited"
    "Recently Added" -> "Newest additions"
    "Recently Played" -> "Playback history"
    "Most Played" -> "By play count"
    "Not Played" -> "Never played"
    "Download", BuiltInPlaylists.YT_DLP -> "Downloaded tracks"
    else -> ""
}

@Composable
fun PlaylistsScreen(
    onPlaylistClick: (Playlist) -> Unit,
    viewModel: PlaylistsViewModel = rememberTideViewModel { PlaylistsViewModel(it.repository) },
) {
    val playlists by viewModel.playlists.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var playlistPendingDelete by remember { mutableStateOf<Playlist?>(null) }
    var playlistPendingRename by remember { mutableStateOf<Playlist?>(null) }
    var renameText by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(TideColors.background)) {
        com.example.tidemusic.ui.common.ThinTopBar(title = "Playlists")
        Box(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize().padding(vertical = 8.dp)) {
                item {
                    Text(
                        text = "Built-in Playlists",
                        style = MaterialTheme.typography.titleSmall,
                        color = TideColors.textSecondary,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
                    )
                }

                val builtIns = playlists.filter { it.isBuiltIn }
                items(builtIns, key = { it.id }) { playlist ->
                    PlaylistRow(
                        playlist = playlist,
                        icon = builtInIcon(playlist.name),
                        description = builtInDescription(playlist.name),
                        onClick = { onPlaylistClick(playlist) },
                        isBuiltIn = true,
                    )
                }

                val userPlaylists = playlists.filter { !it.isBuiltIn }
                if (userPlaylists.isNotEmpty()) {
                    item {
                        Text(
                            text = "My Playlists",
                            style = MaterialTheme.typography.titleSmall,
                            color = TideColors.textSecondary,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
                        )
                    }
                    items(userPlaylists, key = { it.id }) { playlist ->
                        PlaylistRow(
                            playlist = playlist,
                            icon = if (playlist.name == BuiltInPlaylists.YT_DLP) {
                                Icons.Rounded.Download
                            } else {
                                Icons.Rounded.QueueMusic
                            },
                            description = if (playlist.name == BuiltInPlaylists.YT_DLP) {
                                "Downloaded with yt-dlp · can be deleted"
                            } else {
                                "Custom playlist"
                            },
                            onClick = { onPlaylistClick(playlist) },
                            isBuiltIn = false,
                            onDelete = { playlistPendingDelete = playlist },
                            onRename = {
                                renameText = playlist.name
                                playlistPendingRename = playlist
                            },
                        )
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }

            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = TideColors.accent,
                contentColor = TideColors.textPrimary,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Create Playlist")
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false; newPlaylistName = "" },
            title = { Text("Create Playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = { Text("Playlist name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            viewModel.createPlaylist(newPlaylistName.trim())
                            newPlaylistName = ""
                            showCreateDialog = false
                        }
                    },
                    enabled = newPlaylistName.isNotBlank(),
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false; newPlaylistName = "" }) {
                    Text("Cancel")
                }
            },
            containerColor = TideColors.surfaceElevated,
        )
    }

    playlistPendingDelete?.let { pl ->
        AlertDialog(
            onDismissRequest = { playlistPendingDelete = null },
            title = { Text("Delete playlist?") },
            text = {
                Text(
                    "Delete \"${pl.name}\"? Songs stay in your library; only the playlist is removed.",
                    color = TideColors.textSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePlaylist(pl.id)
                        playlistPendingDelete = null
                    },
                ) { Text("Delete", color = TideColors.error) }
            },
            dismissButton = {
                TextButton(onClick = { playlistPendingDelete = null }) { Text("Cancel") }
            },
            containerColor = TideColors.surfaceElevated,
        )
    }

    playlistPendingRename?.let { pl ->
        AlertDialog(
            onDismissRequest = { playlistPendingRename = null },
            title = { Text("Rename playlist") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            viewModel.renamePlaylist(pl.id, renameText.trim())
                        }
                        playlistPendingRename = null
                    },
                    enabled = renameText.isNotBlank(),
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { playlistPendingRename = null }) { Text("Cancel") }
            },
            containerColor = TideColors.surfaceElevated,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistRow(
    playlist: Playlist,
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    isBuiltIn: Boolean,
    onDelete: (() -> Unit)? = null,
    onRename: (() -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    if (!isBuiltIn) menuOpen = true
                },
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isBuiltIn || playlist.name == BuiltInPlaylists.YT_DLP) {
                TideColors.accent
            } else {
                TideColors.textPrimary
            },
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .padding(4.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                color = TideColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TideColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!isBuiltIn) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = "Playlist options",
                        tint = TideColors.textSecondary,
                    )
                }
                com.example.tidemusic.ui.common.CenteredOverflowMenu(
                    visible = menuOpen,
                    onDismiss = { menuOpen = false },
                    title = playlist.name,
                ) {
                    com.example.tidemusic.ui.common.CenteredMenuItem(
                        icon = Icons.Rounded.Edit,
                        label = "Rename",
                    ) {
                        menuOpen = false
                        onRename?.invoke()
                    }
                    com.example.tidemusic.ui.common.CenteredMenuItem(
                        icon = Icons.Rounded.Delete,
                        label = "Delete",
                        destructive = true,
                    ) {
                        menuOpen = false
                        onDelete?.invoke()
                    }
                }
            }
        } else {
            Icon(
                imageVector = Icons.Rounded.QueueMusic,
                contentDescription = null,
                tint = TideColors.textSecondary,
                modifier = Modifier.size(20.dp).padding(end = 8.dp),
            )
        }
    }
}
