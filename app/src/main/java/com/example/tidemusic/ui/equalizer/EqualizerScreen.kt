package com.example.tidemusic.ui.equalizer

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tidemusic.data.db.EqPresetEntity
import com.example.tidemusic.di.ServiceLocator
import com.example.tidemusic.domain.LibraryRepository
import com.example.tidemusic.playback.EqPresetSeeds
import com.example.tidemusic.theme.TideColors
import com.example.tidemusic.ui.common.ThinTopBar
import com.example.tidemusic.ui.rememberTideViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

private const val NUM_BANDS = 10

/** ISO 10-band EQ centers (Hz). */
private val BAND_LABELS = listOf("31Hz", "62Hz", "125Hz", "250Hz", "500Hz", "1kHz", "2kHz", "4kHz", "8kHz", "16kHz")

enum class OutputDevice(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val key: String,
) {
    GLOBAL("Global / Default", Icons.Rounded.Public, "global"),
    WIRED_HEADPHONES("Wired Headphones / AUX", Icons.Rounded.Headphones, "wired"),
    BLUETOOTH("Bluetooth Audio", Icons.Rounded.Bluetooth, "bt"),
    PHONE_SPEAKER("Phone Speaker", Icons.Rounded.Speaker, "speaker"),
    CAR_AUDIO("Car Audio", Icons.Rounded.DirectionsCar, "car"),
    USB_DAC("USB DAC / External", Icons.Rounded.Usb, "usb"),
}

class EqualizerViewModel(
    private val repository: LibraryRepository,
) : ViewModel() {

    val presets: StateFlow<List<EqPresetEntity>> = repository.observeEqPresets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EqPresetSeeds.builtIns)

    private val _currentDevice = MutableStateFlow(OutputDevice.GLOBAL)
    val currentDevice: StateFlow<OutputDevice> = _currentDevice.asStateFlow()

    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _gains = MutableStateFlow(FloatArray(NUM_BANDS))
    val gains: StateFlow<FloatArray> = _gains

    private val _bassStrength = MutableStateFlow(0f)
    val bassStrength: StateFlow<Float> = _bassStrength.asStateFlow()

    private val _virtualizerStrength = MutableStateFlow(0f)
    val virtualizerStrength: StateFlow<Float> = _virtualizerStrength.asStateFlow()

    private val _preampGain = MutableStateFlow(0f)
    val preampGain: StateFlow<Float> = _preampGain.asStateFlow()

    private val _activePresetId = MutableStateFlow<Long?>(null)
    val activePresetId: StateFlow<Long?> = _activePresetId.asStateFlow()

    init {
        viewModelScope.launch {
            val current = repository.observeEqPresets().first()
            if (current.isEmpty()) {
                EqPresetSeeds.builtIns.forEach { repository.saveEqPreset(it) }
            }
            val active = repository.observeActiveEqPreset().first()
            if (active != null) {
                applyPresetEntity(active)
            } else {
                val flat = current.firstOrNull { it.name == "Flat" } ?: EqPresetSeeds.builtIns.first()
                applyPresetEntity(flat)
            }
        }
    }

    private fun applyPresetEntity(preset: EqPresetEntity) {
        _activePresetId.value = preset.id
        val parsed = preset.bandGains.split(",").map { it.trim().toFloatOrNull() ?: 0f }
        val newGains = FloatArray(NUM_BANDS) { idx -> parsed.getOrElse(idx) { 0f } }
        _gains.value = newGains
        _preampGain.value = preset.preampGain
    }

    fun selectPreset(preset: EqPresetEntity) {
        viewModelScope.launch {
            repository.setActiveEqPreset(preset.id)
            applyPresetEntity(preset)
        }
    }

    fun selectDevice(device: OutputDevice, context: Context) {
        val prev = _currentDevice.value
        val prefs = context.getSharedPreferences("eq_device_profiles", Context.MODE_PRIVATE)

        // Save current device profile values
        prefs.edit()
            .putString("dev_${prev.key}_gains", _gains.value.joinToString(","))
            .putFloat("dev_${prev.key}_bass", _bassStrength.value)
            .putFloat("dev_${prev.key}_virt", _virtualizerStrength.value)
            .putFloat("dev_${prev.key}_preamp", _preampGain.value)
            .apply()

        _currentDevice.value = device

        // Load new device profile values if saved
        val savedGains = prefs.getString("dev_${device.key}_gains", null)
        if (savedGains != null) {
            val parsed = savedGains.split(",").map { it.trim().toFloatOrNull() ?: 0f }
            _gains.value = FloatArray(NUM_BANDS) { idx -> parsed.getOrElse(idx) { 0f } }
            _bassStrength.value = prefs.getFloat("dev_${device.key}_bass", 0f)
            _virtualizerStrength.value = prefs.getFloat("dev_${device.key}_virt", 0f)
            _preampGain.value = prefs.getFloat("dev_${device.key}_preamp", 0f)
        }
    }

    fun saveCustomPreset(name: String) {
        viewModelScope.launch {
            val entity = EqPresetEntity(
                name = name.trim().ifBlank { "Custom Preset" },
                isBuiltIn = false,
                bandGains = _gains.value.joinToString(","),
                preampGain = _preampGain.value,
            )
            val newId = repository.saveEqPreset(entity)
            _activePresetId.value = newId
        }
    }

    fun deletePreset(id: Long) {
        viewModelScope.launch {
            repository.deleteEqPreset(id)
            val all = repository.observeEqPresets().first()
            val flat = all.firstOrNull { it.name == "Flat" }
            if (flat != null) {
                selectPreset(flat)
            }
        }
    }

    fun resetToFlat() {
        _gains.value = FloatArray(NUM_BANDS) { 0f }
        _bassStrength.value = 0f
        _virtualizerStrength.value = 0f
        _preampGain.value = 0f
        val flatPreset = presets.value.firstOrNull { it.name == "Flat" }
        if (flatPreset != null) {
            _activePresetId.value = flatPreset.id
        }
    }

    fun setBand(index: Int, value: Float) {
        val copy = _gains.value.copyOf()
        copy[index] = value
        _gains.value = copy
        _activePresetId.value = null
    }

    fun setBass(v: Float) { _bassStrength.value = v.coerceIn(0f, 1f) }
    fun setVirtualizer(v: Float) { _virtualizerStrength.value = v.coerceIn(0f, 1f) }
    fun setPreamp(v: Float) { _preampGain.value = v.coerceIn(-10f, 10f) }
    fun setEnabled(v: Boolean) { _enabled.value = v }
}

@Composable
fun EqualizerScreen(
    onBack: () -> Unit,
    viewModel: EqualizerViewModel = rememberTideViewModel { EqualizerViewModel(it.repository) },
) {
    val context = LocalContext.current
    val presets by viewModel.presets.collectAsState()
    val currentDevice by viewModel.currentDevice.collectAsState()
    val enabled by viewModel.enabled.collectAsState()
    val gains by viewModel.gains.collectAsState()
    val bassStrength by viewModel.bassStrength.collectAsState()
    val virtualizerStrength by viewModel.virtualizerStrength.collectAsState()
    val preampGain by viewModel.preampGain.collectAsState()
    val activePresetId by viewModel.activePresetId.collectAsState()

    var showSaveDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }
    var presetPendingDelete by remember { mutableStateOf<EqPresetEntity?>(null) }
    var deviceDropdownOpen by remember { mutableStateOf(false) }

    val audioSessionId = remember {
        val sId = ServiceLocator.playbackController.audioSessionId
        if (sId > 0) sId else 0
    }

    // System AudioFX instances
    var systemEq by remember { mutableStateOf<Equalizer?>(null) }
    var systemBass by remember { mutableStateOf<BassBoost?>(null) }
    var systemVirt by remember { mutableStateOf<Virtualizer?>(null) }
    var bandRange by remember { mutableStateOf(-1200..1200) }
    var numSystemBands by remember { mutableIntStateOf(NUM_BANDS) }

    DisposableEffect(audioSessionId, enabled) {
        if (!enabled) {
            try { systemEq?.enabled = false } catch (_: Exception) {}
            try { systemBass?.enabled = false } catch (_: Exception) {}
            try { systemVirt?.enabled = false } catch (_: Exception) {}
        } else {
            try {
                val eq = Equalizer(0, audioSessionId)
                val bass = BassBoost(0, audioSessionId)
                val virt = Virtualizer(0, audioSessionId)

                eq.enabled = true
                bass.enabled = true
                virt.enabled = true

                systemEq = eq
                systemBass = bass
                systemVirt = virt

                numSystemBands = eq.numberOfBands.toInt().coerceAtLeast(1)
                val minLevel = eq.bandLevelRange.getOrNull(0)?.toInt() ?: -1200
                val maxLevel = eq.bandLevelRange.getOrNull(1)?.toInt() ?: 1200
                bandRange = minLevel..maxLevel

                for (b in 0 until minOf(numSystemBands, gains.size)) {
                    val level = (gains[b] * 100).toInt().coerceIn(bandRange.first, bandRange.last).toShort()
                    eq.setBandLevel(b.toShort(), level)
                }
                bass.setStrength((bassStrength * 1000).toInt().toShort().coerceIn(0, 1000))
                virt.setStrength((virtualizerStrength * 1000).toInt().toShort().coerceIn(0, 1000))
            } catch (_: Exception) {}
        }
        onDispose {
            try { systemEq?.release(); systemEq = null } catch (_: Exception) {}
            try { systemBass?.release(); systemBass = null } catch (_: Exception) {}
            try { systemVirt?.release(); systemVirt = null } catch (_: Exception) {}
        }
    }

    LaunchedEffect(gains, systemEq, enabled) {
        if (enabled && systemEq != null) {
            try {
                systemEq?.let { eq ->
                    for (b in 0 until minOf(numSystemBands, gains.size)) {
                        val level = (gains[b] * 100).toInt().coerceIn(bandRange.first, bandRange.last).toShort()
                        eq.setBandLevel(b.toShort(), level)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(bassStrength, systemBass, enabled) {
        if (enabled && systemBass != null) {
            try {
                systemBass?.setStrength((bassStrength * 1000).toInt().toShort().coerceIn(0, 1000))
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(virtualizerStrength, systemVirt, enabled) {
        if (enabled && systemVirt != null) {
            try {
                systemVirt?.setStrength((virtualizerStrength * 1000).toInt().toShort().coerceIn(0, 1000))
            } catch (_: Exception) {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TideColors.background),
    ) {
        ThinTopBar(
            title = "Equalizer",
            onBack = onBack,
            trailing = {
                IconButton(
                    onClick = {
                        viewModel.resetToFlat()
                        Toast.makeText(context, "Reset to Flat", Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RestartAlt,
                        contentDescription = "Reset EQ",
                        tint = TideColors.textPrimary,
                    )
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Output Device & Master Power Row ────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(TideColors.surfaceElevated)
                    .border(1.dp, TideColors.outline, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Output Device Profile",
                        style = MaterialTheme.typography.labelSmall,
                        color = TideColors.textSecondary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { deviceDropdownOpen = true }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = currentDevice.icon,
                                contentDescription = null,
                                tint = if (enabled) TideColors.accent else TideColors.textSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = currentDevice.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                                color = TideColors.textPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Rounded.ArrowDropDown,
                                contentDescription = "Select Device",
                                tint = TideColors.textSecondary,
                            )
                        }

                        DropdownMenu(
                            expanded = deviceDropdownOpen,
                            onDismissRequest = { deviceDropdownOpen = false },
                            modifier = Modifier.background(TideColors.surfaceElevated),
                        ) {
                            OutputDevice.entries.forEach { dev ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = dev.icon,
                                                contentDescription = null,
                                                tint = if (dev == currentDevice) TideColors.accent else TideColors.textSecondary,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Text(
                                                text = dev.title,
                                                color = if (dev == currentDevice) TideColors.accent else TideColors.textPrimary,
                                                fontWeight = if (dev == currentDevice) FontWeight.Bold else FontWeight.Normal,
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.selectDevice(dev, context)
                                        deviceDropdownOpen = false
                                    },
                                )
                            }
                        }
                    }
                }

                Switch(
                    checked = enabled,
                    onCheckedChange = { viewModel.setEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = TideColors.accent,
                        uncheckedThumbColor = TideColors.textSecondary,
                        uncheckedTrackColor = TideColors.outline,
                    ),
                )
            }

            // ── Presets Selector Row ────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Presets",
                        style = MaterialTheme.typography.titleSmall,
                        color = TideColors.textSecondary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RoundActionButton(
                            icon = Icons.Rounded.Save,
                            label = "Save",
                            onClick = { showSaveDialog = true },
                        )
                        RoundActionButton(
                            icon = Icons.Rounded.RestartAlt,
                            label = "Flat",
                            onClick = { viewModel.resetToFlat() },
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    presets.forEach { preset ->
                        val isSelected = activePresetId == preset.id
                        PresetPillChip(
                            preset = preset,
                            isSelected = isSelected,
                            enabled = enabled,
                            onClick = { viewModel.selectPreset(preset) },
                            onDelete = if (!preset.isBuiltIn) {
                                { presetPendingDelete = preset }
                            } else null,
                        )
                    }
                }
            }

            // ── 10-Band Graphic Equalizer Card ──────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(TideColors.surfaceElevated)
                    .border(1.dp, TideColors.outline, RoundedCornerShape(18.dp))
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = if (enabled) TideColors.accent else TideColors.textSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "10-Band Graphic EQ",
                            style = MaterialTheme.typography.titleMedium,
                            color = TideColors.textPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = "±12 dB",
                        style = MaterialTheme.typography.labelSmall,
                        color = TideColors.textSecondary,
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(NUM_BANDS) { i ->
                        VerticalBandSlider(
                            value = gains[i],
                            onValueChange = { v -> viewModel.setBand(i, v) },
                            label = BAND_LABELS[i],
                            enabled = enabled,
                            sliderHeight = 160.dp,
                        )
                    }
                }
            }

            // ── Audio Enhancements (Bass, Virtualizer, Preamp) with Thin Sliders ─────
            Text(
                text = "Audio Enhancements",
                style = MaterialTheme.typography.titleSmall,
                color = TideColors.textSecondary,
                fontWeight = FontWeight.SemiBold,
            )

            // Bass Boost Card
            EffectSliderCard(
                title = "Bass Boost",
                icon = Icons.Rounded.VolumeUp,
                value = bassStrength,
                onValueChange = viewModel::setBass,
                valueLabel = "${(bassStrength * 100).toInt()}%",
                enabled = enabled,
            )

            // 3D Virtualizer / Surround Card
            EffectSliderCard(
                title = "3D Virtualizer / Surround",
                icon = Icons.Rounded.SurroundSound,
                value = virtualizerStrength,
                onValueChange = viewModel::setVirtualizer,
                valueLabel = "${(virtualizerStrength * 100).toInt()}%",
                enabled = enabled,
            )

            // Preamp / Loudness Card
            EffectSliderCard(
                title = "Pre-Amplifier Stage",
                icon = Icons.Rounded.Equalizer,
                value = (preampGain + 10f) / 20f,
                onValueChange = { fraction ->
                    val db = (fraction * 20f) - 10f
                    viewModel.setPreamp(db)
                },
                valueLabel = String.format(Locale.US, "%+.1f dB", preampGain),
                enabled = enabled,
            )

            Spacer(Modifier.height(16.dp))
        }
    }

    // Save Preset Dialog
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false; newPresetName = "" },
            title = { Text("Save Custom Preset", color = TideColors.textPrimary) },
            text = {
                Column {
                    Text(
                        "Save the current 10-band slider levels and preamp settings as a custom preset.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TideColors.textSecondary,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    OutlinedTextField(
                        value = newPresetName,
                        onValueChange = { newPresetName = it },
                        placeholder = { Text("Preset name (e.g. My Acoustic)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPresetName.isNotBlank()) {
                            viewModel.saveCustomPreset(newPresetName)
                            showSaveDialog = false
                            newPresetName = ""
                            Toast.makeText(context, "Preset saved", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = newPresetName.isNotBlank(),
                ) {
                    Text("Save", color = TideColors.accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false; newPresetName = "" }) {
                    Text("Cancel", color = TideColors.textSecondary)
                }
            },
            containerColor = TideColors.surfaceElevated,
        )
    }

    // Delete Custom Preset Dialog
    presetPendingDelete?.let { preset ->
        AlertDialog(
            onDismissRequest = { presetPendingDelete = null },
            title = { Text("Delete Preset?", color = TideColors.textPrimary) },
            text = {
                Text(
                    "Are you sure you want to delete custom preset \"${preset.name}\"?",
                    color = TideColors.textSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePreset(preset.id)
                        presetPendingDelete = null
                        Toast.makeText(context, "Preset deleted", Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Text("Delete", color = TideColors.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { presetPendingDelete = null }) {
                    Text("Cancel", color = TideColors.textSecondary)
                }
            },
            containerColor = TideColors.surfaceElevated,
        )
    }
}

/**
 * Compact round button with circular icon and rounded pill styling.
 */
@Composable
private fun RoundActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(TideColors.surfaceElevated)
            .border(1.dp, TideColors.outline, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TideColors.accent,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TideColors.textPrimary,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * Preset pill chip with round badge and active indicator.
 */
@Composable
private fun PresetPillChip(
    preset: EqPresetEntity,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val bg = when {
        isSelected && enabled -> TideColors.accent
        isSelected && !enabled -> TideColors.accent.copy(alpha = 0.4f)
        else -> TideColors.surfaceElevated
    }
    val contentColor = when {
        isSelected && enabled -> Color.White
        else -> TideColors.textPrimary
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(
                width = 1.dp,
                color = if (isSelected) TideColors.accent else TideColors.outline,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = preset.name,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            ),
            color = contentColor,
        )
        if (onDelete != null) {
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = "Delete preset",
                tint = if (isSelected) Color.White.copy(alpha = 0.8f) else TideColors.textSecondary,
                modifier = Modifier
                    .size(16.dp)
                    .clickable(onClick = onDelete),
            )
        }
    }
}

/**
 * Clean card for audio enhancement effect sliders with thin line track and round thumb knob.
 */
@Composable
private fun EffectSliderCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueLabel: String,
    enabled: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TideColors.surfaceElevated)
            .border(1.dp, TideColors.outline, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) TideColors.accent else TideColors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TideColors.textPrimary,
                    fontWeight = FontWeight.Medium,
                )
            }
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                color = if (enabled && value > 0.01f) TideColors.accent else TideColors.textSecondary,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(4.dp))

        ThinLineHorizontalSlider(
            value = value.coerceIn(0f, 1f),
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
    }
}
