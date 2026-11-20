package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppThemeMode
import com.example.data.FontRepository
import com.example.data.ProjectRepository
import com.example.data.StudioPreferences
import com.example.data.ThemeSettings
import com.example.data.TypeframeDatabase
import com.example.model.CanvasLayer
import com.example.model.CropRect
import com.example.model.FontFamilyGroup
import com.example.model.FontItem
import com.example.model.ImageLayer
import com.example.model.LayerBlendMode
import com.example.model.PaintLayer
import com.example.model.PaintStroke
import com.example.model.ProjectDocument
import com.example.model.ShapeKind
import com.example.model.ShapeLayer
import com.example.model.StrokePoint
import com.example.model.TextLayer
import com.example.model.TextStyleSpan
import com.example.render.CanvasRenderer
import com.example.render.ExportEngine
import com.example.render.ExportMode
import com.example.render.ExportResult
import com.example.render.ExportScaleOption
import com.example.typography.GlyphAlternateMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.min

enum class EditorTab {
    NONE,
    TEXT_STYLE,
    FONTS,
    OPENTYPE,
    COLOR,
    SHAPE,
    BRUSH,
    TRANSFORM,
    CANVAS
}

enum class ColorTarget {
    TEXT_FILL,
    TEXT_STROKE,
    TEXT_SHADOW,
    SHAPE_FILL,
    SHAPE_STROKE,
    BRUSH_COLOR,
    CANVAS_BG
}

data class BrushSettings(
    val sizePx: Float = 36f,
    val hardness: Float = 0.85f,
    val opacity: Float = 1f,
    val flow: Float = 1f,
    val colorArgb: Int = 0xFFFF5A36.toInt(),
    val isEraser: Boolean = false
)

data class StudioUiState(
    val isLoading: Boolean = true,
    val document: ProjectDocument? = null,
    val selectedLayerId: String? = null,
    val activeTab: EditorTab = EditorTab.NONE,
    val activeColorTarget: ColorTarget = ColorTarget.TEXT_FILL,
    val isLayersPanelOpen: Boolean = false,
    val isBrushMode: Boolean = false,
    val brushSettings: BrushSettings = BrushSettings(),
    val isInlineTextEditing: Boolean = false,
    val inlineSelectionStart: Int = 0,
    val inlineSelectionEnd: Int = 0,
    val isEyedropperActive: Boolean = false,
    val eyedropperBitmap: Bitmap? = null,
    val showCropDialog: Boolean = false,
    val showExportDialog: Boolean = false,
    val showSettingsDialog: Boolean = false,
    val isExporting: Boolean = false,
    val lastExportResult: ExportResult? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val alternateMatches: List<GlyphAlternateMatch> = emptyList(),
    val statusMessage: String? = null
)

class StudioViewModel(application: Application) : AndroidViewModel(application) {
    private val db = TypeframeDatabase.getInstance(application)
    val projectRepository = ProjectRepository(application, db.projectDao())
    val fontRepository = FontRepository(application, db.fontDao())
    val preferences = StudioPreferences.getInstance(application)

    val fonts: StateFlow<List<FontItem>> = fontRepository.observeAllFonts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val themeSettings: StateFlow<ThemeSettings> = preferences.themeSettings
    val favoriteFonts: StateFlow<Set<String>> = preferences.favoriteFonts
    val savedColors: StateFlow<List<Int>> = preferences.savedColors
    val recentColors: StateFlow<List<Int>> = preferences.recentColors

    val fontFamilies: StateFlow<List<FontFamilyGroup>> = combine(fonts, favoriteFonts) { list, favs ->
        fontRepository.groupIntoFamilies(list, favs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(StudioUiState())
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    private val undoStack = ArrayDeque<ProjectDocument>()
    private val redoStack = ArrayDeque<ProjectDocument>()
    private var transientCommitted = false

    fun loadProject(projectId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            fontRepository.getBundledFontsImmediate()
            val doc = projectRepository.getDocument(projectId)
            undoStack.clear()
            redoStack.clear()
            val firstSelected = doc?.layers?.lastOrNull()?.id
            val initialTab = when (doc?.layers?.lastOrNull()) {
                is TextLayer -> EditorTab.TEXT_STYLE
                is ShapeLayer -> EditorTab.SHAPE
                is ImageLayer -> EditorTab.TRANSFORM
                is PaintLayer -> EditorTab.BRUSH
                null -> EditorTab.NONE
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    document = doc,
                    selectedLayerId = firstSelected,
                    activeTab = initialTab,
                    isLayersPanelOpen = false,
                    isBrushMode = false,
                    isInlineTextEditing = false,
                    canUndo = false,
                    canRedo = false
                )
            }
            refreshAlternateMatches()
        }
    }

    fun selectTab(tab: EditorTab) {
        _uiState.update { state ->
            val toggled = if (state.activeTab == tab) EditorTab.NONE else tab
            val target = resolveDefaultColorTarget(state, toggled)
            val brushActive = toggled == EditorTab.BRUSH
            state.copy(
                activeTab = toggled,
                activeColorTarget = target,
                isBrushMode = brushActive,
                isInlineTextEditing = if (brushActive) false else state.isInlineTextEditing
            )
        }
    }

    fun toggleLayersPanel() {
        _uiState.update { it.copy(isLayersPanelOpen = !it.isLayersPanelOpen) }
    }

    fun setLayersPanelOpen(open: Boolean) {
        _uiState.update { it.copy(isLayersPanelOpen = open) }
    }

    fun setColorTarget(target: ColorTarget) {
        _uiState.update {
            it.copy(
                activeColorTarget = target,
                activeTab = EditorTab.COLOR,
                isBrushMode = false
            )
        }
    }

    fun selectLayer(layerId: String?) {
        _uiState.update { state ->
            val layer = state.document?.layers?.find { it.id == layerId }
            val keepInline = state.isInlineTextEditing && layer is TextLayer && layer.id == state.selectedLayerId
            val nextTab = when {
                layer == null -> if (state.isBrushMode) EditorTab.BRUSH else EditorTab.NONE
                state.isBrushMode && (layer is PaintLayer || layer is ImageLayer) -> EditorTab.BRUSH
                layer is TextLayer && state.activeTab in setOf(EditorTab.SHAPE, EditorTab.TRANSFORM, EditorTab.BRUSH, EditorTab.NONE) ->
                    EditorTab.TEXT_STYLE
                layer is ShapeLayer && state.activeTab in setOf(EditorTab.TEXT_STYLE, EditorTab.FONTS, EditorTab.OPENTYPE, EditorTab.BRUSH, EditorTab.NONE) ->
                    EditorTab.SHAPE
                layer is ImageLayer && state.activeTab in setOf(EditorTab.TEXT_STYLE, EditorTab.FONTS, EditorTab.OPENTYPE, EditorTab.SHAPE, EditorTab.NONE) ->
                    EditorTab.TRANSFORM
                layer is PaintLayer -> EditorTab.BRUSH
                else -> state.activeTab
            }
            state.copy(
                selectedLayerId = layerId,
                activeTab = nextTab,
                isInlineTextEditing = keepInline,
                isBrushMode = nextTab == EditorTab.BRUSH,
                activeColorTarget = resolveDefaultColorTarget(state.copy(selectedLayerId = layerId), nextTab)
            )
        }
        refreshAlternateMatches()
    }

    fun beginTransientGesture() {
        val current = _uiState.value.document ?: return
        if (!transientCommitted) {
            pushUndoSnapshot(current)
            transientCommitted = true
        }
    }

    fun endTransientGesture() {
        transientCommitted = false
        _uiState.update {
            it.copy(
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    fun updateDocumentTransient(transform: (ProjectDocument) -> ProjectDocument) {
        val current = _uiState.value.document ?: return
        val next = transform(current)
        _uiState.update { it.copy(document = next) }
    }

    fun updateDocument(transform: (ProjectDocument) -> ProjectDocument) {
        val current = _uiState.value.document ?: return
        pushUndoSnapshot(current)
        val next = transform(current)
        _uiState.update {
            it.copy(
                document = next,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
        refreshAlternateMatches()
    }

    private fun pushUndoSnapshot(snapshot: ProjectDocument) {
        if (undoStack.size >= 40) undoStack.removeFirst()
        undoStack.addLast(snapshot)
        redoStack.clear()
    }

    fun undo() {
        val current = _uiState.value.document ?: return
        if (undoStack.isEmpty()) return
        val prev = undoStack.removeLast()
        redoStack.addLast(current)
        _uiState.update {
            it.copy(
                document = prev,
                selectedLayerId = prev.layers.find { l -> l.id == it.selectedLayerId }?.id
                    ?: prev.layers.lastOrNull()?.id,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
        refreshAlternateMatches()
    }

    fun redo() {
        val current = _uiState.value.document ?: return
        if (redoStack.isEmpty()) return
        val next = redoStack.removeLast()
        undoStack.addLast(current)
        _uiState.update {
            it.copy(
                document = next,
                selectedLayerId = next.layers.find { l -> l.id == it.selectedLayerId }?.id
                    ?: next.layers.lastOrNull()?.id,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
        refreshAlternateMatches()
    }

    // ---- Layer creation ----

    fun addTextLayer() {
        val doc = _uiState.value.document ?: return
        val defaultFontId = fonts.value.firstOrNull()?.id ?: "asset:IBMPlexSansArabic-Regular.otf"
        val defaultText = "خط عربي أصيل\nHTYPE Studio"
        val newLayer = TextLayer(
            name = "Text ${doc.layers.count { it is TextLayer } + 1}",
            xPx = doc.canvasWidthPx / 2f,
            yPx = doc.canvasHeightPx / 2f,
            fontId = defaultFontId,
            fontSizePx = (doc.canvasWidthPx * 0.085f).coerceIn(48f, 140f),
            boxWidthPx = doc.canvasWidthPx * 0.84f,
            text = defaultText
        )
        updateDocument { it.copy(layers = it.layers + newLayer) }
        _uiState.update {
            it.copy(
                selectedLayerId = newLayer.id,
                activeTab = EditorTab.TEXT_STYLE,
                activeColorTarget = ColorTarget.TEXT_FILL,
                isBrushMode = false,
                isInlineTextEditing = true,
                inlineSelectionStart = 0,
                inlineSelectionEnd = defaultText.length
            )
        }
        refreshAlternateMatches()
    }

    fun addShapeLayer(kind: ShapeKind) {
        val doc = _uiState.value.document ?: return
        val baseDim = (min(doc.canvasWidthPx, doc.canvasHeightPx) * 0.34f).toInt().toFloat()
        val newLayer = when (kind) {
            ShapeKind.LINE -> ShapeLayer(
                name = "Line",
                shapeKind = ShapeKind.LINE,
                xPx = doc.canvasWidthPx / 2f,
                yPx = doc.canvasHeightPx / 2f,
                widthPx = baseDim * 1.4f,
                heightPx = 24f,
                strokeWidthPx = 10f,
                fillEnabled = false,
                strokeEnabled = true
            )
            ShapeKind.RECTANGLE -> ShapeLayer(
                name = "Rectangle",
                shapeKind = ShapeKind.RECTANGLE,
                xPx = doc.canvasWidthPx / 2f,
                yPx = doc.canvasHeightPx / 2f,
                widthPx = baseDim,
                heightPx = baseDim * 0.65f,
                strokeWidthPx = 8f,
                cornerRadiusPx = 24f,
                fillEnabled = true,
                strokeEnabled = true
            )
            ShapeKind.CIRCLE -> ShapeLayer(
                name = "Circle",
                shapeKind = ShapeKind.CIRCLE,
                xPx = doc.canvasWidthPx / 2f,
                yPx = doc.canvasHeightPx / 2f,
                widthPx = baseDim,
                heightPx = baseDim,
                strokeWidthPx = 8f,
                fillEnabled = true,
                strokeEnabled = true
            )
        }
        updateDocument { it.copy(layers = it.layers + newLayer) }
        _uiState.update {
            it.copy(
                selectedLayerId = newLayer.id,
                activeTab = EditorTab.SHAPE,
                activeColorTarget = ColorTarget.SHAPE_STROKE,
                isBrushMode = false,
                isInlineTextEditing = false
            )
        }
    }

    fun addPaintLayer(): PaintLayer? {
        val doc = _uiState.value.document ?: return null
        val newLayer = PaintLayer(
            name = "Paint ${doc.layers.count { it is PaintLayer } + 1}",
            canvasWidthPx = doc.canvasWidthPx,
            canvasHeightPx = doc.canvasHeightPx
        )
        updateDocument { it.copy(layers = it.layers + newLayer) }
        _uiState.update {
            it.copy(
                selectedLayerId = newLayer.id,
                activeTab = EditorTab.BRUSH,
                isBrushMode = true,
                isInlineTextEditing = false,
                activeColorTarget = ColorTarget.BRUSH_COLOR
            )
        }
        return newLayer
    }

    fun importImageLayer(uri: Uri) {
        val doc = _uiState.value.document ?: return
        viewModelScope.launch {
            projectRepository.copyImageToProjectMedia(doc.id, uri)
                .onSuccess { media ->
                    val fitScale = min(
                        (doc.canvasWidthPx * 0.85f) / media.naturalWidth,
                        (doc.canvasHeightPx * 0.85f) / media.naturalHeight
                    )
                    val layer = ImageLayer(
                        name = "Photo ${doc.layers.count { it is ImageLayer } + 1}",
                        xPx = doc.canvasWidthPx / 2f,
                        yPx = doc.canvasHeightPx / 2f,
                        imagePath = media.path,
                        widthPx = media.naturalWidth * fitScale,
                        heightPx = media.naturalHeight * fitScale,
                        naturalWidthPx = media.naturalWidth,
                        naturalHeightPx = media.naturalHeight
                    )
                    updateDocument { it.copy(layers = it.layers + layer) }
                    _uiState.update {
                        it.copy(
                            selectedLayerId = layer.id,
                            activeTab = EditorTab.TRANSFORM,
                            isBrushMode = false,
                            isInlineTextEditing = false
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(statusMessage = err.message ?: "Failed to import photo.") }
                }
        }
    }

    // ---- Direct On-Canvas Rich Text Editing ----

    fun enterInlineTextEditing(layerId: String? = null) {
        val targetId = layerId ?: _uiState.value.selectedLayerId
        val textLayer = _uiState.value.document?.layers?.find { it.id == targetId } as? TextLayer ?: return
        if (textLayer.isLocked) return
        _uiState.update {
            it.copy(
                selectedLayerId = textLayer.id,
                isInlineTextEditing = true,
                isBrushMode = false,
                inlineSelectionStart = 0,
                inlineSelectionEnd = textLayer.text.length,
                activeColorTarget = ColorTarget.TEXT_FILL
            )
        }
    }

    fun exitInlineTextEditing() {
        _uiState.update { it.copy(isInlineTextEditing = false) }
    }

    fun updateInlineSelection(start: Int, end: Int) {
        val lo = minOf(start, end).coerceAtLeast(0)
        val hi = maxOf(start, end).coerceAtLeast(0)
        _uiState.update {
            it.copy(inlineSelectionStart = lo, inlineSelectionEnd = hi)
        }
    }

    fun updateInlineTextContent(newText: String, selStart: Int, selEnd: Int) {
        val layer = selectedLayer() as? TextLayer ?: return
        val adjustedSpans = adjustSpansForTextEdit(layer.spans, layer.text, newText)
        updateSelectedText { it.copy(text = newText, spans = adjustedSpans) }
        val lo = minOf(selStart, selEnd).coerceIn(0, newText.length)
        val hi = maxOf(selStart, selEnd).coerceIn(0, newText.length)
        _uiState.update {
            it.copy(inlineSelectionStart = lo, inlineSelectionEnd = hi)
        }
    }

    fun applyStyleToInlineSelection(
        fontId: String? = null,
        fontSizePx: Float? = null,
        colorArgb: Int? = null,
        toggleBold: Boolean = false,
        toggleItalic: Boolean = false,
        toggleUnderline: Boolean = false
    ) {
        val layer = selectedLayer() as? TextLayer ?: return
        val state = _uiState.value
        val len = layer.text.length
        if (len == 0) return

        val start = state.inlineSelectionStart.coerceIn(0, len)
        val end = state.inlineSelectionEnd.coerceIn(0, len)
        val hasPartialSelection = state.isInlineTextEditing && start < end && !(start == 0 && end == len)

        if (!hasPartialSelection) {
            // Whole layer styling when nothing partial is selected (or entire text selected)
            updateSelectedText { current ->
                var next = current
                if (fontId != null) next = next.copy(fontId = fontId)
                if (fontSizePx != null) next = next.copy(fontSizePx = fontSizePx.coerceIn(18f, 360f))
                if (colorArgb != null) next = next.copy(colorArgb = colorArgb)
                if (toggleBold || toggleItalic || toggleUnderline) {
                    val existingFull = current.spans.find { it.start == 0 && it.end == len }
                    val newSpan = TextStyleSpan(
                        start = 0,
                        end = len,
                        fontId = existingFull?.fontId,
                        fontSizePx = existingFull?.fontSizePx,
                        colorArgb = existingFull?.colorArgb,
                        isBold = if (toggleBold) !(existingFull?.isBold ?: false) else existingFull?.isBold,
                        isItalic = if (toggleItalic) !(existingFull?.isItalic ?: false) else existingFull?.isItalic,
                        isUnderline = if (toggleUnderline) !(existingFull?.isUnderline ?: false) else existingFull?.isUnderline
                    )
                    val others = current.spans.filterNot { it.start == 0 && it.end == len }
                    next = next.copy(spans = others + newSpan)
                }
                next
            }
        } else {
            // Range-specific rich span without splitting into separate layers
            updateSelectedText { current ->
                val exact = current.spans.find { it.start == start && it.end == end }
                val merged = TextStyleSpan(
                    start = start,
                    end = end,
                    fontId = fontId ?: exact?.fontId,
                    fontSizePx = fontSizePx?.coerceIn(18f, 360f) ?: exact?.fontSizePx,
                    colorArgb = colorArgb ?: exact?.colorArgb,
                    isBold = if (toggleBold) !(exact?.isBold ?: false) else exact?.isBold,
                    isItalic = if (toggleItalic) !(exact?.isItalic ?: false) else exact?.isItalic,
                    isUnderline = if (toggleUnderline) !(exact?.isUnderline ?: false) else exact?.isUnderline
                )
                val updatedList = current.spans.filterNot { it.start == start && it.end == end } + merged
                current.copy(spans = updatedList)
            }
        }
        colorArgb?.let { preferences.recordRecentColor(it) }
    }

    fun clearInlineRangeSpans() {
        val state = _uiState.value
        val layer = selectedLayer() as? TextLayer ?: return
        val start = state.inlineSelectionStart
        val end = state.inlineSelectionEnd
        updateSelectedText { current ->
            val nextSpans = if (state.isInlineTextEditing && start < end && !(start == 0 && end == layer.text.length)) {
                current.spans.filterNot { it.start < end && it.end > start }
            } else {
                emptyList()
            }
            current.copy(spans = nextSpans)
        }
    }

    // ---- Font Manager ----

    fun importCustomFont(uri: Uri) {
        importMultipleFonts(listOf(uri))
    }

    fun importMultipleFonts(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            var importedCount = 0
            var lastFont: FontItem? = null
            var lastError: String? = null
            for (uri in uris) {
                fontRepository.importFontFromUri(uri)
                    .onSuccess { item ->
                        importedCount++
                        lastFont = item
                    }
                    .onFailure { err ->
                        lastError = err.message
                    }
            }
            val last = lastFont
            if (last != null) {
                val selId = _uiState.value.selectedLayerId
                val selectedText = _uiState.value.document?.layers?.find { it.id == selId } as? TextLayer
                if (selectedText != null) {
                    applyFontToSelectedText(last)
                }
            }
            val msg = when {
                importedCount == 1 && last != null -> "Loaded ${last.displayName}"
                importedCount > 1 -> "Imported $importedCount font files"
                else -> lastError ?: "Could not import font files."
            }
            _uiState.update { it.copy(statusMessage = msg) }
        }
    }

    fun toggleFavoriteFontFamily(familyKey: String) {
        preferences.toggleFavoriteFontFamily(familyKey)
    }

    fun applyFontToSelectedText(fontItem: FontItem) {
        val state = _uiState.value
        val selectedText = selectedLayer() as? TextLayer
        val hasPartial = state.isInlineTextEditing &&
            selectedText != null &&
            state.inlineSelectionStart < state.inlineSelectionEnd &&
            !(state.inlineSelectionStart == 0 && state.inlineSelectionEnd == selectedText.text.length)

        if (hasPartial) {
            applyStyleToInlineSelection(fontId = fontItem.id)
        } else {
            val defaultMap = fontItem.metadata.features
                .filter { it.isUserToggleable && it.defaultEnabled }
                .associate { it.tag to 1 }
            updateSelectedText { it.copy(fontId = fontItem.id, openTypeFeatures = defaultMap) }
        }
    }

    fun deleteImportedFont(fontItem: FontItem) {
        viewModelScope.launch {
            val fallbackId = fonts.value.firstOrNull { it.isBundled }?.id
                ?: "asset:IBMPlexSansArabic-Regular.otf"
            updateDocument { doc ->
                doc.copy(
                    layers = doc.layers.map { layer ->
                        if (layer is TextLayer && layer.fontId == fontItem.id) {
                            layer.copy(
                                fontId = fallbackId,
                                spans = layer.spans.map { s ->
                                    if (s.fontId == fontItem.id) s.copy(fontId = fallbackId) else s
                                }
                            )
                        } else layer
                    }
                )
            }
            fontRepository.deleteImportedFont(fontItem)
            _uiState.update { it.copy(statusMessage = "Removed ${fontItem.displayName}") }
        }
    }

    // ---- Brush & Eraser ----

    fun updateBrushSettings(transform: (BrushSettings) -> BrushSettings) {
        _uiState.update { it.copy(brushSettings = transform(it.brushSettings)) }
    }

    fun commitBrushStroke(points: List<StrokePoint>) {
        if (points.isEmpty()) return
        val doc = _uiState.value.document ?: return
        val brush = _uiState.value.brushSettings
        val stroke = PaintStroke(
            points = points,
            colorArgb = brush.colorArgb,
            sizePx = brush.sizePx,
            hardness = brush.hardness,
            opacity = brush.opacity,
            flow = brush.flow,
            isEraser = brush.isEraser
        )
        if (!brush.isEraser) {
            preferences.recordRecentColor(brush.colorArgb)
        }

        val selected = selectedLayer()
        if (brush.isEraser && selected is ImageLayer && !selected.isLocked) {
            updateDocument { d ->
                d.copy(layers = d.layers.map {
                    if (it.id == selected.id && it is ImageLayer) {
                        it.copy(eraseMaskStrokes = it.eraseMaskStrokes + stroke)
                    } else it
                })
            }
            return
        }

        val targetPaintLayer = when {
            selected is PaintLayer && !selected.isLocked && selected.isVisible -> selected
            else -> doc.layers.lastOrNull { it is PaintLayer && !it.isLocked && it.isVisible } as? PaintLayer
        }

        if (targetPaintLayer != null) {
            updateDocument { d ->
                d.copy(layers = d.layers.map {
                    if (it.id == targetPaintLayer.id && it is PaintLayer) {
                        it.copy(strokes = it.strokes + stroke)
                    } else it
                })
            }
            _uiState.update { it.copy(selectedLayerId = targetPaintLayer.id) }
        } else {
            val newPaint = PaintLayer(
                name = "Paint ${doc.layers.count { it is PaintLayer } + 1}",
                canvasWidthPx = doc.canvasWidthPx,
                canvasHeightPx = doc.canvasHeightPx,
                strokes = listOf(stroke)
            )
            updateDocument { d -> d.copy(layers = d.layers + newPaint) }
            _uiState.update { it.copy(selectedLayerId = newPaint.id) }
        }
    }

    fun clearImageEraseMask(layerId: String) {
        updateDocument { doc ->
            doc.copy(layers = doc.layers.map {
                if (it.id == layerId && it is ImageLayer) it.copy(eraseMaskStrokes = emptyList()) else it
            })
        }
    }

    // ---- Layer Mutators ----

    fun updateSelectedText(transform: (TextLayer) -> TextLayer) {
        val selId = _uiState.value.selectedLayerId ?: return
        updateDocument { doc ->
            doc.copy(
                layers = doc.layers.map { if (it.id == selId && it is TextLayer) transform(it) else it }
            )
        }
    }

    fun updateSelectedShape(transform: (ShapeLayer) -> ShapeLayer) {
        val selId = _uiState.value.selectedLayerId ?: return
        updateDocument { doc ->
            doc.copy(
                layers = doc.layers.map { if (it.id == selId && it is ShapeLayer) transform(it) else it }
            )
        }
    }

    fun updateSelectedImage(transform: (ImageLayer) -> ImageLayer) {
        val selId = _uiState.value.selectedLayerId ?: return
        updateDocument { doc ->
            doc.copy(
                layers = doc.layers.map { if (it.id == selId && it is ImageLayer) transform(it) else it }
            )
        }
    }

    fun applyCropToSelectedImage(cropRect: CropRect) {
        val sel = selectedLayer() as? ImageLayer ?: return
        val cropW = (cropRect.right - cropRect.left).coerceAtLeast(0.05f)
        val cropH = (cropRect.bottom - cropRect.top).coerceAtLeast(0.05f)
        val targetAspect = (sel.naturalWidthPx * cropW) / (sel.naturalHeightPx * cropH)
        val newHeight = (sel.widthPx / targetAspect).coerceIn(48f, 4096f)
        updateSelectedImage { it.copy(cropRect = cropRect, heightPx = newHeight) }
        setShowCropDialog(false)
    }

    fun updateSelectedLayerTransform(
        xPx: Float? = null,
        yPx: Float? = null,
        rotationDeg: Float? = null,
        scaleX: Float? = null,
        scaleY: Float? = null,
        opacity: Float? = null
    ) {
        val selId = _uiState.value.selectedLayerId ?: return
        updateDocument { doc ->
            doc.copy(
                layers = doc.layers.map { layer ->
                    if (layer.id == selId) {
                        layer.withBaseTransform(
                            xPx = xPx ?: layer.xPx,
                            yPx = yPx ?: layer.yPx,
                            rotationDeg = rotationDeg ?: layer.rotationDeg,
                            scaleX = scaleX ?: layer.scaleX,
                            scaleY = scaleY ?: layer.scaleY,
                            opacity = opacity ?: layer.opacity
                        )
                    } else layer
                }
            )
        }
    }

    fun toggleOpenTypeFeature(tag: String, newValue: Int) {
        updateSelectedText { textLayer ->
            val updated = textLayer.openTypeFeatures.toMutableMap()
            updated[tag] = newValue.coerceAtLeast(0)
            textLayer.copy(openTypeFeatures = updated)
        }
    }

    fun moveLayerUp(layerId: String) {
        updateDocument { doc ->
            val idx = doc.layers.indexOfFirst { it.id == layerId }
            if (idx in 0 until doc.layers.lastIndex) {
                val mutable = doc.layers.toMutableList()
                val item = mutable.removeAt(idx)
                mutable.add(idx + 1, item)
                doc.copy(layers = mutable)
            } else doc
        }
    }

    fun moveLayerDown(layerId: String) {
        updateDocument { doc ->
            val idx = doc.layers.indexOfFirst { it.id == layerId }
            if (idx > 0) {
                val mutable = doc.layers.toMutableList()
                val item = mutable.removeAt(idx)
                mutable.add(idx - 1, item)
                doc.copy(layers = mutable)
            } else doc
        }
    }

    fun reorderLayer(fromIndex: Int, toIndex: Int) {
        updateDocument { doc ->
            if (fromIndex !in doc.layers.indices || toIndex !in doc.layers.indices || fromIndex == toIndex) {
                doc
            } else {
                val mutable = doc.layers.toMutableList()
                val moved = mutable.removeAt(fromIndex)
                mutable.add(toIndex, moved)
                doc.copy(layers = mutable)
            }
        }
    }

    fun renameLayer(layerId: String, newName: String) {
        val clean = newName.trim().ifBlank { return }
        updateDocument { doc ->
            doc.copy(layers = doc.layers.map {
                if (it.id == layerId) it.withBaseTransform(name = clean) else it
            })
        }
    }

    fun setLayerBlendMode(layerId: String, blendMode: LayerBlendMode) {
        updateDocument { doc ->
            doc.copy(layers = doc.layers.map {
                if (it.id == layerId) it.withBaseTransform(blendMode = blendMode) else it
            })
        }
    }

    fun setLayerOpacity(layerId: String, opacity: Float) {
        updateDocument { doc ->
            doc.copy(layers = doc.layers.map {
                if (it.id == layerId) it.withBaseTransform(opacity = opacity.coerceIn(0.02f, 1f)) else it
            })
        }
    }

    fun toggleLayerVisibility(layerId: String) {
        updateDocument { doc ->
            doc.copy(layers = doc.layers.map {
                if (it.id == layerId) it.withBaseTransform(isVisible = !it.isVisible) else it
            })
        }
    }

    fun toggleLayerLock(layerId: String) {
        updateDocument { doc ->
            doc.copy(layers = doc.layers.map {
                if (it.id == layerId) it.withBaseTransform(isLocked = !it.isLocked) else it
            })
        }
    }

    fun duplicateLayer(layerId: String) {
        val doc = _uiState.value.document ?: return
        val target = doc.layers.find { it.id == layerId } ?: return
        val copyId = UUID.randomUUID().toString()
        val offset = if (target is PaintLayer) 0f else 32f
        val dup: CanvasLayer = when (target) {
            is ImageLayer -> target.copy(id = copyId, name = "${target.name} Copy", xPx = target.xPx + offset, yPx = target.yPx + offset)
            is TextLayer -> target.copy(id = copyId, name = "${target.name} Copy", xPx = target.xPx + offset, yPx = target.yPx + offset)
            is ShapeLayer -> target.copy(id = copyId, name = "${target.name} Copy", xPx = target.xPx + offset, yPx = target.yPx + offset)
            is PaintLayer -> target.copy(id = copyId, name = "${target.name} Copy")
        }
        updateDocument { it.copy(layers = it.layers + dup) }
        _uiState.update { it.copy(selectedLayerId = dup.id) }
    }

    fun deleteLayer(layerId: String) {
        updateDocument { doc ->
            val filtered = doc.layers.filterNot { it.id == layerId }
            doc.copy(layers = filtered)
        }
        _uiState.update { state ->
            val nextSelected = if (state.selectedLayerId == layerId) {
                state.document?.layers?.lastOrNull()?.id
            } else state.selectedLayerId
            state.copy(
                selectedLayerId = nextSelected,
                isInlineTextEditing = if (state.selectedLayerId == layerId) false else state.isInlineTextEditing
            )
        }
    }

    // ---- Color, Saved Colors & Eyedropper ----

    fun applyColorToActiveTarget(argb: Int) {
        val state = _uiState.value
        val target = state.activeColorTarget
        val selectedText = selectedLayer() as? TextLayer
        val hasPartialTextSel = target == ColorTarget.TEXT_FILL &&
            state.isInlineTextEditing &&
            selectedText != null &&
            state.inlineSelectionStart < state.inlineSelectionEnd &&
            !(state.inlineSelectionStart == 0 && state.inlineSelectionEnd == selectedText.text.length)

        if (hasPartialTextSel) {
            applyStyleToInlineSelection(colorArgb = argb)
            return
        }

        when (target) {
            ColorTarget.TEXT_FILL -> updateSelectedText { it.copy(colorArgb = argb) }
            ColorTarget.TEXT_STROKE -> updateSelectedText {
                it.copy(stroke = it.stroke.copy(enabled = true, colorArgb = argb))
            }
            ColorTarget.TEXT_SHADOW -> updateSelectedText {
                it.copy(shadow = it.shadow.copy(enabled = true, colorArgb = argb))
            }
            ColorTarget.SHAPE_FILL -> updateSelectedShape {
                it.copy(fillEnabled = true, fillColorArgb = argb)
            }
            ColorTarget.SHAPE_STROKE -> updateSelectedShape {
                it.copy(strokeEnabled = true, strokeColorArgb = argb)
            }
            ColorTarget.BRUSH_COLOR -> updateBrushSettings {
                it.copy(colorArgb = argb, isEraser = false)
            }
            ColorTarget.CANVAS_BG -> updateDocument {
                it.copy(backgroundColorArgb = argb, isTransparentBackground = false)
            }
        }
        preferences.recordRecentColor(argb)
    }

    fun addSavedColor(argb: Int) {
        preferences.addSavedColor(argb)
    }

    fun removeSavedColor(argb: Int) {
        preferences.removeSavedColor(argb)
    }

    fun activateEyedropper(target: ColorTarget? = null) {
        val doc = _uiState.value.document ?: return
        val resolvedTarget = target ?: _uiState.value.activeColorTarget
        viewModelScope.launch {
            val fontsMap = fonts.value.associateBy { it.id }
            val bitmap = withContext(Dispatchers.Default) {
                val scale = (900f / maxOf(doc.canvasWidthPx, doc.canvasHeightPx)).coerceAtMost(1f)
                CanvasRenderer.renderDocumentToBitmap(
                    doc = doc,
                    fontRepository = fontRepository,
                    fontsById = fontsMap,
                    forceTransparentBackground = false,
                    scaleFactor = scale,
                    isExportMode = false
                )
            }
            _uiState.update {
                it.copy(
                    isEyedropperActive = true,
                    activeColorTarget = resolvedTarget,
                    eyedropperBitmap = bitmap
                )
            }
        }
    }

    fun cancelEyedropper() {
        _uiState.update { state ->
            state.eyedropperBitmap?.recycle()
            state.copy(isEyedropperActive = false, eyedropperBitmap = null)
        }
    }

    fun finishEyedropper(argb: Int) {
        applyColorToActiveTarget(argb)
        preferences.recordRecentColor(argb)
        cancelEyedropper()
        _uiState.update {
            it.copy(statusMessage = "Sampled #${Integer.toHexString(argb).uppercase().padStart(8, '0').takeLast(6)}")
        }
    }

    // ---- Theme & Dialogs ----

    fun setThemeMode(mode: AppThemeMode) {
        preferences.setThemeMode(mode)
    }

    fun setAccentColor(argb: Int) {
        preferences.setAccentColor(argb)
    }

    fun setShowCropDialog(show: Boolean) {
        _uiState.update { it.copy(showCropDialog = show) }
    }

    fun setShowExportDialog(show: Boolean) {
        _uiState.update { it.copy(showExportDialog = show, lastExportResult = if (!show) null else it.lastExportResult) }
    }

    fun setShowSettingsDialog(show: Boolean) {
        _uiState.update { it.copy(showSettingsDialog = show) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    // ---- Save & Export ----

    fun saveProject(onSaved: (() -> Unit)? = null) {
        val doc = _uiState.value.document ?: return
        viewModelScope.launch {
            val fontsMap = fonts.value.associateBy { it.id }
            val thumb = withContext(Dispatchers.Default) {
                val scale = (420f / maxOf(doc.canvasWidthPx, doc.canvasHeightPx)).coerceAtMost(1f)
                CanvasRenderer.renderDocumentToBitmap(
                    doc = doc,
                    fontRepository = fontRepository,
                    fontsById = fontsMap,
                    forceTransparentBackground = doc.isTransparentBackground,
                    scaleFactor = scale,
                    isExportMode = false
                )
            }
            projectRepository.saveDocument(doc, thumb)
            thumb.recycle()
            onSaved?.invoke()
        }
    }

    fun renderExportPreview(mode: ExportMode, textOnly: Boolean): Bitmap? {
        val doc = _uiState.value.document ?: return null
        val fontsMap = fonts.value.associateBy { it.id }
        return ExportEngine.renderPreviewThumbnail(doc, fontRepository, fontsMap, mode, textOnly)
    }

    fun export(mode: ExportMode, scaleOption: ExportScaleOption = ExportScaleOption.X1, textOnly: Boolean = false) {
        val doc = _uiState.value.document ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            val fontsMap = fonts.value.associateBy { it.id }
            ExportEngine.export(
                context = getApplication(),
                doc = doc,
                fontRepository = fontRepository,
                fontsById = fontsMap,
                mode = mode,
                scaleOption = scaleOption,
                textOnly = textOnly
            ).onSuccess { result ->
                val formatName = when (mode) {
                    ExportMode.FULL_IMAGE_PNG -> "PNG"
                    ExportMode.TRANSPARENT_PNG -> if (textOnly) "Text-Only PNG" else "Transparent PNG"
                    ExportMode.HIGH_RES_JPEG -> "JPEG"
                    ExportMode.PDF_DOCUMENT -> "PDF"
                }
                val destFolder = if (mode == ExportMode.PDF_DOCUMENT) "Downloads/HTYPE" else "Pictures/HTYPE"
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        lastExportResult = result,
                        statusMessage = if (result.galleryUri != null) {
                            "Saved ${result.widthPx}×${result.heightPx} $formatName to $destFolder"
                        } else {
                            "Prepared ${result.widthPx}×${result.heightPx} $formatName"
                        }
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        statusMessage = err.message ?: "Export failed."
                    )
                }
            }
        }
    }

    fun saveExportToCustomUri(
        uri: Uri,
        mode: ExportMode,
        scaleOption: ExportScaleOption = ExportScaleOption.X1,
        textOnly: Boolean = false
    ) {
        val doc = _uiState.value.document ?: return
        viewModelScope.launch {
            val fontsMap = fonts.value.associateBy { it.id }
            ExportEngine.writeExportToUri(
                context = getApplication(),
                targetUri = uri,
                doc = doc,
                fontRepository = fontRepository,
                fontsById = fontsMap,
                mode = mode,
                scaleOption = scaleOption,
                textOnly = textOnly
            ).onSuccess {
                _uiState.update { it.copy(statusMessage = "Exported file successfully.") }
            }.onFailure { err ->
                _uiState.update { it.copy(statusMessage = err.message ?: "Failed to save file.") }
            }
        }
    }

    fun selectedLayer(): CanvasLayer? {
        val doc = _uiState.value.document ?: return null
        val id = _uiState.value.selectedLayerId ?: return null
        return doc.layers.find { it.id == id }
    }

    fun activeColorValue(): Int {
        val doc = _uiState.value.document
        val layer = selectedLayer()
        return when (_uiState.value.activeColorTarget) {
            ColorTarget.TEXT_FILL -> (layer as? TextLayer)?.colorArgb ?: 0xFFFFFFFF.toInt()
            ColorTarget.TEXT_STROKE -> (layer as? TextLayer)?.stroke?.colorArgb ?: 0xFF000000.toInt()
            ColorTarget.TEXT_SHADOW -> (layer as? TextLayer)?.shadow?.colorArgb ?: 0x99000000.toInt()
            ColorTarget.SHAPE_FILL -> (layer as? ShapeLayer)?.fillColorArgb ?: 0x33FF5A36
            ColorTarget.SHAPE_STROKE -> (layer as? ShapeLayer)?.strokeColorArgb ?: 0xFFFF5A36.toInt()
            ColorTarget.BRUSH_COLOR -> _uiState.value.brushSettings.colorArgb
            ColorTarget.CANVAS_BG -> doc?.backgroundColorArgb ?: 0xFF14161D.toInt()
        }
    }

    private fun refreshAlternateMatches() {
        val textLayer = selectedLayer() as? TextLayer
        if (textLayer == null) {
            _uiState.update { it.copy(alternateMatches = emptyList()) }
            return
        }
        val fontItem = fonts.value.find { it.id == textLayer.fontId }
        val tags = fontItem?.metadata?.features
            ?.filter { it.isUserToggleable }
            ?.map { it.tag }
            ?.toSet()
            ?: setOf("salt", "aalt", "ss01", "ss02", "ss03", "ss04", "ss05", "ss06", "swsh")
        viewModelScope.launch(Dispatchers.Default) {
            val matches = fontRepository.findAlternatesForText(textLayer.fontId, textLayer.text, tags)
            _uiState.update { it.copy(alternateMatches = matches) }
        }
    }

    private fun resolveDefaultColorTarget(state: StudioUiState, tab: EditorTab): ColorTarget {
        if (tab == EditorTab.CANVAS) return ColorTarget.CANVAS_BG
        if (tab == EditorTab.BRUSH) return ColorTarget.BRUSH_COLOR
        val layer = state.document?.layers?.find { it.id == state.selectedLayerId }
        return when (layer) {
            is TextLayer -> if (state.activeColorTarget in setOf(
                    ColorTarget.TEXT_FILL, ColorTarget.TEXT_STROKE, ColorTarget.TEXT_SHADOW
                )
            ) state.activeColorTarget else ColorTarget.TEXT_FILL
            is ShapeLayer -> if (state.activeColorTarget in setOf(
                    ColorTarget.SHAPE_FILL, ColorTarget.SHAPE_STROKE
                )
            ) state.activeColorTarget else ColorTarget.SHAPE_STROKE
            is PaintLayer -> ColorTarget.BRUSH_COLOR
            else -> ColorTarget.CANVAS_BG
        }
    }

    companion object {
        fun adjustSpansForTextEdit(
            spans: List<TextStyleSpan>,
            oldText: String,
            newText: String
        ): List<TextStyleSpan> {
            if (spans.isEmpty() || oldText == newText) return spans
            val oldLen = oldText.length
            val newLen = newText.length
            var prefix = 0
            while (prefix < oldLen && prefix < newLen && oldText[prefix] == newText[prefix]) {
                prefix++
            }
            var suffix = 0
            while (suffix < (oldLen - prefix) &&
                suffix < (newLen - prefix) &&
                oldText[oldLen - 1 - suffix] == newText[newLen - 1 - suffix]
            ) {
                suffix++
            }
            val oldChangedEnd = oldLen - suffix
            val delta = newLen - oldLen
            return spans.mapNotNull { span ->
                val s = span.start
                val e = span.end
                val (ns, ne) = when {
                    e <= prefix -> s to e
                    s >= oldChangedEnd -> (s + delta) to (e + delta)
                    else -> {
                        val clampedS = s.coerceIn(0, newLen)
                        val clampedE = (e + delta).coerceIn(clampedS, newLen)
                        clampedS to clampedE
                    }
                }
                if (ne > ns) span.copy(start = ns.coerceIn(0, newLen), end = ne.coerceIn(0, newLen)) else null
            }
        }
    }
}
