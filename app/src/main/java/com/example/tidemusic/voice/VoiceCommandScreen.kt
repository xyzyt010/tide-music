package com.example.tidemusic.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicNone
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tidemusic.theme.TideColors
import com.example.tidemusic.ui.common.PlayerArtwork
import com.example.tidemusic.util.formatDuration

@Composable
fun VoiceCommandScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: VoiceCommandViewModel = viewModel {
        VoiceCommandViewModel(context.applicationContext)
    }

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        viewModel.onPermissionGranted(granted)
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        hasMicPermission = granted
        viewModel.onPermissionGranted(granted)
        if (!granted) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val isInitializing by viewModel.isInitializing.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val isWakeWordActive by viewModel.isWakeWordActive.collectAsState()
    val isVadSpeechActive by viewModel.isVadSpeechActive.collectAsState()
    val transcript by viewModel.transcript.collectAsState()
    val partialTranscript by viewModel.partialTranscript.collectAsState()
    val lastIntent by viewModel.lastIntent.collectAsState()
    val matchedSong by viewModel.matchedSong.collectAsState()
    val matchConfidence by viewModel.matchConfidence.collectAsState()
    val audioRms by viewModel.audioRms.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val history by viewModel.commandHistory.collectAsState()

    val animatedRms by animateFloatAsState(
        targetValue = if (isListening || isVadSpeechActive) audioRms else 0f,
        animationSpec = tween(durationMillis = 80, easing = LinearEasing),
        label = "rms_anim"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isListening) 1.25f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TideColors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Command Based Playing",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    ),
                    color = TideColors.textPrimary
                )
                Text(
                    text = "Wake word & on-device STT decision engine",
                    style = MaterialTheme.typography.bodySmall,
                    color = TideColors.textSecondary
                )
            }

            // Wake word status pill
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isWakeWordActive) TideColors.accent.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.08f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isWakeWordActive) TideColors.accent.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.12f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .clickable { viewModel.toggleWakeWord(!isWakeWordActive) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isWakeWordActive) Icons.Rounded.RecordVoiceOver else Icons.Rounded.MicNone,
                        contentDescription = null,
                        tint = if (isWakeWordActive) TideColors.accent else TideColors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isWakeWordActive) "\"Hey Jarvis\" On" else "Wake Off",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isWakeWordActive) TideColors.accent else TideColors.textSecondary
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // Permission card if not granted
        if (!hasMicPermission) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = TideColors.error.copy(alpha = 0.15f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, TideColors.error.copy(alpha = 0.35f))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "Microphone Permission Required",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TideColors.error
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Microphone access is needed for continuous wake word recognition (\"Hey Jarvis\") and speech-to-text music playback commands.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TideColors.textPrimary
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        colors = ButtonDefaults.buttonColors(containerColor = TideColors.error)
                    ) {
                        Text("Grant Permission", color = Color.White)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }

        // Initializing banner if AI models are unpacking/loading
        if (isInitializing) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = TideColors.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, TideColors.accent.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = TideColors.accent
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = TideColors.textPrimary
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
        }

        // Centerpiece Interactive Audio Visualizer & Mic Button
        Box(
            modifier = Modifier
                .size(220.dp),
            contentAlignment = Alignment.Center
        ) {
            // Outermost pulsating glow aura
            Box(
                modifier = Modifier
                    .size((170 + animatedRms * 50).dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                (if (isListening) TideColors.accent else TideColors.accent.copy(alpha = 0.4f)).copy(alpha = 0.28f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Secondary wave ring
            Box(
                modifier = Modifier
                    .size((140 + animatedRms * 30).dp)
                    .clip(CircleShape)
                    .background(
                        if (isListening) TideColors.accent.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.05f)
                    )
                    .border(
                        1.5.dp,
                        if (isListening) TideColors.accent.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.12f),
                        CircleShape
                    )
            )

            // Primary Interactive Mic Button
            val buttonColor by animateColorAsState(
                targetValue = if (isListening) TideColors.accent else TideColors.surface,
                label = "btn_color"
            )

            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(CircleShape)
                    .background(buttonColor)
                    .border(2.dp, if (isListening) Color.White else TideColors.outline, CircleShape)
                    .clickable {
                        if (!hasMicPermission) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            viewModel.toggleListening()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                    contentDescription = if (isListening) "Stop Listening" else "Start Listening",
                    tint = if (isListening) Color.Black else TideColors.textPrimary,
                    modifier = Modifier.size(42.dp)
                )
            }
        }

        // Status Row: TenVAD indicator + Engine Status
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isVadSpeechActive) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF00E676).copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E676))
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "TenVAD: Speech",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF00E676)
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = TideColors.surface.copy(alpha = 0.8f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isListening) Color(0xFF00E676) else if (isWakeWordActive) TideColors.accent else Color.Gray)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = TideColors.textPrimary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // Live Transcript Display
        val displayText = partialTranscript.ifBlank { transcript }
        if (displayText.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = TideColors.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = TideColors.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (partialTranscript.isNotBlank()) "Listening…" else "Recognized Speech",
                            style = MaterialTheme.typography.labelSmall,
                            color = TideColors.textSecondary
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "“$displayText”",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        ),
                        color = TideColors.textPrimary
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        // Matched Song & Decision Card
        if (matchedSong != null) {
            val song = matchedSong!!
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = TideColors.accent.copy(alpha = 0.12f)),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, TideColors.accent.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlayerArtwork(
                        song = song,
                        size = 56.dp,
                        isPlaying = true
                    )

                    Spacer(Modifier.width(14.dp))

                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF00E676).copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "PLAYING MATCH",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color(0xFF00E676),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "${(matchConfidence * 100).toInt()}% match",
                                style = MaterialTheme.typography.labelSmall,
                                color = TideColors.textSecondary
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = TideColors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${song.artist} • ${formatDuration(song.durationMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TideColors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Quick Suggestion Chips (Common voice queries for 1-tap testing)
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = "Try Saying Or Tapping:",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = TideColors.textSecondary
            )
            Spacer(Modifier.height(8.dp))

            val quickCommands = listOf(
                "Play the winner takes it all",
                "Pause the current song",
                "Play the paused song",
                "Next song",
                "Previous song",
                "Turn on shuffle",
                "Repeat current song",
                "Favorite this song",
                "Volume up"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickCommands.forEach { cmd ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = TideColors.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier.clickable {
                            viewModel.executeQuickCommand(cmd)
                        }
                    ) {
                        Text(
                            text = cmd,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = TideColors.textPrimary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Recent Executed Commands History
        if (history.isNotEmpty()) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = "Recent Voice Commands",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = TideColors.textSecondary
                )
                Spacer(Modifier.height(8.dp))

                history.take(4).forEach { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = TideColors.surface.copy(alpha = 0.6f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "“${item.transcript}”",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = TideColors.textPrimary
                                )
                                Text(
                                    text = item.actionDescription,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TideColors.textSecondary
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        // Technical Inference Architecture Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Psychology,
                        contentDescription = null,
                        tint = TideColors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Voice AI & Inference Pipeline",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = TideColors.textPrimary
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "• AudioRecord: 16 kHz 16-bit PCM continuous capture\n• VAD & microWakeWord: Energy-contour trigger for \"Hey Jarvis\"\n• STT ASR: On-device offline-first speech recognizer\n• Decision Model: Laya-compatible typed intent & fuzzy Levenshtein entity router",
                    style = MaterialTheme.typography.labelSmall.copy(lineHeight = 16.sp),
                    color = TideColors.textSecondary
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
