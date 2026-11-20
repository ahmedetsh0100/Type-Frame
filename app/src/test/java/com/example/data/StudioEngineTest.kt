package com.example.data

import android.content.Context
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.model.CropRect
import com.example.model.ImageLayer
import com.example.model.LayerBlendMode
import com.example.model.PaintLayer
import com.example.model.PaintStroke
import com.example.model.ProjectDocument
import com.example.model.ShadowStyle
import com.example.model.ShapeKind
import com.example.model.ShapeLayer
import com.example.model.StrokePoint
import com.example.model.StrokeStyle
import com.example.model.TextLayer
import com.example.model.TextStyleSpan
import com.example.render.CanvasRenderer
import com.example.render.ExportEngine
import com.example.render.ExportMode
import com.example.render.ExportScaleOption
import com.example.typography.ArabicTypographyEngine
import com.example.typography.TextAlignMode
import com.example.typography.TextDirectionMode
import com.example.viewmodel.StudioViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StudioEngineTest {

    @Test
    fun `project serializer round-trips Arabic text, rich spans, OpenType features, blend modes, paint layers, and scaleXY`() {
        val doc = ProjectDocument(
            id = "proj-1",
            title = "Arabic Poster",
            canvasWidthPx = 1080,
            canvasHeightPx = 1350,
            backgroundColorArgb = 0xFF101218.toInt(),
            isTransparentBackground = false,
            layers = listOf(
                ImageLayer(
                    id = "img-1",
                    name = "Background",
                    xPx = 540f,
                    yPx = 675f,
                    rotationDeg = 12f,
                    scaleX = 1.25f,
                    scaleY = 0.85f,
                    opacity = 0.9f,
                    blendMode = LayerBlendMode.MULTIPLY,
                    imagePath = "/tmp/sample.png",
                    widthPx = 900f,
                    heightPx = 1100f,
                    naturalWidthPx = 1800,
                    naturalHeightPx = 2200,
                    cropRect = CropRect(0.1f, 0.1f, 0.9f, 0.9f),
                    eraseMaskStrokes = listOf(
                        PaintStroke(
                            points = listOf(StrokePoint(10f, 10f), StrokePoint(40f, 50f)),
                            sizePx = 28f,
                            hardness = 0.6f,
                            isEraser = true
                        )
                    )
                ),
                ShapeLayer(
                    id = "shp-1",
                    name = "Gold Frame",
                    xPx = 540f,
                    yPx = 675f,
                    blendMode = LayerBlendMode.OVERLAY,
                    shapeKind = ShapeKind.RECTANGLE,
                    widthPx = 800f,
                    heightPx = 400f,
                    strokeWidthPx = 12f,
                    cornerRadiusPx = 32f,
                    fillEnabled = false,
                    strokeEnabled = true,
                    strokeColorArgb = 0xFFFFB84D.toInt()
                ),
                TextLayer(
                    id = "txt-1",
                    name = "Headline",
                    xPx = 540f,
                    yPx = 675f,
                    scaleX = 1.1f,
                    scaleY = 1.0f,
                    blendMode = LayerBlendMode.SCREEN,
                    text = "بِسْمِ التصميم HTYPE",
                    fontId = "asset:IBMPlexSansArabic-Regular.otf",
                    fontSizePx = 110f,
                    alignMode = TextAlignMode.CENTER,
                    directionMode = TextDirectionMode.RTL,
                    stroke = StrokeStyle(enabled = true, colorArgb = Color.BLACK, widthPx = 6f),
                    shadow = ShadowStyle(enabled = true, blurRadiusPx = 18f, offsetYPx = 8f),
                    openTypeFeatures = mapOf("salt" to 1, "ss06" to 1, "liga" to 1, "calt" to 1),
                    spans = listOf(
                        TextStyleSpan(
                            start = 7,
                            end = 14,
                            fontId = "asset:IBMPlexSansArabic-Bold.otf",
                            colorArgb = 0xFFFF5A36.toInt(),
                            isBold = true,
                            isUnderline = true
                        )
                    )
                ),
                PaintLayer(
                    id = "paint-1",
                    name = "Brush Strokes",
                    canvasWidthPx = 1080,
                    canvasHeightPx = 1350,
                    blendMode = LayerBlendMode.DARKEN,
                    strokes = listOf(
                        PaintStroke(
                            points = listOf(StrokePoint(100f, 100f), StrokePoint(300f, 240f)),
                            colorArgb = 0xFF2EE6A8.toInt(),
                            sizePx = 42f,
                            hardness = 0.5f,
                            opacity = 0.8f,
                            flow = 0.9f
                        )
                    )
                )
            )
        )

        val json = ProjectSerializer.toJson(doc)
        val restored = ProjectSerializer.fromJson(json)

        assertEquals(doc.id, restored.id)
        assertEquals(doc.title, restored.title)
        assertEquals(4, restored.layers.size)

        val restoredImg = restored.layers[0] as ImageLayer
        assertEquals(0.1f, restoredImg.cropRect.left, 0.001f)
        assertEquals(12f, restoredImg.rotationDeg, 0.001f)
        assertEquals(1.25f, restoredImg.scaleX, 0.001f)
        assertEquals(LayerBlendMode.MULTIPLY, restoredImg.blendMode)
        assertEquals(1, restoredImg.eraseMaskStrokes.size)

        val restoredShape = restored.layers[1] as ShapeLayer
        assertEquals(ShapeKind.RECTANGLE, restoredShape.shapeKind)
        assertEquals(LayerBlendMode.OVERLAY, restoredShape.blendMode)

        val restoredText = restored.layers[2] as TextLayer
        assertEquals("بِسْمِ التصميم HTYPE", restoredText.text)
        assertEquals(LayerBlendMode.SCREEN, restoredText.blendMode)
        assertEquals(1, restoredText.spans.size)
        assertEquals("asset:IBMPlexSansArabic-Bold.otf", restoredText.spans[0].fontId)
        assertEquals(true, restoredText.spans[0].isBold)
        assertEquals(true, restoredText.spans[0].isUnderline)

        val restoredPaint = restored.layers[3] as PaintLayer
        assertEquals(LayerBlendMode.DARKEN, restoredPaint.blendMode)
        assertEquals(1, restoredPaint.strokes.size)
        assertEquals(2, restoredPaint.strokes[0].points.size)
    }

    @Test
    fun `font repository bundles multi-weight Arabic fonts and groups into families with favorites first`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = TypeframeDatabase.getInstance(context)
        val fontRepo = FontRepository(context, db.fontDao())
        val bundled = fontRepo.getBundledFontsImmediate()

        assertTrue("Expected at least 6 bundled font weights", bundled.size >= 6)

        val families = fontRepo.groupIntoFamilies(bundled, favoriteFamilyKeys = setOf("amiri"))
        assertTrue(families.size >= 2)
        assertEquals("amiri", families.first().familyKey)
        assertTrue(families.first().isFavorite)
        assertTrue(families.first().weights.size >= 3)

        val plex = families.first { it.familyKey.contains("plex") }
        assertTrue(plex.supportsArabic)
        assertEquals(3, plex.weights.size)
        assertTrue(plex.weights.first().metadata.weightValue <= plex.weights.last().metadata.weightValue)
    }

    @Test
    fun `rich inline text spans preserve Arabic RTL shaping and adjust on edits`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = TypeframeDatabase.getInstance(context)
        val fontRepo = FontRepository(context, db.fontDao())
        val bundled = fontRepo.getBundledFontsImmediate()
        val regularTf = fontRepo.resolveTypeface("asset:IBMPlexSansArabic-Regular.otf")

        val text = "تصميم HTYPE العربي"
        val spans = listOf(
            TextStyleSpan(start = 6, end = 11, fontId = "asset:IBMPlexSansArabic-Bold.otf", colorArgb = 0xFFFF5A36.toInt(), isBold = true)
        )
        val (layout, run) = ArabicTypographyEngine.shapeAndMeasure(
            text = text,
            typeface = regularTf,
            textSizePx = 64f,
            maxWidthPx = 600,
            featureValues = mapOf("liga" to 1, "calt" to 1),
            availableFeatures = bundled.first().metadata.features,
            spans = spans,
            resolveSpanTypeface = { fontRepo.resolveTypeface(it) }
        )

        assertTrue(run.isRtl)
        assertTrue(run.containsArabic)
        assertTrue(layout.height > 0)

        // Editing prefix shifts span start/end accurately
        val shifted = StudioViewModel.adjustSpansForTextEdit(spans, text, "يا $text")
        assertEquals(9, shifted.first().start)
        assertEquals(14, shifted.first().end)
    }

    @Test
    fun `canvas renderer and export engine produce genuine transparent PNG, text-only PNG, JPEG, and PDF`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = TypeframeDatabase.getInstance(context)
        val fontRepo = FontRepository(context, db.fontDao())
        val fonts = fontRepo.getBundledFontsImmediate().associateBy { it.id }

        val doc = ProjectDocument(
            title = "HTYPE Export Test",
            canvasWidthPx = 400,
            canvasHeightPx = 400,
            backgroundColorArgb = 0xFF202530.toInt(),
            isTransparentBackground = false,
            layers = listOf(
                ShapeLayer(
                    xPx = 200f,
                    yPx = 200f,
                    shapeKind = ShapeKind.RECTANGLE,
                    widthPx = 360f,
                    heightPx = 360f,
                    strokeWidthPx = 8f,
                    fillEnabled = true,
                    fillColorArgb = 0xFFFF5A36.toInt()
                ),
                PaintLayer(
                    canvasWidthPx = 400,
                    canvasHeightPx = 400,
                    strokes = listOf(
                        PaintStroke(
                            points = listOf(StrokePoint(40f, 40f), StrokePoint(120f, 120f)),
                            colorArgb = 0xFF2EE6A8.toInt(),
                            sizePx = 18f,
                            hardness = 0.8f
                        )
                    )
                ),
                TextLayer(
                    xPx = 200f,
                    yPx = 200f,
                    text = "خط عربي",
                    fontId = "asset:IBMPlexSansArabic-Regular.otf",
                    fontSizePx = 56f,
                    boxWidthPx = 320f,
                    openTypeFeatures = mapOf("salt" to 1, "liga" to 1, "calt" to 1)
                )
            )
        )

        val transparentBmp = CanvasRenderer.renderDocumentToBitmap(
            doc = doc,
            fontRepository = fontRepo,
            fontsById = fonts,
            forceTransparentBackground = true
        )
        assertEquals(400, transparentBmp.width)
        assertEquals(400, transparentBmp.height)
        assertEquals(0, Color.alpha(transparentBmp.getPixel(5, 5)))
        transparentBmp.recycle()

        // Text-only transparent mode skips the 360x360 rectangle around edges
        val textOnlyBmp = CanvasRenderer.renderDocumentToBitmap(
            doc = doc,
            fontRepository = fontRepo,
            fontsById = fonts,
            forceTransparentBackground = true,
            textOnlyMode = true
        )
        assertEquals(0, Color.alpha(textOnlyBmp.getPixel(35, 35)))
        textOnlyBmp.recycle()

        val fullBmp = CanvasRenderer.renderDocumentToBitmap(
            doc = doc,
            fontRepository = fontRepo,
            fontsById = fonts,
            forceTransparentBackground = false
        )
        assertEquals(255, Color.alpha(fullBmp.getPixel(5, 5)))
        fullBmp.recycle()

        // Verify ExportEngine dimensions scaling
        val (w2, h2) = ExportEngine.computeExportDimensions(doc, ExportScaleOption.X2)
        assertEquals(800 to 800, w2 to h2)

        // Verify ExportEngine writes valid files to custom URIs for PNG, JPEG
        val tmpDir = File(context.cacheDir, "unit_exports").apply { mkdirs() }
        val pngFile = File(tmpDir, "out_transparent.png")
        val pngRes = ExportEngine.writeExportToUri(
            context = context,
            targetUri = Uri.fromFile(pngFile),
            doc = doc,
            fontRepository = fontRepo,
            fontsById = fonts,
            mode = ExportMode.TRANSPARENT_PNG,
            scaleOption = ExportScaleOption.X2,
            textOnly = true
        )
        assertTrue(pngRes.exceptionOrNull()?.stackTraceToString() ?: "", pngRes.isSuccess)
        assertTrue(pngFile.exists() && pngFile.length() > 100)

        val jpgFile = File(tmpDir, "out.jpg")
        val jpgRes = ExportEngine.writeExportToUri(
            context = context,
            targetUri = Uri.fromFile(jpgFile),
            doc = doc,
            fontRepository = fontRepo,
            fontsById = fonts,
            mode = ExportMode.HIGH_RES_JPEG,
            scaleOption = ExportScaleOption.X2
        )
        assertTrue(jpgRes.exceptionOrNull()?.stackTraceToString() ?: "", jpgRes.isSuccess)
        assertNotNull(jpgFile.length())
        assertTrue(jpgFile.length() > 100)

        val pdfFile = File(tmpDir, "out.pdf")
        val pdfRes = ExportEngine.writeExportToUri(
            context = context,
            targetUri = Uri.fromFile(pdfFile),
            doc = doc,
            fontRepository = fontRepo,
            fontsById = fonts,
            mode = ExportMode.PDF_DOCUMENT,
            scaleOption = ExportScaleOption.X1
        )
        assertTrue(pdfRes.exceptionOrNull()?.stackTraceToString() ?: "", pdfRes.isSuccess)
        assertTrue(pdfFile.exists() && pdfFile.length() > 100)
        val pdfText = pdfFile.readBytes().toString(Charsets.ISO_8859_1)
        assertTrue(pdfText.startsWith("%PDF-"))
        assertTrue(pdfText.contains("%%EOF"))
    }
}
