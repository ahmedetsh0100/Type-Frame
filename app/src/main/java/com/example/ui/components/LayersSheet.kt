package com.example.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.CanvasLayer
import com.example.model.ImageLayer
import com.example.model.ShapeLayer
import com.example.model.TextLayer
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCoral
import com.example.ui.theme.StudioDanger
import com.example.ui.theme.StudioMint
import com.example.ui.theme.StudioSurfaceHighest
import com.example.ui.theme.StudioTextMuted
import com.example.ui.theme.StudioTextSecondary

@Composable
fun LayersSheet(
    layers: List<CanvasLayer>,
    selectedLayerId: String?,
    onSelectLayer: (String) -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit,
    onToggleVisibility: (String) -> Unit,
    onToggleLock: (String) -> Unit,
    onDuplicate: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Layers (${layers.size}) • Top layer draws in front",
            style = MaterialTheme.typography.labelLarge,
            color = StudioTextSecondary
        )

        if (layers.isEmpty()) {
            Text(
                "No layers yet. Add a Photo, Text, or Shape below.",
                style = MaterialTheme.typography.bodySmall,
                color = StudioTextMuted
            )
            return@Column
        }

        // Display top-most layer first
        val reversed = layers.asReversed()
        reversed.forEachIndexed { revIdx, layer ->
            val actualIdx = layers.lastIndex - revIdx
            val isSelected = layer.id == selectedLayerId
            val icon = when (layer) {
                is ImageLayer -> Icons.Default.Image
                is TextLayer -> Icons.Default.TextFields
                is ShapeLayer -> Icons.Default.Category
            }
            val subtitle = when (layer) {
                is ImageLayer -> "Photo • ${layer.naturalWidthPx}×${layer.naturalHeightPx}"
                is TextLayer -> layer.text.replace('\n', ' ')
                is ShapeLayer -> "${layer.shapeKind.name} • ${layer.widthPx.toInt()}×${layer.heightPx.toInt()}"
            }

            Surface(
                color = StudioSurfaceHighest,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) StudioCoral else StudioBorder,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelectLayer(layer.id) }
                    .testTag("layer_row_${layer.id}")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = layer.name,
                        tint = if (isSelected) StudioCoral else StudioTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = layer.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (layer.isVisible) Color.White else StudioTextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = StudioTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = { onMoveUp(layer.id) },
                        enabled = actualIdx < layers.lastIndex,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.ArrowUpward,
                            contentDescription = "Bring forward",
                            tint = if (actualIdx < layers.lastIndex) Color.White else StudioTextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { onMoveDown(layer.id) },
                        enabled = actualIdx > 0,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.ArrowDownward,
                            contentDescription = "Send backward",
                            tint = if (actualIdx > 0) Color.White else StudioTextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { onToggleVisibility(layer.id) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            if (layer.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle visibility",
                            tint = if (layer.isVisible) StudioMint else StudioTextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { onToggleLock(layer.id) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            if (layer.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = "Toggle lock",
                            tint = if (layer.isLocked) StudioCoral else StudioTextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { onDuplicate(layer.id) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Duplicate layer",
                            tint = StudioTextSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    IconButton(
                        onClick = { onDelete(layer.id) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete layer",
                            tint = StudioDanger,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
