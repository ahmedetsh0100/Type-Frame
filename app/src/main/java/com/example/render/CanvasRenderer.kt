package com.example.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.StaticLayout
import android.util.LruCache
import com.example.data.FontRepository
import com.example.model.CanvasLayer
import com.example.model.FontItem
import com.example.model.ImageLayer
import com.example.model.ProjectDocument
import com.example.model.ShapeKind
import com.example.model.ShapeLayer
import com.example.model.TextLayer
import com.example.typography.ArabicTypographyEngine
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object CanvasRenderer {

    // 48MB preview bitmap cache tuned for mid-range Android devices
    private val previewCache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun getPreviewBitmap(path: String, maxDimensionPx: Int = 1440): Bitmap? {
        val cacheKey = "$path@$maxDimensionPx"
        previewCache.get(cacheKey)?.let { if (!it.isRecycled) return it }
        val decoded = decodeSampledBitmap(path, maxDimensionPx) ?: return null
        previewCache.put(cacheKey, decoded)
        return decoded
    }

    fun getExportBitmap(path: String): Bitmap? = decodeSampledBitmap(path, maxDimensionPx = 4096)

    private fun decodeSampledBitmap(path: String, maxDimensionPx: Int): Bitmap? {
        return runCatching {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, boundsOpts)
            val rawW = boundsOpts.outWidth
            val rawH = boundsOpts.outHeight
            if (rawW <= 0 || rawH <= 0) return null

            var sampleSize = 1
            while (rawW / sampleSize > maxDimensionPx || rawH / sampleSize > maxDimensionPx) {
                sampleSize *= 2
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(path, opts)
        }.getOrNull()
    }

    fun renderDocumentToCanvas(
        canvas: Canvas,
        doc: ProjectDocument,
        fontRepository: FontRepository,
        fontsById: Map<String, FontItem>,
        forceTransparentBackground: Boolean = false,
        isExportMode: Boolean = false
    ) {
        if (!forceTransparentBackground && !doc.isTransparentBackground) {
            val bgPaint = Paint().apply {
                color = doc.backgroundColorArgb
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, doc.canvasWidthPx.toFloat(), doc.canvasHeightPx.toFloat(), bgPaint)
        }

        for (layer in doc.layers) {
            if (!layer.isVisible) continue
            drawLayer(
                canvas = canvas,
                layer = layer,
                fontRepository = fontRepository,
                fontsById = fontsById,
                isExportMode = isExportMode
            )
        }
    }

    fun renderDocumentToBitmap(
        doc: ProjectDocument,
        fontRepository: FontRepository,
        fontsById: Map<String, FontItem>,
        forceTransparentBackground: Boolean,
        scaleFactor: Float = 1f,
        isExportMode: Boolean = true
    ): Bitmap {
        val targetW = max(1, (doc.canvasWidthPx * scaleFactor).toInt())
        val targetH = max(1, (doc.canvasHeightPx * scaleFactor).toInt())
        val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        if (!forceTransparentBackground && !doc.isTransparentBackground) {
            bitmap.eraseColor(doc.backgroundColorArgb)
        } else {
            bitmap.eraseColor(Color.TRANSPARENT)
        }
        val canvas = Canvas(bitmap)
        if (scaleFactor != 1f) {
            canvas.scale(scaleFactor, scaleFactor)
        }
        renderDocumentToCanvas(
            canvas = canvas,
            doc = doc,
            fontRepository = fontRepository,
            fontsById = fontsById,
            forceTransparentBackground = forceTransparentBackground,
            isExportMode = isExportMode
        )
        return bitmap
    }

    fun drawLayer(
        canvas: Canvas,
        layer: CanvasLayer,
        fontRepository: FontRepository,
        fontsById: Map<String, FontItem>,
        isExportMode: Boolean
    ) {
        canvas.save()
        canvas.translate(layer.xPx, layer.yPx)
        canvas.rotate(layer.rotationDeg)

        when (layer) {
            is ImageLayer -> drawImageLayer(canvas, layer, isExportMode)
            is ShapeLayer -> drawShapeLayer(canvas, layer)
            is TextLayer -> drawTextLayer(canvas, layer, fontRepository, fontsById)
        }

        canvas.restore()
    }

    private fun drawImageLayer(canvas: Canvas, layer: ImageLayer, isExportMode: Boolean) {
        val bitmap = if (isExportMode) {
            getExportBitmap(layer.imagePath)
        } else {
            getPreviewBitmap(layer.imagePath)
        } ?: return

        val crop = layer.cropRect
        val srcLeft = (crop.left.coerceIn(0f, 0.99f) * bitmap.width).toInt()
        val srcTop = (crop.top.coerceIn(0f, 0.99f) * bitmap.height).toInt()
        val srcRight = (crop.right.coerceIn(crop.left + 0.01f, 1f) * bitmap.width).toInt()
        val srcBottom = (crop.bottom.coerceIn(crop.top + 0.01f, 1f) * bitmap.height).toInt()
        val srcRect = Rect(srcLeft, srcTop, max(srcLeft + 1, srcRight), max(srcTop + 1, srcBottom))

        val halfW = layer.widthPx / 2f
        val halfH = layer.heightPx / 2f
        val dstRect = RectF(-halfW, -halfH, halfW, halfH)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            alpha = (layer.opacity.coerceIn(0f, 1f) * 255).toInt()
        }
        canvas.drawBitmap(bitmap, srcRect, dstRect, paint)
    }

    private fun drawShapeLayer(canvas: Canvas, layer: ShapeLayer) {
        val alphaInt = (layer.opacity.coerceIn(0f, 1f) * 255).toInt()
        if (alphaInt <= 0) return

        val needsSaveLayer = layer.opacity < 0.999f && layer.fillEnabled && layer.strokeEnabled
        if (needsSaveLayer) {
            val pad = layer.strokeWidthPx + 4f
            canvas.saveLayerAlpha(
                -layer.widthPx / 2f - pad,
                -layer.heightPx / 2f - pad,
                layer.widthPx / 2f + pad,
                layer.heightPx / 2f + pad,
                alphaInt
            )
        }

        val effectiveAlpha = if (needsSaveLayer) 255 else alphaInt
        val halfW = layer.widthPx / 2f
        val halfH = layer.heightPx / 2f
        val rect = RectF(-halfW, -halfH, halfW, halfH)

        when (layer.shapeKind) {
            ShapeKind.LINE -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = layer.strokeWidthPx.coerceAtLeast(1f)
                    strokeCap = Paint.Cap.ROUND
                    color = applyAlpha(layer.strokeColorArgb, effectiveAlpha)
                }
                canvas.drawLine(-halfW, 0f, halfW, 0f, paint)
            }
            ShapeKind.RECTANGLE -> {
                if (layer.fillEnabled) {
                    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                        color = applyAlpha(layer.fillColorArgb, effectiveAlpha)
                    }
                    canvas.drawRoundRect(rect, layer.cornerRadiusPx, layer.cornerRadiusPx, fillPaint)
                }
                if (layer.strokeEnabled && layer.strokeWidthPx > 0f) {
                    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = layer.strokeWidthPx
                        strokeJoin = Paint.Join.ROUND
                        color = applyAlpha(layer.strokeColorArgb, effectiveAlpha)
                    }
                    canvas.drawRoundRect(rect, layer.cornerRadiusPx, layer.cornerRadiusPx, strokePaint)
                }
            }
            ShapeKind.CIRCLE -> {
                if (layer.fillEnabled) {
                    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                        color = applyAlpha(layer.fillColorArgb, effectiveAlpha)
                    }
                    canvas.drawOval(rect, fillPaint)
                }
                if (layer.strokeEnabled && layer.strokeWidthPx > 0f) {
                    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = layer.strokeWidthPx
                        color = applyAlpha(layer.strokeColorArgb, effectiveAlpha)
                    }
                    canvas.drawOval(rect, strokePaint)
                }
            }
        }

        if (needsSaveLayer) {
            canvas.restore()
        }
    }

    private fun drawTextLayer(
        canvas: Canvas,
        layer: TextLayer,
        fontRepository: FontRepository,
        fontsById: Map<String, FontItem>
    ) {
        if (layer.text.isEmpty()) return
        val typeface = fontRepository.resolveTypeface(layer.fontId)
        val availableFeatures = fontsById[layer.fontId]?.metadata?.features ?: emptyList()

        val (staticLayout, _) = ArabicTypographyEngine.shapeAndMeasure(
            text = layer.text,
            typeface = typeface,
            textSizePx = layer.fontSizePx,
            maxWidthPx = layer.boxWidthPx.toInt().coerceAtLeast(100),
            featureValues = layer.openTypeFeatures,
            availableFeatures = availableFeatures,
            directionMode = layer.directionMode,
            alignMode = layer.alignMode,
            lineHeightMultiplier = layer.lineHeightMultiplier,
            letterSpacingEm = layer.letterSpacingEm,
            preserveArabicJoining = layer.preserveArabicJoining
        )

        val layoutW = staticLayout.width.toFloat()
        val layoutH = staticLayout.height.toFloat()
        val alphaInt = (layer.opacity.coerceIn(0f, 1f) * 255).toInt()
        if (alphaInt <= 0) return

        val shadowPad = if (layer.shadow.enabled) {
            layer.shadow.blurRadiusPx * 2f + max(kotlin.math.abs(layer.shadow.offsetXPx), kotlin.math.abs(layer.shadow.offsetYPx)) + 16f
        } else 16f
        val strokePad = if (layer.stroke.enabled) layer.stroke.widthPx + 8f else 8f
        val pad = shadowPad + strokePad

        canvas.save()
        canvas.translate(-layoutW / 2f, -layoutH / 2f)

        // Use saveLayerAlpha so stroke + fill composite crisply without bleed at < 1.0 opacity
        val useLayer = layer.opacity < 0.999f || (layer.stroke.enabled && layer.stroke.widthPx > 0f)
        if (useLayer) {
            canvas.saveLayerAlpha(-pad, -pad, layoutW + pad, layoutH + pad, alphaInt)
        }

        val paint = staticLayout.paint

        // 1. Stroke pass (underneath fill)
        if (layer.stroke.enabled && layer.stroke.widthPx > 0f) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = layer.stroke.widthPx
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = layer.stroke.colorArgb
            if (layer.shadow.enabled) {
                paint.setShadowLayer(
                    layer.shadow.blurRadiusPx.coerceAtLeast(0.5f),
                    layer.shadow.offsetXPx,
                    layer.shadow.offsetYPx,
                    layer.shadow.colorArgb
                )
            } else {
                paint.clearShadowLayer()
            }
            staticLayout.draw(canvas)
        }

        // 2. Fill pass (on top)
        paint.style = Paint.Style.FILL
        paint.strokeWidth = 0f
        paint.color = layer.colorArgb
        if (layer.shadow.enabled && !layer.stroke.enabled) {
            paint.setShadowLayer(
                layer.shadow.blurRadiusPx.coerceAtLeast(0.5f),
                layer.shadow.offsetXPx,
                layer.shadow.offsetYPx,
                layer.shadow.colorArgb
            )
        } else {
            paint.clearShadowLayer()
        }
        staticLayout.draw(canvas)

        if (useLayer) {
            canvas.restore()
        }
        canvas.restore()
    }

    fun getLayerLocalSize(
        layer: CanvasLayer,
        fontRepository: FontRepository,
        fontsById: Map<String, FontItem>
    ): Pair<Float, Float> {
        return when (layer) {
            is ImageLayer -> layer.widthPx to layer.heightPx
            is ShapeLayer -> when (layer.shapeKind) {
                ShapeKind.LINE -> layer.widthPx to max(48f, layer.strokeWidthPx + 24f)
                else -> layer.widthPx to layer.heightPx
            }
            is TextLayer -> {
                val typeface = fontRepository.resolveTypeface(layer.fontId)
                val availableFeatures = fontsById[layer.fontId]?.metadata?.features ?: emptyList()
                val (layout, _) = ArabicTypographyEngine.shapeAndMeasure(
                    text = layer.text.ifBlank { " " },
                    typeface = typeface,
                    textSizePx = layer.fontSizePx,
                    maxWidthPx = layer.boxWidthPx.toInt().coerceAtLeast(100),
                    featureValues = layer.openTypeFeatures,
                    availableFeatures = availableFeatures,
                    directionMode = layer.directionMode,
                    alignMode = layer.alignMode,
                    lineHeightMultiplier = layer.lineHeightMultiplier,
                    letterSpacingEm = layer.letterSpacingEm,
                    preserveArabicJoining = layer.preserveArabicJoining
                )
                layout.width.toFloat().coerceAtLeast(64f) to layout.height.toFloat().coerceAtLeast(48f)
            }
        }
    }

    fun hitTestLayer(
        doc: ProjectDocument,
        canvasX: Float,
        canvasY: Float,
        fontRepository: FontRepository,
        fontsById: Map<String, FontItem>
    ): CanvasLayer? {
        for (layer in doc.layers.asReversed()) {
            if (!layer.isVisible) continue
            val (w, h) = getLayerLocalSize(layer, fontRepository, fontsById)
            val dx = canvasX - layer.xPx
            val dy = canvasY - layer.yPx
            val rad = Math.toRadians(-layer.rotationDeg.toDouble())
            val localX = (dx * cos(rad) - dy * sin(rad)).toFloat()
            val localY = (dx * sin(rad) + dy * cos(rad)).toFloat()
            val pad = 28f
            if (kotlin.math.abs(localX) <= w / 2f + pad && kotlin.math.abs(localY) <= h / 2f + pad) {
                return layer
            }
        }
        return null
    }

    private fun applyAlpha(colorArgb: Int, alpha0To255: Int): Int {
        val baseAlpha = Color.alpha(colorArgb)
        val combined = (baseAlpha * alpha0To255) / 255
        return Color.argb(combined, Color.red(colorArgb), Color.green(colorArgb), Color.blue(colorArgb))
    }
}
