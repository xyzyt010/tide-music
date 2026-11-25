package com.example.tidemusic.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tidemusic.theme.TideColors

@Composable
fun WelcomeGuideDialog(
    onGrantPermissions: () -> Unit,
    onDismiss: () -> Unit,
) {
    var currentStep by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TideColors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top Bar with Skip and Progress Indicators
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Step indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(3) { index ->
                        val isSelected = index == currentStep
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(if (isSelected) 24.dp else 8.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (isSelected) TideColors.accent
                                    else Color.White.copy(alpha = 0.20f)
                                )
                        )
                    }
                }

                if (currentStep < 2) {
                    OutlinedButton(
                        onClick = { currentStep = 2 },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TideColors.textSecondary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        modifier = Modifier.height(34.dp),
                    ) {
                        Text("Skip", fontSize = 12.sp)
                    }
                }
            }

            // Step Body Animated
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) },
                label = "onboardingSlide",
                modifier = Modifier.weight(1f),
            ) { step ->
                when (step) {
                    0 -> OnboardingStepPage(
                        icon = Icons.Rounded.GraphicEq,
                        tag = "STUDIO QUALITY",
                        title = "Lossless Audio &\nSmart Equalizer",
                        description = "Experience pristine offline playback with a 10-band ISO equalizer and automatic hardware profiles for Bluetooth headphones, AUX, and Speakers.",
                    )
                    1 -> OnboardingStepPage(
                        icon = Icons.Rounded.Lyrics,
                        tag = "REAL-TIME SYNC",
                        title = "Synchronized Lyrics &\nInteractive Seeking",
                        description = "Sing along with vocal-timed lyric highlighting. Tap any lyric line to instantly jump playback directly to that exact moment.",
                    )
                    2 -> PermissionStepPage()
                }
            }

            // Bottom Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (currentStep > 0 && currentStep < 2) {
                    OutlinedButton(
                        onClick = { currentStep-- },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    ) {
                        Text("Back", color = TideColors.textPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (currentStep < 2) {
                    Button(
                        onClick = { currentStep++ },
                        modifier = Modifier
                            .weight(2f)
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TideColors.accent),
                    ) {
                        Text("Next", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Rounded.ArrowForward, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                } else {
                    Button(
                        onClick = onGrantPermissions,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TideColors.accent),
                    ) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Grant Access & Start Listening",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingStepPage(
    icon: ImageVector,
    tag: String,
    title: String,
    description: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            TideColors.accent.copy(alpha = 0.35f),
                            TideColors.accent.copy(alpha = 0.05f)
                        )
                    )
                )
                .border(1.5.dp, TideColors.accent.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TideColors.accent,
                modifier = Modifier.size(56.dp),
            )
        }

        Spacer(Modifier.height(32.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(TideColors.accent.copy(alpha = 0.15f))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = tag,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = TideColors.accent,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
            )
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = TideColors.textPrimary,
            textAlign = TextAlign.Center,
            lineHeight = 34.sp,
        )

        Spacer(Modifier.height(14.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
            color = TideColors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PermissionStepPage() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(TideColors.accent.copy(alpha = 0.15f))
                .border(1.5.dp, TideColors.accent.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.FolderSpecial,
                contentDescription = null,
                tint = TideColors.accent,
                modifier = Modifier.size(46.dp),
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "Storage & Library Setup",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = TideColors.textPrimary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Tide Music runs 100% offline. To index your music collection, read synced .lrc lyrics, and save downloaded tracks, storage access is required.",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
            color = TideColors.textSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(TideColors.surfaceElevated)
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PermissionItem(
                icon = Icons.Rounded.MusicNote,
                title = "Music & Audio Files",
                description = "Scans offline songs from your device storage.",
            )
            PermissionItem(
                icon = Icons.Rounded.FolderSpecial,
                title = "All Files Access",
                description = "Required on Android 11+ to import .lrc lyrics and save to /Music/TideMusic.",
            )
        }
    }
}

@Composable
private fun PermissionItem(
    icon: ImageVector,
    title: String,
    description: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(TideColors.accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = TideColors.accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = TideColors.textPrimary)
            Text(text = description, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = TideColors.textSecondary)
        }
    }
}
