package com.example.tidemusic.ui.info

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.tidemusic.theme.TideColors
import com.example.tidemusic.ui.common.ThinTopBar

/** Help & Info — app version, brief feature overview, attribution. */
@Composable
fun HelpInfoScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ThinTopBar(title = "Help & Info", onBack = onBack)
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Tide Music", style = MaterialTheme.typography.titleLarge, color = TideColors.textPrimary)
            Text(
                text = "Tide Music is a private, offline-first Android music player. The " +
                    "entire library lives locally on the device; no telemetry, no remote " +
                    "playback, no online lyric lookups. yt-dlp downloads are hardcoded " +
                    "IPv4-only and never tunnel over IPv6.",
                style = MaterialTheme.typography.bodyMedium,
                color = TideColors.textSecondary,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
    }
}
