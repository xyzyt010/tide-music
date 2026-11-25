package com.example.tidemusic.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.tidemusic.theme.TideColors

enum class ColorTarget(val title: String) {
    THEME("Theme Color (Background)"),
    HIGHLIGHT("Highlight Color (Accent)"),
    TEXT("Text Color (Primary)");
}

@Composable
fun RgbColorPickerDialog(
    target: ColorTarget,
    initialColor: Color,
    defaultColor: Color,
    onColorSelected: (Color) -> Unit,
    onResetToDefault: () -> Unit,
    onDismiss: () -> Unit,
) {
    var r by remember { mutableFloatStateOf(initialColor.red * 255f) }
    var g by remember { mutableFloatStateOf(initialColor.green * 255f) }
    var b by remember { mutableFloatStateOf(initialColor.blue * 255f) }

    val currentColor = Color(
        red = (r / 255f).coerceIn(0f, 1f),
        green = (g / 255f).coerceIn(0f, 1f),
        blue = (b / 255f).coerceIn(0f, 1f),
        alpha = 1f,
    )

    fun toHex(c: Color): String {
        val red = (c.red * 255).toInt().coerceIn(0, 255)
        val green = (c.green * 255).toInt().coerceIn(0, 255)
        val blue = (c.blue * 255).toInt().coerceIn(0, 255)
        return String.format("%02X%02X%02X", red, green, blue)
    }

    var hexText by remember { mutableStateOf(toHex(currentColor)) }
    val focusManager = LocalFocusManager.current

    fun updateFromRgb(newR: Float, newG: Float, newB: Float) {
        r = newR.coerceIn(0f, 255f)
        g = newG.coerceIn(0f, 255f)
        b = newB.coerceIn(0f, 255f)
        hexText = toHex(Color(r / 255f, g / 255f, b / 255f, 1f))
    }

    fun updateFromHex(hex: String) {
        val clean = hex.removePrefix("#").trim()
        if (clean.length == 6) {
            try {
                val red = clean.substring(0, 2).toInt(16)
                val green = clean.substring(2, 4).toInt(16)
                val blue = clean.substring(4, 6).toInt(16)
                r = red.toFloat()
                g = green.toFloat()
                b = blue.toFloat()
            } catch (_: Exception) {}
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = TideColors.surfaceElevated,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 680.dp)
                .padding(vertical = 16.dp),
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Dialog Title
                Text(
                    text = target.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TideColors.textPrimary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Live Preview: Before vs After
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Current",
                            style = MaterialTheme.typography.labelSmall,
                            color = TideColors.textSecondary
                        )
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(initialColor)
                                .border(1.5.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "#${toHex(initialColor)}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TideColors.textSecondary
                        )
                    }

                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = TideColors.textSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp)
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "New Color",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = TideColors.textPrimary
                        )
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(currentColor)
                                .border(2.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (target == ColorTarget.TEXT) {
                                Text("Aa", color = currentColor, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "#${toHex(currentColor)}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = TideColors.textPrimary
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Manual Hex Input
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { input ->
                        val filtered = input.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }.take(6)
                        hexText = filtered
                        if (filtered.length == 6) {
                            updateFromHex(filtered)
                        }
                    },
                    label = { Text("Hex Code (#RRGGBB)") },
                    prefix = { Text("#", fontWeight = FontWeight.Bold) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        updateFromHex(hexText)
                    }),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TideColors.accent,
                        unfocusedBorderColor = TideColors.outline,
                        focusedTextColor = TideColors.textPrimary,
                        unfocusedTextColor = TideColors.textPrimary,
                    )
                )

                Spacer(Modifier.height(16.dp))

                // Continuous Hue Rainbow Gradient Slider
                Text(
                    text = "Hue Spectrum",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = TideColors.textSecondary,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                val hueColors = listOf(
                    Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.horizontalGradient(hueColors))
                        .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val ratio = (offset.x / size.width).coerceIn(0f, 1f)
                                val hsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(currentColor.toArgb(), hsv)
                                hsv[0] = ratio * 360f
                                if (hsv[1] < 0.15f) hsv[1] = 0.95f
                                if (hsv[2] < 0.15f) hsv[2] = 0.95f
                                val argb = android.graphics.Color.HSVToColor(hsv)
                                updateFromRgb(
                                    android.graphics.Color.red(argb).toFloat(),
                                    android.graphics.Color.green(argb).toFloat(),
                                    android.graphics.Color.blue(argb).toFloat()
                                )
                            }
                        }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures { change, _ ->
                                change.consume()
                                val ratio = (change.position.x / size.width).coerceIn(0f, 1f)
                                val hsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(currentColor.toArgb(), hsv)
                                hsv[0] = ratio * 360f
                                if (hsv[1] < 0.15f) hsv[1] = 0.95f
                                if (hsv[2] < 0.15f) hsv[2] = 0.95f
                                val argb = android.graphics.Color.HSVToColor(hsv)
                                updateFromRgb(
                                    android.graphics.Color.red(argb).toFloat(),
                                    android.graphics.Color.green(argb).toFloat(),
                                    android.graphics.Color.blue(argb).toFloat()
                                )
                            }
                        }
                )

                Spacer(Modifier.height(16.dp))

                // Individual Red, Green, Blue Sliders
                // Red Slider
                ColorSliderRow(
                    label = "R",
                    value = r,
                    max = 255f,
                    activeColor = Color(0xFFEF4444),
                    onValueChange = { updateFromRgb(it, g, b) }
                )

                // Green Slider
                ColorSliderRow(
                    label = "G",
                    value = g,
                    max = 255f,
                    activeColor = Color(0xFF10B981),
                    onValueChange = { updateFromRgb(r, it, b) }
                )

                // Blue Slider
                ColorSliderRow(
                    label = "B",
                    value = b,
                    max = 255f,
                    activeColor = Color(0xFF3B82F6),
                    onValueChange = { updateFromRgb(r, g, it) }
                )

                Spacer(Modifier.height(12.dp))

                // Quick Palette Swatches
                Text(
                    text = "Quick Presets",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = TideColors.textSecondary,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                val presetColors = when (target) {
                    ColorTarget.THEME -> listOf(
                        Color(0xFF000000), Color(0xFF0A0E17), Color(0xFF12161A),
                        Color(0xFF130E20), Color(0xFF181818), Color(0xFF1F2937),
                        Color(0xFFFFFFFF), Color(0xFFF3F4F6)
                    )
                    ColorTarget.HIGHLIGHT -> listOf(
                        Color(0xFF0781FA), Color(0xFF00E5FF), Color(0xFF10B981),
                        Color(0xFFF59E0B), Color(0xFFEF4444), Color(0xFFEC4899),
                        Color(0xFF8B5CF6), Color(0xFFE11D48)
                    )
                    ColorTarget.TEXT -> listOf(
                        Color(0xFFFFFFFF), Color(0xFFF3F4F6), Color(0xFFE5E7EB),
                        Color(0xFFE0F2FE), Color(0xFFFEF3C7), Color(0xFFFCE7F3),
                        Color(0xFF000000), Color(0xFF1F2937)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    presetColors.forEach { preset ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(preset)
                                .border(
                                    width = if (toHex(preset) == toHex(currentColor)) 2.5.dp else 1.dp,
                                    color = if (toHex(preset) == toHex(currentColor)) Color.White else Color.White.copy(alpha = 0.25f),
                                    shape = CircleShape
                                )
                                .clickable {
                                    updateFromRgb(preset.red * 255f, preset.green * 255f, preset.blue * 255f)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (toHex(preset) == toHex(currentColor)) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = if (preset.red * 0.299 + preset.green * 0.587 + preset.blue * 0.114 > 0.5) Color.Black else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            onResetToDefault()
                            onDismiss()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TideColors.error),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TideColors.error.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reset", fontSize = 12.sp)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel", fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                onColorSelected(currentColor)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TideColors.accent),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Apply", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorSliderRow(
    label: String,
    value: Float,
    max: Float,
    activeColor: Color,
    onValueChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label: ${value.toInt()}",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
            color = activeColor,
            modifier = Modifier.width(52.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..max,
            colors = SliderDefaults.colors(
                thumbColor = activeColor,
                activeTrackColor = activeColor,
                inactiveTrackColor = activeColor.copy(alpha = 0.2f),
            ),
            modifier = Modifier.weight(1f)
        )
    }
}
