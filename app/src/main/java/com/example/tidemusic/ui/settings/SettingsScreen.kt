package com.example.tidemusic.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.unit.dp
import com.example.tidemusic.di.ServiceLocator
import com.example.tidemusic.theme.TideColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.tidemusic.ui.common.ThinTopBar

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        ThinTopBar(title = "Settings", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // ── Theme ──────────────────────────────────
            Text(
                "Theme",
                style = MaterialTheme.typography.titleSmall,
                color = TideColors.textSecondary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            val isDarkMode by ServiceLocator.settingsManager.isDarkMode.collectAsState()
            SettingsRow(
                icon = Icons.Rounded.MusicNote,
                title = "Dark mode",
                subtitle = "Use dark theme",
                trailing = {
                    Switch(
                        checked = isDarkMode,
                        onCheckedChange = { ServiceLocator.settingsManager.setDarkMode(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = TideColors.accent),
                    )
                },
            )

            // ── Playback ──────────────────────────────────
            Text(
                "Playback",
                style = MaterialTheme.typography.titleSmall,
                color = TideColors.textSecondary,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )
            SettingsRow(
                icon = Icons.Rounded.MusicNote,
                title = "Glass effects",
                subtitle = "Frosted blur overlays on player and mini-player",
                trailing = {
                    val glassEnabled by ServiceLocator.settingsManager.isGlassEffectsEnabled.collectAsState()
                    Switch(
                        checked = glassEnabled,
                        onCheckedChange = { ServiceLocator.settingsManager.setGlassEffectsEnabled(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = TideColors.accent),
                    )
                },
            )

            // ── Library ──────────────────────────────────
            Text(
                "Library",
                style = MaterialTheme.typography.titleSmall,
                color = TideColors.textSecondary,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )
            SettingsRow(
                icon = Icons.Rounded.Folder,
                title = "Scan Library",
                subtitle = "Incremental rescan of MediaStore",
                onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            try { ServiceLocator.repository.rescanIncremental() } catch (_: Exception) {}
                        }
                    }
                },
            )

            // ── About ──────────────────────────────────
            Text(
                "About",
                style = MaterialTheme.typography.titleSmall,
                color = TideColors.textSecondary,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )
            SettingsRow(
                icon = Icons.Rounded.Info,
                title = "Tide Music",
                subtitle = "Version 1.0.0 · Private, offline-first music player",
            )
            SettingsRow(
                icon = Icons.Rounded.CheckCircle,
                title = "IPv4 Mode",
                subtitle = "yt-dlp downloads are forced to IPv4",
            )
        }
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TideColors.accent,
            modifier = Modifier.padding(end = 16.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = TideColors.textPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TideColors.textSecondary)
        }
        trailing?.invoke()
    }
}
