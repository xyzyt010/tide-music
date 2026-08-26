package com.example.tidemusic.ui.equalizer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tidemusic.theme.TideColors
import java.util.Locale
import kotlin.math.abs

/**
 * A standard, high-precision vertical band slider for the Equalizer.
 * Features a straight line track, 0dB center indicator, active fill line,
 * round thumb knob, live dB readout on top, and frequency tag on bottom.
 */
@Composable
fun VerticalBandSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = -12f..12f,
    sliderHeight: Dp = 160.dp,
    enabled: Boolean = true,
) {
    val currentVal by rememberUpdatedState(value)
    val onValChange by rememberUpdatedState(onValueChange)

    val minVal = valueRange.start
    val maxVal = valueRange.endInclusive
    val totalRange = maxVal - minVal

    // dB value display string (+3.5 dB, 0 dB, -2.0 dB)
    val dbText = remember(value) {
        val rounded = Math.round(value * 10f) / 10f
        when {
            abs(rounded) < 0.15f -> "0 dB"
            rounded > 0f -> String.format(Locale.US, "+%.1f", rounded)
            else -> String.format(Locale.US, "%.1f", rounded)
        }
    }

    Column(
        modifier = modifier.width(44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Live dB readout on top
        Text(
            text = dbText,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = if (abs(value) > 0.2f) FontWeight.Bold else FontWeight.Normal,
            ),
            color = if (!enabled) TideColors.textSecondary.copy(alpha = 0.5f)
            else if (abs(value) > 0.2f) TideColors.accent
            else TideColors.textSecondary,
            maxLines = 1,
        )

        Spacer(Modifier.height(8.dp))

        // Vertical straight slider canvas
        val trackBg = if (enabled) TideColors.outline.copy(alpha = 0.8f) else TideColors.outline.copy(alpha = 0.3f)
        val centerTickColor = if (enabled) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.15f)
        val activeColor = if (enabled) TideColors.accent else TideColors.textSecondary.copy(alpha = 0.4f)
        val thumbColor = if (enabled) TideColors.accent else TideColors.textSecondary.copy(alpha = 0.5f)

        Box(
            modifier = Modifier
                .width(44.dp)
                .height(sliderHeight)
                .pointerInput(enabled, minVal, maxVal) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { offset ->
                        val thumbRadiusPx = 10.dp.toPx()
                        val trackHeight = size.height - (2 * thumbRadiusPx)
                        val touchYInTrack = (offset.y - thumbRadiusPx).coerceIn(0f, trackHeight)
                        val fraction = 1f - (touchYInTrack / trackHeight)
                        var newVal = minVal + fraction * totalRange
                        if (abs(newVal) < 0.45f) newVal = 0f
                        onValChange(newVal.coerceIn(minVal, maxVal))
                    }
                }
                .pointerInput(enabled, minVal, maxVal) {
                    if (!enabled) return@pointerInput
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val thumbRadiusPx = 10.dp.toPx()
                            val trackHeight = size.height - (2 * thumbRadiusPx)
                            if (trackHeight > 0) {
                                val deltaFraction = -(dragAmount / trackHeight)
                                var newVal = currentVal + deltaFraction * totalRange
                                if (abs(newVal) < 0.45f && abs(dragAmount) < 8f) newVal = 0f
                                onValChange(newVal.coerceIn(minVal, maxVal))
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val thumbRadius = 9.dp.toPx()
                val topY = thumbRadius
                val bottomY = size.height - thumbRadius
                val usableHeight = bottomY - topY
                val zeroY = topY + usableHeight * (maxVal / totalRange)

                val fraction = ((currentVal - minVal) / totalRange).coerceIn(0f, 1f)
                val thumbY = bottomY - (fraction * usableHeight)

                // 1. Inactive full vertical track line
                drawLine(
                    color = trackBg,
                    start = Offset(cx, topY),
                    end = Offset(cx, bottomY),
                    strokeWidth = 3.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )

                // 2. 0 dB Center Horizontal Notch
                drawLine(
                    color = centerTickColor,
                    start = Offset(cx - 7.dp.toPx(), zeroY),
                    end = Offset(cx + 7.dp.toPx(), zeroY),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )

                // 3. Active Track Line from 0 dB to Thumb
                if (abs(thumbY - zeroY) > 1f) {
                    drawLine(
                        color = activeColor,
                        start = Offset(cx, zeroY),
                        end = Offset(cx, thumbY),
                        strokeWidth = 3.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }

                // 4. Outer Thumb Glow / Ring
                drawCircle(
                    color = Color.Black.copy(alpha = 0.4f),
                    radius = thumbRadius + 2.5.dp.toPx(),
                    center = Offset(cx, thumbY),
                )

                // 5. Standard Round Thumb Knob
                drawCircle(
                    color = thumbColor,
                    radius = thumbRadius,
                    center = Offset(cx, thumbY),
                )

                // 6. Thumb Inner Center Dot (white)
                drawCircle(
                    color = if (enabled) Color.White else Color.Transparent,
                    radius = 3.dp.toPx(),
                    center = Offset(cx, thumbY),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Frequency label underneath
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = if (enabled) TideColors.textPrimary else TideColors.textSecondary.copy(alpha = 0.5f),
            maxLines = 1,
        )
    }
}
