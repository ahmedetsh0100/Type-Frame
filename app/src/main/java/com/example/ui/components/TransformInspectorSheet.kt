package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.CanvasLayer
import com.example.model.ImageLayer
import com.example.model.ProjectDocument
import com.example.model.ShapeKind
import com.example.model.ShapeLayer
import com.example.model.TextLayer
import com.example.ui.theme.LocalStudioPalette
import kotlin.math.roundToInt

@Composable
fun TransformInspectorSheet(
    document: ProjectDocument,
    layer: CanvasLayer?,
    onOpenCrop: () -> Unit,
    onUpdateLayer: (CanvasLayer) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalStudioPalette.current
    if (layer == null) {
        Column(modifier = modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                "Tap any layer on the canvas to resize, stretch, rotate, crop, or reposition.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary
            )
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (layer is ImageLayer) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onOpenCrop,
                    modifier = Modifier.weight(1f).testTag("open_crop_button")
                ) {
                    Icon(Icons.Default.Crop, contentDescription = "Crop image")
                    Spacer(Modifier.width(8.dp))
                    Text("Crop Photo")
                }
                OutlinedButton(
                    onClick = {
                        val scale = kotlin.math.min(
                            document.canvasWidthPx / layer.widthPx,
                            document.canvasHeightPx / layer.heightPx
                        )
                        onUpdateLayer(
                            layer.copy(
                                widthPx = layer.widthPx * scale,
                                heightPx = layer.heightPx * scale,
                                xPx = document.canvasWidthPx / 2f,
                                yPx = document.canvasHeightPx / 2f,
                                scaleX = 1f,
                                scaleY = 1f
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Fit Canvas")
                }
            }

            StudioSliderRow(
                label = "Width (keeps aspect)",
                valueLabel = "${layer.widthPx.roundToInt()} × ${layer.heightPx.roundToInt()} px",
                value = layer.widthPx,
                range = 64f..(document.canvasWidthPx * 2f),
                onValueChange = { newW ->
                    val ratio = layer.heightPx / layer.widthPx.coerceAtLeast(1f)
                    onUpdateLayer(layer.copy(widthPx = newW, heightPx = (newW * ratio).coerceAtLeast(24f)))
                }
            )
        }

        if (layer is TextLayer) {
            StudioSliderRow(
                label = "Text Wrap Width",
                valueLabel = "${layer.boxWidthPx.roundToInt()} px",
                value = layer.boxWidthPx,
                range = 160f..(document.canvasWidthPx * 1.4f),
                onValueChange = { w -> onUpdateLayer(layer.copy(boxWidthPx = w)) }
            )
        }

        if (layer is ShapeLayer && layer.shapeKind != ShapeKind.LINE) {
            StudioSliderRow(
                label = "Scale Size",
                valueLabel = "${layer.widthPx.roundToInt()} × ${layer.heightPx.roundToInt()} px",
                value = layer.widthPx,
                range = 40f..(document.canvasWidthPx * 1.6f),
                onValueChange = { w ->
                    val ratio = layer.heightPx / layer.widthPx.coerceAtLeast(1f)
                    onUpdateLayer(layer.copy(widthPx = w, heightPx = (w * ratio).coerceAtLeast(20f)))
                }
            )
        }

        // Independent Horizontal & Vertical Stretch (Scale X / Scale Y)
        StudioSliderRow(
            label = "Stretch X (Width Scale)",
            valueLabel = String.format("%.2f×", layer.scaleX),
            value = layer.scaleX,
            range = 0.25f..3.0f,
            onValueChange = { sx -> onUpdateLayer(layer.withBaseTransform(scaleX = sx)) }
        )
        StudioSliderRow(
            label = "Stretch Y (Height Scale)",
            valueLabel = String.format("%.2f×", layer.scaleY),
            value = layer.scaleY,
            range = 0.25f..3.0f,
            onValueChange = { sy -> onUpdateLayer(layer.withBaseTransform(scaleY = sy)) }
        )
        if (kotlin.math.abs(layer.scaleX - 1f) > 0.02f || kotlin.math.abs(layer.scaleY - 1f) > 0.02f) {
            OutlinedButton(
                onClick = { onUpdateLayer(layer.withBaseTransform(scaleX = 1f, scaleY = 1f)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Reset Stretch (1:1)")
            }
        }

        // Rotation controls
        StudioSliderRow(
            label = "Rotation",
            valueLabel = "${layer.rotationDeg.roundToInt()}°",
            value = layer.rotationDeg,
            range = 0f..360f,
            onValueChange = { deg -> onUpdateLayer(layer.withBaseTransform(rotationDeg = deg)) }
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = {
                    val next = ((layer.rotationDeg - 90f) % 360f + 360f) % 360f
                    onUpdateLayer(layer.withBaseTransform(rotationDeg = next))
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.AutoMirrored.Filled.RotateLeft, contentDescription = "Rotate left 90 degrees")
                Spacer(Modifier.width(4.dp))
                Text("-90°")
            }
            OutlinedButton(
                onClick = { onUpdateLayer(layer.withBaseTransform(rotationDeg = 0f)) },
                modifier = Modifier.weight(1f)
            ) {
                Text("0°")
            }
            OutlinedButton(
                onClick = {
                    val next = (layer.rotationDeg + 90f) % 360f
                    onUpdateLayer(layer.withBaseTransform(rotationDeg = next))
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate right 90 degrees")
                Spacer(Modifier.width(4.dp))
                Text("+90°")
            }
        }

        StudioSliderRow(
            label = "Position X",
            valueLabel = "${layer.xPx.roundToInt()} px",
            value = layer.xPx,
            range = 0f..document.canvasWidthPx.toFloat(),
            onValueChange = { x -> onUpdateLayer(layer.withBaseTransform(xPx = x)) }
        )
        StudioSliderRow(
            label = "Position Y",
            valueLabel = "${layer.yPx.roundToInt()} px",
            value = layer.yPx,
            range = 0f..document.canvasHeightPx.toFloat(),
            onValueChange = { y -> onUpdateLayer(layer.withBaseTransform(yPx = y)) }
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = { onUpdateLayer(layer.withBaseTransform(xPx = document.canvasWidthPx / 2f)) },
                modifier = Modifier.weight(1f)
            ) { Text("Center H") }
            OutlinedButton(
                onClick = { onUpdateLayer(layer.withBaseTransform(yPx = document.canvasHeightPx / 2f)) },
                modifier = Modifier.weight(1f)
            ) { Text("Center V") }
        }

        StudioSliderRow(
            label = "Opacity",
            valueLabel = "${(layer.opacity * 100).roundToInt()}%",
            value = layer.opacity,
            range = 0.05f..1f,
            onValueChange = { op -> onUpdateLayer(layer.withBaseTransform(opacity = op)) }
        )
    }
}
