package com.example.data

import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.model.CropRect
import com.example.model.ImageLayer
import com.example.model.ProjectDocument
import com.example.model.ShadowStyle
import com.example.model.ShapeKind
import com.example.model.ShapeLayer
import com.example.model.StrokeStyle
import com.example.model.TextLayer
import com.example.render.CanvasRenderer
import com.example.typography.TextAlignMode
import com.example.typography.TextDirectionMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StudioEngineTest {

    @Test
    fun `project serializer round-trips Arabic text, OpenType features, shapes, and image crops`() {
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
                    opacity = 0.9f,
                    imagePath = "/tmp/sample.png",
                    widthPx = 900f,
                    heightPx = 1100f,
                    naturalWidthPx = 1800,
                    naturalHeightPx = 2200,
                    cropRect = CropRect(0.1f, 0.1f, 0.9f, 0.9f)
                ),
                ShapeLayer(
                    id = "shp-1",
                    name = "Gold Frame",
                    xPx = 540f,
                    yPx = 675f,
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
                    text = "بِسْمِ التصميم",
                    fontId = "asset:IBMPlexSansArabic-Regular.otf",
                    fontSizePx = 110f,
                    alignMode = TextAlignMode.CENTER,
                    directionMode = TextDirectionMode.RTL,
                    stroke = StrokeStyle(enabled = true, colorArgb = Color.BLACK, widthPx = 6f),
                    shadow = ShadowStyle(enabled = true, blurRadiusPx = 18f, offsetYPx = 8f),
                    openTypeFeatures = mapOf("salt" to 1, "ss06" to 1, "liga" to 1, "calt" to 1)
                )
            )
        )

        val json = ProjectSerializer.toJson(doc)
        val restored = ProjectSerializer.fromJson(json)

        assertEquals(doc.id, restored.id)
        assertEquals(doc.title, restored.title)
        assertEquals(3, restored.layers.size)

        val restoredImg = restored.layers[0] as ImageLayer
        assertEquals(0.1f, restoredImg.cropRect.left, 0.001f)
        assertEquals(12f, restoredImg.rotationDeg, 0.001f)

        val restoredShape = restored.layers[1] as ShapeLayer
        assertEquals(ShapeKind.RECTANGLE, restoredShape.shapeKind)
        assertEquals(800f, restoredShape.widthPx, 0.001f)
        assertEquals(12f, restoredShape.strokeWidthPx, 0.001f)

        val restoredText = restored.layers[2] as TextLayer
        assertEquals("بِسْمِ التصميم", restoredText.text)
        assertEquals(1, restoredText.openTypeFeatures["salt"])
        assertEquals(1, restoredText.openTypeFeatures["ss06"])
        assertTrue(restoredText.stroke.enabled)
        assertTrue(restoredText.shadow.enabled)
    }

    @Test
    fun `canvas renderer produces transparent and opaque ARGB_8888 PNG bitmaps`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = TypeframeDatabase.getInstance(context)
        val fontRepo = FontRepository(context, db.fontDao())
        val fonts = fontRepo.getBundledFontsImmediate().associateBy { it.id }

        val doc = ProjectDocument(
            title = "Export Test",
            canvasWidthPx = 400,
            canvasHeightPx = 400,
            backgroundColorArgb = 0xFF202530.toInt(),
            isTransparentBackground = false,
            layers = listOf(
                ShapeLayer(
                    xPx = 200f,
                    yPx = 200f,
                    shapeKind = ShapeKind.CIRCLE,
                    widthPx = 220f,
                    heightPx = 220f,
                    strokeWidthPx = 8f,
                    fillEnabled = true,
                    fillColorArgb = 0xFFFF5A36.toInt()
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

        val fullBmp = CanvasRenderer.renderDocumentToBitmap(
            doc = doc,
            fontRepository = fontRepo,
            fontsById = fonts,
            forceTransparentBackground = false
        )
        assertEquals(255, Color.alpha(fullBmp.getPixel(5, 5)))
        fullBmp.recycle()
    }
}
