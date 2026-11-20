package com.example.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.FontItem
import com.example.model.TextLayer
import com.example.typography.FontFormat
import com.example.typography.GlyphAlternateMatch
import com.example.typography.OpenTypeFeatureInfo
import com.example.ui.theme.LocalStudioPalette

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OpenTypeInspectorSheet(
    textLayer: TextLayer?,
    fontItem: FontItem?,
    alternateMatches: List<GlyphAlternateMatch>,
    onToggleFeature: (String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalStudioPalette.current
    if (textLayer == null || fontItem == null) {
        Column(
            modifier = modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Select a text layer to configure its real OpenType GSUB/GPOS features.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary
            )
        }
        return
    }

    val meta = fontItem.metadata
    val userFeatures = meta.features.filter { it.isUserToggleable }
    val engineFeatures = meta.features.filter { !it.isUserToggleable }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            color = palette.surfaceElevated,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        fontItem.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.textPrimary
                    )
                    Surface(
                        color = palette.accentSoft,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        val fmt = when (meta.format) {
                            FontFormat.OTF_CFF -> "OTF CFF"
                            FontFormat.TTF_GLYF -> "TTF"
                            FontFormat.UNKNOWN -> "FONT"
                        }
                        Text(
                            "$fmt • ${meta.glyphCount} glyphs",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.accent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DiagnosticBadge(
                        text = if (meta.hasArabicShaping) "Arabic Shaping: init/medi/fina" else "Standard Shaping",
                        active = meta.hasArabicShaping
                    )
                    DiagnosticBadge(
                        text = if (meta.hasMarkPositioning) "Diacritics: mark/mkmk" else "No GPOS Mark",
                        active = meta.hasMarkPositioning
                    )
                    if (meta.hasCursiveAttachment) {
                        DiagnosticBadge(text = "Cursive: curs", active = true)
                    }
                    DiagnosticBadge(
                        text = "Scripts: ${meta.availableScripts.joinToString(", ").ifBlank { "DFLT" }}",
                        active = true
                    )
                }
            }
        }

        if (alternateMatches.isNotEmpty()) {
            Surface(
                color = palette.mintSoft,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Characters in your text with detected OpenType substitutions:",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.mint
                    )
                    val summary = alternateMatches
                        .groupBy { it.featureTag }
                        .entries
                        .joinToString("  •  ") { (tag, list) ->
                            val chars = list.map { it.character }.distinct().take(6).joinToString("")
                            "$tag: $chars"
                        }
                    Text(
                        summary,
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textPrimary
                    )
                }
            }
        }

        Text(
            "Detected OpenType Features (${userFeatures.size})",
            style = MaterialTheme.typography.labelLarge,
            color = palette.textSecondary
        )

        if (userFeatures.isEmpty()) {
            Text(
                "This font has no optional GSUB/GPOS stylistic features beyond mandatory script shaping.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textMuted
            )
        } else {
            userFeatures.forEach { feat ->
                OpenTypeFeatureRow(
                    feature = feat,
                    currentValue = textLayer.openTypeFeatures[feat.tag]
                        ?: if (feat.defaultEnabled) 1 else 0,
                    onValueChange = { newVal -> onToggleFeature(feat.tag, newVal) }
                )
            }
        }

        if (engineFeatures.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                "Active Mandatory HarfBuzz Lookups: ${engineFeatures.joinToString(", ") { it.tag }}",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textMuted
            )
        }
    }
}

@Composable
private fun OpenTypeFeatureRow(
    feature: OpenTypeFeatureInfo,
    currentValue: Int,
    onValueChange: (Int) -> Unit
) {
    val palette = LocalStudioPalette.current
    val isEnabled = currentValue > 0
    Surface(
        color = palette.surfaceHighest,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isEnabled) palette.accent.copy(alpha = 0.65f) else palette.border,
                shape = RoundedCornerShape(12.dp)
            )
            .testTag("opentype_feature_${feature.tag}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Surface(
                color = if (isEnabled) palette.accent else palette.surfaceElevated,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = feature.tag,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (isEnabled) Color.White else palette.textPrimary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = feature.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = palette.textPrimary
                )
                val altsInfo = if (feature.maxAlternateCount > 1) {
                    "${feature.coveredGlyphCount} glyphs • up to ${feature.maxAlternateCount} alts"
                } else {
                    "${feature.coveredGlyphCount} glyphs • ${feature.source.name}"
                }
                Text(
                    text = altsInfo,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary
                )
            }

            if (feature.maxAlternateCount > 1) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onValueChange((currentValue - 1).coerceAtLeast(0)) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Previous alternate", tint = palette.textPrimary)
                    }
                    Text(
                        text = if (currentValue == 0) "OFF" else "#$currentValue",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isEnabled) palette.mint else palette.textMuted,
                        modifier = Modifier.width(32.dp)
                    )
                    IconButton(
                        onClick = { onValueChange((currentValue + 1).coerceAtMost(feature.maxAlternateCount)) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Next alternate", tint = palette.textPrimary)
                    }
                }
            } else {
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { checked -> onValueChange(if (checked) 1 else 0) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = palette.accent
                    )
                )
            }
        }
    }
}

@Composable
private fun DiagnosticBadge(text: String, active: Boolean) {
    val palette = LocalStudioPalette.current
    Surface(
        color = if (active) palette.mintSoft else palette.surfaceHighest,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) palette.mint else palette.textMuted,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}
