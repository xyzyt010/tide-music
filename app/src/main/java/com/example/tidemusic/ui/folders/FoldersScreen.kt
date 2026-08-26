package com.example.tidemusic.ui.folders

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tidemusic.di.ServiceLocator
import com.example.tidemusic.domain.Folder
import com.example.tidemusic.domain.LibraryRepository
import com.example.tidemusic.theme.TideColors
import com.example.tidemusic.ui.common.ThinTopBar
import com.example.tidemusic.ui.rememberTideViewModel
import com.example.tidemusic.util.formatAggregateDuration
import com.example.tidemusic.util.formatCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FoldersViewModel(
    private val repository: LibraryRepository,
) : ViewModel() {
    val roots: StateFlow<List<Folder>> = repository.observeRootFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun excludeFolder(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = ServiceLocator.settingsManager.getExcludedFolders().toMutableSet()
            current.add(path)
            ServiceLocator.settingsManager.saveExcludedFolders(current)
            repository.rescanFull()
        }
    }

    fun removeExclusion(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = ServiceLocator.settingsManager.getExcludedFolders().toMutableSet()
            current.remove(path)
            ServiceLocator.settingsManager.saveExcludedFolders(current)
            repository.rescanFull()
        }
    }

    fun rescan() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.rescanFull()
        }
    }
}

@Composable
fun FoldersScreen(
    onFolderClick: (Folder) -> Unit,
    viewModel: FoldersViewModel = rememberTideViewModel { FoldersViewModel(it.repository) },
) {
    val roots by viewModel.roots.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var showExclusionsDialog by remember { mutableStateOf(false) }
    var folderToExclude by remember { mutableStateOf<Folder?>(null) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        ThinTopBar(
            title = "Folders",
            trailing = {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Folder Settings")
                    }
                    com.example.tidemusic.ui.common.CenteredOverflowMenu(
                        visible = showMenu,
                        onDismiss = { showMenu = false },
                        title = "Folder Options",
                    ) {
                        com.example.tidemusic.ui.common.CenteredMenuItem(
                            icon = Icons.Rounded.FolderOff,
                            label = "Folder Exclusion / Inclusion",
                        ) {
                            showMenu = false
                            showExclusionsDialog = true
                        }
                        com.example.tidemusic.ui.common.CenteredMenuItem(
                            icon = Icons.Rounded.Refresh,
                            label = "Rescan Folders",
                        ) {
                            showMenu = false
                            viewModel.rescan()
                        }
                    }
                }
            }
        )

        LazyColumn(Modifier.fillMaxSize().weight(1f).padding(top = 8.dp)) {
            items(roots, key = { it.id }) { folder ->
                FolderRow(
                    folder = folder,
                    onClick = { onFolderClick(folder) },
                    onLongClick = { folderToExclude = folder }
                )
            }
        }
    }

    // Long press exclude confirmation
    folderToExclude?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderToExclude = null },
            title = { Text("Exclude Folder") },
            text = { Text("Do you want to exclude '${folder.path}' from music scanning?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.excludeFolder(folder.path)
                    folderToExclude = null
                }) {
                    Text("Exclude", color = TideColors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToExclude = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Exclusion Management Dialog
    if (showExclusionsDialog) {
        val excludedFolders = remember { ServiceLocator.settingsManager.getExcludedFolders().toList() }
        var customPath by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showExclusionsDialog = false },
            title = { Text("Folder Exclusion & Inclusion") },
            text = {
                Column {
                    Text(
                        "Excluded Folders:",
                        style = MaterialTheme.typography.titleSmall,
                        color = TideColors.textPrimary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    if (excludedFolders.isEmpty()) {
                        Text(
                            "No folders excluded.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TideColors.textSecondary
                        )
                    } else {
                        excludedFolders.forEach { path ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = path.substringAfterLast('/'),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TideColors.textPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = {
                                    viewModel.removeExclusion(path)
                                    showExclusionsDialog = false
                                }) {
                                    Text("Include Back", color = TideColors.accent)
                                }
                            }
                        }
                    }
                    
                    androidx.compose.material3.HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.1f)
                    )

                    Text(
                        "Exclude Custom Folder Path:",
                        style = MaterialTheme.typography.titleSmall,
                        color = TideColors.textPrimary
                    )
                    OutlinedTextField(
                        value = customPath,
                        onValueChange = { customPath = it },
                        placeholder = { Text("/storage/emulated/0/WhatsApp") },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (customPath.isNotBlank()) {
                        viewModel.excludeFolder(customPath.trim())
                    }
                    showExclusionsDialog = false
                }) {
                    Text("Save & Rescan", color = TideColors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExclusionsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderRow(
    folder: Folder,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { onLongClick?.invoke() }
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = null,
                tint = TideColors.accent,
                modifier = Modifier.padding(end = 16.dp).size(32.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.path.substringAfterLast('/').ifBlank { folder.path },
                    style = MaterialTheme.typography.bodyLarge,
                    color = TideColors.textPrimary,
                    modifier = Modifier.basicMarquee(),
                    maxLines = 1
                )
                Text(
                    text = "${formatCount(folder.songCount)} songs",
                    style = MaterialTheme.typography.bodySmall,
                    color = TideColors.textSecondary,
                    maxLines = 1
                )
            }
        }
        Text(
            text = formatAggregateDuration(folder.totalDurationMs),
            style = MaterialTheme.typography.bodySmall,
            color = TideColors.textSecondary,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}
