package com.example.tidemusic.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.tidemusic.domain.Playlist
import com.example.tidemusic.theme.TideColors

/**
 * State holder for multi-select mode in song lists.
 * Call [toggle] on long-press to enter selection mode and select the first item.
 * Call [toggle] on tap (while active) to toggle a single item.
 * Call [clear] to exit selection mode.
 */
@Stable
class MultiSelectState {
    var active by mutableStateOf(false)
        private set
    var selectedIds by mutableStateOf(emptySet<Long>())
        private set

    val count get() = selectedIds.size

    fun toggle(id: Long) {
        if (!active) {
            active = true
            selectedIds = setOf(id)
        } else {
            selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
            if (selectedIds.isEmpty()) active = false
        }
    }

    fun selectAll(ids: Collection<Long>) {
        selectedIds = ids.toSet()
        if (selectedIds.isNotEmpty()) active = true
    }

    fun clear() {
        active = false
        selectedIds = emptySet()
    }

    fun isSelected(id: Long) = id in selectedIds
}

@Composable
fun rememberMultiSelectState(): MultiSelectState = remember { MultiSelectState() }

/**
 * Floating action bar shown at the bottom of a song list when multi-select is active.
 * Provides: select-all, deselect (close), add to playlist, and delete buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiSelectActionBar(
    state: MultiSelectState,
    allIds: List<Long>,
    playlists: List<Playlist>,
    onDelete: (Set<Long>) -> Unit,
    onAddToPlaylist: (playlistId: Long, songIds: List<Long>) -> Unit,
    modifier: Modifier = Modifier,
    onRename: ((Long, String) -> Unit)? = null,
) {
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }

    AnimatedVisibility(
        visible = state.active,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(TideColors.surfaceElevated)
                .padding(4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
            // Close / deselect all
            IconButton(onClick = { state.clear() }) {
                Icon(Icons.Rounded.Close, "Deselect all", tint = TideColors.textPrimary)
            }
            Text(
                "${state.count} selected",
                style = MaterialTheme.typography.titleSmall,
                color = TideColors.textPrimary,
            )
            // Rename (only if 1 selected)
            if (state.count == 1 && onRename != null) {
                IconButton(onClick = { 
                    renameText = ""
                    showRenameDialog = true 
                }) {
                    Icon(Icons.Rounded.Edit, "Rename", tint = TideColors.textPrimary)
                }
            } else {
                // Select all
                IconButton(onClick = { state.selectAll(allIds) }) {
                    Icon(Icons.Rounded.SelectAll, "Select all", tint = TideColors.textPrimary)
                }
            }
            // Add to playlist
            IconButton(onClick = { showPlaylistPicker = true }) {
                Icon(Icons.Rounded.PlaylistAdd, "Add to playlist", tint = TideColors.accent)
            }
            // Delete
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(Icons.Rounded.Delete, "Delete selected", tint = TideColors.error)
            }
        }
        }
    }

    // Rename dialog
    if (showRenameDialog) {
        BasicAlertDialog(
            onDismissRequest = { showRenameDialog = false },
            content = {
                Column(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(TideColors.surfaceElevated)
                        .padding(24.dp)
                        .width(300.dp)
                ) {
                    Text(
                        "Rename file",
                        style = MaterialTheme.typography.titleMedium,
                        color = TideColors.textPrimary,
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        singleLine = true,
                        placeholder = { Text("New file name (e.g. song.mp3)") }
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showRenameDialog = false }) {
                            Text("Cancel", color = TideColors.accent)
                        }
                        TextButton(onClick = {
                            if (renameText.isNotBlank()) {
                                onRename?.invoke(state.selectedIds.first(), renameText)
                            }
                            showRenameDialog = false
                            state.clear()
                        }) {
                            Text("Rename", color = TideColors.accent)
                        }
                    }
                }
            },
        )
    }

    // Playlist picker dialog
    if (showPlaylistPicker) {
        BasicAlertDialog(
            onDismissRequest = { showPlaylistPicker = false },
            content = {
                Column(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(TideColors.surfaceElevated)
                        .padding(24.dp)
                        .width(300.dp)
                ) {
                    Text(
                        "Add ${state.count} song${if (state.count != 1) "s" else ""} to…",
                        style = MaterialTheme.typography.titleMedium,
                        color = TideColors.textPrimary,
                    )
                    val userPlaylists = playlists.filterNot { it.isBuiltIn }
                    if (userPlaylists.isEmpty()) {
                        Text(
                            "No custom playlists yet. Create one from the Playlists tab.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TideColors.textSecondary,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    } else {
                        LazyColumn(Modifier.padding(top = 12.dp)) {
                            items(userPlaylists) { playlist ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onAddToPlaylist(playlist.id, state.selectedIds.toList())
                                            showPlaylistPicker = false
                                            state.clear()
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        playlist.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = TideColors.textPrimary,
                                    )
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showPlaylistPicker = false }) {
                            Text("Cancel", color = TideColors.accent)
                        }
                    }
                }
            },
        )
    }

    // Delete confirmation dialog
    if (showDeleteConfirm) {
        BasicAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            content = {
                Column(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(TideColors.surfaceElevated)
                        .padding(24.dp)
                        .width(300.dp)
                ) {
                    Text(
                        "Delete ${state.count} song${if (state.count != 1) "s" else ""}?",
                        style = MaterialTheme.typography.titleMedium,
                        color = TideColors.textPrimary,
                    )
                    Text(
                        "This will permanently remove the selected files from your device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TideColors.textSecondary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showDeleteConfirm = false }) {
                            Text("Cancel", color = TideColors.accent)
                        }
                        TextButton(onClick = {
                            onDelete(state.selectedIds)
                            showDeleteConfirm = false
                            state.clear()
                        }) {
                            Text("Delete", color = TideColors.error)
                        }
                    }
                }
            },
        )
    }
}
