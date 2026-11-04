package com.example.tidemusic.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.example.tidemusic.di.ServiceLocator
import com.example.tidemusic.theme.Accent
import com.example.tidemusic.theme.Background
import com.example.tidemusic.theme.LightBackground
import com.example.tidemusic.theme.LightTextPrimary
import com.example.tidemusic.theme.TextPrimary
import com.example.tidemusic.theme.TideColors
import com.example.tidemusic.ui.common.ThinTopBar
import com.example.tidemusic.ui.common.verticalScrollbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var activeColorPickerTarget by remember { mutableStateOf<ColorTarget?>(null) }

    val isDarkMode by ServiceLocator.settingsManager.isDarkMode.collectAsState()
    val customThemeInt by ServiceLocator.settingsManager.customThemeColor.collectAsState()
    val customHighlightInt by ServiceLocator.settingsManager.customHighlightColor.collectAsState()
    val customTextInt by ServiceLocator.settingsManager.customTextColor.collectAsState()

    fun formatHex(c: Color): String {
        val r = (c.red * 255).toInt().coerceIn(0, 255)
        val g = (c.green * 255).toInt().coerceIn(0, 255)
        val b = (c.blue * 255).toInt().coerceIn(0, 255)
        return String.format("#%02X%02X%02X", r, g, b)
    }

    val currentThemeColor = customThemeInt?.let { Color(it) } ?: if (isDarkMode) Background else LightBackground
    val currentHighlightColor = customHighlightInt?.let { Color(it) } ?: Accent
    val currentTextColor = customTextInt?.let { Color(it) } ?: if (isDarkMode) TextPrimary else LightTextPrimary

    val scrollState = rememberScrollState()

    Column(Modifier.fillMaxSize()) {
        ThinTopBar(title = "Settings", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .verticalScrollbar(scrollState)
                .padding(16.dp),
        ) {
            // ── Theme & Appearance ──────────────────────────────────
            Text(
                "Appearance & Colors",
                style = MaterialTheme.typography.titleSmall,
                color = TideColors.textSecondary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            SettingsRow(
                icon = Icons.Rounded.MusicNote,
                title = "Dark theme",
                subtitle = "Enable dark mode throughout the app",
                trailing = {
                    Switch(
                        checked = isDarkMode,
                        onCheckedChange = { ServiceLocator.settingsManager.setDarkMode(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = TideColors.accent),
                    )
                },
            )

            val glassEnabled by ServiceLocator.settingsManager.isGlassEffectsEnabled.collectAsState()
            SettingsRow(
                icon = Icons.Rounded.MusicNote,
                title = "Player backdrop",
                subtitle = "Use dynamic song artwork haze instead of solid theme",
                trailing = {
                    Switch(
                        checked = glassEnabled,
                        onCheckedChange = { ServiceLocator.settingsManager.setGlassEffectsEnabled(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = TideColors.accent),
                    )
                },
            )

            // Custom Highlight / Accent RGB Selector
            SettingsRow(
                icon = Icons.Rounded.Palette,
                title = "Highlight color",
                subtitle = "RGB & Hex selector for controls, tabs, and accents",
                onClick = { activeColorPickerTarget = ColorTarget.HIGHLIGHT },
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(currentHighlightColor)
                                .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                        )
                        Spacer(Modifier.padding(start = 6.dp))
                        Text(
                            text = formatHex(currentHighlightColor),
                            style = MaterialTheme.typography.labelSmall,
                            color = TideColors.textSecondary,
                        )
                    }
                },
            )

            // Custom Theme / Background RGB Selector
            SettingsRow(
                icon = Icons.Rounded.Palette,
                title = "Theme color",
                subtitle = "Custom RGB & Hex background theme color",
                onClick = { activeColorPickerTarget = ColorTarget.THEME },
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(currentThemeColor)
                                .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                        )
                        Spacer(Modifier.padding(start = 6.dp))
                        Text(
                            text = formatHex(currentThemeColor),
                            style = MaterialTheme.typography.labelSmall,
                            color = TideColors.textSecondary,
                        )
                    }
                },
            )

            // Custom Text Color RGB Selector
            SettingsRow(
                icon = Icons.Rounded.Palette,
                title = "Text color",
                subtitle = "Custom RGB & Hex color for titles and primary text",
                onClick = { activeColorPickerTarget = ColorTarget.TEXT },
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(currentTextColor)
                                .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                        )
                        Spacer(Modifier.padding(start = 6.dp))
                        Text(
                            text = formatHex(currentTextColor),
                            style = MaterialTheme.typography.labelSmall,
                            color = TideColors.textSecondary,
                        )
                    }
                },
            )

            // ── Interface & Playback ──────────────────────────────────
            Text(
                "Interface",
                style = MaterialTheme.typography.titleSmall,
                color = TideColors.textSecondary,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )
            val miniPlayerEnabled by ServiceLocator.settingsManager.isMiniPlayerEnabled.collectAsState()
            SettingsRow(
                icon = Icons.Rounded.MusicNote,
                title = "Mini player bar",
                subtitle = "Show floating playback bar above the navigation menu",
                trailing = {
                    Switch(
                        checked = miniPlayerEnabled,
                        onCheckedChange = { ServiceLocator.settingsManager.setMiniPlayerEnabled(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = TideColors.accent),
                    )
                },
            )

            // ── Audio & Loudness ──────────────────────────────────
            Text(
                "Audio & Loudness",
                style = MaterialTheme.typography.titleSmall,
                color = TideColors.textSecondary,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )
            val smartLoudnessEnabled by ServiceLocator.settingsManager.isSmartLoudnessEnabled.collectAsState()
            SettingsRow(
                icon = Icons.Rounded.MusicNote,
                title = "Smart Volume Extra",
                subtitle = "Hardware-accelerated loudness extension (+3.0 dB clean DSP boost matching YouTube)",
                trailing = {
                    Switch(
                        checked = smartLoudnessEnabled,
                        onCheckedChange = { ServiceLocator.settingsManager.setSmartLoudnessEnabled(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = TideColors.accent),
                    )
                },
            )

            // ── Background Execution ─────────────────────
            val context = androidx.compose.ui.platform.LocalContext.current
            Text(
                "Background Playback",
                style = MaterialTheme.typography.titleSmall,
                color = TideColors.textSecondary,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )
            val isBatteryUnrestricted = remember(context) {
                com.example.tidemusic.util.BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
            }
            SettingsRow(
                icon = Icons.Rounded.CheckCircle,
                title = "Run Unrestricted in Background",
                subtitle = if (isBatteryUnrestricted) {
                    "Allowed — music won't stop when app is closed from recents"
                } else {
                    "Restricted by Android — tap to allow uninterrupted playback"
                },
                onClick = {
                    com.example.tidemusic.util.BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
                },
                trailing = {
                    Text(
                        if (isBatteryUnrestricted) "Allowed" else "Fix",
                        color = if (isBatteryUnrestricted) TideColors.accent else TideColors.textSecondary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
            )

            var hasNotifPerm by remember {
                mutableStateOf(
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.POST_NOTIFICATIONS
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    } else true
                )
            }
            SettingsRow(
                icon = Icons.Rounded.MusicNote,
                title = "System Media Controls (Engine 1)",
                subtitle = if (hasNotifPerm) {
                    "Active — standard system controls, lock screen, and ColorOS Live Alerts"
                } else {
                    "Permission needed — tap to allow notifications"
                },
                onClick = {
                    try {
                        val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                },
                trailing = {
                    Text(
                        if (hasNotifPerm) "Active" else "Enable",
                        color = if (hasNotifPerm) TideColors.accent else TideColors.textSecondary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
            )

            // ── Native Aqua Dynamics / Fluid Cloud ─────
            SettingsRow(
                icon = Icons.Rounded.MusicNote,
                title = "Aqua Dynamics & Fluid Cloud",
                subtitle = "Native ColorOS / Realme UI status bar capsule & punch-hole pill — zero overlay permissions required",
                onClick = {},
                trailing = {
                    Text(
                        "Active",
                        color = TideColors.accent,
                        style = MaterialTheme.typography.labelMedium,
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
                subtitle = "Version 1.6.3 · Private, offline-first music player",
            )
            SettingsRow(
                icon = Icons.Rounded.CheckCircle,
                title = "IPv4 Mode",
                subtitle = "yt-dlp downloads are forced to IPv4",
            )
        }
    }

    activeColorPickerTarget?.let { target ->
        val (initialColor, defaultColor) = when (target) {
            ColorTarget.THEME -> currentThemeColor to (if (isDarkMode) Background else LightBackground)
            ColorTarget.HIGHLIGHT -> currentHighlightColor to Accent
            ColorTarget.TEXT -> currentTextColor to (if (isDarkMode) TextPrimary else LightTextPrimary)
        }
        RgbColorPickerDialog(
            target = target,
            initialColor = initialColor,
            defaultColor = defaultColor,
            onColorSelected = { selectedColor ->
                when (target) {
                    ColorTarget.THEME -> ServiceLocator.settingsManager.setCustomThemeColor(selectedColor.toArgb())
                    ColorTarget.HIGHLIGHT -> ServiceLocator.settingsManager.setCustomHighlightColor(selectedColor.toArgb())
                    ColorTarget.TEXT -> ServiceLocator.settingsManager.setCustomTextColor(selectedColor.toArgb())
                }
            },
            onResetToDefault = {
                when (target) {
                    ColorTarget.THEME -> ServiceLocator.settingsManager.setCustomThemeColor(null)
                    ColorTarget.HIGHLIGHT -> ServiceLocator.settingsManager.setCustomHighlightColor(null)
                    ColorTarget.TEXT -> ServiceLocator.settingsManager.setCustomTextColor(null)
                }
            },
            onDismiss = { activeColorPickerTarget = null }
        )
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
