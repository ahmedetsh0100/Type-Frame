package com.example.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.FontItem
import com.example.typography.FontFormat
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCoral
import com.example.ui.theme.StudioCoralSoft
import com.example.ui.theme.StudioMint
import com.example.ui.theme.StudioMintSoft
import com.example.ui.theme.StudioSurfaceHighest
import com.example.ui.theme.StudioTextMuted
import com.example.ui.theme.StudioTextSecondary

@Composable
fun FontsSheet(
    fonts: List<FontItem>,
    selectedFontId: String?,
    onImportFontClick: () -> Unit,
    onSelectFont: (FontItem) -> Unit,
    onDeleteImportedFont: (FontItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = onImportFontClick,
            modifier = Modifier.fillMaxWidth().testTag("import_font_button")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Import OTF or TTF font")
            Spacer(Modifier.width(8.dp))
            Text("Import Custom .OTF / .TTF Font")
        }

        fonts.forEach { font ->
            val isSelected = font.id == selectedFontId
            Surface(
                color = StudioSurfaceHighest,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) StudioCoral else StudioBorder,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelectFont(font) }
                    .testTag("font_item_${font.fileName}")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = font.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val formatLabel = when (font.metadata.format) {
                                FontFormat.OTF_CFF -> "OTF"
                                FontFormat.TTF_GLYF -> "TTF"
                                FontFormat.UNKNOWN -> "FONT"
                            }
                            SmallTagChip(formatLabel, StudioCoralSoft, StudioCoral)
                            if (font.metadata.supportsArabic) {
                                SmallTagChip("ARABIC", StudioMintSoft, StudioMint)
                            }
                            val otCount = font.metadata.features.count { it.isUserToggleable }
                            SmallTagChip("$otCount OT Features", Color(0xFF232631), StudioTextSecondary)
                            if (!font.isBundled) {
                                SmallTagChip("CUSTOM", Color(0xFF2B253A), Color(0xFFC4A7FF))
                            }
                        }
                    }

                    if (!font.isBundled) {
                        IconButton(onClick = { onDeleteImportedFont(font) }) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Remove imported font",
                                tint = StudioTextMuted
                            )
                        }
                    }
                    if (isSelected) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Selected font",
                            tint = StudioCoral,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallTagChip(text: String, bg: Color, fg: Color) {
    Surface(color = bg, shape = RoundedCornerShape(5.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
