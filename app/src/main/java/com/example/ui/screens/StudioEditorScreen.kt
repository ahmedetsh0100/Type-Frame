package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.ImageLayer
import com.example.model.ShapeKind
import com.example.model.ShapeLayer
import com.example.model.TextLayer
import com.example.render.ExportMode
import com.example.render.ExportScaleOption
import com.example.ui.components.BrushInspectorSheet
import com.example.ui.components.CanvasSettingsSheet
import com.example.ui.components.ColorPickerSheet
import com.example.ui.components.ExportDialog
import com.example.ui.components.FloatingLayersPanel
import com.example.ui.components.FontsSheet
import com.example.ui.components.ImageCropDialog
import com.example.ui.components.OpenTypeInspectorSheet
import com.example.ui.components.SettingsDialog
import com.example.ui.components.ShapeInspectorSheet
import com.example.ui.components.StudioCanvasStage
import com.example.ui.components.TextStyleInspectorSheet
import com.example.ui.components.TransformInspectorSheet
import com.example.ui.theme.LocalStudioPalette
import com.example.viewmodel.ColorTarget
import com.example.viewmodel.EditorTab
import com.example.viewmodel.StudioViewModel

@Composable
fun StudioEditorScreen(
    projectId: String,
    viewModel: StudioViewModel,
    onNavigateBack: () -> Unit
) {
    val palette = LocalStudioPalette.current

    LaunchedEffect(projectId) {
        viewModel.loadProject(projectId)
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val fonts by viewModel.fonts.collectAsStateWithLifecycle()
    val fontFamilies by viewModel.fontFamilies.collectAsStateWithLifecycle()
    val themeSettings by viewModel.themeSettings.collectAsStateWithLifecycle()
    val savedColors by viewModel.savedColors.collectAsStateWithLifecycle()
    val recentColors by viewModel.recentColors.collectAsStateWithLifecycle()
    val fontsById = remember(fonts) { fonts.associateBy { it.id } }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.statusMessage) {
        state.statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    val saveAndExit = {
        viewModel.saveProject(onSaved = onNavigateBack)
    }

    BackHandler {
        when {
            state.isInlineTextEditing -> viewModel.exitInlineTextEditing()
            state.isLayersPanelOpen -> viewModel.setLayersPanelOpen(false)
            else -> saveAndExit()
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { viewModel.importImageLayer(it) }
    }

    val multiFontPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.importMultipleFonts(uris)
    }

    var pendingExportMode by remember { mutableStateOf(ExportMode.FULL_IMAGE_PNG) }
    var pendingExportScale by remember { mutableStateOf(ExportScaleOption.X1) }
    var pendingTextOnly by remember { mutableStateOf(false) }

    val createPngLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri: Uri? ->
        uri?.let { viewModel.saveExportToCustomUri(it, pendingExportMode, pendingExportScale, pendingTextOnly) }
    }
    val createJpegLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/jpeg")
    ) { uri: Uri? ->
        uri?.let { viewModel.saveExportToCustomUri(it, pendingExportMode, pendingExportScale, pendingTextOnly) }
    }
    val createPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        uri?.let { viewModel.saveExportToCustomUri(it, pendingExportMode, pendingExportScale, pendingTextOnly) }
    }

    val doc = state.document
    val selectedLayer = doc?.layers?.find { it.id == state.selectedLayerId }

    Scaffold(
        containerColor = palette.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        if (state.isLoading || doc == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = palette.accent)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.background)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Studio Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.surface)
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                IconButton(
                    onClick = saveAndExit,
                    modifier = Modifier.testTag("editor_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Save and exit", tint = palette.textPrimary)
                }

                IconButton(
                    onClick = { viewModel.undo() },
                    enabled = state.canUndo,
                    modifier = Modifier.testTag("undo_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo",
                        tint = if (state.canUndo) palette.textPrimary else palette.textMuted
                    )
                }
                IconButton(
                    onClick = { viewModel.redo() },
                    enabled = state.canRedo,
                    modifier = Modifier.testTag("redo_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo",
                        tint = if (state.canRedo) palette.textPrimary else palette.textMuted
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { viewModel.selectTab(EditorTab.CANVAS) }
                        .padding(horizontal = 6.dp)
                ) {
                    Text(
                        text = doc.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${doc.canvasWidthPx}×${doc.canvasHeightPx} px",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary
                    )
                }

                IconButton(
                    onClick = { viewModel.setShowSettingsDialog(true) },
                    modifier = Modifier.testTag("editor_settings_button")
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Theme settings",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Button(
                    onClick = { viewModel.setShowExportDialog(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                    shape = RoundedCornerShape(99.dp),
                    modifier = Modifier.height(38.dp).testTag("export_button")
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Export", style = MaterialTheme.typography.labelLarge)
                }
            }

            // Center WYSIWYG Canvas Stage + Floating Layers Button & Panel
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                StudioCanvasStage(
                    document = doc,
                    selectedLayerId = state.selectedLayerId,
                    fontsById = fontsById,
                    fontRepository = viewModel.fontRepository,
                    isEyedropperActive = state.isEyedropperActive,
                    eyedropperBitmap = state.eyedropperBitmap,
                    isBrushMode = state.isBrushMode,
                    brushSettings = state.brushSettings,
                    isInlineTextEditing = state.isInlineTextEditing,
                    inlineSelectionStart = state.inlineSelectionStart,
                    inlineSelectionEnd = state.inlineSelectionEnd,
                    onSelectLayer = { viewModel.selectLayer(it) },
                    onDoubleTapTextLayer = { viewModel.enterInlineTextEditing(it.id) },
                    onInlineTextChanged = { txt, s, e -> viewModel.updateInlineTextContent(txt, s, e) },
                    onInlineSelectionChanged = { s, e -> viewModel.updateInlineSelection(s, e) },
                    onApplyInlineStyle = { sizeDelta, bold, italic, underline ->
                        val curText = selectedLayer as? TextLayer
                        val targetSize = if (sizeDelta != null && curText != null) {
                            (curText.fontSizePx + sizeDelta).coerceIn(18f, 360f)
                        } else null
                        viewModel.applyStyleToInlineSelection(
                            fontSizePx = targetSize,
                            toggleBold = bold,
                            toggleItalic = italic,
                            toggleUnderline = underline
                        )
                    },
                    onOpenColorForInlineText = { viewModel.setColorTarget(ColorTarget.TEXT_FILL) },
                    onOpenFontsForInlineText = { viewModel.selectTab(EditorTab.FONTS) },
                    onClearInlineSpans = { viewModel.clearInlineRangeSpans() },
                    onExitInlineTextEditing = { viewModel.exitInlineTextEditing() },
                    onCommitBrushStroke = { pts -> viewModel.commitBrushStroke(pts) },
                    onBeginGesture = { viewModel.beginTransientGesture() },
                    onUpdateLayerTransient = { updated ->
                        viewModel.updateDocumentTransient { d ->
                            d.copy(layers = d.layers.map { if (it.id == updated.id) updated else it })
                        }
                    },
                    onEndGesture = { viewModel.endTransientGesture() },
                    onFinishEyedropper = { argb -> viewModel.finishEyedropper(argb) },
                    onCancelEyedropper = { viewModel.cancelEyedropper() },
                    modifier = Modifier.fillMaxSize()
                )

                // Small Floating Layers Edge Pill Button (opens floating panel only when requested)
                if (!state.isEyedropperActive && !state.isLayersPanelOpen) {
                    Surface(
                        onClick = { viewModel.toggleLayersPanel() },
                        color = palette.surfaceElevated.copy(alpha = 0.92f),
                        shape = RoundedCornerShape(topStart = 99.dp, bottomStart = 99.dp),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .border(
                                width = 1.dp,
                                color = palette.border,
                                shape = RoundedCornerShape(topStart = 99.dp, bottomStart = 99.dp)
                            )
                            .testTag("floating_layers_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 9.dp, bottom = 9.dp)
                        ) {
                            Icon(
                                Icons.Default.Layers,
                                contentDescription = "Open Layers",
                                tint = palette.mint,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "${doc.layers.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.textPrimary
                            )
                        }
                    }
                }

                // Dismissible Floating Layer Panel
                androidx.compose.animation.AnimatedVisibility(
                    visible = state.isLayersPanelOpen && !state.isEyedropperActive,
                    enter = fadeIn() + slideInHorizontally(initialOffsetX = { it / 2 }),
                    exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it / 2 }),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 10.dp)
                ) {
                    FloatingLayersPanel(
                        layers = doc.layers,
                        selectedLayerId = state.selectedLayerId,
                        onClose = { viewModel.setLayersPanelOpen(false) },
                        onSelectLayer = { viewModel.selectLayer(it) },
                        onReorderLayer = { from, to -> viewModel.reorderLayer(from, to) },
                        onMoveUp = { viewModel.moveLayerUp(it) },
                        onMoveDown = { viewModel.moveLayerDown(it) },
                        onToggleVisibility = { viewModel.toggleLayerVisibility(it) },
                        onToggleLock = { viewModel.toggleLayerLock(it) },
                        onDuplicate = { viewModel.duplicateLayer(it) },
                        onDelete = { viewModel.deleteLayer(it) },
                        onRename = { id, newName -> viewModel.renameLayer(id, newName) },
                        onOpacityChanged = { id, op -> viewModel.setLayerOpacity(id, op) },
                        onBlendModeChanged = { id, bm -> viewModel.setLayerBlendMode(id, bm) }
                    )
                }
            }

            // Expandable Bottom Context Inspector Drawer
            AnimatedVisibility(visible = state.activeTab != EditorTab.NONE && !state.isEyedropperActive) {
                Surface(
                    color = palette.surface,
                    shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 264.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = tabHeaderLabel(state.activeTab),
                                style = MaterialTheme.typography.titleSmall,
                                color = palette.textPrimary
                            )
                            IconButton(
                                onClick = { viewModel.selectTab(EditorTab.NONE) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close panel", tint = palette.textSecondary)
                            }
                        }
                        HorizontalDivider(color = palette.border)

                        when (state.activeTab) {
                            EditorTab.TEXT_STYLE -> TextStyleInspectorSheet(
                                textLayer = selectedLayer as? TextLayer,
                                onEditTextClick = { viewModel.enterInlineTextEditing() },
                                onUpdateText = { viewModel.updateSelectedText(it) },
                                onUpdateOpacity = { op -> viewModel.updateSelectedLayerTransform(opacity = op) },
                                onPickColorTarget = { target -> viewModel.setColorTarget(target) }
                            )
                            EditorTab.FONTS -> FontsSheet(
                                fontFamilies = fontFamilies,
                                selectedFontId = (selectedLayer as? TextLayer)?.fontId,
                                fontRepository = viewModel.fontRepository,
                                onImportFontClick = {
                                    multiFontPicker.launch(
                                        arrayOf(
                                            "font/otf",
                                            "font/ttf",
                                            "application/x-font-otf",
                                            "application/x-font-ttf",
                                            "application/octet-stream",
                                            "*/*"
                                        )
                                    )
                                },
                                onSelectFont = { font ->
                                    if (selectedLayer is TextLayer) {
                                        viewModel.applyFontToSelectedText(font)
                                    } else {
                                        viewModel.addTextLayer()
                                        viewModel.applyFontToSelectedText(font)
                                    }
                                },
                                onToggleFavoriteFamily = { key -> viewModel.toggleFavoriteFontFamily(key) },
                                onDeleteImportedFont = { font -> viewModel.deleteImportedFont(font) }
                            )
                            EditorTab.OPENTYPE -> {
                                val textLayer = selectedLayer as? TextLayer
                                val fontItem = textLayer?.let { fontsById[it.fontId] }
                                OpenTypeInspectorSheet(
                                    textLayer = textLayer,
                                    fontItem = fontItem,
                                    alternateMatches = state.alternateMatches,
                                    onToggleFeature = { tag, value -> viewModel.toggleOpenTypeFeature(tag, value) }
                                )
                            }
                            EditorTab.COLOR -> ColorPickerSheet(
                                selectedLayer = selectedLayer,
                                activeColorTarget = state.activeColorTarget,
                                currentColorArgb = viewModel.activeColorValue(),
                                savedColors = savedColors,
                                recentColors = recentColors,
                                onSelectColorTarget = { viewModel.setColorTarget(it) },
                                onColorChanged = { argb -> viewModel.applyColorToActiveTarget(argb) },
                                onSaveColor = { argb -> viewModel.addSavedColor(argb) },
                                onStartEyedropper = { viewModel.activateEyedropper() }
                            )
                            EditorTab.SHAPE -> ShapeInspectorSheet(
                                shapeLayer = selectedLayer as? ShapeLayer,
                                onAddShape = { kind -> viewModel.addShapeLayer(kind) },
                                onUpdateShape = { viewModel.updateSelectedShape(it) },
                                onUpdateOpacity = { op -> viewModel.updateSelectedLayerTransform(opacity = op) },
                                onPickColorTarget = { target -> viewModel.setColorTarget(target) }
                            )
                            EditorTab.BRUSH -> BrushInspectorSheet(
                                brushSettings = state.brushSettings,
                                selectedLayer = selectedLayer,
                                onUpdateBrush = { viewModel.updateBrushSettings(it) },
                                onAddPaintLayer = { viewModel.addPaintLayer() },
                                onPickBrushColor = { viewModel.setColorTarget(ColorTarget.BRUSH_COLOR) },
                                onStartEyedropper = { viewModel.activateEyedropper(ColorTarget.BRUSH_COLOR) },
                                onRestoreErasedImage = { id -> viewModel.clearImageEraseMask(id) }
                            )
                            EditorTab.TRANSFORM -> TransformInspectorSheet(
                                document = doc,
                                layer = selectedLayer,
                                onOpenCrop = { viewModel.setShowCropDialog(true) },
                                onUpdateLayer = { updated ->
                                    viewModel.updateDocument { d ->
                                        d.copy(layers = d.layers.map { if (it.id == updated.id) updated else it })
                                    }
                                }
                            )
                            EditorTab.CANVAS -> CanvasSettingsSheet(
                                document = doc,
                                onUpdateDocument = { viewModel.updateDocument(it) },
                                onPickBackgroundColor = { viewModel.setColorTarget(ColorTarget.CANVAS_BG) }
                            )
                            EditorTab.NONE -> {}
                        }
                    }
                }
            }

            // Bottom Scrollable Tool Dock
            HorizontalDivider(color = palette.border)
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.surface)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                DockActionButton(
                    icon = Icons.Default.AddPhotoAlternate,
                    label = "+ Photo",
                    active = false,
                    accent = palette.mint,
                    testTag = "dock_add_photo",
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
                DockActionButton(
                    icon = Icons.Default.TextFields,
                    label = "+ Text",
                    active = state.isInlineTextEditing,
                    accent = palette.accent,
                    testTag = "dock_add_text",
                    onClick = { viewModel.addTextLayer() }
                )
                DockActionButton(
                    icon = Icons.Default.Brush,
                    label = "Brush",
                    active = state.activeTab == EditorTab.BRUSH,
                    accent = palette.accent,
                    testTag = "dock_brush",
                    onClick = { viewModel.selectTab(EditorTab.BRUSH) }
                )
                DockActionButton(
                    icon = Icons.Default.Category,
                    label = "Shapes",
                    active = state.activeTab == EditorTab.SHAPE,
                    accent = palette.accent,
                    testTag = "dock_shapes",
                    onClick = {
                        if (selectedLayer !is ShapeLayer) {
                            viewModel.addShapeLayer(ShapeKind.RECTANGLE)
                        } else {
                            viewModel.selectTab(EditorTab.SHAPE)
                        }
                    }
                )
                DockActionButton(
                    icon = Icons.Default.FontDownload,
                    label = "Fonts",
                    active = state.activeTab == EditorTab.FONTS,
                    accent = palette.accent,
                    testTag = "dock_fonts",
                    onClick = { viewModel.selectTab(EditorTab.FONTS) }
                )
                DockActionButton(
                    icon = Icons.Default.AutoAwesome,
                    label = "OpenType",
                    active = state.activeTab == EditorTab.OPENTYPE,
                    accent = palette.accent,
                    testTag = "dock_opentype",
                    onClick = { viewModel.selectTab(EditorTab.OPENTYPE) }
                )
                DockActionButton(
                    icon = Icons.Default.Tune,
                    label = "Style",
                    active = state.activeTab == EditorTab.TEXT_STYLE,
                    accent = palette.accent,
                    testTag = "dock_text_style",
                    onClick = { viewModel.selectTab(EditorTab.TEXT_STYLE) }
                )
                DockActionButton(
                    icon = Icons.Default.Palette,
                    label = "Color",
                    active = state.activeTab == EditorTab.COLOR,
                    accent = palette.accent,
                    testTag = "dock_color",
                    onClick = { viewModel.selectTab(EditorTab.COLOR) }
                )
                DockActionButton(
                    icon = Icons.Default.OpenWith,
                    label = "Transform",
                    active = state.activeTab == EditorTab.TRANSFORM,
                    accent = palette.accent,
                    testTag = "dock_transform",
                    onClick = { viewModel.selectTab(EditorTab.TRANSFORM) }
                )
                DockActionButton(
                    icon = Icons.Default.AspectRatio,
                    label = "Canvas",
                    active = state.activeTab == EditorTab.CANVAS,
                    accent = palette.accent,
                    testTag = "dock_canvas",
                    onClick = { viewModel.selectTab(EditorTab.CANVAS) }
                )
            }
        }
    }

    if (state.showCropDialog && selectedLayer is ImageLayer) {
        ImageCropDialog(
            imageLayer = selectedLayer,
            onApply = { crop -> viewModel.applyCropToSelectedImage(crop) },
            onDismiss = { viewModel.setShowCropDialog(false) }
        )
    }

    if (state.showSettingsDialog) {
        SettingsDialog(
            themeSettings = themeSettings,
            onSelectThemeMode = { viewModel.setThemeMode(it) },
            onSelectAccentColor = { viewModel.setAccentColor(it) },
            onDismiss = { viewModel.setShowSettingsDialog(false) }
        )
    }

    if (state.showExportDialog && doc != null) {
        ExportDialog(
            document = doc,
            isExporting = state.isExporting,
            lastExportResult = state.lastExportResult,
            onRenderPreview = { mode, textOnly -> viewModel.renderExportPreview(mode, textOnly) },
            onExport = { mode, scale, textOnly -> viewModel.export(mode, scale, textOnly) },
            onSaveAsCustomFile = { mode, scale, textOnly ->
                pendingExportMode = mode
                pendingExportScale = scale
                pendingTextOnly = textOnly
                val safeTitle = doc.title.replace(Regex("[^A-Za-z0-9_\\-]"), "_").ifBlank { "HTYPE" }
                when (mode) {
                    ExportMode.PDF_DOCUMENT -> createPdfLauncher.launch("${safeTitle}.pdf")
                    ExportMode.HIGH_RES_JPEG -> createJpegLauncher.launch("${safeTitle}.jpg")
                    ExportMode.FULL_IMAGE_PNG -> createPngLauncher.launch("${safeTitle}_full.png")
                    ExportMode.TRANSPARENT_PNG -> createPngLauncher.launch("${safeTitle}_transparent.png")
                }
            },
            onDismiss = { viewModel.setShowExportDialog(false) }
        )
    }
}

@Composable
private fun DockActionButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    accent: Color,
    testTag: String,
    onClick: () -> Unit
) {
    val palette = LocalStudioPalette.current
    val bg = if (active) palette.accentSoft else Color.Transparent
    val fg = if (active) accent else palette.textPrimary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = fg,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = fg
        )
    }
}

private fun tabHeaderLabel(tab: EditorTab): String = when (tab) {
    EditorTab.TEXT_STYLE -> "Text Styling & Arabic Spacing"
    EditorTab.FONTS -> "Fonts & Weights"
    EditorTab.OPENTYPE -> "OpenType Features (HarfBuzz)"
    EditorTab.COLOR -> "Color, Palette & Eyedropper"
    EditorTab.SHAPE -> "Vector Shapes & Dimensions"
    EditorTab.BRUSH -> "Brush & Non-Destructive Eraser"
    EditorTab.TRANSFORM -> "Transform, Stretch & Crop"
    EditorTab.CANVAS -> "Canvas & Background"
    EditorTab.NONE -> ""
}
