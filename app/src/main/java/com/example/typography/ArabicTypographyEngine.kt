package com.example.typography

import android.graphics.Typeface
import android.graphics.text.TextRunShaper
import android.os.Build
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextDirectionHeuristic
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.MetricAffectingSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import com.example.model.TextStyleSpan
import java.io.File

enum class TextDirectionMode {
    AUTO,
    RTL,
    LTR
}

enum class TextAlignMode {
    START,
    CENTER,
    END
}

data class ShapedGlyphSnapshot(
    val glyphId: Int,
    val x: Float,
    val y: Float
)

data class ShapedRunResult(
    val featureSettings: String,
    val isRtl: Boolean,
    val containsArabic: Boolean,
    val preserveJoiningActive: Boolean,
    val totalAdvance: Float,
    val glyphCount: Int,
    val glyphs: List<ShapedGlyphSnapshot>,
    val lineCount: Int,
    val widthPx: Float,
    val heightPx: Float
)

private class TypefaceWithFeaturesSpan(
    private val customTypeface: Typeface,
    private val featureSettings: String?
) : MetricAffectingSpan() {
    override fun updateDrawState(tp: TextPaint) {
        tp.typeface = customTypeface
        if (!featureSettings.isNullOrBlank()) {
            tp.fontFeatureSettings = featureSettings
        }
    }

    override fun updateMeasureState(tp: TextPaint) {
        tp.typeface = customTypeface
        if (!featureSettings.isNullOrBlank()) {
            tp.fontFeatureSettings = featureSettings
        }
    }
}

/**
 * Native Android HarfBuzz + FreeType typography engine with Arabic RTL shaping,
 * diacritic (harakat/tashkeel) preservation, rich inline span support, and OpenType features.
 */
object ArabicTypographyEngine {

    private val defaultOnFeatures = setOf("liga", "calt", "kern")

    fun containsArabicScript(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (isArabicCodePoint(cp)) return true
            i += Character.charCount(cp)
        }
        return false
    }

    fun isArabicCodePoint(cp: Int): Boolean {
        return cp in 0x0600..0x06FF ||
            cp in 0x0750..0x077F ||
            cp in 0x08A0..0x08FF ||
            cp in 0xFB50..0xFDFF ||
            cp in 0xFE70..0xFEFF
    }

    fun isArabicDiacritic(cp: Int): Boolean {
        return cp in 0x0610..0x061A ||
            cp in 0x064B..0x065F ||
            cp == 0x0670 ||
            cp in 0x06D6..0x06DC ||
            cp in 0x06DF..0x06E4 ||
            cp in 0x06E7..0x06E8 ||
            cp in 0x06EA..0x06ED
    }

    fun countArabicDiacritics(text: String): Int {
        var count = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (isArabicDiacritic(cp)) count++
            i += Character.charCount(cp)
        }
        return count
    }

    fun resolveIsRtl(text: String, mode: TextDirectionMode): Boolean = when (mode) {
        TextDirectionMode.RTL -> true
        TextDirectionMode.LTR -> false
        TextDirectionMode.AUTO -> {
            var i = 0
            var result: Boolean? = null
            while (i < text.length) {
                val cp = text.codePointAt(i)
                val dir = Character.getDirectionality(cp)
                if (dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT ||
                    dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
                ) {
                    result = true
                    break
                } else if (dir == Character.DIRECTIONALITY_LEFT_TO_RIGHT) {
                    result = false
                    break
                }
                i += Character.charCount(cp)
            }
            result ?: false
        }
    }

    fun buildFontFeatureSettings(
        featureValues: Map<String, Int>,
        availableFeatures: List<OpenTypeFeatureInfo> = emptyList()
    ): String {
        val availableTags = availableFeatures.map { it.tag }.toSet()
        val parts = mutableListOf<String>()

        for (tag in defaultOnFeatures) {
            if (availableTags.isEmpty() || tag in availableTags || tag in featureValues) {
                val value = featureValues[tag] ?: 1
                parts.add("\"$tag\" $value")
            }
        }

        featureValues.entries
            .filter { (tag, value) -> tag !in defaultOnFeatures && value > 0 }
            .sortedBy { it.key }
            .forEach { (tag, value) ->
                if (availableTags.isEmpty() || tag in availableTags) {
                    parts.add("\"$tag\" $value")
                }
            }

        return parts.joinToString(", ")
    }

    fun configureTextPaint(
        paint: TextPaint,
        typeface: Typeface?,
        textSizePx: Float,
        text: String,
        featureSettings: String,
        letterSpacingEm: Float = 0f,
        preserveArabicJoining: Boolean = true
    ) {
        paint.isAntiAlias = true
        paint.isSubpixelText = true
        paint.typeface = typeface ?: Typeface.DEFAULT
        paint.textSize = textSizePx
        paint.fontFeatureSettings = featureSettings.ifBlank { null }

        val hasArabic = containsArabicScript(text)
        paint.letterSpacing = if (hasArabic && preserveArabicJoining) 0f else letterSpacingEm
    }

    fun buildRichSpannable(
        text: String,
        spans: List<TextStyleSpan>,
        featureSettings: String?,
        resolveTypeface: (String) -> Typeface,
        includeColors: Boolean = true
    ): CharSequence {
        if (spans.isEmpty() || text.isEmpty()) return text
        val builder = SpannableStringBuilder(text)
        val len = text.length
        for (span in spans) {
            val s = span.start.coerceIn(0, len)
            val e = span.end.coerceIn(0, len)
            if (s >= e) continue

            span.fontId?.let { fId ->
                builder.setSpan(
                    TypefaceWithFeaturesSpan(resolveTypeface(fId), featureSettings),
                    s,
                    e,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            span.fontSizePx?.let { px ->
                builder.setSpan(
                    AbsoluteSizeSpan(px.toInt().coerceAtLeast(10), false),
                    s,
                    e,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            if (includeColors) {
                span.colorArgb?.let { argb ->
                    builder.setSpan(
                        ForegroundColorSpan(argb),
                        s,
                        e,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
            val bold = span.isBold == true
            val italic = span.isItalic == true
            if (bold && italic) {
                builder.setSpan(StyleSpan(Typeface.BOLD_ITALIC), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else if (bold) {
                builder.setSpan(StyleSpan(Typeface.BOLD), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else if (italic) {
                builder.setSpan(StyleSpan(Typeface.ITALIC), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (span.isUnderline == true) {
                builder.setSpan(UnderlineSpan(), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return builder
    }

    fun shapeAndMeasure(
        text: String,
        typeface: Typeface?,
        textSizePx: Float,
        maxWidthPx: Int,
        featureValues: Map<String, Int>,
        availableFeatures: List<OpenTypeFeatureInfo> = emptyList(),
        directionMode: TextDirectionMode = TextDirectionMode.AUTO,
        alignMode: TextAlignMode = TextAlignMode.CENTER,
        lineHeightMultiplier: Float = 1.18f,
        letterSpacingEm: Float = 0f,
        preserveArabicJoining: Boolean = true,
        spans: List<TextStyleSpan> = emptyList(),
        resolveSpanTypeface: (String) -> Typeface = { typeface ?: Typeface.DEFAULT },
        includeSpanColors: Boolean = true
    ): Pair<StaticLayout, ShapedRunResult> {
        val isRtl = resolveIsRtl(text, directionMode)
        val hasArabic = containsArabicScript(text)
        val featureSettings = buildFontFeatureSettings(featureValues, availableFeatures)

        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)
        configureTextPaint(
            paint = paint,
            typeface = typeface,
            textSizePx = textSizePx,
            text = text,
            featureSettings = featureSettings,
            letterSpacingEm = letterSpacingEm,
            preserveArabicJoining = preserveArabicJoining
        )

        val layoutAlignment = resolveLayoutAlignment(alignMode, isRtl)
        val directionHeuristic = resolveDirectionHeuristic(directionMode)
        val richSequence = buildRichSpannable(
            text = text,
            spans = spans,
            featureSettings = featureSettings.ifBlank { null },
            resolveTypeface = resolveSpanTypeface,
            includeColors = includeSpanColors
        )

        val safeWidth = maxWidthPx.coerceAtLeast(1)
        val staticLayout = StaticLayout.Builder
            .obtain(richSequence, 0, richSequence.length, paint, safeWidth)
            .setAlignment(layoutAlignment)
            .setTextDirection(directionHeuristic)
            .setLineSpacing(0f, lineHeightMultiplier.coerceIn(0.6f, 3.0f))
            .setIncludePad(true)
            .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .build()

        val glyphs = mutableListOf<ShapedGlyphSnapshot>()
        var totalAdvance = 0f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && text.isNotEmpty()) {
            val firstLine = text.substringBefore('\n')
            if (firstLine.isNotEmpty()) {
                val shaped = TextRunShaper.shapeTextRun(
                    firstLine,
                    0,
                    firstLine.length,
                    0,
                    firstLine.length,
                    0f,
                    0f,
                    isRtl,
                    paint
                )
                totalAdvance = shaped.advance
                for (i in 0 until shaped.glyphCount()) {
                    glyphs.add(
                        ShapedGlyphSnapshot(
                            glyphId = shaped.getGlyphId(i),
                            x = shaped.getGlyphX(i),
                            y = shaped.getGlyphY(i)
                        )
                    )
                }
            }
        } else if (text.isNotEmpty()) {
            totalAdvance = paint.measureText(text.substringBefore('\n'))
        }

        val maxLineW = (0 until staticLayout.lineCount)
            .maxOfOrNull { staticLayout.getLineWidth(it) } ?: 0f

        return staticLayout to ShapedRunResult(
            featureSettings = featureSettings,
            isRtl = isRtl,
            containsArabic = hasArabic,
            preserveJoiningActive = hasArabic && preserveArabicJoining,
            totalAdvance = totalAdvance,
            glyphCount = glyphs.size,
            glyphs = glyphs,
            lineCount = staticLayout.lineCount,
            widthPx = maxLineW,
            heightPx = staticLayout.height.toFloat()
        )
    }

    fun resolveLayoutAlignment(alignMode: TextAlignMode, isRtl: Boolean): Layout.Alignment {
        return when (alignMode) {
            TextAlignMode.CENTER -> Layout.Alignment.ALIGN_CENTER
            TextAlignMode.START -> if (isRtl) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL
            TextAlignMode.END -> if (isRtl) Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_OPPOSITE
        }
    }

    fun resolveDirectionHeuristic(mode: TextDirectionMode): TextDirectionHeuristic {
        return when (mode) {
            TextDirectionMode.AUTO -> TextDirectionHeuristics.FIRSTSTRONG_RTL
            TextDirectionMode.RTL -> TextDirectionHeuristics.RTL
            TextDirectionMode.LTR -> TextDirectionHeuristics.LTR
        }
    }

    fun loadTypefaceFromFile(file: File): Typeface {
        return Typeface.createFromFile(file)
    }
}
