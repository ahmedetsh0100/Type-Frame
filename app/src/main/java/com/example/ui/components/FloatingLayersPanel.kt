package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.CanvasLayer
import com.example.model.ImageLayer
import com.example.model.LayerBlendMode
import com.example.model.PaintLayer
import com.example.model.ShapeLayer
import com.example.model.TextLayer
import com.example.ui.theme.LocalStudioPalette
import kotlin.math.roundToInt

@Composable
fun FloatingLayersPanel(
    layers: List<CanvasLayer>,
    selectedLayerId: String?,
    onClose: () -> Unit,
    onSelectLayer: (String) -> Unit,
    onReorderLayer: (fromActualIdx: Int, toActualIdx: Int) -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit,
    onToggleVisibility: (String) -> Unit,
    onToggleLock: (String) -> Unit,
    onDuplicate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onOpacityChanged: (String, Float) -> Unit,
    onBlendModeChanged: (String, LayerBlendMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalStudioPalette.current
    val density = LocalDensity.current
    var renamingLayer by remember { mutableStateOf<CanvasLayer?>(null) }

    val selectedLayer = layers.find { it.id == selectedLayerId }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = palette.surface.copy(alpha = 0.96f),
        tonalElevation = 10.dp,
        shadowElevation = 12.dp,
        modifier = modifier
            .widthIn(min = 276.dp, max = 330.dp)
            .border(1.dp, palette.border, RoundedCornerShape(18.dp))
            .testTag("floating_layers_panel")
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        "Layers (${layers.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.textPrimary
                    )
                    Text(
                        "Drag handle to reorder • Front on top",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textMuted
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp).testTag("close_layers_panel_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close layers panel", tint = palette.textSecondary)
                }
            }

            HorizontalDivider(color = palette.border)

            // Selected layer opacity & blend mode controls
            if (selectedLayer != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.surfaceElevated, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Layer Opacity", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                        Text("${(selectedLayer.opacity * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = palette.textPrimary)
                    }
                    Slider(
                        value = selectedLayer.opacity,
                        onValueChange = { onOpacityChanged(selectedLayer.id, it) },
                        valueRange = 0.02f..1f,
                        colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent),
                        modifier = Modifier.height(26.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Text("Blend:", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                        LayerBlendMode.entries.forEach { mode ->
                            val active = selectedLayer.blendMode == mode
                            Surface(
                                onClick = { onBlendModeChanged(selectedLayer.id, mode) },
                                color = if (active) palette.accent else palette.surfaceHighest,
                                shape = RoundedCornerShape(7.dp)
                            ) {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (active) Color.White else palette.textPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (layers.isEmpty()) {
                Text(
                    "No layers yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textMuted,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                val reversed = layers.asReversed()
                val rowHeightPx = with(density) { 58.dp.toPx() }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    reversed.forEachIndexed { revIdx, layer ->
                        val actualIdx = layers.lastIndex - revIdx
                        val isSelected = layer.id == selectedLayerId
                        var dragAccumY by remember(layer.id, actualIdx) { mutableFloatStateOf(0f) }

                        val icon = when (layer) {
                            is ImageLayer -> Icons.Default.Image
                            is TextLayer -> Icons.Default.TextFields
                            is ShapeLayer -> Icons.Default.Category
                            is PaintLayer -> Icons.Default.Brush
                        }

                        Surface(
                            color = if (isSelected) palette.accentSoft else palette.surfaceHighest,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) palette.accent else palette.border,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { onSelectLayer(layer.id) }
                                .testTag("layer_row_${layer.id}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DragHandle,
                                    contentDescription = "Drag to reorder ${layer.name}",
                                    tint = palette.textMuted,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .pointerInput(layer.id, actualIdx, layers.size) {
                                            detectVerticalDragGestures(
                                                onDragStart = { dragAccumY = 0f },
                                                onVerticalDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dragAccumY += dragAmount
                                                    val steps = (dragAccumY / rowHeightPx).toInt()
                                                    if (steps != 0) {
                                                        // Moving down in reversed list = lower actual index
                                                        val targetActual = (actualIdx - steps).coerceIn(0, layers.lastIndex)
                                                        if (targetActual != actualIdx) {
                                                            onReorderLayer(actualIdx, targetActual)
                                                            dragAccumY -= steps * rowHeightPx
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) palette.accent else palette.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = layer.name,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (layer.isVisible) palette.textPrimary else palette.textMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (layer.blendMode != LayerBlendMode.NORMAL || layer.opacity < 0.99f) {
                                        Text(
                                            text = "${layer.blendMode.label} • ${(layer.opacity * 100).roundToInt()}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = palette.textMuted,
                                            maxLines = 1
                                        )
                                    }
                                }

                                IconButton(onClick = { renamingLayer = layer }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Edit, contentDescription = "Rename layer", tint = palette.textSecondary, modifier = Modifier.size(15.dp))
                                }
                                IconButton(
                                    onClick = { onMoveUp(layer.id) },
                                    enabled = actualIdx < layers.lastIndex,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ArrowUpward,
                                        contentDescription = "Bring forward",
                                        tint = if (actualIdx < layers.lastIndex) palette.textPrimary else palette.textMuted,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onMoveDown(layer.id) },
                                    enabled = actualIdx > 0,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ArrowDownward,
                                        contentDescription = "Send backward",
                                        tint = if (actualIdx > 0) palette.textPrimary else palette.textMuted,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                IconButton(onClick = { onToggleVisibility(layer.id) }, modifier = Modifier.size(28.dp)) {
                                    Icon(
                                        if (layer.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Toggle visibility",
                                        tint = if (layer.isVisible) palette.mint else palette.textMuted,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                IconButton(onClick = { onToggleLock(layer.id) }, modifier = Modifier.size(28.dp)) {
                                    Icon(
                                        if (layer.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                        contentDescription = "Toggle lock",
                                        tint = if (layer.isLocked) palette.accent else palette.textMuted,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                IconButton(onClick = { onDuplicate(layer.id) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate layer", tint = palette.textSecondary, modifier = Modifier.size(14.dp))
                                }
                                IconButton(onClick = { onDelete(layer.id) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete layer", tint = palette.danger, modifier = Modifier.size(15.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    renamingLayer?.let { target ->
        var draft by remember(target.id) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renamingLayer = null },
            containerColor = palette.surface,
            title = { Text("Rename Layer", style = MaterialTheme.typography.titleMedium) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    label = { Text("Layer name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    onRename(target.id, draft)
                    renamingLayer = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingLayer = null }) { Text("Cancel") }
            }
        )
    }
}
