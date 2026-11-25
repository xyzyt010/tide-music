package com.example.tidemusic.ui.equalizer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.tidemusic.theme.TideColors

/**
 * A sleek, high-precision horizontal slider featuring a thin straight line track
 * and a standard round thumb knob with inner dot. Moves with 1-to-1 tactile precision.
 */
@Composable
fun ThinLineHorizontalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    sliderHeight: Dp = 28.dp,
    enabled: Boolean = true,
) {
    val currentVal by rememberUpdatedState(value)
    val onValChange by rememberUpdatedState(onValueChange)

    val minVal = valueRange.start
    val maxVal = valueRange.endInclusive
    val totalRange = (maxVal - minVal).coerceAtLeast(0.0001f)

    val trackBg = if (enabled) TideColors.outline.copy(alpha = 0.8f) else TideColors.outline.copy(alpha = 0.3f)
    val activeColor = if (enabled) TideColors.accent else TideColors.textSecondary.copy(alpha = 0.4f)
    val thumbColor = if (enabled) TideColors.accent else TideColors.textSecondary.copy(alpha = 0.5f)

    Box(
        modifier = modifier
            .height(sliderHeight)
            .pointerInput(enabled, minVal, maxVal) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    val thumbRadiusPx = 8.dp.toPx()
                    val trackWidth = size.width - (2 * thumbRadiusPx)
                    if (trackWidth > 0) {
                        val touchXInTrack = (offset.x - thumbRadiusPx).coerceIn(0f, trackWidth)
                        val fraction = touchXInTrack / trackWidth
                        val newVal = minVal + fraction * totalRange
                        onValChange(newVal.coerceIn(minVal, maxVal))
                    }
                }
            }
            .pointerInput(enabled, minVal, maxVal) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        val thumbRadiusPx = 8.dp.toPx()
                        val trackWidth = size.width - (2 * thumbRadiusPx)
                        if (trackWidth > 0) {
                            val touchXInTrack = (offset.x - thumbRadiusPx).coerceIn(0f, trackWidth)
                            val fraction = touchXInTrack / trackWidth
                            val newVal = minVal + fraction * totalRange
                            onValChange(newVal.coerceIn(minVal, maxVal))
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val thumbRadiusPx = 8.dp.toPx()
                        val trackWidth = size.width - (2 * thumbRadiusPx)
                        if (trackWidth > 0) {
                            val touchXInTrack = (change.position.x - thumbRadiusPx).coerceIn(0f, trackWidth)
                            val fraction = touchXInTrack / trackWidth
                            val newVal = minVal + fraction * totalRange
                            onValChange(newVal.coerceIn(minVal, maxVal))
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cy = size.height / 2f
            val thumbRadius = 8.dp.toPx()
            val leftX = thumbRadius
            val rightX = size.width - thumbRadius
            val usableWidth = (rightX - leftX).coerceAtLeast(1f)

            val fraction = ((currentVal - minVal) / totalRange).coerceIn(0f, 1f)
            val thumbX = leftX + (fraction * usableWidth)

            // 1. Inactive full horizontal track line (thin 2.5dp)
            drawLine(
                color = trackBg,
                start = Offset(leftX, cy),
                end = Offset(rightX, cy),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
            )

            // 2. Active track line from left to thumb
            if (thumbX > leftX) {
                drawLine(
                    color = activeColor,
                    start = Offset(leftX, cy),
                    end = Offset(thumbX, cy),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            // 3. Thumb Outer Shadow Ring
            drawCircle(
                color = Color.Black.copy(alpha = 0.35f),
                radius = thumbRadius + 1.5.dp.toPx(),
                center = Offset(thumbX, cy),
            )

            // 4. Standard Round Thumb Knob
            drawCircle(
                color = thumbColor,
                radius = thumbRadius,
                center = Offset(thumbX, cy),
            )

            // 5. Thumb Inner Center Dot (white)
            drawCircle(
                color = if (enabled) Color.White else Color.Transparent,
                radius = 2.5.dp.toPx(),
                center = Offset(thumbX, cy),
            )
        }
    }
}
