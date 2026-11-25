package com.example.tidemusic.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.tidemusic.data.db.BookmarkEntity
import com.example.tidemusic.di.ServiceLocator
import com.example.tidemusic.domain.Song
import com.example.tidemusic.theme.TideColors
import com.example.tidemusic.util.formatDuration
import kotlinx.coroutines.launch

/**
 * Functional Bookmarks & Notes Dialog allowing users to save timestamped notes for tracks
 * and jump to bookmarks during playback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksDialog(
    song: Song,
    currentPositionMs: Long,
    onSeekTo: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val bookmarks by ServiceLocator.repository.observeBookmarks(song.id).collectAsState(initial = emptyList())
    var noteText by remember { mutableStateOf("") }
    var showAddForm by remember { mutableStateOf(false) }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        content = {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(TideColors.surfaceElevated)
                    .padding(20.dp)
                    .width(330.dp)
                    .heightIn(max = 520.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Bookmark,
                            contentDescription = null,
                            tint = TideColors.accent,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Bookmarks & Notes",
                            style = MaterialTheme.typography.titleMedium,
                            color = TideColors.textPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (!showAddForm) {
                        IconButton(onClick = { showAddForm = true }) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Add Bookmark",
                                tint = TideColors.accent,
                            )
                        }
                    }
                }

                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = TideColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
                )

                HorizontalDivider(color = TideColors.outline, thickness = 1.dp)

                if (showAddForm) {
                    Column(Modifier.padding(vertical = 12.dp)) {
                        Text(
                            text = "Add bookmark at ${formatDuration(currentPositionMs)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = TideColors.accent,
                            fontWeight = FontWeight.SemiBold,
                        )
                        OutlinedTextField(
                            value = noteText,
                            onValueChange = { noteText = it },
                            placeholder = { Text("Optional note (e.g. Guitar solo)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = { showAddForm = false; noteText = "" }) {
                                Text("Cancel", color = TideColors.textSecondary)
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    scope.launch {
                                        ServiceLocator.repository.addBookmark(
                                            songId = song.id,
                                            timestampMs = currentPositionMs,
                                            note = noteText.trim(),
                                        )
                                        noteText = ""
                                        showAddForm = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = TideColors.accent),
                            ) {
                                Text("Save")
                            }
                        }
                    }
                    HorizontalDivider(color = TideColors.outline, thickness = 1.dp)
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(vertical = 8.dp),
                ) {
                    if (bookmarks.isEmpty()) {
                        item {
                            Text(
                                text = "No bookmarks yet. Tap '+' to bookmark the current playback position.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TideColors.textSecondary,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }
                    } else {
                        items(bookmarks, key = { it.id }) { bm ->
                            BookmarkRow(
                                bookmark = bm,
                                onSeek = {
                                    onSeekTo(bm.timestampMs)
                                    onDismiss()
                                },
                                onDelete = {
                                    scope.launch {
                                        ServiceLocator.repository.deleteBookmark(bm.id)
                                    }
                                },
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close", color = TideColors.accent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
    )
}

@Composable
private fun BookmarkRow(
    bookmark: BookmarkEntity,
    onSeek: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSeek)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatDuration(bookmark.timestampMs),
                style = MaterialTheme.typography.titleSmall,
                color = TideColors.accent,
                fontWeight = FontWeight.Bold,
            )
            if (bookmark.note.isNotBlank()) {
                Text(
                    text = bookmark.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = TideColors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = "Delete",
                tint = TideColors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
