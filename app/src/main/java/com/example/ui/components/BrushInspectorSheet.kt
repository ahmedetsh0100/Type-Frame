package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.CanvasLayer
import com.example.model.ImageLayer
import com.example.ui.theme.LocalStudioPalette
import com.example.viewmodel.BrushSettings
import kotlin.math.roundToInt

@Composable
fun BrushInspectorSheet(
    brushSettings: BrushSettings,
    selectedLayer: CanvasLayer?,
    onUpdateBrush: ((BrushSettings) -> BrushSettings) -> Unit,
    onAddPaintLayer: () -> Unit,
    onPickBrushColor: () -> Unit,
    onStartEyedropper: () -> Unit,
    onRestoreErasedImage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalStudioPalette.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FilterChip(
                selected = !brushSettings.isEraser,
                onClick = { onUpdateBrush { it.copy(isEraser = false) } },
                leadingIcon = { Icon(Icons.Default.Brush, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text("Brush") },
                modifier = Modifier.testTag("brush_mode_chip")
            )
            FilterChip(
                selected = brushSettings.isEraser,
                onClick = { onUpdateBrush { it.copy(isEraser = true) } },
                leadingIcon = { Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text("Eraser") },
                modifier = Modifier.testTag("eraser_mode_chip")
            )

            Spacer(Modifier.weight(1f))

            if (!brushSettings.isEraser) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(brushSettings.colorArgb))
                        .border(1.5.dp, palette.border, CircleShape)
                        .clickable(onClick = onPickBrushColor)
                )
                Surface(
                    onClick = onStartEyedropper,
                    color = palette.mintSoft,
                    shape = RoundedCornerShape(99.dp)
                ) {
                    Icon(
                        Icons.Default.Colorize,
                        contentDescription = "Sample brush color",
                        tint = palette.mint,
                        modifier = Modifier.padding(7.dp).size(16.dp)
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = onAddPaintLayer,
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                modifier = Modifier.weight(1f).testTag("add_paint_layer_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("New Paint Layer")
            }

            if (selectedLayer is ImageLayer && selectedLayer.eraseMaskStrokes.isNotEmpty()) {
                OutlinedButton(
                    onClick = { onRestoreErasedImage(selectedLayer.id) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Restore Photo")
                }
            }
        }

        val hint = when {
            brushSettings.isEraser && selectedLayer is ImageLayer ->
                "Erasing non-destructively on photo '${selectedLayer.name}'. Drag on canvas."
            brushSettings.isEraser ->
                "Erasing on active Paint Layer. Drag on canvas."
            else ->
                "Drag on canvas to paint on dedicated Paint Layer."
        }
        Text(text = hint, style = MaterialTheme.typography.labelSmall, color = palette.mint)

        StudioSliderRow(
            label = "Size",
            valueLabel = "${brushSettings.sizePx.roundToInt()} px",
            value = brushSettings.sizePx,
            range = 4f..180f,
            onValueChange = { s -> onUpdateBrush { it.copy(sizePx = s) } }
        )
        StudioSliderRow(
            label = "Hardness (Soft ↔ Crisp)",
            valueLabel = "${(brushSettings.hardness * 100).roundToInt()}%",
            value = brushSettings.hardness,
            range = 0.05f..1f,
            onValueChange = { h -> onUpdateBrush { it.copy(hardness = h) } }
        )
        StudioSliderRow(
            label = "Opacity",
            valueLabel = "${(brushSettings.opacity * 100).roundToInt()}%",
            value = brushSettings.opacity,
            range = 0.05f..1f,
            onValueChange = { o -> onUpdateBrush { it.copy(opacity = o) } }
        )
        StudioSliderRow(
            label = "Flow",
            valueLabel = "${(brushSettings.flow * 100).roundToInt()}%",
            value = brushSettings.flow,
            range = 0.1f..1f,
            onValueChange = { f -> onUpdateBrush { it.copy(flow = f) } }
        )
    }
}
