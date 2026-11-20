package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Palette
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.ui.components.CanvasSettingsSheet
import com.example.ui.components.ColorPickerSheet
import com.example.ui.components.ExportPngDialog
import com.example.ui.components.FontsSheet
import com.example.ui.components.ImageCropDialog
import com.example.ui.components.LayersSheet
import com.example.ui.components.OpenTypeInspectorSheet
import com.example.ui.components.ShapeInspectorSheet
import com.example.ui.components.StudioCanvasStage
import com.example.ui.components.TextEditDialog
import com.example.ui.components.TextStyleInspectorSheet
import com.example.ui.components.TransformInspectorSheet
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCoral
import com.example.ui.theme.StudioCoralSoft
import com.example.ui.theme.StudioMint
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioTextMuted
import com.example.ui.theme.StudioTextSecondary
import com.example.viewmodel.ColorTarget
import com.example.viewmodel.EditorTab
import com.example.viewmodel.StudioViewModel

@Composable
fun StudioEditorScreen(
    projectId: String,
    viewModel: StudioViewModel,
    onNavigateBack: () -> Unit
) {
    LaunchedEffect(projectId) {
        viewModel.loadProject(projectId)
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val fonts by viewModel.fonts.collectAsStateWithLifecycle()
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
    BackHandler { saveAndExit() }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { viewModel.importImageLayer(it) }
    }

    val fontPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importCustomFont(it) }
    }

    var pendingExportMode by remember { mutableStateOf(ExportMode.FULL_IMAGE_PNG) }
    var pendingExportScale by remember { mutableIntStateOf(1) }
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri: Uri? ->
        uri?.let { viewModel.saveExportToCustomUri(it, pendingExportMode, pendingExportScale) }
    }

    val doc = state.document
    val selectedLayer = doc?.layers?.find { it.id == state.selectedLayerId }

    Scaffold(
        containerColor = StudioObsidian,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        if (state.isLoading || doc == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = StudioCoral)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(StudioObsidian)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Studio Action Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StudioSurface)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                IconButton(
                    onClick = saveAndExit,
                    modifier = Modifier.testTag("editor_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Save and exit", tint = Color.White)
                }

                IconButton(
                    onClick = { viewModel.undo() },
                    enabled = state.canUndo,
                    modifier = Modifier.testTag("undo_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo",
                        tint = if (state.canUndo) Color.White else StudioTextMuted
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
                        tint = if (state.canRedo) Color.White else StudioTextMuted
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
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${doc.canvasWidthPx}×${doc.canvasHeightPx} px",
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioTextSecondary
                    )
                }

                Surface(
                    onClick = { viewModel.selectTab(EditorTab.LAYERS) },
                    color = if (state.activeTab == EditorTab.LAYERS) StudioCoralSoft else StudioSurfaceElevated,
                    shape = RoundedCornerShape(99.dp),
                    modifier = Modifier.border(1.dp, StudioBorder, RoundedCornerShape(99.dp))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Default.Layers,
                            contentDescription = "Layers",
                            tint = StudioMint,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "${doc.layers.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                }

                Spacer(Modifier.width(6.dp))

                Button(
                    onClick = { viewModel.setShowExportDialog(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = StudioCoral),
                    shape = RoundedCornerShape(99.dp),
                    modifier = Modifier.height(38.dp).testTag("export_button")
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Export", style = MaterialTheme.typography.labelLarge)
                }
            }

            // Center WYSIWYG Canvas Stage
            StudioCanvasStage(
                document = doc,
                selectedLayerId = state.selectedLayerId,
                fontsById = fontsById,
                fontRepository = viewModel.fontRepository,
                isEyedropperActive = state.isEyedropperActive,
                eyedropperBitmap = state.eyedropperBitmap,
                onSelectLayer = { viewModel.selectLayer(it) },
                onDoubleTapTextLayer = { viewModel.setShowTextEditorDialog(true) },
                onBeginGesture = { viewModel.beginTransientGesture() },
                onUpdateLayerTransient = { updated ->
                    viewModel.updateDocumentTransient { d ->
                        d.copy(layers = d.layers.map { if (it.id == updated.id) updated else it })
                    }
                },
                onEndGesture = { viewModel.endTransientGesture() },
                onFinishEyedropper = { argb -> viewModel.finishEyedropper(argb) },
                onCancelEyedropper = { viewModel.cancelEyedropper() },
                modifier = Modifier.weight(1f)
            )

            // Expandable Context Inspector Drawer
            AnimatedVisibility(visible = state.activeTab != EditorTab.NONE && !state.isEyedropperActive) {
                Surface(
                    color = StudioSurface,
                    shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 270.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = tabHeaderLabel(state.activeTab),
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White
                            )
                            IconButton(
                                onClick = { viewModel.selectTab(EditorTab.NONE) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close panel", tint = StudioTextSecondary)
                            }
                        }
                        HorizontalDivider(color = StudioBorder)

                        when (state.activeTab) {
                            EditorTab.TEXT_STYLE -> TextStyleInspectorSheet(
                                textLayer = selectedLayer as? TextLayer,
                                onEditTextClick = { viewModel.setShowTextEditorDialog(true) },
                                onUpdateText = { viewModel.updateSelectedText(it) },
                                onUpdateOpacity = { op -> viewModel.updateSelectedLayerTransform(opacity = op) },
                                onPickColorTarget = { target -> viewModel.setColorTarget(target) }
                            )
                            EditorTab.FONTS -> FontsSheet(
                                fonts = fonts,
                                selectedFontId = (selectedLayer as? TextLayer)?.fontId,
                                onImportFontClick = {
                                    fontPicker.launch(
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
                                onDeleteImportedFont = { font ->
                                    viewModel.deleteImportedFont(font)
                                }
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
                                onSelectColorTarget = { viewModel.setColorTarget(it) },
                                onColorChanged = { argb -> viewModel.applyColorToActiveTarget(argb) },
                                onStartEyedropper = { viewModel.activateEyedropper() }
                            )
                            EditorTab.SHAPE -> ShapeInspectorSheet(
                                shapeLayer = selectedLayer as? ShapeLayer,
                                onAddShape = { kind -> viewModel.addShapeLayer(kind) },
                                onUpdateShape = { viewModel.updateSelectedShape(it) },
                                onUpdateOpacity = { op -> viewModel.updateSelectedLayerTransform(opacity = op) },
                                onPickColorTarget = { target -> viewModel.setColorTarget(target) }
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
                            EditorTab.LAYERS -> LayersSheet(
                                layers = doc.layers,
                                selectedLayerId = state.selectedLayerId,
                                onSelectLayer = { viewModel.selectLayer(it) },
                                onMoveUp = { viewModel.moveLayerUp(it) },
                                onMoveDown = { viewModel.moveLayerDown(it) },
                                onToggleVisibility = { viewModel.toggleLayerVisibility(it) },
                                onToggleLock = { viewModel.toggleLayerLock(it) },
                                onDuplicate = { viewModel.duplicateLayer(it) },
                                onDelete = { viewModel.deleteLayer(it) }
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

            // Bottom Picsart-Style Scrollable Tool Dock
            HorizontalDivider(color = StudioBorder)
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StudioSurface)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                DockActionButton(
                    icon = Icons.Default.AddPhotoAlternate,
                    label = "+ Photo",
                    active = false,
                    accent = StudioMint,
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
                    active = false,
                    accent = StudioCoral,
                    testTag = "dock_add_text",
                    onClick = { viewModel.addTextLayer() }
                )
                DockActionButton(
                    icon = Icons.Default.Category,
                    label = "Shapes",
                    active = state.activeTab == EditorTab.SHAPE,
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
                    testTag = "dock_fonts",
                    onClick = { viewModel.selectTab(EditorTab.FONTS) }
                )
                DockActionButton(
                    icon = Icons.Default.AutoAwesome,
                    label = "OpenType",
                    active = state.activeTab == EditorTab.OPENTYPE,
                    testTag = "dock_opentype",
                    onClick = { viewModel.selectTab(EditorTab.OPENTYPE) }
                )
                DockActionButton(
                    icon = Icons.Default.Tune,
                    label = "Text Style",
                    active = state.activeTab == EditorTab.TEXT_STYLE,
                    testTag = "dock_text_style",
                    onClick = { viewModel.selectTab(EditorTab.TEXT_STYLE) }
                )
                DockActionButton(
                    icon = Icons.Default.Palette,
                    label = "Color",
                    active = state.activeTab == EditorTab.COLOR,
                    testTag = "dock_color",
                    onClick = { viewModel.selectTab(EditorTab.COLOR) }
                )
                DockActionButton(
                    icon = Icons.Default.OpenWith,
                    label = "Transform",
                    active = state.activeTab == EditorTab.TRANSFORM,
                    testTag = "dock_transform",
                    onClick = { viewModel.selectTab(EditorTab.TRANSFORM) }
                )
                DockActionButton(
                    icon = Icons.Default.Layers,
                    label = "Layers",
                    active = state.activeTab == EditorTab.LAYERS,
                    testTag = "dock_layers",
                    onClick = { viewModel.selectTab(EditorTab.LAYERS) }
                )
                DockActionButton(
                    icon = Icons.Default.AspectRatio,
                    label = "Canvas",
                    active = state.activeTab == EditorTab.CANVAS,
                    testTag = "dock_canvas",
                    onClick = { viewModel.selectTab(EditorTab.CANVAS) }
                )
            }
        }
    }

    if (state.showTextEditorDialog && selectedLayer is TextLayer) {
        TextEditDialog(
            initialText = selectedLayer.text,
            onConfirm = { newText ->
                viewModel.updateSelectedText { it.copy(text = newText) }
                viewModel.setShowTextEditorDialog(false)
            },
            onDismiss = { viewModel.setShowTextEditorDialog(false) }
        )
    }

    if (state.showCropDialog && selectedLayer is ImageLayer) {
        ImageCropDialog(
            imageLayer = selectedLayer,
            onApply = { crop -> viewModel.applyCropToSelectedImage(crop) },
            onDismiss = { viewModel.setShowCropDialog(false) }
        )
    }

    if (state.showExportDialog && doc != null) {
        ExportPngDialog(
            document = doc,
            isExporting = state.isExporting,
            lastExportResult = state.lastExportResult,
            onExport = { mode, scale -> viewModel.exportPng(mode, scale) },
            onSaveAsCustomFile = { mode, scale ->
                pendingExportMode = mode
                pendingExportScale = scale
                val suffix = if (mode == ExportMode.TRANSPARENT_PNG) "transparent" else "full"
                createDocumentLauncher.launch("TYPEFRAME_${suffix}.png")
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
    accent: Color = StudioCoral,
    testTag: String,
    onClick: () -> Unit
) {
    val bg = if (active) StudioCoralSoft else Color.Transparent
    val fg = if (active) accent else Color.White
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
    EditorTab.FONTS -> "Custom .OTF & .TTF Fonts"
    EditorTab.OPENTYPE -> "OpenType Features (HarfBuzz / FreeType)"
    EditorTab.COLOR -> "Color Picker & Eyedropper"
    EditorTab.SHAPE -> "Vector Shapes & Manual Dimensions"
    EditorTab.TRANSFORM -> "Transform, Crop & Position"
    EditorTab.LAYERS -> "Layer Stack"
    EditorTab.CANVAS -> "Canvas & Background"
    EditorTab.NONE -> ""
}
