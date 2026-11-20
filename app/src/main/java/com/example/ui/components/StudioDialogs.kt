package com.example.ui.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.ProjectDocument
import com.example.render.ExportMode
import com.example.render.ExportResult
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioMint
import com.example.ui.theme.StudioMintSoft
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioTextSecondary

@Composable
fun TextEditDialog(
    initialText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember(initialText) { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioSurface,
        title = { Text("Edit Text (Arabic & English)", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Full RTL Arabic shaping, diacritics (harakat), and mixed BiDi supported.",
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioMint
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth().testTag("text_content_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text.ifBlank { "TYPEFRAME" }) },
                modifier = Modifier.testTag("confirm_text_edit_button")
            ) {
                Text("Done")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ExportPngDialog(
    document: ProjectDocument,
    isExporting: Boolean,
    lastExportResult: ExportResult?,
    onExport: (ExportMode, Int) -> Unit,
    onSaveAsCustomFile: (ExportMode, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var mode by remember {
        mutableStateOf(if (document.isTransparentBackground) ExportMode.TRANSPARENT_PNG else ExportMode.FULL_IMAGE_PNG)
    }
    var scaleMultiplier by remember { mutableIntStateOf(1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioSurface,
        title = { Text("Export High-Res PNG", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Choose lossless full-image or transparent alpha PNG output.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StudioTextSecondary
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == ExportMode.FULL_IMAGE_PNG,
                        onClick = { mode = ExportMode.FULL_IMAGE_PNG },
                        label = { Text("Full Image PNG") },
                        modifier = Modifier.testTag("export_mode_full")
                    )
                    FilterChip(
                        selected = mode == ExportMode.TRANSPARENT_PNG,
                        onClick = { mode = ExportMode.TRANSPARENT_PNG },
                        label = { Text("Transparent PNG") },
                        modifier = Modifier.testTag("export_mode_transparent")
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = scaleMultiplier == 1,
                        onClick = { scaleMultiplier = 1 },
                        label = { Text("1× (${document.canvasWidthPx}×${document.canvasHeightPx})") }
                    )
                    FilterChip(
                        selected = scaleMultiplier == 2,
                        onClick = { scaleMultiplier = 2 },
                        label = { Text("2× (${document.canvasWidthPx * 2}×${document.canvasHeightPx * 2})") }
                    )
                }

                if (lastExportResult != null) {
                    Surface(
                        color = StudioMintSoft,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, StudioMint.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val kb = (lastExportResult.fileSizeBytes / 1024).coerceAtLeast(1)
                            Text(
                                "Exported ${lastExportResult.widthPx}×${lastExportResult.heightPx} PNG ($kb KB)",
                                style = MaterialTheme.typography.titleSmall,
                                color = StudioMint
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "image/png"
                                            putExtra(Intent.EXTRA_STREAM, lastExportResult.shareUri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share PNG"))
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Share PNG", modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Share")
                                }
                                OutlinedButton(
                                    onClick = { onSaveAsCustomFile(mode, scaleMultiplier) },
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
                onClick = { onExport(mode, scaleMultiplier) },
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
                Text(if (isExporting) "Rendering…" else "Export PNG")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
