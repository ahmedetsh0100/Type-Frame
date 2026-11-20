package com.example.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FontRepository
import com.example.model.FontFamilyGroup
import com.example.model.FontItem
import com.example.ui.theme.LocalStudioPalette

private enum class FontFilter {
    ALL,
    FAVORITES,
    ARABIC
}

@Composable
fun FontsSheet(
    fontFamilies: List<FontFamilyGroup>,
    selectedFontId: String?,
    fontRepository: FontRepository,
    onImportFontClick: () -> Unit,
    onSelectFont: (FontItem) -> Unit,
    onToggleFavoriteFamily: (String) -> Unit,
    onDeleteImportedFont: (FontItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalStudioPalette.current
    var filter by remember { mutableStateOf(FontFilter.ALL) }

    val activeFamily = remember(fontFamilies, selectedFontId) {
        fontFamilies.firstOrNull { grp -> grp.weights.any { it.id == selectedFontId } }
            ?: fontFamilies.firstOrNull()
    }

    val filteredFamilies = remember(fontFamilies, filter) {
        when (filter) {
            FontFilter.ALL -> fontFamilies
            FontFilter.FAVORITES -> fontFamilies.filter { it.isFavorite }
            FontFilter.ARABIC -> fontFamilies.filter { it.supportsArabic }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Filter chips + compact + Import pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
        ) {
            FilterChip(
                selected = filter == FontFilter.ALL,
                onClick = { filter = FontFilter.ALL },
                label = { Text("All") }
            )
            FilterChip(
                selected = filter == FontFilter.FAVORITES,
                onClick = { filter = FontFilter.FAVORITES },
                label = { Text("Favorites") }
            )
            FilterChip(
                selected = filter == FontFilter.ARABIC,
                onClick = { filter = FontFilter.ARABIC },
                label = { Text("Arabic") }
            )
            Spacer(Modifier.weight(1f))
            Surface(
                onClick = onImportFontClick,
                color = palette.accent,
                shape = RoundedCornerShape(99.dp),
                modifier = Modifier.testTag("import_font_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Import OTF or TTF fonts", tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Import", style = MaterialTheme.typography.labelMedium, color = Color.White)
                }
            }
        }

        // Horizontal swipeable Picsart-style Font Family Strip
        if (filteredFamilies.isEmpty()) {
            Text(
                text = if (filter == FontFilter.FAVORITES) "Star a font family to pin it here." else "No fonts match this filter.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredFamilies, key = { it.familyKey }) { group ->
                    val defaultWeight = group.weights.firstOrNull {
                        it.metadata.weightValue == 400 && !it.metadata.isItalic
                    } ?: group.weights.first()
                    val isSelectedFamily = group.weights.any { it.id == selectedFontId }
                    val tf = remember(defaultWeight.id) { fontRepository.resolveTypeface(defaultWeight.id) }

                    Surface(
                        color = if (isSelectedFamily) palette.accentSoft else palette.surfaceHighest,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .width(126.dp)
                            .border(
                                width = if (isSelectedFamily) 1.5.dp else 1.dp,
                                color = if (isSelectedFamily) palette.accent else palette.border,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { onSelectFont(defaultWeight) }
                            .testTag("font_family_${group.familyKey}")
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (group.supportsArabic) "أبجد" else "Abc",
                                    fontFamily = FontFamily(tf),
                                    fontSize = 20.sp,
                                    color = palette.textPrimary,
                                    maxLines = 1
                                )
                                IconButton(
                                    onClick = { onToggleFavoriteFamily(group.familyKey) },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        imageVector = if (group.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = "Favorite ${group.familyName}",
                                        tint = if (group.isFavorite) palette.accent else palette.textMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = group.familyName,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelectedFamily) palette.accent else palette.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (group.weights.size > 1) "${group.weights.size} weights" else group.weights.first().metadata.subfamilyName,
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Available Weights Row for Active Family
        if (activeFamily != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp)
            ) {
                Text(
                    text = "Weight:",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.textSecondary
                )
                activeFamily.weights.forEach { weightFont ->
                    val isSelected = weightFont.id == selectedFontId
                    val weightLabel = weightFont.metadata.subfamilyName.ifBlank { "Regular" }
                    Surface(
                        onClick = { onSelectFont(weightFont) },
                        color = if (isSelected) palette.accent else palette.surfaceElevated,
                        shape = RoundedCornerShape(99.dp),
                        modifier = Modifier
                            .border(
                                width = 1.dp,
                                color = if (isSelected) palette.accent else palette.border,
                                shape = RoundedCornerShape(99.dp)
                            )
                            .testTag("font_item_${weightFont.fileName}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = weightLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) Color.White else palette.textPrimary
                            )
                            if (!weightFont.isBundled) {
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Remove ${weightFont.displayName}",
                                    tint = if (isSelected) Color.White else palette.textMuted,
                                    modifier = Modifier
                                        .size(15.dp)
                                        .clickable { onDeleteImportedFont(weightFont) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
