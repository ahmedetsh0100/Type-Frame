package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.example.R
import com.example.data.ProjectEntity
import com.example.model.DefaultCanvasPresets
import com.example.ui.components.SettingsDialog
import com.example.ui.theme.LocalStudioPalette
import com.example.viewmodel.ProjectsViewModel
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun ProjectsHomeScreen(
    viewModel: ProjectsViewModel,
    onOpenProject: (String) -> Unit
) {
    val palette = LocalStudioPalette.current
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val fonts by viewModel.fonts.collectAsStateWithLifecycle()
    val themeSettings by viewModel.themeSettings.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showNewCanvasDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { viewModel.createProjectFromImage(it, onOpenProject) }
    }

    val multiFontPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.importMultipleFonts(uris)
    }

    Scaffold(
        containerColor = palette.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 14.dp,
                bottom = 28.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // HTYPE Brand Header
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.htype_icon_clean),
                            contentDescription = "HTYPE monogram",
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                        )
                        Column {
                            Text(
                                text = "HTYPE",
                                style = MaterialTheme.typography.displaySmall,
                                color = palette.textPrimary
                            )
                            Text(
                                text = "Arabic & OpenType Design Studio",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textSecondary
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            onClick = {
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
                            color = palette.mintSoft,
                            shape = RoundedCornerShape(99.dp),
                            modifier = Modifier
                                .border(1.dp, palette.mint.copy(alpha = 0.4f), RoundedCornerShape(99.dp))
                                .testTag("home_import_font_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Icon(
                                    Icons.Default.FontDownload,
                                    contentDescription = "Import OTF or TTF fonts",
                                    tint = palette.mint,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = "+Fonts",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = palette.mint
                                )
                            }
                        }

                        IconButton(
                            onClick = { showSettingsDialog = true },
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("home_settings_button")
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Theme & Settings",
                                tint = palette.textPrimary
                            )
                        }
                    }
                }
            }

            // Hero Action Card
            item(span = { GridItemSpan(maxLineSpan) }) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = palette.surfaceElevated,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, palette.border, RoundedCornerShape(20.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        palette.accentSoft.copy(alpha = 0.7f),
                                        palette.surfaceElevated,
                                        palette.mintSoft.copy(alpha = 0.35f)
                                    )
                                )
                            )
                            .padding(18.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(
                                text = "Start Designing",
                                style = MaterialTheme.typography.titleLarge,
                                color = palette.textPrimary
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = {
                                        photoPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .testTag("home_import_photo_button")
                                ) {
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Import Photo")
                                }
                                OutlinedButton(
                                    onClick = { showNewCanvasDialog = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .testTag("home_new_canvas_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Blank Canvas")
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                DefaultCanvasPresets.take(4).forEach { preset ->
                                    Surface(
                                        onClick = {
                                            viewModel.createBlankProject(
                                                title = "${preset.label} Frame",
                                                widthPx = preset.widthPx,
                                                heightPx = preset.heightPx,
                                                transparentBg = false,
                                                onCreated = onOpenProject
                                            )
                                        },
                                        color = palette.surfaceHighest,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = preset.ratioLabel,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = palette.textPrimary
                                            )
                                            Text(
                                                text = "${preset.widthPx}×${preset.heightPx}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = palette.textSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section Title
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Text(
                        text = "Editable Projects (${projects.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.textPrimary
                    )
                    Text(
                        text = "${fonts.size} installed weights",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textMuted
                    )
                }
            }

            if (projects.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Surface(
                        color = palette.surface,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, palette.border, RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Layers,
                                contentDescription = null,
                                tint = palette.accent,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                "No saved projects yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.textPrimary
                            )
                            Text(
                                "Import a photo or start a blank canvas with Arabic & English OpenType typography.",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textSecondary
                            )
                        }
                    }
                }
            } else {
                items(projects, key = { it.id }) { project ->
                    ProjectCard(
                        project = project,
                        onOpen = { onOpenProject(project.id) },
                        onDelete = { viewModel.deleteProject(project.id) }
                    )
                }
            }
        }
    }

    if (showNewCanvasDialog) {
        NewCanvasDialog(
            onCreate = { title, w, h, transparent ->
                showNewCanvasDialog = false
                viewModel.createBlankProject(title, w, h, transparent, onOpenProject)
            },
            onDismiss = { showNewCanvasDialog = false }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            themeSettings = themeSettings,
            onSelectThemeMode = { viewModel.setThemeMode(it) },
            onSelectAccentColor = { viewModel.setAccentColor(it) },
            onDismiss = { showSettingsDialog = false }
        )
    }
}

@Composable
private fun ProjectCard(
    project: ProjectEntity,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val palette = LocalStudioPalette.current
    val thumbFile = project.thumbnailPath?.let { File(it) }?.takeIf { it.exists() }
    val dateLabel = remember(project.updatedAtEpochMs) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(project.updatedAtEpochMs))
    }

    Surface(
        color = palette.surfaceElevated,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, palette.border, RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
            .testTag("project_card_${project.id}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(palette.surface)
            ) {
                if (thumbFile != null) {
                    Image(
                        painter = rememberAsyncImagePainter(thumbFile),
                        contentDescription = project.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.Layers,
                        contentDescription = null,
                        tint = palette.textMuted,
                        modifier = Modifier.align(Alignment.Center).size(36.dp)
                    )
                }

                Surface(
                    color = Color.Black.copy(alpha = 0.68f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                ) {
                    Text(
                        text = "${project.canvasWidthPx}×${project.canvasHeightPx}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = project.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${project.layerCount} layers • $dateLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Delete project",
                        tint = palette.danger,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewCanvasDialog(
    onCreate: (String, Int, Int, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalStudioPalette.current
    var title by remember { mutableStateOf("Untitled Frame") }
    var widthStr by remember { mutableStateOf("1080") }
    var heightStr by remember { mutableStateOf("1350") }
    var transparentBg by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = { Text("New Canvas", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Project Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DefaultCanvasPresets.forEach { preset ->
                        FilterChip(
                            selected = widthStr == preset.widthPx.toString() && heightStr == preset.heightPx.toString(),
                            onClick = {
                                widthStr = preset.widthPx.toString()
                                heightStr = preset.heightPx.toString()
                            },
                            label = { Text(preset.ratioLabel) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = widthStr,
                        onValueChange = { widthStr = it.filter { c -> c.isDigit() }.take(4) },
                        label = { Text("Width px") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = heightStr,
                        onValueChange = { heightStr = it.filter { c -> c.isDigit() }.take(4) },
                        label = { Text("Height px") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Transparent Background", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = transparentBg, onCheckedChange = { transparentBg = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val w = widthStr.toIntOrNull()?.coerceIn(256, 4096) ?: 1080
                    val h = heightStr.toIntOrNull()?.coerceIn(256, 4096) ?: 1350
                    onCreate(title.ifBlank { "Untitled Frame" }, w, h, transparentBg)
                },
                modifier = Modifier.testTag("create_canvas_confirm_button")
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
