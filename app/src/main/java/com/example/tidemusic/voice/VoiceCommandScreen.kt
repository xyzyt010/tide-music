package com.example.tidemusic.voice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tidemusic.theme.TideColors
import com.example.tidemusic.ui.common.ArtworkTile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VoiceCommandScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: VoiceCommandViewModel = viewModel {
        VoiceCommandViewModel(context.applicationContext)
    }

    val inputText by viewModel.inputText.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val matchedSong by viewModel.matchedSong.collectAsState()
    val matchConfidence by viewModel.matchConfidence.collectAsState()
    val matchType by viewModel.matchType.collectAsState()
    val history by viewModel.commandHistory.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(TideColors.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header Section
        item {
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(TideColors.accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = "Assistant",
                        tint = TideColors.accent,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Tide Assistant",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        ),
                        color = TideColors.textPrimary
                    )
                    Text(
                        text = "Typed command routing & fuzzy library search",
                        style = MaterialTheme.typography.bodySmall,
                        color = TideColors.textSecondary
                    )
                }
            }
        }

        // 2. Command Input Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { viewModel.onInputTextChanged(it) },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            text = "e.g. \"play starboy\", \"shuffle all\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TideColors.textSecondary.copy(alpha = 0.7f)
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputText.isNotBlank()) {
                                viewModel.submitCommand()
                            }
                        }
                    ),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = TideColors.surface,
                        unfocusedContainerColor = TideColors.surface,
                        focusedBorderColor = TideColors.accent,
                        unfocusedBorderColor = TideColors.outline,
                        focusedTextColor = TideColors.textPrimary,
                        unfocusedTextColor = TideColors.textPrimary,
                        cursorColor = TideColors.accent
                    ),
                    trailingIcon = {
                        if (inputText.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onInputTextChanged("") }) {
                                Icon(
                                    imageVector = Icons.Rounded.Clear,
                                    contentDescription = "Clear",
                                    tint = TideColors.textSecondary
                                )
                            }
                        }
                    }
                )

                Spacer(Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (inputText.isNotBlank()) TideColors.accent
                            else TideColors.accent.copy(alpha = 0.35f)
                        )
                        .clickable(enabled = inputText.isNotBlank()) {
                            viewModel.submitCommand()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        // 3. Quick Action Chips
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Quick Actions",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = TideColors.textSecondary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionChip("Shuffle All", Icons.Rounded.Shuffle) {
                        viewModel.executeQuickCommand("shuffle all")
                    }
                    ActionChip("Play Paused", Icons.Rounded.PlayArrow) {
                        viewModel.executeQuickCommand("play")
                    }
                    ActionChip("Next Track", Icons.Rounded.SkipNext) {
                        viewModel.executeQuickCommand("next")
                    }
                    ActionChip("Previous", Icons.Rounded.SkipPrevious) {
                        viewModel.executeQuickCommand("previous")
                    }
                    ActionChip("Repeat Queue", Icons.Rounded.Repeat) {
                        viewModel.executeQuickCommand("repeat all")
                    }
                    ActionChip("Repeat One", Icons.Rounded.Repeat) {
                        viewModel.executeQuickCommand("repeat one")
                    }
                    ActionChip("Volume Up", Icons.Rounded.VolumeUp) {
                        viewModel.executeQuickCommand("volume up")
                    }
                    ActionChip("Volume Down", Icons.Rounded.VolumeDown) {
                        viewModel.executeQuickCommand("volume down")
                    }
                }
            }
        }

        // 4. Status / Result Feedback Card
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = TideColors.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, TideColors.outline, RoundedCornerShape(14.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = "Status",
                        tint = TideColors.accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TideColors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 5. Matched Song Card (if available)
        if (matchedSong != null) {
            item {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    val song = matchedSong!!
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = TideColors.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, TideColors.accent.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                            .clickable { viewModel.playMatchedSong(song) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ArtworkTile(song = song, size = 48.dp, rounded = 10.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = TideColors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = song.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TideColors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (matchType.isNotBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "$matchType (${(matchConfidence * 100).toInt()}%)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TideColors.accent
                                    )
                                }
                            }
                            IconButton(onClick = { viewModel.playMatchedSong(song) }) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = "Play",
                                    tint = TideColors.accent,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 6. Command History Section
        if (history.isNotEmpty()) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = "History",
                        tint = TideColors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Recent Commands",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = TideColors.textSecondary
                    )
                }
            }

            items(history) { item ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = TideColors.surface.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.executeQuickCommand(item.transcript) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.transcript,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = TideColors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.actionDescription,
                                style = MaterialTheme.typography.bodySmall,
                                color = TideColors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = formatTime(item.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = TideColors.textSecondary.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(80.dp)) // padding for bottom bar
        }
    }
}

@Composable
private fun ActionChip(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = TideColors.surface,
        modifier = Modifier
            .border(1.dp, TideColors.outline, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TideColors.accent,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = TideColors.textPrimary
            )
        }
    }
}

private fun formatTime(millis: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(millis))
}
