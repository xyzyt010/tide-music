package com.example.tidemusic.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TimerOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.tidemusic.playback.SleepTimerManager
import com.example.tidemusic.theme.TideColors
import com.example.tidemusic.ui.LocalMediaController
import com.example.tidemusic.ui.common.ThinTopBar
import com.example.tidemusic.util.formatDuration

@Composable
fun SleepTimerScreen(onBack: () -> Unit) {
    val controller = LocalMediaController.current
    val remainingMs by SleepTimerManager.remainingMs.collectAsState()

    Column(Modifier.fillMaxSize()) {
        ThinTopBar(title = "Sleep Timer", onBack = onBack)
        Column(
            Modifier.fillMaxSize().padding(16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Pick a duration. Playback pauses when the timer runs out — even if you leave this screen.",
                style = MaterialTheme.typography.bodyMedium,
                color = TideColors.textSecondary,
            )
            if (remainingMs > 0) {
                Text(
                    text = "Time remaining: ${formatDuration(remainingMs)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = TideColors.accent,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                AssistChip(
                    onClick = { SleepTimerManager.cancel() },
                    label = { Text("Cancel Timer") },
                    leadingIcon = { Icon(Icons.Rounded.TimerOff, null, tint = TideColors.error) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = TideColors.error.copy(alpha = 0.15f),
                    ),
                )
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(5, 15, 30, 60).forEach { mins ->
                        AssistChip(
                            onClick = {
                                SleepTimerManager.start(minutes = mins) { controller?.pause() }
                            },
                            label = { Text("${mins}m") },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(90, 120, 180).forEach { mins ->
                        AssistChip(
                            onClick = {
                                SleepTimerManager.start(minutes = mins) { controller?.pause() }
                            },
                            label = { Text("${mins / 60}h${if (mins % 60 != 0) " ${mins % 60}m" else ""}") },
                        )
                    }
                }
            }
        }
    }
}
