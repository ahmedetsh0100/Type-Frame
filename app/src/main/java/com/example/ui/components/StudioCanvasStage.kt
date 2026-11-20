package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.data.FontRepository
import com.example.model.CanvasLayer
import com.example.model.FontItem
import com.example.model.ImageLayer
import com.example.model.ProjectDocument
import com.example.model.ShapeKind
import com.example.model.ShapeLayer
import com.example.model.TextLayer
import com.example.render.CanvasRenderer
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCoral
import com.example.ui.theme.StudioMint
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurfaceElevated
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class DragAction {
    NONE,
    MOVE_LAYER,
    RESIZE_LAYER,
    ROTATE_LAYER
}

@Composable
fun StudioCanvasStage(
    document: ProjectDocument,
    selectedLayerId: String?,
    fontsById: Map<String, FontItem>,
    fontRepository: FontRepository,
    isEyedropperActive: Boolean,
    eyedropperBitmap: Bitmap?,
    onSelectLayer: (String?) -> Unit,
    onDoubleTapTextLayer: (TextLayer) -> Unit,
    onBeginGesture: () -> Unit,
    onUpdateLayerTransient: (CanvasLayer) -> Unit,
    onEndGesture: () -> Unit,
    onFinishEyedropper: (Int) -> Unit,
    onCancelEyedropper: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // Eyedropper live state
    var eyedropperTouchPx by remember(isEyedropperActive) { mutableStateOf<Offset?>(null) }
    var sampledColorArgb by remember(isEyedropperActive) { mutableIntStateOf(0xFFFF5A36.toInt()) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(StudioObsidian)
            .testTag("studio_canvas_stage")
    ) {
        val viewportW = with(density) { maxWidth.toPx() }
        val viewportH = with(density) { maxHeight.toPx() }
        val paddingPx = with(density) { 36.dp.toPx() }

        val fitScale = remember(viewportW, viewportH, document.canvasWidthPx, document.canvasHeightPx) {
            val availW = (viewportW - paddingPx).coerceAtLeast(100f)
            val availH = (viewportH - paddingPx).coerceAtLeast(100f)
            min(availW / document.canvasWidthPx, availH / document.canvasHeightPx)
        }

        val totalScale = fitScale * zoomScale
        val canvasDisplayW = document.canvasWidthPx * totalScale
        val canvasDisplayH = document.canvasHeightPx * totalScale
        val canvasOriginX = (viewportW - canvasDisplayW) / 2f + panOffset.x
        val canvasOriginY = (viewportH - canvasDisplayH) / 2f + panOffset.y

        fun screenToCanvas(screenPt: Offset): Offset {
            return Offset(
                (screenPt.x - canvasOriginX) / totalScale,
                (screenPt.y - canvasOriginY) / totalScale
            )
        }

        fun canvasToScreen(canvasPt: Offset): Offset {
            return Offset(
                canvasOriginX + canvasPt.x * totalScale,
                canvasOriginY + canvasPt.y * totalScale
            )
        }

        val selectedLayer = document.layers.find { it.id == selectedLayerId }
        val selectedBounds = remember(selectedLayer, fontsById, document.layers) {
            selectedLayer?.let {
                CanvasRenderer.getLayerLocalSize(it, fontRepository, fontsById)
            }
        }

        var dragAction by remember { mutableStateOf(DragAction.NONE) }
        var dragStartLayer by remember { mutableStateOf<CanvasLayer?>(null) }
        var dragStartCanvasPt by remember { mutableStateOf(Offset.Zero) }

        val gestureModifier = if (isEyedropperActive && eyedropperBitmap != null) {
            Modifier.pointerInput(isEyedropperActive, eyedropperBitmap, totalScale, canvasOriginX, canvasOriginY) {
                detectDragGestures(
                    onDragStart = { pos ->
                        eyedropperTouchPx = pos
                        val c = screenToCanvas(pos)
                        sampledColorArgb = samplePixelFromBitmap(eyedropperBitmap, c.x, c.y, document)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        eyedropperTouchPx = change.position
                        val c = screenToCanvas(change.position)
                        sampledColorArgb = samplePixelFromBitmap(eyedropperBitmap, c.x, c.y, document)
                    }
                )
            }
        } else {
            Modifier
                .pointerInput(document, selectedLayerId, totalScale, canvasOriginX, canvasOriginY) {
                    detectTapGestures(
                        onTap = { pos ->
                            val canvasPt = screenToCanvas(pos)
                            val hit = CanvasRenderer.hitTestLayer(
                                doc = document,
                                canvasX = canvasPt.x,
                                canvasY = canvasPt.y,
                                fontRepository = fontRepository,
                                fontsById = fontsById
                            )
                            onSelectLayer(hit?.id)
                        },
                        onDoubleTap = { pos ->
                            val canvasPt = screenToCanvas(pos)
                            val hit = CanvasRenderer.hitTestLayer(
                                doc = document,
                                canvasX = canvasPt.x,
                                canvasY = canvasPt.y,
                                fontRepository = fontRepository,
                                fontsById = fontsById
                            )
                            if (hit is TextLayer && !hit.isLocked) {
                                onSelectLayer(hit.id)
                                onDoubleTapTextLayer(hit)
                            }
                        }
                    )
                }
                .pointerInput(document, selectedLayerId, totalScale, canvasOriginX, canvasOriginY) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            val canvasPt = screenToCanvas(pos)
                            val sel = document.layers.find { it.id == selectedLayerId }
                            if (sel != null && !sel.isLocked && sel.isVisible && selectedBounds != null) {
                                val (w, h) = selectedBounds
                                val rotateHandleCanvas = computeRotateHandleCanvasPos(sel, h)
                                val resizeHandleCanvas = computeCornerHandleCanvasPos(sel, w, h)
                                val handleRadiusCanvas = with(density) { 26.dp.toPx() } / totalScale

                                when {
                                    hypot(canvasPt.x - rotateHandleCanvas.x, canvasPt.y - rotateHandleCanvas.y) <= handleRadiusCanvas -> {
                                        dragAction = DragAction.ROTATE_LAYER
                                        dragStartLayer = sel
                                        dragStartCanvasPt = canvasPt
                                        onBeginGesture()
                                        return@detectDragGestures
                                    }
                                    hypot(canvasPt.x - resizeHandleCanvas.x, canvasPt.y - resizeHandleCanvas.y) <= handleRadiusCanvas -> {
                                        dragAction = DragAction.RESIZE_LAYER
                                        dragStartLayer = sel
                                        dragStartCanvasPt = canvasPt
                                        onBeginGesture()
                                        return@detectDragGestures
                                    }
                                }
                            }

                            val hit = CanvasRenderer.hitTestLayer(
                                doc = document,
                                canvasX = canvasPt.x,
                                canvasY = canvasPt.y,
                                fontRepository = fontRepository,
                                fontsById = fontsById
                            )
                            if (hit != null && !hit.isLocked) {
                                onSelectLayer(hit.id)
                                dragAction = DragAction.MOVE_LAYER
                                dragStartLayer = hit
                                dragStartCanvasPt = canvasPt
                                onBeginGesture()
                            } else {
                                dragAction = DragAction.NONE
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val start = dragStartLayer ?: return@detectDragGestures
                            val currentCanvasPt = screenToCanvas(change.position)
                            when (dragAction) {
                                DragAction.MOVE_LAYER -> {
                                    val dx = currentCanvasPt.x - dragStartCanvasPt.x
                                    val dy = currentCanvasPt.y - dragStartCanvasPt.y
                                    onUpdateLayerTransient(
                                        start.withBaseTransform(
                                            xPx = start.xPx + dx,
                                            yPx = start.yPx + dy
                                        )
                                    )
                                }
                                DragAction.ROTATE_LAYER -> {
                                    val angleRad = atan2(
                                        (currentCanvasPt.y - start.yPx).toDouble(),
                                        (currentCanvasPt.x - start.xPx).toDouble()
                                    )
                                    var deg = (Math.toDegrees(angleRad).toFloat() + 90f) % 360f
                                    if (deg < 0f) deg += 360f
                                    // Snap near 0, 90, 180, 270
                                    val snapTargets = listOf(0f, 90f, 180f, 270f, 360f)
                                    for (snap in snapTargets) {
                                        if (kotlin.math.abs(deg - snap) < 3.5f) {
                                            deg = if (snap == 360f) 0f else snap
                                            break
                                        }
                                    }
                                    onUpdateLayerTransient(start.withBaseTransform(rotationDeg = deg))
                                }
                                DragAction.RESIZE_LAYER -> {
                                    val distStart = hypot(
                                        dragStartCanvasPt.x - start.xPx,
                                        dragStartCanvasPt.y - start.yPx
                                    ).coerceAtLeast(24f)
                                    val distNow = hypot(
                                        currentCanvasPt.x - start.xPx,
                                        currentCanvasPt.y - start.yPx
                                    ).coerceAtLeast(24f)
                                    val scale = (distNow / distStart).coerceIn(0.25f, 4.0f)
                                    val updated: CanvasLayer = when (start) {
                                        is ImageLayer -> start.copy(
                                            widthPx = (start.widthPx * scale).coerceIn(48f, 4096f),
                                            heightPx = (start.heightPx * scale).coerceIn(48f, 4096f)
                                        )
                                        is ShapeLayer -> start.copy(
                                            widthPx = (start.widthPx * scale).coerceIn(24f, 4096f),
                                            heightPx = if (start.shapeKind == ShapeKind.LINE) start.heightPx
                                            else (start.heightPx * scale).coerceIn(24f, 4096f)
                                        )
                                        is TextLayer -> start.copy(
                                            fontSizePx = (start.fontSizePx * scale).coerceIn(18f, 360f),
                                            boxWidthPx = (start.boxWidthPx * scale).coerceIn(140f, document.canvasWidthPx * 2f)
                                        )
                                    }
                                    onUpdateLayerTransient(updated)
                                }
                                DragAction.NONE -> {
                                    panOffset += dragAmount
                                }
                            }
                        },
                        onDragEnd = {
                            if (dragAction != DragAction.NONE) onEndGesture()
                            dragAction = DragAction.NONE
                            dragStartLayer = null
                        },
                        onDragCancel = {
                            if (dragAction != DragAction.NONE) onEndGesture()
                            dragAction = DragAction.NONE
                            dragStartLayer = null
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        if (dragAction == DragAction.NONE && zoom != 1f) {
                            zoomScale = (zoomScale * zoom).coerceIn(0.4f, 4.0f)
                            panOffset += pan
                        }
                    }
                }
        }

        Canvas(modifier = Modifier.fillMaxSize().then(gestureModifier)) {
            // 1. Draw transparency checkerboard inside the document frame
            drawCheckerboard(
                origin = Offset(canvasOriginX, canvasOriginY),
                size = Size(canvasDisplayW, canvasDisplayH),
                cellPx = with(density) { 12.dp.toPx() }
            )

            // 2. Render document at true canvas scale
            drawIntoCanvas { composeCanvas ->
                val native = composeCanvas.nativeCanvas
                native.save()
                native.translate(canvasOriginX, canvasOriginY)
                native.scale(totalScale, totalScale)
                native.clipRect(0f, 0f, document.canvasWidthPx.toFloat(), document.canvasHeightPx.toFloat())
                CanvasRenderer.renderDocumentToCanvas(
                    canvas = native,
                    doc = document,
                    fontRepository = fontRepository,
                    fontsById = fontsById,
                    forceTransparentBackground = false,
                    isExportMode = false
                )
                native.restore()
            }

            // 3. Draw crisp canvas border
            drawRect(
                color = StudioBorder,
                topLeft = Offset(canvasOriginX, canvasOriginY),
                size = Size(canvasDisplayW, canvasDisplayH),
                style = Stroke(width = 1.5.dp.toPx())
            )

            // 4. Draw Selection Box & Handles
            if (!isEyedropperActive && selectedLayer != null && selectedLayer.isVisible && selectedBounds != null) {
                val (localW, localH) = selectedBounds
                val centerScreen = canvasToScreen(Offset(selectedLayer.xPx, selectedLayer.yPx))
                val boxW = localW * totalScale
                val boxH = localH * totalScale
                val handleLen = with(density) { 28.dp.toPx() }

                rotate(degrees = selectedLayer.rotationDeg, pivot = centerScreen) {
                    val topLeft = Offset(centerScreen.x - boxW / 2f, centerScreen.y - boxH / 2f)
                    drawRect(
                        color = if (selectedLayer.isLocked) StudioMint else StudioCoral,
                        topLeft = topLeft,
                        size = Size(boxW, boxH),
                        style = Stroke(
                            width = 1.8.dp.toPx(),
                            pathEffect = if (selectedLayer.isLocked) {
                                PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                            } else null
                        )
                    )

                    if (!selectedLayer.isLocked) {
                        // Top rotation stem & knob
                        val topCenter = Offset(centerScreen.x, topLeft.y)
                        val rotKnob = Offset(centerScreen.x, topLeft.y - handleLen)
                        drawLine(
                            color = StudioCoral,
                            start = topCenter,
                            end = rotKnob,
                            strokeWidth = 1.5.dp.toPx()
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 7.dp.toPx(),
                            center = rotKnob
                        )
                        drawCircle(
                            color = StudioCoral,
                            radius = 7.dp.toPx(),
                            center = rotKnob,
                            style = Stroke(width = 2.dp.toPx())
                        )

                        // Bottom-right resize knob
                        val resizeKnob = Offset(topLeft.x + boxW, topLeft.y + boxH)
                        drawCircle(
                            color = StudioCoral,
                            radius = 7.5.dp.toPx(),
                            center = resizeKnob
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 7.5.dp.toPx(),
                            center = resizeKnob,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
            }
        }

        // Zoom reset badge
        if (kotlin.math.abs(zoomScale - 1f) > 0.05f || panOffset != Offset.Zero) {
            Surface(
                onClick = {
                    zoomScale = 1f
                    panOffset = Offset.Zero
                },
                shape = RoundedCornerShape(99.dp),
                color = StudioSurfaceElevated.copy(alpha = 0.92f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset canvas zoom",
                        tint = StudioMint,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${(zoomScale * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                }
            }
        }

        // Eyedropper Loupe + Confirm Bar
        if (isEyedropperActive) {
            val touch = eyedropperTouchPx
            if (touch != null && eyedropperBitmap != null) {
                val loupeSize = 86.dp
                val loupePx = with(density) { loupeSize.toPx() }
                val xOff = (touch.x - loupePx / 2f).roundToInt().coerceIn(8, (viewportW - loupePx - 8).roundToInt())
                val yOff = (touch.y - loupePx - 48).roundToInt().coerceAtLeast(8)
                Box(
                    modifier = Modifier
                        .offset { IntOffset(xOff, yOff) }
                        .size(loupeSize)
                        .clip(CircleShape)
                        .background(Color(sampledColorArgb))
                        .border(3.dp, Color.White, CircleShape)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(14.dp)
                            .border(1.5.dp, Color.Black.copy(alpha = 0.7f), CircleShape)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(99.dp),
                color = StudioSurfaceElevated,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color(sampledColorArgb))
                            .border(1.5.dp, Color.White, CircleShape)
                    )
                    val hex = "#${Integer.toHexString(sampledColorArgb).uppercase().padStart(8, '0').takeLast(6)}"
                    Text(
                        text = "Drag photo to sample • $hex",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                    IconButton(
                        onClick = { onFinishEyedropper(sampledColorArgb) },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(StudioCoral)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Apply sampled color", tint = Color.White)
                    }
                    IconButton(
                        onClick = onCancelEyedropper,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel eyedropper", tint = Color.White)
                    }
                }
            }
        }
    }
}

private fun samplePixelFromBitmap(
    bitmap: Bitmap,
    canvasX: Float,
    canvasY: Float,
    doc: ProjectDocument
): Int {
    val normX = (canvasX / doc.canvasWidthPx).coerceIn(0f, 0.999f)
    val normY = (canvasY / doc.canvasHeightPx).coerceIn(0f, 0.999f)
    val px = (normX * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
    val py = (normY * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
    val argb = bitmap.getPixel(px, py)
    return if (AndroidColor.alpha(argb) < 16) AndroidColor.BLACK else argb
}

private fun computeRotateHandleCanvasPos(layer: CanvasLayer, localH: Float): Offset {
    val rad = Math.toRadians(layer.rotationDeg.toDouble())
    val dy = -(localH / 2f + 72f)
    return Offset(
        layer.xPx + (-dy * sin(rad)).toFloat(),
        layer.yPx + (dy * cos(rad)).toFloat()
    )
}

private fun computeCornerHandleCanvasPos(layer: CanvasLayer, localW: Float, localH: Float): Offset {
    val rad = Math.toRadians(layer.rotationDeg.toDouble())
    val dx = localW / 2f
    val dy = localH / 2f
    return Offset(
        layer.xPx + (dx * cos(rad) - dy * sin(rad)).toFloat(),
        layer.yPx + (dx * sin(rad) + dy * cos(rad)).toFloat()
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCheckerboard(
    origin: Offset,
    size: Size,
    cellPx: Float
) {
    val c1 = Color(0xFF1A1C24)
    val c2 = Color(0xFF232631)
    drawRect(color = c1, topLeft = origin, size = size)
    val cols = (size.width / cellPx).toInt() + 1
    val rows = (size.height / cellPx).toInt() + 1
    for (r in 0 until rows) {
        for (c in 0 until cols) {
            if ((r + c) % 2 == 0) {
                val left = origin.x + c * cellPx
                val top = origin.y + r * cellPx
                val w = min(cellPx, origin.x + size.width - left)
                val h = min(cellPx, origin.y + size.height - top)
                if (w > 0f && h > 0f) {
                    drawRect(color = c2, topLeft = Offset(left, top), size = Size(w, h))
                }
            }
        }
    }
}
