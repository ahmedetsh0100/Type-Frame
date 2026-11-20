package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.Paint as NativePaint
import android.graphics.Path as NativePath
import android.graphics.BlurMaskFilter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatClear
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FontRepository
import com.example.model.CanvasLayer
import com.example.model.FontItem
import com.example.model.ImageLayer
import com.example.model.PaintLayer
import com.example.model.ProjectDocument
import com.example.model.ShapeKind
import com.example.model.ShapeLayer
import com.example.model.StrokePoint
import com.example.model.TextLayer
import com.example.render.CanvasRenderer
import com.example.typography.ArabicTypographyEngine
import com.example.ui.theme.LocalStudioPalette
import com.example.viewmodel.BrushSettings
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class DragAction {
    NONE,
    MOVE_LAYER,
    RESIZE_LAYER,
    STRETCH_X,
    STRETCH_Y,
    ROTATE_LAYER
}

private data class SnapGuide(
    val isVertical: Boolean,
    val canvasCoord: Float
)

@Composable
fun StudioCanvasStage(
    document: ProjectDocument,
    selectedLayerId: String?,
    fontsById: Map<String, FontItem>,
    fontRepository: FontRepository,
    isEyedropperActive: Boolean,
    eyedropperBitmap: Bitmap?,
    isBrushMode: Boolean,
    brushSettings: BrushSettings,
    isInlineTextEditing: Boolean,
    inlineSelectionStart: Int,
    inlineSelectionEnd: Int,
    onSelectLayer: (String?) -> Unit,
    onDoubleTapTextLayer: (TextLayer) -> Unit,
    onInlineTextChanged: (String, Int, Int) -> Unit,
    onInlineSelectionChanged: (Int, Int) -> Unit,
    onApplyInlineStyle: (
        fontSizeDeltaPx: Float?,
        toggleBold: Boolean,
        toggleItalic: Boolean,
        toggleUnderline: Boolean
    ) -> Unit,
    onOpenColorForInlineText: () -> Unit,
    onOpenFontsForInlineText: () -> Unit,
    onClearInlineSpans: () -> Unit,
    onExitInlineTextEditing: () -> Unit,
    onCommitBrushStroke: (List<StrokePoint>) -> Unit,
    onBeginGesture: () -> Unit,
    onUpdateLayerTransient: (CanvasLayer) -> Unit,
    onEndGesture: () -> Unit,
    onFinishEyedropper: (Int) -> Unit,
    onCancelEyedropper: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalStudioPalette.current
    val density = LocalDensity.current
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // Eyedropper live state
    var eyedropperTouchPx by remember(isEyedropperActive) { mutableStateOf<Offset?>(null) }
    var sampledColorArgb by remember(isEyedropperActive) { mutableIntStateOf(0xFFFF5A36.toInt()) }

    // Live brush drawing points
    val activeBrushPoints = remember { mutableStateListOf<StrokePoint>() }
    var brushPointerScreen by remember { mutableStateOf<Offset?>(null) }

    // Live alignment snap guides
    var activeGuides by remember { mutableStateOf<List<SnapGuide>>(emptyList()) }
    var activeRotationBadge by remember { mutableStateOf<Float?>(null) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .testTag("studio_canvas_stage")
    ) {
        val viewportW = with(density) { maxWidth.toPx() }
        val viewportH = with(density) { maxHeight.toPx() }
        val paddingPx = with(density) { 40.dp.toPx() }

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

        val gestureModifier = when {
            isEyedropperActive && eyedropperBitmap != null -> {
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
            }
            isBrushMode -> {
                Modifier.pointerInput(isBrushMode, totalScale, canvasOriginX, canvasOriginY, selectedLayerId, brushSettings) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            activeBrushPoints.clear()
                            brushPointerScreen = pos
                            val c = screenToCanvas(pos)
                            activeBrushPoints.add(StrokePoint(c.x, c.y))
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            brushPointerScreen = change.position
                            val c = screenToCanvas(change.position)
                            val prev = activeBrushPoints.lastOrNull()
                            if (prev == null || hypot(c.x - prev.x, c.y - prev.y) >= 2.5f) {
                                activeBrushPoints.add(StrokePoint(c.x, c.y))
                            }
                        },
                        onDragEnd = {
                            if (activeBrushPoints.isNotEmpty()) {
                                onCommitBrushStroke(activeBrushPoints.toList())
                            }
                            activeBrushPoints.clear()
                            brushPointerScreen = null
                        },
                        onDragCancel = {
                            activeBrushPoints.clear()
                            brushPointerScreen = null
                        }
                    )
                }
            }
            else -> {
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
                                if (hit?.id != selectedLayerId && isInlineTextEditing) {
                                    onExitInlineTextEditing()
                                }
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
                    // Unified smart multi-touch: 1-finger handles & drag, 2-finger pinch/rotate on selected layer or zoom/pan canvas
                    .pointerInput(document, selectedLayerId, totalScale, canvasOriginX, canvasOriginY) {
                        awaitEachGesture {
                            val firstDown = awaitFirstDown(requireUnconsumed = false)
                            var isMultiTouch = false
                            var pinchGestureBegun = false
                            var initialSelectedForPinch: CanvasLayer? = null

                            val downCanvasPt = screenToCanvas(firstDown.position)
                            val sel = document.layers.find { it.id == selectedLayerId }
                            val bounds = sel?.let {
                                CanvasRenderer.getLayerLocalSize(it, fontRepository, fontsById)
                            }
                            val handleHitRadiusCanvas = with(density) { 26.dp.toPx() } / totalScale

                            dragAction = DragAction.NONE
                            dragStartLayer = null

                            if (sel != null && !sel.isLocked && sel.isVisible && bounds != null && sel !is PaintLayer) {
                                val (w, h) = bounds
                                val rotPos = computeLocalRotatedOffset(sel, 0f, -(h / 2f + 72f))
                                val cornerPos = computeLocalRotatedOffset(sel, w / 2f, h / 2f)
                                val rightEdgePos = computeLocalRotatedOffset(sel, w / 2f, 0f)
                                val bottomEdgePos = computeLocalRotatedOffset(sel, 0f, h / 2f)

                                when {
                                    hypot(downCanvasPt.x - rotPos.x, downCanvasPt.y - rotPos.y) <= handleHitRadiusCanvas -> {
                                        dragAction = DragAction.ROTATE_LAYER
                                        dragStartLayer = sel
                                    }
                                    hypot(downCanvasPt.x - cornerPos.x, downCanvasPt.y - cornerPos.y) <= handleHitRadiusCanvas -> {
                                        dragAction = DragAction.RESIZE_LAYER
                                        dragStartLayer = sel
                                    }
                                    hypot(downCanvasPt.x - rightEdgePos.x, downCanvasPt.y - rightEdgePos.y) <= handleHitRadiusCanvas -> {
                                        dragAction = DragAction.STRETCH_X
                                        dragStartLayer = sel
                                    }
                                    hypot(downCanvasPt.x - bottomEdgePos.x, downCanvasPt.y - bottomEdgePos.y) <= handleHitRadiusCanvas -> {
                                        dragAction = DragAction.STRETCH_Y
                                        dragStartLayer = sel
                                    }
                                }
                            }

                            if (dragAction == DragAction.NONE) {
                                val hit = CanvasRenderer.hitTestLayer(
                                    doc = document,
                                    canvasX = downCanvasPt.x,
                                    canvasY = downCanvasPt.y,
                                    fontRepository = fontRepository,
                                    fontsById = fontsById
                                )
                                if (hit != null && !hit.isLocked) {
                                    if (hit.id != selectedLayerId) {
                                        if (isInlineTextEditing) onExitInlineTextEditing()
                                        onSelectLayer(hit.id)
                                    }
                                    dragAction = DragAction.MOVE_LAYER
                                    dragStartLayer = hit
                                }
                            }

                            dragStartCanvasPt = downCanvasPt
                            var startedOneFingerMove = false
                            var livePinchLayer: CanvasLayer? = null

                            while (true) {
                                val event = awaitPointerEvent()
                                val pressedCount = event.changes.count { it.pressed }
                                if (pressedCount == 0) break

                                if (pressedCount >= 2) {
                                    isMultiTouch = true
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    val rot = event.calculateRotation()
                                    val centroidCanvas = screenToCanvas(event.calculateCentroid())

                                    if (!pinchGestureBegun) {
                                        val candidate = document.layers.find { it.id == selectedLayerId }
                                        val hitSelectedForPinch = candidate != null &&
                                            !candidate.isLocked &&
                                            candidate.isVisible &&
                                            candidate !is PaintLayer &&
                                            isPointNearLayer(candidate, centroidCanvas, fontRepository, fontsById, extraPad = 140f)
                                        if (hitSelectedForPinch) {
                                            initialSelectedForPinch = candidate
                                            livePinchLayer = candidate
                                            onBeginGesture()
                                        }
                                        pinchGestureBegun = true
                                    }

                                    val currentPinch = livePinchLayer
                                    if (currentPinch != null) {
                                        val nextLayer = applyTwoFingerTransform(
                                            layer = currentPinch,
                                            zoom = zoom,
                                            panCanvas = Offset(pan.x / totalScale, pan.y / totalScale),
                                            rotationDeltaDeg = rot
                                        )
                                        livePinchLayer = nextLayer
                                        activeRotationBadge = nextLayer.rotationDeg
                                        onUpdateLayerTransient(nextLayer)
                                    } else {
                                        zoomScale = (zoomScale * zoom).coerceIn(0.35f, 5.0f)
                                        panOffset += pan
                                    }
                                    event.changes.forEach { it.consume() }
                                } else if (!isMultiTouch && pressedCount == 1) {
                                    val change = event.changes.first()
                                    val deltaFromStart = change.position - firstDown.position
                                    if (!startedOneFingerMove && hypot(deltaFromStart.x, deltaFromStart.y) < viewConfiguration.touchSlop) {
                                        continue
                                    }
                                    if (!startedOneFingerMove) {
                                        startedOneFingerMove = true
                                        if (dragAction != DragAction.NONE) {
                                            onBeginGesture()
                                        }
                                    }
                                    change.consume()
                                    val currentCanvasPt = screenToCanvas(change.position)
                                    val start = dragStartLayer

                                    if (start != null && dragAction != DragAction.NONE) {
                                        when (dragAction) {
                                            DragAction.MOVE_LAYER -> {
                                                val rawX = start.xPx + (currentCanvasPt.x - dragStartCanvasPt.x)
                                                val rawY = start.yPx + (currentCanvasPt.y - dragStartCanvasPt.y)
                                                val (snappedX, snappedY, guides) = computeSnapAndGuides(
                                                    rawX = rawX,
                                                    rawY = rawY,
                                                    movingId = start.id,
                                                    doc = document
                                                )
                                                activeGuides = guides
                                                onUpdateLayerTransient(start.withBaseTransform(xPx = snappedX, yPx = snappedY))
                                            }
                                            DragAction.ROTATE_LAYER -> {
                                                val angleRad = atan2(
                                                    (currentCanvasPt.y - start.yPx).toDouble(),
                                                    (currentCanvasPt.x - start.xPx).toDouble()
                                                )
                                                var deg = (Math.toDegrees(angleRad).toFloat() + 90f) % 360f
                                                if (deg < 0f) deg += 360f
                                                for (snap in listOf(0f, 90f, 180f, 270f, 360f)) {
                                                    if (abs(deg - snap) < 3.5f) {
                                                        deg = if (snap == 360f) 0f else snap
                                                        break
                                                    }
                                                }
                                                activeRotationBadge = deg
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
                                                val factor = (distNow / distStart).coerceIn(0.25f, 4.0f)
                                                val updated: CanvasLayer = when (start) {
                                                    is ImageLayer -> start.copy(
                                                        widthPx = (start.widthPx * factor).coerceIn(48f, 4096f),
                                                        heightPx = (start.heightPx * factor).coerceIn(48f, 4096f)
                                                    )
                                                    is ShapeLayer -> start.copy(
                                                        widthPx = (start.widthPx * factor).coerceIn(24f, 4096f),
                                                        heightPx = if (start.shapeKind == ShapeKind.LINE) start.heightPx
                                                        else (start.heightPx * factor).coerceIn(24f, 4096f)
                                                    )
                                                    is TextLayer -> start.copy(
                                                        fontSizePx = (start.fontSizePx * factor).coerceIn(18f, 360f),
                                                        boxWidthPx = (start.boxWidthPx * factor).coerceIn(140f, document.canvasWidthPx * 2f)
                                                    )
                                                    is PaintLayer -> start
                                                }
                                                onUpdateLayerTransient(updated)
                                            }
                                            DragAction.STRETCH_X -> {
                                                val localNow = toLayerLocal(start, currentCanvasPt)
                                                val localStart = toLayerLocal(start, dragStartCanvasPt)
                                                val ratio = (abs(localNow.x) / abs(localStart.x).coerceAtLeast(16f)).coerceIn(0.3f, 3.5f)
                                                val newScaleX = (start.scaleX * ratio).coerceIn(0.25f, 4.0f)
                                                onUpdateLayerTransient(start.withBaseTransform(scaleX = newScaleX))
                                            }
                                            DragAction.STRETCH_Y -> {
                                                val localNow = toLayerLocal(start, currentCanvasPt)
                                                val localStart = toLayerLocal(start, dragStartCanvasPt)
                                                val ratio = (abs(localNow.y) / abs(localStart.y).coerceAtLeast(16f)).coerceIn(0.3f, 3.5f)
                                                val newScaleY = (start.scaleY * ratio).coerceIn(0.25f, 4.0f)
                                                onUpdateLayerTransient(start.withBaseTransform(scaleY = newScaleY))
                                            }
                                            DragAction.NONE -> {}
                                        }
                                    } else {
                                        val delta = change.position - change.previousPosition
                                        panOffset += delta
                                    }
                                }
                            }

                            if (startedOneFingerMove && dragAction != DragAction.NONE || initialSelectedForPinch != null) {
                                onEndGesture()
                            }
                            activeGuides = emptyList()
                            activeRotationBadge = null
                            dragAction = DragAction.NONE
                            dragStartLayer = null
                        }
                    }
            }
        }

        Canvas(modifier = Modifier.fillMaxSize().then(gestureModifier)) {
            // 1. Transparency checkerboard inside canvas frame
            drawCheckerboard(
                origin = Offset(canvasOriginX, canvasOriginY),
                size = Size(canvasDisplayW, canvasDisplayH),
                cellPx = with(density) { 12.dp.toPx() },
                isDarkTheme = palette.isDark
            )

            // 2. Render document + live in-progress brush preview
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

                // Live brush stroke preview
                if (activeBrushPoints.isNotEmpty()) {
                    val livePaint = NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply {
                        style = NativePaint.Style.STROKE
                        strokeWidth = brushSettings.sizePx.coerceAtLeast(1f)
                        strokeCap = NativePaint.Cap.ROUND
                        strokeJoin = NativePaint.Join.ROUND
                        color = if (brushSettings.isEraser) AndroidColor.argb(140, 255, 255, 255) else brushSettings.colorArgb
                        if (!brushSettings.isEraser) {
                            val combined = (AndroidColor.alpha(brushSettings.colorArgb) / 255f) *
                                brushSettings.opacity * brushSettings.flow
                            alpha = (combined.coerceIn(0.05f, 1f) * 255).toInt()
                        }
                        val blurRadius = (1f - brushSettings.hardness.coerceIn(0f, 1f)) * (brushSettings.sizePx * 0.45f)
                        if (blurRadius > 0.75f) {
                            maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
                        }
                    }
                    if (activeBrushPoints.size == 1) {
                        livePaint.style = NativePaint.Style.FILL
                        val pt = activeBrushPoints.first()
                        native.drawCircle(pt.x, pt.y, brushSettings.sizePx / 2f, livePaint)
                    } else {
                        val path = NativePath()
                        path.moveTo(activeBrushPoints[0].x, activeBrushPoints[0].y)
                        for (i in 1 until activeBrushPoints.size) {
                            val p = activeBrushPoints[i - 1]
                            val c = activeBrushPoints[i]
                            path.quadTo(p.x, p.y, (p.x + c.x) / 2f, (p.y + c.y) / 2f)
                        }
                        path.lineTo(activeBrushPoints.last().x, activeBrushPoints.last().y)
                        native.drawPath(path, livePaint)
                    }
                }
                native.restore()
            }

            // 3. Canvas frame border
            drawRect(
                color = palette.border,
                topLeft = Offset(canvasOriginX, canvasOriginY),
                size = Size(canvasDisplayW, canvasDisplayH),
                style = Stroke(width = 1.5.dp.toPx())
            )

            // 4. Live Snapping Alignment Guides
            activeGuides.forEach { guide ->
                if (guide.isVertical) {
                    val sx = canvasOriginX + guide.canvasCoord * totalScale
                    drawLine(
                        color = palette.mint,
                        start = Offset(sx, canvasOriginY),
                        end = Offset(sx, canvasOriginY + canvasDisplayH),
                        strokeWidth = 1.4.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))
                    )
                } else {
                    val sy = canvasOriginY + guide.canvasCoord * totalScale
                    drawLine(
                        color = palette.mint,
                        start = Offset(canvasOriginX, sy),
                        end = Offset(canvasOriginX + canvasDisplayW, sy),
                        strokeWidth = 1.4.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))
                    )
                }
            }

            // 5. Brush cursor preview circle
            brushPointerScreen?.let { ptr ->
                val radiusPx = (brushSettings.sizePx * totalScale / 2f).coerceAtLeast(4f)
                drawCircle(
                    color = if (brushSettings.isEraser) Color.White else palette.accent,
                    radius = radiusPx,
                    center = ptr,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            // 6. Clean Selection Frame & Transform Handles
            if (!isEyedropperActive && !isBrushMode && selectedLayer != null &&
                selectedLayer.isVisible && selectedBounds != null && selectedLayer !is PaintLayer
            ) {
                val (localW, localH) = selectedBounds
                val centerScreen = canvasToScreen(Offset(selectedLayer.xPx, selectedLayer.yPx))
                val boxW = localW * totalScale
                val boxH = localH * totalScale
                val handleLen = with(density) { 28.dp.toPx() }
                val frameColor = if (selectedLayer.isLocked) palette.mint else palette.accent

                rotate(degrees = selectedLayer.rotationDeg, pivot = centerScreen) {
                    val topLeft = Offset(centerScreen.x - boxW / 2f, centerScreen.y - boxH / 2f)
                    drawRect(
                        color = frameColor,
                        topLeft = topLeft,
                        size = Size(boxW, boxH),
                        style = Stroke(
                            width = 1.6.dp.toPx(),
                            pathEffect = if (selectedLayer.isLocked) {
                                PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                            } else null
                        )
                    )

                    if (!selectedLayer.isLocked) {
                        // Top rotation stem & knob
                        val topCenter = Offset(centerScreen.x, topLeft.y)
                        val rotKnob = Offset(centerScreen.x, topLeft.y - handleLen)
                        drawLine(color = frameColor, start = topCenter, end = rotKnob, strokeWidth = 1.5.dp.toPx())
                        drawCircle(color = Color.White, radius = 6.5.dp.toPx(), center = rotKnob)
                        drawCircle(color = frameColor, radius = 6.5.dp.toPx(), center = rotKnob, style = Stroke(2.dp.toPx()))

                        // Bottom-right corner resize knob
                        val resizeKnob = Offset(topLeft.x + boxW, topLeft.y + boxH)
                        drawCircle(color = frameColor, radius = 7.dp.toPx(), center = resizeKnob)
                        drawCircle(color = Color.White, radius = 7.dp.toPx(), center = resizeKnob, style = Stroke(2.dp.toPx()))

                        // Right & Bottom independent stretch handles (pill handles)
                        val rightKnob = Offset(topLeft.x + boxW, centerScreen.y)
                        drawCircle(color = palette.mint, radius = 5.dp.toPx(), center = rightKnob)
                        drawCircle(color = Color.White, radius = 5.dp.toPx(), center = rightKnob, style = Stroke(1.5.dp.toPx()))

                        val bottomKnob = Offset(centerScreen.x, topLeft.y + boxH)
                        drawCircle(color = palette.mint, radius = 5.dp.toPx(), center = bottomKnob)
                        drawCircle(color = Color.White, radius = 5.dp.toPx(), center = bottomKnob, style = Stroke(1.5.dp.toPx()))
                    }
                }
            }
        }

        // Rotation degree badge while rotating
        activeRotationBadge?.let { deg ->
            Surface(
                shape = RoundedCornerShape(99.dp),
                color = palette.surfaceElevated.copy(alpha = 0.94f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .border(1.dp, palette.accent, RoundedCornerShape(99.dp))
            ) {
                Text(
                    text = "${deg.roundToInt()}°",
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.textPrimary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }

        // Zoom reset badge
        if (abs(zoomScale - 1f) > 0.05f || panOffset != Offset.Zero) {
            Surface(
                onClick = {
                    zoomScale = 1f
                    panOffset = Offset.Zero
                },
                shape = RoundedCornerShape(99.dp),
                color = palette.surfaceElevated.copy(alpha = 0.92f),
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
                        tint = palette.mint,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${(zoomScale * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textPrimary
                    )
                }
            }
        }

        // Direct On-Canvas Rich Text Contextual Editor Overlay
        if (isInlineTextEditing && selectedLayer is TextLayer && !isEyedropperActive && !isBrushMode) {
            OnCanvasRichTextOverlay(
                textLayer = selectedLayer,
                selectionStart = inlineSelectionStart,
                selectionEnd = inlineSelectionEnd,
                onTextAndSelectionChanged = onInlineTextChanged,
                onSelectionChanged = onInlineSelectionChanged,
                onApplyStyle = onApplyInlineStyle,
                onOpenColor = onOpenColorForInlineText,
                onOpenFonts = onOpenFontsForInlineText,
                onClearSpans = onClearInlineSpans,
                onDone = onExitInlineTextEditing,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
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
                color = palette.surfaceElevated,
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
                        text = "Drag anywhere to sample • $hex",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textPrimary
                    )
                    IconButton(
                        onClick = { onFinishEyedropper(sampledColorArgb) },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(palette.accent)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Apply sampled color", tint = Color.White)
                    }
                    IconButton(
                        onClick = onCancelEyedropper,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel eyedropper", tint = palette.textPrimary)
                    }
                }
            }
        }
    }
}

private data class WordToken(val word: String, val start: Int, val end: Int)

@Composable
private fun OnCanvasRichTextOverlay(
    textLayer: TextLayer,
    selectionStart: Int,
    selectionEnd: Int,
    onTextAndSelectionChanged: (String, Int, Int) -> Unit,
    onSelectionChanged: (Int, Int) -> Unit,
    onApplyStyle: (
        fontSizeDeltaPx: Float?,
        toggleBold: Boolean,
        toggleItalic: Boolean,
        toggleUnderline: Boolean
    ) -> Unit,
    onOpenColor: () -> Unit,
    onOpenFonts: () -> Unit,
    onClearSpans: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalStudioPalette.current
    val focusRequester = remember { FocusRequester() }
    val len = textLayer.text.length
    val safeStart = selectionStart.coerceIn(0, len)
    val safeEnd = selectionEnd.coerceIn(safeStart, len)

    var tfv by remember(textLayer.id) {
        mutableStateOf(TextFieldValue(textLayer.text, TextRange(safeStart, safeEnd)))
    }
    LaunchedEffect(textLayer.text, safeStart, safeEnd) {
        if (tfv.text != textLayer.text || tfv.selection.start != safeStart || tfv.selection.end != safeEnd) {
            tfv = TextFieldValue(textLayer.text, TextRange(safeStart, safeEnd))
        }
    }
    LaunchedEffect(textLayer.id) {
        runCatching { focusRequester.requestFocus() }
    }

    val isRtl = remember(textLayer.text) { ArabicTypographyEngine.containsArabicScript(textLayer.text) }
    val words = remember(textLayer.text) {
        Regex("\\S+").findAll(textLayer.text).map {
            WordToken(it.value, it.range.first, it.range.last + 1)
        }.toList()
    }

    val hasPartialSel = safeStart < safeEnd && !(safeStart == 0 && safeEnd == len)
    val selectionLabel = if (hasPartialSel) {
        "Selection (${safeEnd - safeStart} chars)"
    } else {
        "All Text • Tap word to style individually"
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = palette.surfaceElevated.copy(alpha = 0.96f),
        tonalElevation = 8.dp,
        modifier = modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .border(1.dp, palette.accent.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
            .testTag("on_canvas_text_editor")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header + Done
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = selectionLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hasPartialSel) palette.mint else palette.textSecondary
                )
                Surface(
                    onClick = onDone,
                    color = palette.accent,
                    shape = RoundedCornerShape(99.dp),
                    modifier = Modifier.testTag("inline_text_done_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Finish editing text", tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Done", style = MaterialTheme.typography.labelMedium, color = Color.White)
                    }
                }
            }

            // Live direct text field
            CompositionLocalProvider(
                LocalLayoutDirection provides if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
            ) {
                BasicTextField(
                    value = tfv,
                    onValueChange = { next ->
                        tfv = next
                        if (next.text != textLayer.text) {
                            onTextAndSelectionChanged(next.text, next.selection.start, next.selection.end)
                        } else {
                            onSelectionChanged(next.selection.start, next.selection.end)
                        }
                    },
                    textStyle = TextStyle(
                        color = palette.textPrimary,
                        fontSize = 15.sp
                    ),
                    cursorBrush = SolidColor(palette.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.surface)
                        .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .focusRequester(focusRequester)
                        .testTag("inline_canvas_text_input")
                )
            }

            // Quick single-tap word chips for selecting Arabic/English words individually
            if (words.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    val allSelected = safeStart == 0 && safeEnd == len
                    WordChip(
                        label = "All",
                        selected = allSelected,
                        onClick = { onSelectionChanged(0, len) }
                    )
                    words.forEach { tok ->
                        val isTokSelected = safeStart == tok.start && safeEnd == tok.end
                        WordChip(
                            label = tok.word,
                            selected = isTokSelected,
                            onClick = { onSelectionChanged(tok.start, tok.end) }
                        )
                    }
                }
            }

            // Contextual Rich Style Bar
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                IconButton(
                    onClick = { onApplyStyle(null, true, false, false) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.FormatBold, contentDescription = "Bold selection", tint = palette.textPrimary, modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = { onApplyStyle(null, false, true, false) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.FormatItalic, contentDescription = "Italic selection", tint = palette.textPrimary, modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = { onApplyStyle(null, false, false, true) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.FormatUnderlined, contentDescription = "Underline selection", tint = palette.textPrimary, modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = { onApplyStyle(-10f, false, false, false) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.TextDecrease, contentDescription = "Smaller selection text", tint = palette.textPrimary, modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = { onApplyStyle(10f, false, false, false) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.TextIncrease, contentDescription = "Larger selection text", tint = palette.textPrimary, modifier = Modifier.size(18.dp))
                }
                Surface(
                    onClick = onOpenFonts,
                    color = palette.surfaceHighest,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "Font",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                Surface(
                    onClick = onOpenColor,
                    color = palette.surfaceHighest,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color(textLayer.colorArgb))
                                .border(1.dp, Color.White, CircleShape)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text("Color", style = MaterialTheme.typography.labelMedium, color = palette.textPrimary)
                    }
                }
                if (textLayer.spans.isNotEmpty()) {
                    IconButton(onClick = onClearSpans, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.FormatClear, contentDescription = "Clear word formatting", tint = palette.danger, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun WordChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalStudioPalette.current
    Surface(
        onClick = onClick,
        color = if (selected) palette.accentSoft else palette.surface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.border(
            width = 1.dp,
            color = if (selected) palette.accent else palette.border,
            shape = RoundedCornerShape(8.dp)
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            ),
            color = if (selected) palette.accent else palette.textPrimary,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

private fun computeSnapAndGuides(
    rawX: Float,
    rawY: Float,
    movingId: String,
    doc: ProjectDocument
): Triple<Float, Float, List<SnapGuide>> {
    val threshold = 16f
    val guides = mutableListOf<SnapGuide>()
    val xTargets = mutableListOf(doc.canvasWidthPx / 2f, 0f, doc.canvasWidthPx.toFloat())
    val yTargets = mutableListOf(doc.canvasHeightPx / 2f, 0f, doc.canvasHeightPx.toFloat())
    doc.layers.forEach { other ->
        if (other.id != movingId && other.isVisible && other !is PaintLayer) {
            xTargets.add(other.xPx)
            yTargets.add(other.yPx)
        }
    }

    var outX = rawX
    var bestDx = threshold
    for (tx in xTargets) {
        val d = abs(rawX - tx)
        if (d < bestDx) {
            bestDx = d
            outX = tx
        }
    }
    if (bestDx < threshold) guides.add(SnapGuide(isVertical = true, canvasCoord = outX))

    var outY = rawY
    var bestDy = threshold
    for (ty in yTargets) {
        val d = abs(rawY - ty)
        if (d < bestDy) {
            bestDy = d
            outY = ty
        }
    }
    if (bestDy < threshold) guides.add(SnapGuide(isVertical = false, canvasCoord = outY))

    return Triple(outX, outY, guides)
}

private fun applyTwoFingerTransform(
    layer: CanvasLayer,
    zoom: Float,
    panCanvas: Offset,
    rotationDeltaDeg: Float
): CanvasLayer {
    val nextRot = (layer.rotationDeg + rotationDeltaDeg + 360f) % 360f
    val moved = layer.withBaseTransform(
        xPx = layer.xPx + panCanvas.x,
        yPx = layer.yPx + panCanvas.y,
        rotationDeg = nextRot
    )
    if (abs(zoom - 1f) < 0.001f) return moved
    return when (moved) {
        is ImageLayer -> moved.copy(
            widthPx = (moved.widthPx * zoom).coerceIn(48f, 4096f),
            heightPx = (moved.heightPx * zoom).coerceIn(48f, 4096f)
        )
        is ShapeLayer -> moved.copy(
            widthPx = (moved.widthPx * zoom).coerceIn(24f, 4096f),
            heightPx = if (moved.shapeKind == ShapeKind.LINE) moved.heightPx else (moved.heightPx * zoom).coerceIn(24f, 4096f)
        )
        is TextLayer -> moved.copy(
            fontSizePx = (moved.fontSizePx * zoom).coerceIn(18f, 360f),
            boxWidthPx = (moved.boxWidthPx * zoom).coerceIn(140f, 4096f)
        )
        is PaintLayer -> moved
    }
}

private fun isPointNearLayer(
    layer: CanvasLayer,
    canvasPt: Offset,
    fontRepository: FontRepository,
    fontsById: Map<String, FontItem>,
    extraPad: Float
): Boolean {
    val (w, h) = CanvasRenderer.getLayerLocalSize(layer, fontRepository, fontsById)
    val local = toLayerLocal(layer, canvasPt)
    return abs(local.x) <= w / 2f + extraPad && abs(local.y) <= h / 2f + extraPad
}

private fun toLayerLocal(layer: CanvasLayer, canvasPt: Offset): Offset {
    val dx = canvasPt.x - layer.xPx
    val dy = canvasPt.y - layer.yPx
    val rad = Math.toRadians(-layer.rotationDeg.toDouble())
    return Offset(
        (dx * cos(rad) - dy * sin(rad)).toFloat(),
        (dx * sin(rad) + dy * cos(rad)).toFloat()
    )
}

private fun computeLocalRotatedOffset(layer: CanvasLayer, localDx: Float, localDy: Float): Offset {
    val rad = Math.toRadians(layer.rotationDeg.toDouble())
    return Offset(
        layer.xPx + (localDx * cos(rad) - localDy * sin(rad)).toFloat(),
        layer.yPx + (localDx * sin(rad) + localDy * cos(rad)).toFloat()
    )
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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCheckerboard(
    origin: Offset,
    size: Size,
    cellPx: Float,
    isDarkTheme: Boolean
) {
    val c1 = if (isDarkTheme) Color(0xFF1A1C24) else Color(0xFFE8EBF2)
    val c2 = if (isDarkTheme) Color(0xFF232631) else Color(0xFFD9DEE8)
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
