package com.example.ui.components

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.AppThemeMode
import com.example.data.PresetAccentColors
import com.example.data.ThemeSettings
import com.example.model.ProjectDocument
import com.example.render.ExportEngine
import com.example.render.ExportMode
import com.example.render.ExportResult
import com.example.render.ExportScaleOption
import com.example.ui.theme.LocalStudioPalette
import kotlin.math.min

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsDialog(
    themeSettings: ThemeSettings,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    onSelectAccentColor: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalStudioPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = { Text("HTYPE Settings", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "Customize the studio interface colors without affecting your artwork.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary
                )

                Text("Appearance", style = MaterialTheme.typography.labelLarge, color = palette.textPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        AppThemeMode.DARK to "Dark",
                        AppThemeMode.LIGHT to "Light",
                        AppThemeMode.SYSTEM to "System"
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = themeSettings.mode == mode,
                            onClick = { onSelectThemeMode(mode) },
                            label = { Text(label) },
                            modifier = Modifier.testTag("theme_mode_${mode.name.lowercase()}")
                        )
                    }
                }

                Text("Interface Accent", style = MaterialTheme.typography.labelLarge, color = palette.textPrimary)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PresetAccentColors.forEach { (argb, name) ->
                        val selected = themeSettings.accentColorArgb == argb
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) palette.textPrimary else palette.border,
                                    shape = CircleShape
                                )
                                .clickable { onSelectAccentColor(argb) }
                                .testTag("accent_color_${name.lowercase()}")
                        ) {
                            if (selected) {
                                Icon(Icons.Default.Check, contentDescription = "$name accent selected", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, modifier = Modifier.testTag("close_settings_button")) {
                Text("Done")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportDialog(
    document: ProjectDocument,
    isExporting: Boolean,
    lastExportResult: ExportResult?,
    onRenderPreview: (ExportMode, Boolean) -> Bitmap?,
    onExport: (ExportMode, ExportScaleOption, Boolean) -> Unit,
    onSaveAsCustomFile: (ExportMode, ExportScaleOption, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalStudioPalette.current
    val context = LocalContext.current
    var mode by remember {
        mutableStateOf(if (document.isTransparentBackground) ExportMode.TRANSPARENT_PNG else ExportMode.FULL_IMAGE_PNG)
    }
    var scaleOption by remember { mutableStateOf(ExportScaleOption.X1) }
    var textOnly by remember { mutableStateOf(false) }

    val previewBitmap = remember(document, mode, textOnly) {
        onRenderPreview(mode, textOnly)
    }
    DisposableEffect(previewBitmap) {
        onDispose {
            previewBitmap?.recycle()
        }
    }

    val (outW, outH) = remember(document.canvasWidthPx, document.canvasHeightPx, scaleOption) {
        ExportEngine.computeExportDimensions(document, scaleOption)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = { Text("Export Artwork", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Live Checkerboard Preview
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, palette.border, RoundedCornerShape(12.dp))
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cell = 10.dp.toPx()
                        val c1 = if (palette.isDark) Color(0xFF1A1C24) else Color(0xFFE6E9F0)
                        val c2 = if (palette.isDark) Color(0xFF232631) else Color(0xFFD5DAE5)
                        drawRect(c1)
                        val cols = (size.width / cell).toInt() + 1
                        val rows = (size.height / cell).toInt() + 1
                        for (r in 0 until rows) {
                            for (c in 0 until cols) {
                                if ((r + c) % 2 == 0) {
                                    drawRect(
                                        c2,
                                        topLeft = Offset(c * cell, r * cell),
                                        size = Size(min(cell, size.width - c * cell), min(cell, size.height - r * cell))
                                    )
                                }
                            }
                        }
                    }
                    if (previewBitmap != null && !previewBitmap.isRecycled) {
                        Image(
                            bitmap = previewBitmap.asImageBitmap(),
                            contentDescription = "Export preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(8.dp)
                        )
                    }
                }

                Text("Format", style = MaterialTheme.typography.labelLarge, color = palette.textSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = mode == ExportMode.FULL_IMAGE_PNG,
                        onClick = { mode = ExportMode.FULL_IMAGE_PNG },
                        label = { Text("Full PNG") },
                        modifier = Modifier.testTag("export_mode_full")
                    )
                    FilterChip(
                        selected = mode == ExportMode.TRANSPARENT_PNG,
                        onClick = { mode = ExportMode.TRANSPARENT_PNG },
                        label = { Text("Transparent PNG") },
                        modifier = Modifier.testTag("export_mode_transparent")
                    )
                    FilterChip(
                        selected = mode == ExportMode.HIGH_RES_JPEG,
                        onClick = { mode = ExportMode.HIGH_RES_JPEG },
                        label = { Text("JPEG") },
                        modifier = Modifier.testTag("export_mode_jpeg")
                    )
                    FilterChip(
                        selected = mode == ExportMode.PDF_DOCUMENT,
                        onClick = { mode = ExportMode.PDF_DOCUMENT },
                        label = { Text("PDF") },
                        modifier = Modifier.testTag("export_mode_pdf")
                    )
                }

                if (mode == ExportMode.TRANSPARENT_PNG) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Text-Only Transparent PNG", style = MaterialTheme.typography.labelLarge, color = palette.textPrimary)
                            Text("Hides photos & shapes, exports typography only", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                        }
                        Switch(
                            checked = textOnly,
                            onCheckedChange = { textOnly = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = palette.mint)
                        )
                    }
                }

                Text("Resolution ($outW×$outH px)", style = MaterialTheme.typography.labelLarge, color = palette.textSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExportScaleOption.entries.forEach { opt ->
                        FilterChip(
                            selected = scaleOption == opt,
                            onClick = { scaleOption = opt },
                            label = { Text(opt.label) }
                        )
                    }
                }

                if (lastExportResult != null) {
                    Surface(
                        color = palette.mintSoft,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, palette.mint.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val kb = (lastExportResult.fileSizeBytes / 1024).coerceAtLeast(1)
                            Text(
                                "Ready: ${lastExportResult.widthPx}×${lastExportResult.heightPx} ($kb KB)",
                                style = MaterialTheme.typography.titleSmall,
                                color = palette.mint
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = lastExportResult.mimeType
                                            putExtra(Intent.EXTRA_STREAM, lastExportResult.shareUri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share Export"))
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Share export", modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Share")
                                }
                                OutlinedButton(
                                    onClick = { onSaveAsCustomFile(mode, scaleOption, textOnly) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Save As…")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onExport(mode, scaleOption, textOnly) },
                enabled = !isExporting,
                modifier = Modifier.testTag("confirm_export_png_button")
            ) {
                if (isExporting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(if (isExporting) "Rendering…" else "Export")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
