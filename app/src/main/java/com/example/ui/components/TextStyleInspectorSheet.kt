package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.TextLayer
import com.example.typography.ArabicTypographyEngine
import com.example.typography.TextAlignMode
import com.example.typography.TextDirectionMode
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCoral
import com.example.ui.theme.StudioMint
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioTextMuted
import com.example.ui.theme.StudioTextSecondary
import com.example.viewmodel.ColorTarget
import kotlin.math.roundToInt

@Composable
fun TextStyleInspectorSheet(
    textLayer: TextLayer?,
    onEditTextClick: () -> Unit,
    onUpdateText: ((TextLayer) -> TextLayer) -> Unit,
    onUpdateOpacity: (Float) -> Unit,
    onPickColorTarget: (ColorTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    if (textLayer == null) {
        Column(
            modifier = modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Tap a text layer or tap + Text to start styling.",
                style = MaterialTheme.typography.bodyMedium,
                color = StudioTextSecondary
            )
        }
        return
    }

    val hasArabic = ArabicTypographyEngine.containsArabicScript(textLayer.text)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Edit Text Pill + Alignment + Direction
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                onClick = onEditTextClick,
                color = StudioSurfaceElevated,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
                    .testTag("edit_text_content_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit text", tint = StudioCoral, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = textLayer.text.replace('\n', ' '),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
            }

            IconButton(
                onClick = { onUpdateText { it.copy(alignMode = TextAlignMode.START) } }
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.FormatAlignLeft,
                    contentDescription = "Align start",
                    tint = if (textLayer.alignMode == TextAlignMode.START) StudioCoral else StudioTextSecondary
                )
            }
            IconButton(
                onClick = { onUpdateText { it.copy(alignMode = TextAlignMode.CENTER) } }
            ) {
                Icon(
                    Icons.Default.FormatAlignCenter,
                    contentDescription = "Align center",
                    tint = if (textLayer.alignMode == TextAlignMode.CENTER) StudioCoral else StudioTextSecondary
                )
            }
            IconButton(
                onClick = { onUpdateText { it.copy(alignMode = TextAlignMode.END) } }
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.FormatAlignRight,
                    contentDescription = "Align end",
                    tint = if (textLayer.alignMode == TextAlignMode.END) StudioCoral else StudioTextSecondary
                )
            }
        }

        // RTL / AUTO / LTR Direction
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Direction", style = MaterialTheme.typography.labelLarge, color = StudioTextSecondary)
            Spacer(Modifier.weight(1f))
            listOf(
                TextDirectionMode.AUTO to "Auto BiDi",
                TextDirectionMode.RTL to "RTL (عربي)",
                TextDirectionMode.LTR to "LTR"
            ).forEach { (mode, label) ->
                FilterChip(
                    selected = textLayer.directionMode == mode,
                    onClick = { onUpdateText { it.copy(directionMode = mode) } },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        // Size & Box Width & Opacity
        StudioSliderRow(
            label = "Font Size",
            valueLabel = "${textLayer.fontSizePx.roundToInt()} px",
            value = textLayer.fontSizePx,
            range = 20f..260f,
            onValueChange = { v -> onUpdateText { it.copy(fontSizePx = v) } }
        )

        StudioSliderRow(
            label = "Line Height",
            valueLabel = String.format("%.2f×", textLayer.lineHeightMultiplier),
            value = textLayer.lineHeightMultiplier,
            range = 0.75f..2.2f,
            onValueChange = { v -> onUpdateText { it.copy(lineHeightMultiplier = v) } }
        )

        if (!hasArabic || !textLayer.preserveArabicJoining) {
            StudioSliderRow(
                label = "Letter Spacing",
                valueLabel = String.format("%.2f em", textLayer.letterSpacingEm),
                value = textLayer.letterSpacingEm,
                range = -0.05f..0.4f,
                onValueChange = { v -> onUpdateText { it.copy(letterSpacingEm = v) } }
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Arabic Joining Lock (preserves curs/mark shaping)",
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioMint
                )
                Switch(
                    checked = textLayer.preserveArabicJoining,
                    onCheckedChange = { checked -> onUpdateText { it.copy(preserveArabicJoining = checked) } },
                    colors = SwitchDefaults.colors(checkedTrackColor = StudioMint)
                )
            }
        }

        StudioSliderRow(
            label = "Opacity",
            valueLabel = "${(textLayer.opacity * 100).roundToInt()}%",
            value = textLayer.opacity,
            range = 0.05f..1f,
            onValueChange = onUpdateOpacity
        )

        // Stroke Controls
        Surface(
            color = StudioSurfaceElevated,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(textLayer.stroke.colorArgb))
                                .border(1.5.dp, Color.White, CircleShape)
                                .clickable { onPickColorTarget(ColorTarget.TEXT_STROKE) }
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Stroke Outline", style = MaterialTheme.typography.titleSmall, color = Color.White)
                    }
                    Switch(
                        checked = textLayer.stroke.enabled,
                        onCheckedChange = { enabled ->
                            onUpdateText { it.copy(stroke = it.stroke.copy(enabled = enabled)) }
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = StudioCoral)
                    )
                }
                if (textLayer.stroke.enabled) {
                    StudioSliderRow(
                        label = "Stroke Width",
                        valueLabel = "${textLayer.stroke.widthPx.roundToInt()} px",
                        value = textLayer.stroke.widthPx,
                        range = 1f..32f,
                        onValueChange = { w ->
                            onUpdateText { it.copy(stroke = it.stroke.copy(widthPx = w)) }
                        }
                    )
                }
            }
        }

        // Shadow Controls
        Surface(
            color = StudioSurfaceElevated,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(textLayer.shadow.colorArgb))
                                .border(1.5.dp, Color.White, CircleShape)
                                .clickable { onPickColorTarget(ColorTarget.TEXT_SHADOW) }
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Drop Shadow", style = MaterialTheme.typography.titleSmall, color = Color.White)
                    }
                    Switch(
                        checked = textLayer.shadow.enabled,
                        onCheckedChange = { enabled ->
                            onUpdateText { it.copy(shadow = it.shadow.copy(enabled = enabled)) }
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = StudioCoral)
                    )
                }
                if (textLayer.shadow.enabled) {
                    StudioSliderRow(
                        label = "Shadow Blur",
                        valueLabel = "${textLayer.shadow.blurRadiusPx.roundToInt()} px",
                        value = textLayer.shadow.blurRadiusPx,
                        range = 1f..48f,
                        onValueChange = { r ->
                            onUpdateText { it.copy(shadow = it.shadow.copy(blurRadiusPx = r)) }
                        }
                    )
                    StudioSliderRow(
                        label = "Offset Y",
                        valueLabel = "${textLayer.shadow.offsetYPx.roundToInt()} px",
                        value = textLayer.shadow.offsetYPx,
                        range = -40f..40f,
                        onValueChange = { y ->
                            onUpdateText { it.copy(shadow = it.shadow.copy(offsetYPx = y)) }
                        }
                    )
                    StudioSliderRow(
                        label = "Offset X",
                        valueLabel = "${textLayer.shadow.offsetXPx.roundToInt()} px",
                        value = textLayer.shadow.offsetXPx,
                        range = -40f..40f,
                        onValueChange = { x ->
                            onUpdateText { it.copy(shadow = it.shadow.copy(offsetXPx = x)) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun StudioSliderRow(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = StudioTextSecondary)
            Text(valueLabel, style = MaterialTheme.typography.labelMedium, color = Color.White)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = StudioCoral, activeTrackColor = StudioCoral)
        )
    }
}
