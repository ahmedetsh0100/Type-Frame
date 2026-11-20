package com.example.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.CanvasLayer
import com.example.model.ShapeLayer
import com.example.model.TextLayer
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCoral
import com.example.ui.theme.StudioMint
import com.example.ui.theme.StudioSurfaceHighest
import com.example.ui.theme.StudioTextSecondary
import com.example.viewmodel.ColorTarget
import kotlin.math.roundToInt

private val curatedSwatches = listOf(
    0xFFFFFFFF.toInt(),
    0xFF0E1015.toInt(),
    0xFFFF5A36.toInt(),
    0xFFFFB84D.toInt(),
    0xFF2EE6A8.toInt(),
    0xFF5C9DFF.toInt(),
    0xFFE94D89.toInt(),
    0xFF9E7BFF.toInt(),
    0xFFF4E8D1.toInt(),
    0xFFD4AF37.toInt()
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerSheet(
    selectedLayer: CanvasLayer?,
    activeColorTarget: ColorTarget,
    currentColorArgb: Int,
    onSelectColorTarget: (ColorTarget) -> Unit,
    onColorChanged: (Int) -> Unit,
    onStartEyedropper: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hsv = remember(currentColorArgb) {
        FloatArray(3).also { AndroidColor.colorToHSV(currentColorArgb, it) }
    }
    var hue by remember(currentColorArgb) { mutableFloatStateOf(hsv[0]) }
    var sat by remember(currentColorArgb) { mutableFloatStateOf(hsv[1]) }
    var value by remember(currentColorArgb) { mutableFloatStateOf(hsv[2]) }
    var alpha by remember(currentColorArgb) { mutableFloatStateOf(AndroidColor.alpha(currentColorArgb) / 255f) }
    var hexText by remember(currentColorArgb) {
        mutableStateOf(Integer.toHexString(currentColorArgb).uppercase().padStart(8, '0').takeLast(6))
    }

    LaunchedEffect(currentColorArgb) {
        val fresh = FloatArray(3)
        AndroidColor.colorToHSV(currentColorArgb, fresh)
        hue = fresh[0]
        sat = fresh[1]
        value = fresh[2]
        alpha = AndroidColor.alpha(currentColorArgb) / 255f
        hexText = Integer.toHexString(currentColorArgb).uppercase().padStart(8, '0').takeLast(6)
    }

    fun emitColor(h: Float = hue, s: Float = sat, v: Float = value, a: Float = alpha) {
        val rgb = AndroidColor.HSVToColor((a.coerceIn(0f, 1f) * 255).roundToInt(), floatArrayOf(h, s, v))
        onColorChanged(rgb)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Target pills
        val availableTargets = when (selectedLayer) {
            is TextLayer -> listOf(
                ColorTarget.TEXT_FILL to "Fill",
                ColorTarget.TEXT_STROKE to "Stroke",
                ColorTarget.TEXT_SHADOW to "Shadow",
                ColorTarget.CANVAS_BG to "Canvas"
            )
            is ShapeLayer -> listOf(
                ColorTarget.SHAPE_STROKE to "Stroke",
                ColorTarget.SHAPE_FILL to "Fill",
                ColorTarget.CANVAS_BG to "Canvas"
            )
            else -> listOf(ColorTarget.CANVAS_BG to "Canvas")
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                availableTargets.forEach { (target, label) ->
                    FilterChip(
                        selected = activeColorTarget == target,
                        onClick = { onSelectColorTarget(target) },
                        label = { Text(label, style = MaterialTheme.typography.labelLarge) }
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Button(
                onClick = onStartEyedropper,
                colors = ButtonDefaults.buttonColors(containerColor = StudioMint, contentColor = Color(0xFF0F1014)),
                modifier = Modifier.testTag("eyedropper_button")
            ) {
                Icon(Icons.Default.Colorize, contentDescription = "Sample color from image", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Eyedropper")
            }
        }

        // Saturation & Value Spectrum Pad
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(118.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
        ) {
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(hue, alpha) {
                        detectTapGestures { pos ->
                            sat = (pos.x / size.width).coerceIn(0f, 1f)
                            value = (1f - pos.y / size.height).coerceIn(0f, 1f)
                            emitColor(hue, sat, value, alpha)
                        }
                    }
                    .pointerInput(hue, alpha) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            sat = (change.position.x / size.width).coerceIn(0f, 1f)
                            value = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                            emitColor(hue, sat, value, alpha)
                        }
                    }
            ) {
                val hueColor = Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f)))
                drawRect(brush = Brush.horizontalGradient(listOf(Color.White, hueColor)))
                drawRect(brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))

                val cx = sat * size.width
                val cy = (1f - value) * size.height
                drawCircle(Color.White, radius = 9.dp.toPx(), center = Offset(cx, cy), style = Stroke(2.5.dp.toPx()))
                drawCircle(Color.Black, radius = 10.5.dp.toPx(), center = Offset(cx, cy), style = Stroke(1.dp.toPx()))
            }
        }

        // Hue Spectrum Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(99.dp))
        ) {
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(sat, value, alpha) {
                        detectTapGestures { pos ->
                            hue = ((pos.x / size.width) * 360f).coerceIn(0f, 359.9f)
                            emitColor(hue, sat, value, alpha)
                        }
                    }
                    .pointerInput(sat, value, alpha) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            hue = ((change.position.x / size.width) * 360f).coerceIn(0f, 359.9f)
                            emitColor(hue, sat, value, alpha)
                        }
                    }
            ) {
                val rainbow = listOf(
                    Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                )
                drawRect(Brush.horizontalGradient(rainbow))
                val kx = (hue / 360f) * size.width
                drawCircle(Color.White, radius = 10.dp.toPx(), center = Offset(kx, size.height / 2f), style = Stroke(2.5.dp.toPx()))
            }
        }

        // Alpha Slider + Hex Input
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(currentColorArgb))
                    .border(2.dp, Color.White, CircleShape)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Alpha ${(alpha * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = StudioTextSecondary
                )
                Slider(
                    value = alpha,
                    onValueChange = {
                        alpha = it
                        emitColor(hue, sat, value, it)
                    },
                    colors = SliderDefaults.colors(thumbColor = StudioCoral, activeTrackColor = StudioCoral)
                )
            }
            OutlinedTextField(
                value = hexText,
                onValueChange = { input ->
                    val clean = input.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }.take(6)
                    hexText = clean
                    if (clean.length == 6) {
                        val rgb = clean.toInt(16)
                        val aInt = (alpha * 255).roundToInt().coerceIn(0, 255)
                        onColorChanged((aInt shl 24) or rgb)
                    }
                },
                prefix = { Text("#", style = MaterialTheme.typography.labelMedium) },
                singleLine = true,
                textStyle = MaterialTheme.typography.labelMedium,
                modifier = Modifier.width(116.dp)
            )
        }

        // Quick Swatches
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            curatedSwatches.forEach { swatch ->
                val selected = (swatch and 0x00FFFFFF) == (currentColorArgb and 0x00FFFFFF)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(swatch))
                        .border(
                            width = if (selected) 2.5.dp else 1.dp,
                            color = if (selected) StudioCoral else StudioBorder,
                            shape = CircleShape
                        )
                        .clickable {
                            val aInt = (alpha * 255).roundToInt().coerceIn(16, 255)
                            onColorChanged((aInt shl 24) or (swatch and 0x00FFFFFF))
                        }
                )
            }
        }
    }
}
