package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.FontRepository
import com.example.data.ProjectRepository
import com.example.data.TypeframeDatabase
import com.example.model.CanvasLayer
import com.example.model.CropRect
import com.example.model.FontItem
import com.example.model.ImageLayer
import com.example.model.ProjectDocument
import com.example.model.ShapeKind
import com.example.model.ShapeLayer
import com.example.model.TextLayer
import com.example.render.CanvasRenderer
import com.example.render.ExportEngine
import com.example.render.ExportMode
import com.example.render.ExportResult
import com.example.typography.GlyphAlternateMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    TRANSFORM,
    LAYERS,
    CANVAS
}

enum class ColorTarget {
    TEXT_FILL,
    TEXT_STROKE,
    TEXT_SHADOW,
    SHAPE_FILL,
    SHAPE_STROKE,
    CANVAS_BG
}

data class StudioUiState(
    val isLoading: Boolean = true,
    val document: ProjectDocument? = null,
    val selectedLayerId: String? = null,
    val activeTab: EditorTab = EditorTab.NONE,
    val activeColorTarget: ColorTarget = ColorTarget.TEXT_FILL,
    val isEyedropperActive: Boolean = false,
    val eyedropperBitmap: Bitmap? = null,
    val showTextEditorDialog: Boolean = false,
    val showCropDialog: Boolean = false,
    val showExportDialog: Boolean = false,
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

    val fonts: StateFlow<List<FontItem>> = fontRepository.observeAllFonts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
                null -> EditorTab.NONE
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    document = doc,
                    selectedLayerId = firstSelected,
                    activeTab = initialTab,
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
            state.copy(activeTab = toggled, activeColorTarget = target)
        }
    }

    fun setColorTarget(target: ColorTarget) {
        _uiState.update { it.copy(activeColorTarget = target, activeTab = EditorTab.COLOR) }
    }

    fun selectLayer(layerId: String?) {
        _uiState.update { state ->
            val layer = state.document?.layers?.find { it.id == layerId }
            val nextTab = when {
                layer == null -> EditorTab.NONE
                state.activeTab == EditorTab.LAYERS -> EditorTab.LAYERS
                layer is TextLayer && state.activeTab in setOf(EditorTab.SHAPE, EditorTab.TRANSFORM, EditorTab.NONE) ->
                    EditorTab.TEXT_STYLE
                layer is ShapeLayer && state.activeTab in setOf(EditorTab.TEXT_STYLE, EditorTab.FONTS, EditorTab.OPENTYPE, EditorTab.NONE) ->
                    EditorTab.SHAPE
                layer is ImageLayer && state.activeTab in setOf(EditorTab.TEXT_STYLE, EditorTab.FONTS, EditorTab.OPENTYPE, EditorTab.SHAPE, EditorTab.NONE) ->
                    EditorTab.TRANSFORM
                else -> state.activeTab
            }
            state.copy(
                selectedLayerId = layerId,
                activeTab = nextTab,
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

    fun addTextLayer() {
        val doc = _uiState.value.document ?: return
        val defaultFontId = fonts.value.firstOrNull()?.id ?: "asset:IBMPlexSansArabic-Regular.otf"
        val newLayer = TextLayer(
            name = "Text ${doc.layers.count { it is TextLayer } + 1}",
            xPx = doc.canvasWidthPx / 2f,
            yPx = doc.canvasHeightPx / 2f,
            fontId = defaultFontId,
            fontSizePx = (doc.canvasWidthPx * 0.085f).coerceIn(48f, 140f),
            boxWidthPx = doc.canvasWidthPx * 0.84f,
            text = "خط عربي أصيل\nTypeframe Studio"
        )
        updateDocument { it.copy(layers = it.layers + newLayer) }
        _uiState.update {
            it.copy(
                selectedLayerId = newLayer.id,
                activeTab = EditorTab.TEXT_STYLE,
                activeColorTarget = ColorTarget.TEXT_FILL,
                showTextEditorDialog = true
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
                activeColorTarget = ColorTarget.SHAPE_STROKE
            )
        }
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
                        it.copy(selectedLayerId = layer.id, activeTab = EditorTab.TRANSFORM)
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(statusMessage = err.message ?: "Failed to import photo.") }
                }
        }
    }

    fun importCustomFont(uri: Uri) {
        viewModelScope.launch {
            fontRepository.importFontFromUri(uri)
                .onSuccess { fontItem ->
                    val selId = _uiState.value.selectedLayerId
                    val selectedText = _uiState.value.document?.layers?.find { it.id == selId } as? TextLayer
                    if (selectedText != null) {
                        applyFontToSelectedText(fontItem)
                    }
                    _uiState.update {
                        it.copy(statusMessage = "Loaded ${fontItem.displayName} (${fontItem.metadata.features.size} OpenType features)")
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(statusMessage = err.message ?: "Could not import font file.") }
                }
        }
    }

    fun applyFontToSelectedText(fontItem: FontItem) {
        val defaultMap = fontItem.metadata.features
            .filter { it.isUserToggleable && it.defaultEnabled }
            .associate { it.tag to 1 }
        updateSelectedText { it.copy(fontId = fontItem.id, openTypeFeatures = defaultMap) }
    }

    fun deleteImportedFont(fontItem: FontItem) {
        viewModelScope.launch {
            val fallbackId = fonts.value.firstOrNull { it.isBundled }?.id
                ?: "asset:IBMPlexSansArabic-Regular.otf"
            updateDocument { doc ->
                doc.copy(
                    layers = doc.layers.map { layer ->
                        if (layer is TextLayer && layer.fontId == fontItem.id) {
                            layer.copy(fontId = fallbackId)
                        } else layer
                    }
                )
            }
            fontRepository.deleteImportedFont(fontItem)
            _uiState.update { it.copy(statusMessage = "Removed ${fontItem.displayName}") }
        }
    }

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
        val offset = 32f
        val dup: CanvasLayer = when (target) {
            is ImageLayer -> target.copy(id = copyId, name = "${target.name} Copy", xPx = target.xPx + offset, yPx = target.yPx + offset)
            is TextLayer -> target.copy(id = copyId, name = "${target.name} Copy", xPx = target.xPx + offset, yPx = target.yPx + offset)
            is ShapeLayer -> target.copy(id = copyId, name = "${target.name} Copy", xPx = target.xPx + offset, yPx = target.yPx + offset)
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
            state.copy(selectedLayerId = nextSelected)
        }
    }

    fun applyColorToActiveTarget(argb: Int) {
        when (_uiState.value.activeColorTarget) {
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
            ColorTarget.CANVAS_BG -> updateDocument {
                it.copy(backgroundColorArgb = argb, isTransparentBackground = false)
            }
        }
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
        cancelEyedropper()
        _uiState.update { it.copy(statusMessage = "Sampled #${Integer.toHexString(argb).uppercase().padStart(8, '0').takeLast(6)}") }
    }

    fun setShowTextEditorDialog(show: Boolean) {
        _uiState.update { it.copy(showTextEditorDialog = show) }
    }

    fun setShowCropDialog(show: Boolean) {
        _uiState.update { it.copy(showCropDialog = show) }
    }

    fun setShowExportDialog(show: Boolean) {
        _uiState.update { it.copy(showExportDialog = show, lastExportResult = if (!show) null else it.lastExportResult) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

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

    fun exportPng(mode: ExportMode, scaleMultiplier: Int = 1) {
        val doc = _uiState.value.document ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            val fontsMap = fonts.value.associateBy { it.id }
            ExportEngine.exportPng(
                context = getApplication(),
                doc = doc,
                fontRepository = fontRepository,
                fontsById = fontsMap,
                mode = mode,
                scaleMultiplier = scaleMultiplier
            ).onSuccess { result ->
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        lastExportResult = result,
                        statusMessage = if (result.galleryUri != null) {
                            "Saved ${result.widthPx}×${result.heightPx} PNG to Pictures/TYPEFRAME"
                        } else {
                            "Prepared ${result.widthPx}×${result.heightPx} PNG"
                        }
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        statusMessage = err.message ?: "PNG export failed."
                    )
                }
            }
        }
    }

    fun saveExportToCustomUri(uri: Uri, mode: ExportMode, scaleMultiplier: Int = 1) {
        val doc = _uiState.value.document ?: return
        viewModelScope.launch {
            val fontsMap = fonts.value.associateBy { it.id }
            ExportEngine.writePngToUri(
                context = getApplication(),
                targetUri = uri,
                doc = doc,
                fontRepository = fontRepository,
                fontsById = fontsMap,
                mode = mode,
                scaleMultiplier = scaleMultiplier
            ).onSuccess {
                _uiState.update { it.copy(statusMessage = "Exported PNG successfully.") }
            }.onFailure { err ->
                _uiState.update { it.copy(statusMessage = err.message ?: "Failed to write PNG.") }
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
            else -> ColorTarget.CANVAS_BG
        }
    }
}
