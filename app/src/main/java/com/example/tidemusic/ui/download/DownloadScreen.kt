package com.example.tidemusic.ui.download

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tidemusic.theme.TideColors
import com.example.tidemusic.ui.common.CenteredMenuDivider
import com.example.tidemusic.ui.common.CenteredMenuItem
import com.example.tidemusic.ui.common.CenteredOverflowMenu
import com.example.tidemusic.ui.common.MultiSelectActionBar
import com.example.tidemusic.ui.common.SongOverflowMenu
import com.example.tidemusic.ui.common.SongRow
import com.example.tidemusic.ui.common.ThinTopBar
import com.example.tidemusic.ui.common.rememberMultiSelectState
import com.example.tidemusic.ui.rememberTideViewModel
import java.io.File

/**
 * Download screen (spec Section 6.6 + 8.6).
 *
 * Shows active download tasks, quality & engine settings, and songs from the auto-created
 * "yt-dlp" playlist with long-click multi-selection.
 */
@Composable
fun DownloadScreen(viewModel: DownloadViewModel = rememberTideViewModel {
    DownloadViewModel(it.repository, it.downloadManager, it.playbackController)
}) {
    val url by viewModel.url.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val activeTasks by viewModel.activeTasks.collectAsState()
    val ytDlpSongs by viewModel.ytDlpSongs.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val networkStatus by viewModel.networkStatusMessage.collectAsState()
    val downloadWithLyrics by viewModel.downloadWithLyrics.collectAsState()
    val qualityKbps by viewModel.qualityKbps.collectAsState()
    val engine by viewModel.engine.collectAsState()
    val clipboard = LocalClipboardManager.current

    var showSettingsMenu by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showEngineDialog by remember { mutableStateOf(false) }

    val multiSelect = rememberMultiSelectState()

    if (multiSelect.active) {
        BackHandler {
            multiSelect.clear()
        }
    }

    Column(Modifier.fillMaxSize().background(TideColors.background)) {
        if (multiSelect.active) {
            ThinTopBar(
                title = "${multiSelect.count} Selected",
                onBack = { multiSelect.clear() },
                trailing = {
                    IconButton(onClick = { multiSelect.selectAll(ytDlpSongs.map { it.id }) }) {
                        Icon(Icons.Rounded.SelectAll, "Select all", tint = TideColors.textPrimary)
                    }
                },
            )
        } else {
            ThinTopBar(
                title = "Downloads",
                trailing = {
                    Box {
                        IconButton(onClick = { showSettingsMenu = true }) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "Download Settings",
                                tint = TideColors.textPrimary,
                            )
                        }
                        CenteredOverflowMenu(
                            visible = showSettingsMenu,
                            onDismiss = { showSettingsMenu = false },
                            title = "Download Settings",
                        ) {
                            CenteredMenuItem(
                                icon = Icons.Rounded.HighQuality,
                                label = "Audio Quality: $qualityKbps kbps" + if (qualityKbps == 320) " (Recommended)" else "",
                            ) {
                                showSettingsMenu = false
                                showQualityDialog = true
                            }
                            CenteredMenuItem(
                                icon = Icons.Rounded.Tune,
                                label = "Engine: $engine",
                            ) {
                                showSettingsMenu = false
                                showEngineDialog = true
                            }
                            CenteredMenuItem(
                                icon = if (downloadWithLyrics) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                label = if (downloadWithLyrics) "Auto-download Synced Lyrics: ON" else "Auto-download Synced Lyrics: OFF",
                            ) {
                                viewModel.setDownloadWithLyrics(!downloadWithLyrics)
                            }
                            CenteredMenuItem(
                                icon = Icons.Rounded.Refresh,
                                label = "Rescan Library for Downloads",
                            ) {
                                showSettingsMenu = false
                                viewModel.rescanLibrary()
                            }
                            if (tasks.isNotEmpty()) {
                                CenteredMenuItem(
                                    icon = Icons.Rounded.DeleteSweep,
                                    label = "Clear Completed / Failed Tasks",
                                ) {
                                    showSettingsMenu = false
                                    viewModel.clearAll()
                                }
                            }
                            CenteredMenuDivider()
                            CenteredMenuItem(
                                icon = Icons.Rounded.Info,
                                label = "Storage: Music/TideMusic",
                            ) {
                                showSettingsMenu = false
                            }
                        }
                    }
                },
            )
        }

        // Quality selection dialog
        CenteredOverflowMenu(
            visible = showQualityDialog,
            onDismiss = { showQualityDialog = false },
            title = "Select Audio Quality",
        ) {
            val qualities = listOf(
                320 to "High (320 kbps) — Recommended",
                192 to "Medium (192 kbps)",
                128 to "Standard (128 kbps)",
            )
            qualities.forEach { (kbps, label) ->
                CenteredMenuItem(
                    icon = if (qualityKbps == kbps) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                    label = label,
                ) {
                    viewModel.setQualityKbps(kbps)
                }
            }

        }

        // Engine selection dialog
        CenteredOverflowMenu(
            visible = showEngineDialog,
            onDismiss = { showEngineDialog = false },
            title = "Select Downloader Engine",
        ) {
            val engines = listOf(
                "yt-dlp" to "yt-dlp (Recommended / Full features)",
            )
            engines.forEach { (engId, label) ->
                CenteredMenuItem(
                    icon = if (engine == engId) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                    label = label,
                ) {
                    viewModel.setEngine(engId)
                }
            }
        }

        Column(Modifier.fillMaxSize().weight(1f).padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = url,
                    onValueChange = viewModel::setUrl,
                    placeholder = { Text("Paste a URL to download") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                IconButton(onClick = {
                    clipboard.getText()?.let { viewModel.setUrl(it.text) }
                }) { Icon(Icons.Rounded.ContentPaste, null) }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clickable { viewModel.setDownloadWithLyrics(!downloadWithLyrics) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = downloadWithLyrics,
                    onCheckedChange = { viewModel.setDownloadWithLyrics(it) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = TideColors.accent,
                        checkmarkColor = Color.Black,
                    ),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Download with synchronized lyrics (.lrc)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TideColors.textPrimary,
                )
            }

            networkStatus?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = TideColors.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = viewModel::enqueueDownload,
                    enabled = viewModel.canDownload,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TideColors.accent,
                        contentColor = TideColors.textPrimary,
                    ),
                ) {
                    Icon(Icons.Rounded.Download, null)
                    Text("Download")
                }
                if (tasks.isNotEmpty()) {
                    TextButton(onClick = viewModel::clearAll) {
                        Text("Clear", color = TideColors.accent)
                    }
                }
            }

            LazyColumn(Modifier.fillMaxSize().padding(top = 16.dp)) {
                if (activeTasks.isNotEmpty()) {
                    item {
                        Text(
                            text = "Active Downloads",
                            style = MaterialTheme.typography.titleMedium,
                            color = TideColors.textPrimary,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    items(activeTasks, key = { it.id }) { task ->
                        val isDone = task.status.startsWith("Done")
                        val isFailed = task.status.startsWith("Failed")

                        val statusColor = when {
                            isFailed -> TideColors.error
                            task.status.contains("Synced", true) -> Color(0xFF4CAF50)
                            task.status.contains("No Lyrics", true) -> Color(0xFFFFA726)
                            task.status.contains("Audio Only", true) -> TideColors.accent
                            isDone -> Color(0xFF4CAF50)
                            else -> TideColors.textSecondary
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(TideColors.surfaceElevated.copy(alpha = 0.5f))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = null,
                                    tint = statusColor,
                                    modifier = Modifier.padding(end = 10.dp).size(20.dp),
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = task.sourceUrl,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TideColors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(statusColor.copy(alpha = 0.15f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        ) {
                                            Text(
                                                text = task.status,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                                color = statusColor,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                    }
                                }
                                if (!isDone && !isFailed) {
                                    Text(
                                        text = "${task.progress}%",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TideColors.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.removeTask(task.id) },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Remove task",
                                        tint = TideColors.textSecondary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                            if (!isDone && !isFailed) {
                                LinearProgressIndicator(
                                    progress = { task.progress / 100f },
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    color = TideColors.accent,
                                    trackColor = TideColors.outline,
                                )
                            }
                        }
                    }
                }

                // Download playlist songs
                item {
                    Text(
                        text = "Downloaded Songs",
                        style = MaterialTheme.typography.titleMedium,
                        color = TideColors.textPrimary,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                    Text(
                        text = if (ytDlpSongs.isEmpty()) {
                            "No downloaded songs yet"
                        } else {
                            "${ytDlpSongs.size} song${if (ytDlpSongs.size == 1) "" else "s"}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TideColors.textSecondary,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }

                itemsIndexed(ytDlpSongs, key = { _, s -> s.id }) { index, song ->
                    var menuExpanded by remember { mutableStateOf(false) }
                    val hasLrc = remember(song.filePath) {
                        try {
                            if (song.filePath.isNotBlank()) {
                                val audio = File(song.filePath)
                                val lrc = File(audio.parentFile, "${audio.nameWithoutExtension}.lrc")
                                lrc.exists() && lrc.length() > 0L
                            } else false
                        } catch (_: Exception) { false }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            SongRow(
                                song = song,
                                onClick = {
                                    if (multiSelect.active) {
                                        multiSelect.toggle(song.id)
                                    } else {
                                        viewModel.playSongAt(index, ytDlpSongs)
                                    }
                                },
                                multiSelect = multiSelect,
                            )
                        }
                        if (hasLrc && !multiSelect.active) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF4CAF50).copy(alpha = 0.18f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = "LRC",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = Color(0xFF4CAF50),
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        if (!multiSelect.active) {
                            Box {
                                IconButton(onClick = { menuExpanded = true }) {
                                    Icon(
                                        imageVector = Icons.Rounded.MoreVert,
                                        contentDescription = "Options",
                                        tint = TideColors.textSecondary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                SongOverflowMenu(
                                    song = song,
                                    expanded = menuExpanded,
                                    onDismiss = { menuExpanded = false },
                                    showRemoveFromQueue = true,
                                    onRemoveFromQueue = {
                                        viewModel.removeFromYtDlpPlaylist(song.id)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        MultiSelectActionBar(
            state = multiSelect,
            allIds = ytDlpSongs.map { it.id },
            playlists = playlists,
            onDelete = { ids ->
                viewModel.deleteMultipleSongsPermanently(ids)
                multiSelect.clear()
            },
            onAddToPlaylist = { playlistId, songIds ->
                viewModel.addSongsToPlaylist(playlistId, songIds)
                multiSelect.clear()
            },
        )
    }
}
