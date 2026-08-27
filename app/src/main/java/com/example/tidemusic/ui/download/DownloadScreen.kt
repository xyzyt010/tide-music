package com.example.tidemusic.ui.download

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import com.example.tidemusic.theme.TideColors
import com.example.tidemusic.ui.common.CenteredMenuItem
import com.example.tidemusic.ui.common.CenteredMenuDivider
import com.example.tidemusic.ui.common.CenteredOverflowMenu
import com.example.tidemusic.ui.common.SongRow
import com.example.tidemusic.ui.rememberTideViewModel

/**
 * Download screen (spec Section 6.6 + 8.6).
 *
 * Shows active download tasks and songs from the auto-created "yt-dlp" playlist.
 * Songs can be removed from the playlist or deleted permanently.
 */
@Composable
fun DownloadScreen(viewModel: DownloadViewModel = rememberTideViewModel {
    DownloadViewModel(it.repository, it.downloadManager, it.playbackController)
}) {
    val url by viewModel.url.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val activeTasks by viewModel.activeTasks.collectAsState()
    val ytDlpSongs by viewModel.ytDlpSongs.collectAsState()
    val networkStatus by viewModel.networkStatusMessage.collectAsState()
    val downloadWithLyrics by viewModel.downloadWithLyrics.collectAsState()
    val clipboard = LocalClipboardManager.current
    var showSettingsMenu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(TideColors.background)) {
        com.example.tidemusic.ui.common.ThinTopBar(
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
                            icon = if (downloadWithLyrics) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                            label = if (downloadWithLyrics) "Auto-download Synced Lyrics (LRC)" else "Download Lyrics: Disabled",
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
                                label = "Clear Download Tasks History",
                            ) {
                                showSettingsMenu = false
                                viewModel.clearAll()
                            }
                        }
                        CenteredMenuDivider()
                        CenteredMenuItem(
                            icon = Icons.Rounded.Info,
                            label = "Format: Audio (yt-dlp high quality)",
                        ) {
                            showSettingsMenu = false
                        }
                    }
                }
            }
        )
        Column(Modifier.fillMaxSize().weight(1f).padding(16.dp)) {
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
                    androidx.compose.material3.TextButton(onClick = viewModel::clearAll) {
                        Text("Clear tasks", color = TideColors.accent)
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
                                .padding(horizontal = 10.dp, vertical = 8.dp)
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
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
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
                                // Dismiss this task row (removes it from the list; for
                                // finished/failed rows it clears history, for queued rows it
                                // stops tracking).
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
                            "No downloaded songs yet. Downloads are added to the Download playlist."
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
                                onClick = { viewModel.playSongAt(index, ytDlpSongs) },
                            )
                        }
                        if (hasLrc) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF4CAF50).copy(alpha = 0.18f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "LRC",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = Color(0xFF4CAF50),
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = "Options",
                                    tint = TideColors.textSecondary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            com.example.tidemusic.ui.common.SongOverflowMenu(
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
}
