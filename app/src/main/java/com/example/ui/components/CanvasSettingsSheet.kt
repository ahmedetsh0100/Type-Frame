package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.DefaultCanvasPresets
import com.example.model.ProjectDocument
import com.example.ui.theme.LocalStudioPalette

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CanvasSettingsSheet(
    document: ProjectDocument,
    onUpdateDocument: ((ProjectDocument) -> ProjectDocument) -> Unit,
    onPickBackgroundColor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalStudioPalette.current
    var titleText by remember(document.id, document.title) { mutableStateOf(document.title) }
    var widthInput by remember(document.canvasWidthPx) { mutableStateOf(document.canvasWidthPx.toString()) }
    var heightInput by remember(document.canvasHeightPx) { mutableStateOf(document.canvasHeightPx.toString()) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = titleText,
            onValueChange = {
                titleText = it
                onUpdateDocument { d -> d.copy(title = it) }
            },
            label = { Text("Project Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text("Transparent Canvas", style = MaterialTheme.typography.titleSmall, color = palette.textPrimary)
                Text("Preview & export without solid background", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
            }
            Switch(
                checked = document.isTransparentBackground,
                onCheckedChange = { tr -> onUpdateDocument { it.copy(isTransparentBackground = tr) } },
                colors = SwitchDefaults.colors(checkedTrackColor = palette.mint)
            )
        }

        if (!document.isTransparentBackground) {
            OutlinedButton(onClick = onPickBackgroundColor, modifier = Modifier.fillMaxWidth()) {
                Text("Change Canvas Background Color")
            }
        }

        Text("Aspect Ratio Presets", style = MaterialTheme.typography.labelLarge, color = palette.textSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DefaultCanvasPresets.forEach { preset ->
                val selected = document.canvasWidthPx == preset.widthPx && document.canvasHeightPx == preset.heightPx
                FilterChip(
                    selected = selected,
                    onClick = {
                        onUpdateDocument {
                            it.copy(canvasWidthPx = preset.widthPx, canvasHeightPx = preset.heightPx)
                        }
                    },
                    label = { Text("${preset.ratioLabel} (${preset.widthPx}×${preset.heightPx})") }
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = widthInput,
                onValueChange = { raw ->
                    val clean = raw.filter { it.isDigit() }.take(4)
                    widthInput = clean
                    clean.toIntOrNull()?.takeIf { it in 256..4096 }?.let { w ->
                        onUpdateDocument { it.copy(canvasWidthPx = w) }
                    }
                },
                label = { Text("Width (px)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = heightInput,
                onValueChange = { raw ->
                    val clean = raw.filter { it.isDigit() }.take(4)
                    heightInput = clean
                    clean.toIntOrNull()?.takeIf { it in 256..4096 }?.let { h ->
                        onUpdateDocument { it.copy(canvasHeightPx = h) }
                    }
                },
                label = { Text("Height (px)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
