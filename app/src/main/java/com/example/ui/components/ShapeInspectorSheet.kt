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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.ShapeKind
import com.example.model.ShapeLayer
import com.example.ui.theme.LocalStudioPalette
import com.example.viewmodel.ColorTarget
import kotlin.math.roundToInt

@Composable
fun ShapeInspectorSheet(
    shapeLayer: ShapeLayer?,
    onAddShape: (ShapeKind) -> Unit,
    onUpdateShape: ((ShapeLayer) -> ShapeLayer) -> Unit,
    onUpdateOpacity: (Float) -> Unit,
    onPickColorTarget: (ColorTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalStudioPalette.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(onClick = { onAddShape(ShapeKind.LINE) }, modifier = Modifier.weight(1f)) {
                Text("+ Line")
            }
            Button(onClick = { onAddShape(ShapeKind.RECTANGLE) }, modifier = Modifier.weight(1f)) {
                Text("+ Rect")
            }
            Button(onClick = { onAddShape(ShapeKind.CIRCLE) }, modifier = Modifier.weight(1f)) {
                Text("+ Circle")
            }
        }

        if (shapeLayer == null) {
            Text(
                "Tap + Line, + Rect, or + Circle to place a precision vector shape.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary
            )
            return@Column
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            ShapeKind.entries.forEach { kind ->
                FilterChip(
                    selected = shapeLayer.shapeKind == kind,
                    onClick = { onUpdateShape { it.copy(shapeKind = kind) } },
                    label = { Text(kind.name.lowercase().replaceFirstChar { it.uppercase() }) }
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            NumericDimensionField(
                label = if (shapeLayer.shapeKind == ShapeKind.LINE) "Length (px)" else "Width (px)",
                valuePx = shapeLayer.widthPx,
                onCommit = { v -> onUpdateShape { it.copy(widthPx = v.coerceIn(10f, 4096f)) } },
                modifier = Modifier.weight(1f)
            )
            if (shapeLayer.shapeKind != ShapeKind.LINE) {
                NumericDimensionField(
                    label = if (shapeLayer.shapeKind == ShapeKind.CIRCLE) "Height / Ø (px)" else "Height (px)",
                    valuePx = shapeLayer.heightPx,
                    onCommit = { v -> onUpdateShape { it.copy(heightPx = v.coerceIn(10f, 4096f)) } },
                    modifier = Modifier.weight(1f)
                )
            }
            NumericDimensionField(
                label = "Thickness (px)",
                valuePx = shapeLayer.strokeWidthPx,
                onCommit = { v -> onUpdateShape { it.copy(strokeWidthPx = v.coerceIn(1f, 200f)) } },
                modifier = Modifier.weight(1f)
            )
        }

        StudioSliderRow(
            label = if (shapeLayer.shapeKind == ShapeKind.LINE) "Length" else "Width",
            valueLabel = "${shapeLayer.widthPx.roundToInt()} px",
            value = shapeLayer.widthPx,
            range = 20f..2000f,
            onValueChange = { v -> onUpdateShape { it.copy(widthPx = v) } }
        )

        if (shapeLayer.shapeKind != ShapeKind.LINE) {
            StudioSliderRow(
                label = "Height",
                valueLabel = "${shapeLayer.heightPx.roundToInt()} px",
                value = shapeLayer.heightPx,
                range = 20f..2000f,
                onValueChange = { v -> onUpdateShape { it.copy(heightPx = v) } }
            )
        }

        StudioSliderRow(
            label = "Thickness (Stroke)",
            valueLabel = "${shapeLayer.strokeWidthPx.roundToInt()} px",
            value = shapeLayer.strokeWidthPx,
            range = 1f..80f,
            onValueChange = { v -> onUpdateShape { it.copy(strokeWidthPx = v) } }
        )

        if (shapeLayer.shapeKind == ShapeKind.RECTANGLE) {
            StudioSliderRow(
                label = "Corner Radius",
                valueLabel = "${shapeLayer.cornerRadiusPx.roundToInt()} px",
                value = shapeLayer.cornerRadiusPx,
                range = 0f..240f,
                onValueChange = { v -> onUpdateShape { it.copy(cornerRadiusPx = v) } }
            )
        }

        Surface(
            color = palette.surfaceElevated,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(shapeLayer.strokeColorArgb))
                                .border(1.5.dp, palette.border, CircleShape)
                                .clickable { onPickColorTarget(ColorTarget.SHAPE_STROKE) }
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Stroke Color", style = MaterialTheme.typography.titleSmall, color = palette.textPrimary)
                    }
                    Switch(
                        checked = shapeLayer.strokeEnabled,
                        onCheckedChange = { e -> onUpdateShape { it.copy(strokeEnabled = e) } },
                        colors = SwitchDefaults.colors(checkedTrackColor = palette.accent)
                    )
                }

                if (shapeLayer.shapeKind != ShapeKind.LINE) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color(shapeLayer.fillColorArgb))
                                    .border(1.5.dp, palette.border, CircleShape)
                                    .clickable { onPickColorTarget(ColorTarget.SHAPE_FILL) }
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Fill Color", style = MaterialTheme.typography.titleSmall, color = palette.textPrimary)
                        }
                        Switch(
                            checked = shapeLayer.fillEnabled,
                            onCheckedChange = { e -> onUpdateShape { it.copy(fillEnabled = e) } },
                            colors = SwitchDefaults.colors(checkedTrackColor = palette.accent)
                        )
                    }
                }
            }
        }

        StudioSliderRow(
            label = "Opacity",
            valueLabel = "${(shapeLayer.opacity * 100).roundToInt()}%",
            value = shapeLayer.opacity,
            range = 0.05f..1f,
            onValueChange = onUpdateOpacity
        )
    }
}

@Composable
private fun NumericDimensionField(
    label: String,
    valuePx: Float,
    onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember(valuePx.roundToInt()) { mutableStateOf(valuePx.roundToInt().toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val filtered = raw.filter { it.isDigit() }.take(4)
            text = filtered
            filtered.toFloatOrNull()?.let { onCommit(it) }
        },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.labelMedium,
        modifier = modifier
    )
}
