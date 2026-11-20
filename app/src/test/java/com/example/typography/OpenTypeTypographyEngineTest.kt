package com.example.typography

import android.graphics.Typeface
import android.text.TextPaint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OpenTypeTypographyEngineTest {

    private fun readResourceBytes(path: String): ByteArray {
        val stream = javaClass.classLoader?.getResourceAsStream(path)
            ?: error("Missing test resource: $path")
        return stream.use { it.readBytes() }
    }

    @Test
    fun `real OTF Arabic font detects OTTO format, Arabic shaping, and genuine OpenType features without faking`() {
        val otfBytes = readResourceBytes("fonts/IBMPlexSansArabic-Regular.otf")
        val metadata = OpenTypeFontInspector.inspect(otfBytes, "IBMPlexSansArabic")

        assertEquals(FontFormat.OTF_CFF, metadata.format)
        assertTrue("Expected Arabic support in IBM Plex Sans Arabic", metadata.supportsArabic)
        assertTrue("Expected Latin support", metadata.supportsLatin)
        assertTrue("Expected GSUB Arabic init/medi/fina shaping", metadata.hasArabicShaping)
        assertTrue("Expected GPOS mark/mkmk diacritic positioning", metadata.hasMarkPositioning)

        val tags = metadata.features.map { it.tag }.toSet()
        // Real features present in IBMPlexSansArabic-Regular.otf
        assertTrue("Expected salt feature", "salt" in tags)
        assertTrue("Expected calt feature", "calt" in tags)
        assertTrue("Expected liga feature", "liga" in tags)
        assertTrue("Expected dlig feature", "dlig" in tags)
        assertTrue("Expected kern feature", "kern" in tags)
        assertTrue("Expected ss01..ss06 features", setOf("ss01", "ss02", "ss03", "ss04", "ss05", "ss06").all { it in tags })

        // Ensure features NOT compiled into this OTF are NOT faked
        assertFalse("Must not fake swsh when absent from OTF", "swsh" in tags)
        assertFalse("Must not fake ss19 when absent from OTF", "ss19" in tags)

        val saltInfo = metadata.features.first { it.tag == "salt" }
        assertTrue("salt should have real covered glyphs", saltInfo.coveredGlyphCount > 0)
        assertTrue("salt should be user toggleable", saltInfo.isUserToggleable)
    }

    @Test
    fun `real OTF Arabic font discovers actual GSUB alternate glyph IDs for Arabic text`() {
        val otfBytes = readResourceBytes("fonts/IBMPlexSansArabic-Regular.otf")
        val metadata = OpenTypeFontInspector.inspect(otfBytes, "IBMPlexSansArabic")
        val featureTags = metadata.features.filter { it.isUserToggleable }.map { it.tag }.toSet()

        val arabicSample = "كتابة التصميم a g 0"
        val matches = OpenTypeFontInspector.findAlternatesForText(
            bytes = otfBytes,
            text = arabicSample,
            targetFeatures = featureTags
        )

        assertTrue(
            "Expected actual OpenType GSUB alternate/substitution matches for Arabic sample",
            matches.isNotEmpty()
        )
        assertTrue(
            "Expected Arabic Kaf (ك) to match real salt/ss06/aalt substitutions in IBM Plex Sans Arabic OTF",
            matches.any { it.character == "ك" && (it.featureTag == "salt" || it.featureTag == "ss06") }
        )
        assertTrue(
            "Every match should contain real positive alternate glyph IDs",
            matches.all { it.alternateGlyphIds.isNotEmpty() && it.alternateGlyphIds.all { gid -> gid > 0 } }
        )
    }

    @Test
    fun `Amiri TTF detects cursive attachment, diacritic positioning, and ss01-ss08`() {
        val ttfBytes = readResourceBytes("fonts/Amiri-Regular.ttf")
        val metadata = OpenTypeFontInspector.inspect(ttfBytes, "Amiri")

        assertEquals(FontFormat.TTF_GLYF, metadata.format)
        assertTrue(metadata.supportsArabic)
        assertTrue(metadata.hasCursiveAttachment)
        assertTrue(metadata.hasMarkPositioning)
        assertTrue(metadata.hasArabicShaping)

        val tags = metadata.features.map { it.tag }.toSet()
        assertTrue(setOf("ss01", "ss02", "ss03", "ss04", "ss05", "ss06", "ss07", "ss08").all { it in tags })
        assertTrue("calt" in tags && "liga" in tags && "kern" in tags)
        assertFalse("Amiri does not contain dlig", "dlig" in tags)
    }

    @Test
    fun `Arabic shaping preserves diacritics, RTL direction, and cursive joining when letterSpacing is requested`() {
        val otfBytes = readResourceBytes("fonts/IBMPlexSansArabic-Regular.otf")
        val metadata = OpenTypeFontInspector.inspect(otfBytes, "IBMPlexSansArabic")
        val tempOtf = File.createTempFile("test_arabic", ".otf").apply {
            writeBytes(otfBytes)
            deleteOnExit()
        }
        val typeface = Typeface.createFromFile(tempOtf)
        assertNotNull(typeface)

        val arabicWithDiacritics = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ"
        assertTrue(ArabicTypographyEngine.containsArabicScript(arabicWithDiacritics))
        assertTrue(ArabicTypographyEngine.resolveIsRtl(arabicWithDiacritics, TextDirectionMode.AUTO))
        assertTrue(ArabicTypographyEngine.countArabicDiacritics(arabicWithDiacritics) >= 10)

        // Verify Arabic-safe letter spacing prevents cursive joining breakage
        val arabicPaint = TextPaint()
        ArabicTypographyEngine.configureTextPaint(
            paint = arabicPaint,
            typeface = typeface,
            textSizePx = 64f,
            text = arabicWithDiacritics,
            featureSettings = ArabicTypographyEngine.buildFontFeatureSettings(
                featureValues = mapOf("salt" to 1, "ss01" to 1),
                availableFeatures = metadata.features
            ),
            letterSpacingEm = 0.25f,
            preserveArabicJoining = true
        )
        assertEquals(0f, arabicPaint.letterSpacing, 0.0001f)
        assertTrue(arabicPaint.fontFeatureSettings?.contains("\"salt\" 1") == true)
        assertTrue(arabicPaint.fontFeatureSettings?.contains("\"ss01\" 1") == true)

        // Verify pure English applies letter spacing normally
        val englishPaint = TextPaint()
        ArabicTypographyEngine.configureTextPaint(
            paint = englishPaint,
            typeface = typeface,
            textSizePx = 64f,
            text = "TYPEFRAME",
            featureSettings = "\"kern\" 1, \"liga\" 1",
            letterSpacingEm = 0.25f,
            preserveArabicJoining = true
        )
        assertEquals(0.25f, englishPaint.letterSpacing, 0.0001f)

        // Shape full Arabic + mixed BiDi run
        val (layout, runResult) = ArabicTypographyEngine.shapeAndMeasure(
            text = "تصميم TYPEFRAME العربيّ",
            typeface = typeface,
            textSizePx = 72f,
            maxWidthPx = 900,
            featureValues = mapOf("salt" to 1, "calt" to 1, "liga" to 1, "kern" to 1),
            availableFeatures = metadata.features,
            directionMode = TextDirectionMode.AUTO,
            alignMode = TextAlignMode.CENTER
        )
        assertTrue(runResult.isRtl)
        assertTrue(runResult.containsArabic)
        assertTrue(runResult.preserveJoiningActive)
        assertTrue(layout.lineCount >= 1)
        assertTrue(runResult.heightPx > 0f)
    }
}
