package com.example.typography

enum class FontFormat {
    OTF_CFF,
    TTF_GLYF,
    UNKNOWN
}

enum class OpenTypeTableSource {
    GSUB,
    GPOS,
    BOTH
}

data class OpenTypeFeatureInfo(
    val tag: String,
    val displayName: String,
    val description: String,
    val source: OpenTypeTableSource,
    val scripts: Set<String>,
    val maxAlternateCount: Int,
    val coveredGlyphCount: Int,
    val isUserToggleable: Boolean,
    val defaultEnabled: Boolean
)

data class GlyphAlternateMatch(
    val charIndex: Int,
    val codePoint: Int,
    val character: String,
    val baseGlyphId: Int,
    val featureTag: String,
    val alternateGlyphIds: List<Int>
)

data class FontMetadata(
    val familyName: String,
    val subfamilyName: String,
    val fullName: String,
    val format: FontFormat,
    val unitsPerEm: Int,
    val glyphCount: Int,
    val supportsArabic: Boolean,
    val supportsLatin: Boolean,
    val availableScripts: Set<String>,
    val features: List<OpenTypeFeatureInfo>,
    val hasMarkPositioning: Boolean,
    val hasCursiveAttachment: Boolean,
    val hasArabicShaping: Boolean,
    val weightValue: Int = 400,
    val isItalic: Boolean = false
)
