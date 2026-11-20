package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.CropRect
import com.example.model.ImageLayer
import com.example.render.CanvasRenderer
import com.example.ui.theme.LocalStudioPalette
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

private enum class CropHandle {
    NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, MOVE
}

@Composable
fun ImageCropDialog(
    imageLayer: ImageLayer,
    onApply: (CropRect) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalStudioPalette.current
    val bitmap = remember(imageLayer.imagePath) {
        CanvasRenderer.getPreviewBitmap(imageLayer.imagePath, maxDimensionPx = 1280)
    }
    var crop by remember(imageLayer.id) { mutableStateOf(imageLayer.cropRect) }
    var aspectPreset by remember { mutableStateOf<Float?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = palette.background,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Text("Crop Photo", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { crop = CropRect(); aspectPreset = null }) { Text("Reset") }
                }

                Spacer(Modifier.height(12.dp))

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(palette.surface, RoundedCornerShape(16.dp))
                ) {
                    if (bitmap != null) {
                        val density = LocalDensity.current
                        val boxW = with(density) { maxWidth.toPx() }
                        val boxH = with(density) { maxHeight.toPx() }
                        val pad = with(density) { 24.dp.toPx() }
                        val scale = min(
                            (boxW - pad * 2) / bitmap.width,
                            (boxH - pad * 2) / bitmap.height
                        )
                        val imgW = bitmap.width * scale
                        val imgH = bitmap.height * scale
                        val imgLeft = (boxW - imgW) / 2f
                        val imgTop = (boxH - imgH) / 2f

                        var activeHandle by remember { mutableStateOf(CropHandle.NONE) }

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(imgLeft, imgTop, imgW, imgH, aspectPreset) {
                                    detectDragGestures(
                                        onDragStart = { pos ->
                                            val cl = imgLeft + crop.left * imgW
                                            val ct = imgTop + crop.top * imgH
                                            val cr = imgLeft + crop.right * imgW
                                            val cb = imgTop + crop.bottom * imgH
                                            val r = 44f
                                            activeHandle = when {
                                                hypot(pos.x - cl, pos.y - ct) < r -> CropHandle.TOP_LEFT
                                                hypot(pos.x - cr, pos.y - ct) < r -> CropHandle.TOP_RIGHT
                                                hypot(pos.x - cl, pos.y - cb) < r -> CropHandle.BOTTOM_LEFT
                                                hypot(pos.x - cr, pos.y - cb) < r -> CropHandle.BOTTOM_RIGHT
                                                pos.x in cl..cr && pos.y in ct..cb -> CropHandle.MOVE
                                                else -> CropHandle.NONE
                                            }
                                        },
                                        onDrag = { change, drag ->
                                            change.consume()
                                            val dx = drag.x / imgW
                                            val dy = drag.y / imgH
                                            val minSpan = 0.08f
                                            crop = when (activeHandle) {
                                                CropHandle.MOVE -> {
                                                    val w = crop.right - crop.left
                                                    val h = crop.bottom - crop.top
                                                    val nl = (crop.left + dx).coerceIn(0f, 1f - w)
                                                    val nt = (crop.top + dy).coerceIn(0f, 1f - h)
                                                    CropRect(nl, nt, nl + w, nt + h)
                                                }
                                                CropHandle.TOP_LEFT -> crop.copy(
                                                    left = (crop.left + dx).coerceIn(0f, crop.right - minSpan),
                                                    top = (crop.top + dy).coerceIn(0f, crop.bottom - minSpan)
                                                )
                                                CropHandle.TOP_RIGHT -> crop.copy(
                                                    right = (crop.right + dx).coerceIn(crop.left + minSpan, 1f),
                                                    top = (crop.top + dy).coerceIn(0f, crop.bottom - minSpan)
                                                )
                                                CropHandle.BOTTOM_LEFT -> crop.copy(
                                                    left = (crop.left + dx).coerceIn(0f, crop.right - minSpan),
                                                    bottom = (crop.bottom + dy).coerceIn(crop.top + minSpan, 1f)
                                                )
                                                CropHandle.BOTTOM_RIGHT -> crop.copy(
                                                    right = (crop.right + dx).coerceIn(crop.left + minSpan, 1f),
                                                    bottom = (crop.bottom + dy).coerceIn(crop.top + minSpan, 1f)
                                                )
                                                CropHandle.NONE -> crop
                                            }
                                        },
                                        onDragEnd = { activeHandle = CropHandle.NONE }
                                    )
                                }
                        ) {
                            drawImage(
                                image = bitmap.asImageBitmap(),
                                dstOffset = IntOffset(imgLeft.roundToInt(), imgTop.roundToInt()),
                                dstSize = IntSize(imgW.roundToInt(), imgH.roundToInt())
                            )

                            val cl = imgLeft + crop.left * imgW
                            val ct = imgTop + crop.top * imgH
                            val cw = (crop.right - crop.left) * imgW
                            val ch = (crop.bottom - crop.top) * imgH

                            // Dim outside crop
                            val dim = Color.Black.copy(alpha = 0.58f)
                            drawRect(dim, Offset(imgLeft, imgTop), Size(imgW, ct - imgTop))
                            drawRect(dim, Offset(imgLeft, ct + ch), Size(imgW, (imgTop + imgH) - (ct + ch)))
                            drawRect(dim, Offset(imgLeft, ct), Size(cl - imgLeft, ch))
                            drawRect(dim, Offset(cl + cw, ct), Size((imgLeft + imgW) - (cl + cw), ch))

                            drawRect(
                                color = Color.White,
                                topLeft = Offset(cl, ct),
                                size = Size(cw, ch),
                                style = Stroke(width = 2.dp.toPx())
                            )
                            val corners = listOf(
                                Offset(cl, ct),
                                Offset(cl + cw, ct),
                                Offset(cl, ct + ch),
                                Offset(cl + cw, ct + ch)
                            )
                            corners.forEach { pt ->
                                drawCircle(palette.accent, radius = 9.dp.toPx(), center = pt)
                                drawCircle(Color.White, radius = 9.dp.toPx(), center = pt, style = Stroke(2.dp.toPx()))
                            }
                        }
                    } else {
                        Text(
                            "Unable to load image",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val ratios = listOf(
                        "Free" to null,
                        "1:1" to 1f,
                        "4:5" to 0.8f,
                        "3:4" to 0.75f,
                        "16:9" to (16f / 9f)
                    )
                    ratios.forEach { (label, ratio) ->
                        FilterChip(
                            selected = aspectPreset == ratio,
                            onClick = {
                                aspectPreset = ratio
                                if (ratio != null && bitmap != null) {
                                    val imgAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
                                    val normRatio = ratio / imgAspect
                                    val w: Float
                                    val h: Float
                                    if (normRatio >= 1f) {
                                        w = 0.9f
                                        h = (0.9f / normRatio).coerceIn(0.15f, 0.9f)
                                    } else {
                                        h = 0.9f
                                        w = (0.9f * normRatio).coerceIn(0.15f, 0.9f)
                                    }
                                    crop = CropRect(
                                        left = (1f - w) / 2f,
                                        top = (1f - h) / 2f,
                                        right = (1f + w) / 2f,
                                        bottom = (1f + h) / 2f
                                    )
                                }
                            },
                            label = { Text(label) }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }
                    Button(onClick = { onApply(crop) }, modifier = Modifier.weight(1f)) {
                        Text("Apply Crop")
                    }
                }
            }
        }
    }
}
