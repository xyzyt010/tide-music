package com.example.tidemusic.ui.common

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Queue
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.tidemusic.di.ServiceLocator
import com.example.tidemusic.domain.Song
import com.example.tidemusic.theme.TideColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.os.Environment

/**
 * Overflow menu matching Musicolet-style queue/track options (reference screenshots).
 * Opens as a centered, scrollable card with dimmed background.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongOverflowMenu(
    song: Song,
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    showRemoveFromQueue: Boolean = false,
    onRemoveFromQueue: (() -> Unit)? = null,
    onPlayAfterCurrent: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onShowInfo: (() -> Unit)? = null,
    onEditTags: (() -> Unit)? = null,
    onSpeedPitch: (() -> Unit)? = null,
    onDeleted: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mediaController = com.example.tidemusic.ui.LocalMediaController.current
    var isFavorite by remember(song.id, song.isFavorite) { mutableStateOf(song.isFavorite) }
    var showTagDialog by remember { mutableStateOf(false) }

    var showInfoSheet by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    if (showTagDialog) {
        com.example.tidemusic.ui.player.TagEditorDialog(
            song = song,
            onDismiss = { showTagDialog = false },
            onSave = { title, artist, album ->
                scope.launch {
                    try {
                        ServiceLocator.repository.updateTags(song.id, title, artist, album)
                        com.example.tidemusic.util.FileTagWriter.writeTags(context, song, title, artist, album)
                        toast(context, "Tags updated")
                    } catch (e: Exception) {
                        toast(context, "Could not update tags")
                    }
                    showTagDialog = false
                }
            },
        )
    }

    if (showInfoSheet) {
        val infoSheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showInfoSheet = false },
            sheetState = infoSheetState,
            containerColor = TideColors.surfaceElevated,
        ) {
            com.example.tidemusic.ui.player.SongInfoContent(
                song = song,
                playbackDurationMs = song.durationMs,
                onDone = { showInfoSheet = false },
                onEditTags = {
                    showInfoSheet = false
                    showTagDialog = true
                },
            )
        }
    }

    if (showPlaylistDialog) {
        val playlists by ServiceLocator.repository.observePlaylists().collectAsState(initial = emptyList())
        com.example.tidemusic.ui.player.AddToPlaylistDialog(
            song = song,
            playlists = playlists.filterNot { it.isBuiltIn },
            onDismiss = { showPlaylistDialog = false },
            onAdd = { selectedIds ->
                scope.launch {
                    selectedIds.forEach { id -> ServiceLocator.repository.addSongsToPlaylist(id, listOf(song.id)) }
                    showPlaylistDialog = false
                    toast(context, "Added to playlist(s)")
                }
            },
            onCreateNew = { name ->
                scope.launch {
                    val newId = ServiceLocator.repository.createUserPlaylist(name)
                    ServiceLocator.repository.addSongsToPlaylist(newId, listOf(song.id))
                    showPlaylistDialog = false
                    toast(context, "Playlist created")
                }
            },
        )
    }

    if (showDeleteConfirmDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Permanently?", color = TideColors.textPrimary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to delete \"${song.title}\"? This will permanently remove the audio file and wipe all playlist/queue references.",
                    color = TideColors.textSecondary,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        scope.launch {
                            try {
                                ServiceLocator.repository.deletePermanently(listOf(song.id))
                                onDeleted?.invoke()
                                toast(context, "Deleted permanently")
                            } catch (e: Exception) {
                                toast(context, "Delete failed: ${e.message ?: "error"}")
                            }
                        }
                    }
                ) {
                    Text("DELETE", color = TideColors.error, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("CANCEL", color = TideColors.textSecondary)
                }
            },
            containerColor = TideColors.surfaceElevated,
        )
    }

    val lrcPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val lrcText = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                    if (!lrcText.isNullOrBlank()) {
                        val audioFile = File(song.filePath)
                        val targetLrc = if (audioFile.exists() && audioFile.parentFile != null && audioFile.parentFile.canWrite()) {
                            File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.lrc")
                        } else {
                            val tideDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "TideMusic")
                            if (!tideDir.exists()) tideDir.mkdirs()
                            File(tideDir, "${song.title.ifBlank { "song" }}.lrc")
                        }
                        targetLrc.writeText(lrcText)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Lyrics attached successfully!", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to attach lyrics: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    CenteredOverflowMenu(
        visible = expanded,
        onDismiss = onDismiss,
        title = song.title.ifBlank { "Unknown" },
    ) {
        // Favorite toggle row at top
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isFavorite) "Favorited" else "Not favorited",
                style = MaterialTheme.typography.bodyLarge,
                color = if (isFavorite) TideColors.error else TideColors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    val next = !isFavorite
                    isFavorite = next
                    scope.launch {
                        try {
                            ServiceLocator.repository.setFavorite(song.id, next)
                        } catch (_: Exception) {
                            isFavorite = !next
                        }
                    }
                },
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFavorite) TideColors.error else TideColors.textSecondary,
                )
            }
        }

        CenteredMenuItem(Icons.Rounded.PlayArrow, "Play now") {
            onDismiss()
            try {
                ServiceLocator.playbackController.setQueue(listOf(song), 0)
            } catch (_: Exception) {
                toast(context, "Could not start playback")
            }
        }

        CenteredMenuItem(Icons.Rounded.Info, "Song info") {
            onDismiss()
            if (onShowInfo != null) onShowInfo() else showInfoSheet = true
        }

        CenteredMenuItem(Icons.Rounded.Description, "Add / Embed Lyrics (.lrc)") {
            onDismiss()
            lrcPickerLauncher.launch(arrayOf("*/*", "text/*", "application/octet-stream"))
        }

        if (showRemoveFromQueue && onRemoveFromQueue != null) {
            CenteredMenuItem(Icons.Rounded.RemoveCircleOutline, "Remove from this queue") {
                onDismiss(); onRemoveFromQueue()
            }
        }

        CenteredMenuDivider()

        CenteredMenuItem(Icons.Rounded.SkipNext, "Play after current song") {
            onDismiss()
            if (onPlayAfterCurrent != null) {
                onPlayAfterCurrent()
            } else {
                try {
                    ServiceLocator.playbackController.playNext(song)
                    toast(context, "Will play next")
                } catch (_: Exception) {
                    toast(context, "Could not queue song")
                }
            }
        }

        CenteredMenuItem(Icons.Rounded.Queue, "Add to a queue") {
            onDismiss()
            if (onAddToQueue != null) {
                onAddToQueue()
            } else {
                try {
                    ServiceLocator.playbackController.addToQueue(song)
                    toast(context, "Added to queue")
                } catch (_: Exception) {
                    toast(context, "Could not add to queue")
                }
            }
        }

        CenteredMenuItem(Icons.Rounded.PlaylistAdd, "Add to playlists") {
            onDismiss()
            if (onAddToPlaylist != null) {
                onAddToPlaylist()
            } else {
                showPlaylistDialog = true
            }
        }

        CenteredMenuDivider()

        CenteredMenuItem(Icons.Rounded.Edit, "Edit name / artist tags") {
            onDismiss()
            if (onEditTags != null) onEditTags()
            else showTagDialog = true
        }

        CenteredMenuItem(Icons.Rounded.Notifications, "Set as ringtone") {
            onDismiss()
            setAsRingtoneSafe(context, song)
        }

        CenteredMenuItem(Icons.Rounded.Speed, "Play speed and Pitch") {
            onDismiss()
            if (onSpeedPitch != null) onSpeedPitch()
            else toast(context, "Open the player for speed & pitch")
        }

        CenteredMenuItem(Icons.Rounded.Share, "Share") {
            onDismiss()
            shareSongSafe(context, song)
        }

        CenteredMenuItem(Icons.Rounded.Delete, "Delete permanently", destructive = true) {
            onDismiss()
            showDeleteConfirmDialog = true
        }
    }
}

private fun toast(context: Context, msg: String) {
    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}

fun shareSongSafe(context: Context, song: Song) {
    try {
        val path = song.filePath
        if (path.isBlank()) {
            val uri = song.uri
            if (uri.isBlank()) {
                toast(context, "No file to share")
                return
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = song.mimeType.ifBlank { "audio/*" }
                putExtra(Intent.EXTRA_STREAM, Uri.parse(uri))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_TITLE, song.title)
            }
            context.startActivity(Intent.createChooser(intent, "Share ${song.title}"))
            return
        }
        val file = java.io.File(path)
        if (!file.exists()) {
            toast(context, "File not found")
            return
        }
        val authority = "${context.packageName}.fileprovider"
        val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = song.mimeType.ifBlank { "audio/*" }
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_TITLE, song.title)
        }
        context.startActivity(Intent.createChooser(intent, "Share ${song.title}"))
    } catch (e: Exception) {
        toast(context, "Share failed")
    }
}

fun setAsRingtoneSafe(context: Context, song: Song) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(context)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            toast(context, "Grant write settings, then try again")
            return
        }
        val uri = when {
            song.uri.isNotBlank() -> Uri.parse(song.uri)
            song.filePath.isNotBlank() -> Uri.fromFile(java.io.File(song.filePath))
            else -> null
        }
        if (uri == null) {
            toast(context, "No file for ringtone")
            return
        }
        RingtoneManager.setActualDefaultRingtoneUri(
            context,
            RingtoneManager.TYPE_RINGTONE,
            uri,
        )
        toast(context, "Set as ringtone")
    } catch (e: Exception) {
        toast(context, "Could not set ringtone")
    }
}
