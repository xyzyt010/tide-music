package com.example.tidemusic.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tidemusic.domain.ScanProgress
import com.example.tidemusic.theme.TideColors

/**
 * A subtle, sleek horizontal running blue line indicator.
 * Moves smoothly across the track giving a modern loading beam effect.
 */
@Composable
fun SubtleRunningBlueLine(
    modifier: Modifier = Modifier,
    lineHeight: Dp = 2.5.dp,
    trackColor: Color = Color.White.copy(alpha = 0.08f),
) {
    val infiniteTransition = rememberInfiniteTransition(label = "blue_line")
    val progress by infiniteTransition.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "line_progress",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(lineHeight)
            .clip(RoundedCornerShape(lineHeight / 2))
            .background(trackColor),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val beamWidth = w * 0.45f
            val startX = (progress * w) - (beamWidth / 2)

            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFF2979FF).copy(alpha = 0.65f),
                        Color(0xFF00E5FF),
                        Color(0xFF2979FF).copy(alpha = 0.65f),
                        Color.Transparent,
                    ),
                    startX = startX,
                    endX = startX + beamWidth,
                ),
            )
        }
    }
}

/**
 * App launch & library staging screen.
 * Displays the app branding with radar pulses, running blue line, and live track staging counter.
 */
@Composable
fun AppLoadingScreen(
    scanProgress: ScanProgress? = null,
) {
    val infinitePulse = rememberInfiniteTransition(label = "pulse_radar")
    val pulseScale by infinitePulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulseScale",
    )
    val pulseAlpha by infinitePulse.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulseAlpha",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TideColors.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 36.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (scanProgress?.isScanning == true) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .border(1.5.dp, Color(0xFF00E5FF).copy(alpha = pulseAlpha), CircleShape)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF2979FF), Color(0xFF00E5FF))
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = "Tide Music",
                        tint = Color.White,
                        modifier = Modifier.size(42.dp),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Tide Music",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    letterSpacing = 0.5.sp,
                ),
                color = TideColors.textPrimary,
            )

            Spacer(Modifier.height(8.dp))

            val isScanning = scanProgress?.isScanning == true
            Text(
                text = if (isScanning) {
                    if (scanProgress.count > 0) {
                        "${scanProgress.stage} • ${scanProgress.count} tracks found"
                    } else {
                        scanProgress.stage
                    }
                } else {
                    "Preparing audio engine..."
                },
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = if (isScanning) Color(0xFF00E5FF) else TideColors.textSecondary,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(24.dp))

            SubtleRunningBlueLine(
                modifier = Modifier.width(180.dp),
            )
        }
    }
}
