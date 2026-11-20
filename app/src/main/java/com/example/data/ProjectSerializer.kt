package com.example.data

import com.example.model.CanvasLayer
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
import com.example.typography.TextAlignMode
import com.example.typography.TextDirectionMode
import org.json.JSONArray
import org.json.JSONObject

object ProjectSerializer {

    fun toJson(doc: ProjectDocument): String {
        val root = JSONObject().apply {
            put("id", doc.id)
            put("title", doc.title)
            put("canvasWidthPx", doc.canvasWidthPx)
            put("canvasHeightPx", doc.canvasHeightPx)
            put("backgroundColorArgb", doc.backgroundColorArgb)
            put("isTransparentBackground", doc.isTransparentBackground)
            put("updatedAtEpochMs", doc.updatedAtEpochMs)
            val arr = JSONArray()
            for (layer in doc.layers) {
                arr.put(layerToJson(layer))
            }
            put("layers", arr)
        }
        return root.toString()
    }

    fun fromJson(json: String): ProjectDocument {
        val root = JSONObject(json)
        val layersArr = root.optJSONArray("layers") ?: JSONArray()
        val layers = ArrayList<CanvasLayer>(layersArr.length())
        for (i in 0 until layersArr.length()) {
            val obj = layersArr.optJSONObject(i) ?: continue
            layerFromJson(obj)?.let { layers.add(it) }
        }
        return ProjectDocument(
            id = root.getString("id"),
            title = root.optString("title", "Untitled Frame"),
            canvasWidthPx = root.optInt("canvasWidthPx", 1080),
            canvasHeightPx = root.optInt("canvasHeightPx", 1350),
            backgroundColorArgb = root.optInt("backgroundColorArgb", 0xFF14161D.toInt()),
            isTransparentBackground = root.optBoolean("isTransparentBackground", false),
            layers = layers,
            updatedAtEpochMs = root.optLong("updatedAtEpochMs", System.currentTimeMillis())
        )
    }

    private fun layerToJson(layer: CanvasLayer): JSONObject {
        val obj = JSONObject().apply {
            put("id", layer.id)
            put("name", layer.name)
            put("xPx", layer.xPx.toDouble())
            put("yPx", layer.yPx.toDouble())
            put("rotationDeg", layer.rotationDeg.toDouble())
            put("scaleX", layer.scaleX.toDouble())
            put("scaleY", layer.scaleY.toDouble())
            put("opacity", layer.opacity.toDouble())
            put("blendMode", layer.blendMode.name)
            put("isVisible", layer.isVisible)
            put("isLocked", layer.isLocked)
        }
        when (layer) {
            is ImageLayer -> {
                obj.put("type", "image")
                obj.put("imagePath", layer.imagePath)
                obj.put("widthPx", layer.widthPx.toDouble())
                obj.put("heightPx", layer.heightPx.toDouble())
                obj.put("naturalWidthPx", layer.naturalWidthPx)
                obj.put("naturalHeightPx", layer.naturalHeightPx)
                obj.put("crop", JSONObject().apply {
                    put("left", layer.cropRect.left.toDouble())
                    put("top", layer.cropRect.top.toDouble())
                    put("right", layer.cropRect.right.toDouble())
                    put("bottom", layer.cropRect.bottom.toDouble())
                })
                obj.put("eraseMaskStrokes", strokesToJson(layer.eraseMaskStrokes))
            }
            is TextLayer -> {
                obj.put("type", "text")
                obj.put("text", layer.text)
                obj.put("fontId", layer.fontId)
                obj.put("fontSizePx", layer.fontSizePx.toDouble())
                obj.put("boxWidthPx", layer.boxWidthPx.toDouble())
                obj.put("colorArgb", layer.colorArgb)
                obj.put("alignMode", layer.alignMode.name)
                obj.put("directionMode", layer.directionMode.name)
                obj.put("lineHeightMultiplier", layer.lineHeightMultiplier.toDouble())
                obj.put("letterSpacingEm", layer.letterSpacingEm.toDouble())
                obj.put("preserveArabicJoining", layer.preserveArabicJoining)
                obj.put("stroke", JSONObject().apply {
                    put("enabled", layer.stroke.enabled)
                    put("colorArgb", layer.stroke.colorArgb)
                    put("widthPx", layer.stroke.widthPx.toDouble())
                })
                obj.put("shadow", JSONObject().apply {
                    put("enabled", layer.shadow.enabled)
                    put("colorArgb", layer.shadow.colorArgb)
                    put("blurRadiusPx", layer.shadow.blurRadiusPx.toDouble())
                    put("offsetXPx", layer.shadow.offsetXPx.toDouble())
                    put("offsetYPx", layer.shadow.offsetYPx.toDouble())
                })
                val otObj = JSONObject()
                layer.openTypeFeatures.forEach { (k, v) -> otObj.put(k, v) }
                obj.put("openTypeFeatures", otObj)
                val spansArr = JSONArray()
                for (span in layer.spans) {
                    spansArr.put(JSONObject().apply {
                        put("start", span.start)
                        put("end", span.end)
                        span.fontId?.let { put("fontId", it) }
                        span.fontSizePx?.let { put("fontSizePx", it.toDouble()) }
                        span.colorArgb?.let { put("colorArgb", it) }
                        span.isBold?.let { put("isBold", it) }
                        span.isItalic?.let { put("isItalic", it) }
                        span.isUnderline?.let { put("isUnderline", it) }
                    })
                }
                obj.put("spans", spansArr)
            }
            is ShapeLayer -> {
                obj.put("type", "shape")
                obj.put("shapeKind", layer.shapeKind.name)
                obj.put("widthPx", layer.widthPx.toDouble())
                obj.put("heightPx", layer.heightPx.toDouble())
                obj.put("strokeWidthPx", layer.strokeWidthPx.toDouble())
                obj.put("cornerRadiusPx", layer.cornerRadiusPx.toDouble())
                obj.put("fillEnabled", layer.fillEnabled)
                obj.put("fillColorArgb", layer.fillColorArgb)
                obj.put("strokeEnabled", layer.strokeEnabled)
                obj.put("strokeColorArgb", layer.strokeColorArgb)
            }
            is PaintLayer -> {
                obj.put("type", "paint")
                obj.put("canvasWidthPx", layer.canvasWidthPx)
                obj.put("canvasHeightPx", layer.canvasHeightPx)
                obj.put("strokes", strokesToJson(layer.strokes))
            }
        }
        return obj
    }

    private fun strokesToJson(strokes: List<PaintStroke>): JSONArray {
        val arr = JSONArray()
        for (s in strokes) {
            val ptsArr = JSONArray()
            for (pt in s.points) {
                ptsArr.put(JSONObject().apply {
                    put("x", pt.x.toDouble())
                    put("y", pt.y.toDouble())
                })
            }
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("colorArgb", s.colorArgb)
                put("sizePx", s.sizePx.toDouble())
                put("hardness", s.hardness.toDouble())
                put("opacity", s.opacity.toDouble())
                put("flow", s.flow.toDouble())
                put("isEraser", s.isEraser)
                put("points", ptsArr)
            })
        }
        return arr
    }

    private fun strokesFromJson(arr: JSONArray?): List<PaintStroke> {
        if (arr == null) return emptyList()
        val list = ArrayList<PaintStroke>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val ptsArr = o.optJSONArray("points") ?: JSONArray()
            val pts = ArrayList<StrokePoint>(ptsArr.length())
            for (j in 0 until ptsArr.length()) {
                val po = ptsArr.optJSONObject(j) ?: continue
                pts.add(StrokePoint(po.optDouble("x", 0.0).toFloat(), po.optDouble("y", 0.0).toFloat()))
            }
            if (pts.isNotEmpty()) {
                list.add(
                    PaintStroke(
                        id = o.optString("id", java.util.UUID.randomUUID().toString()),
                        points = pts,
                        colorArgb = o.optInt("colorArgb", 0xFFFF5A36.toInt()),
                        sizePx = o.optDouble("sizePx", 36.0).toFloat(),
                        hardness = o.optDouble("hardness", 0.85).toFloat(),
                        opacity = o.optDouble("opacity", 1.0).toFloat(),
                        flow = o.optDouble("flow", 1.0).toFloat(),
                        isEraser = o.optBoolean("isEraser", false)
                    )
                )
            }
        }
        return list
    }

    private fun layerFromJson(obj: JSONObject): CanvasLayer? {
        val id = obj.optString("id")
        val name = obj.optString("name", "Layer")
        val xPx = obj.optDouble("xPx", 540.0).toFloat()
        val yPx = obj.optDouble("yPx", 540.0).toFloat()
        val rotationDeg = obj.optDouble("rotationDeg", 0.0).toFloat()
        val scaleX = obj.optDouble("scaleX", 1.0).toFloat()
        val scaleY = obj.optDouble("scaleY", 1.0).toFloat()
        val opacity = obj.optDouble("opacity", 1.0).toFloat()
        val blendMode = runCatching {
            LayerBlendMode.valueOf(obj.optString("blendMode", "NORMAL"))
        }.getOrDefault(LayerBlendMode.NORMAL)
        val isVisible = obj.optBoolean("isVisible", true)
        val isLocked = obj.optBoolean("isLocked", false)

        return when (obj.optString("type")) {
            "image" -> {
                val cropObj = obj.optJSONObject("crop")
                val crop = if (cropObj != null) {
                    CropRect(
                        left = cropObj.optDouble("left", 0.0).toFloat(),
                        top = cropObj.optDouble("top", 0.0).toFloat(),
                        right = cropObj.optDouble("right", 1.0).toFloat(),
                        bottom = cropObj.optDouble("bottom", 1.0).toFloat()
                    )
                } else CropRect()
                ImageLayer(
                    id = id,
                    name = name,
                    xPx = xPx,
                    yPx = yPx,
                    rotationDeg = rotationDeg,
                    scaleX = scaleX,
                    scaleY = scaleY,
                    opacity = opacity,
                    blendMode = blendMode,
                    isVisible = isVisible,
                    isLocked = isLocked,
                    imagePath = obj.getString("imagePath"),
                    widthPx = obj.optDouble("widthPx", 600.0).toFloat(),
                    heightPx = obj.optDouble("heightPx", 600.0).toFloat(),
                    naturalWidthPx = obj.optInt("naturalWidthPx", 600),
                    naturalHeightPx = obj.optInt("naturalHeightPx", 600),
                    cropRect = crop,
                    eraseMaskStrokes = strokesFromJson(obj.optJSONArray("eraseMaskStrokes"))
                )
            }
            "text" -> {
                val strokeObj = obj.optJSONObject("stroke")
                val stroke = if (strokeObj != null) {
                    StrokeStyle(
                        enabled = strokeObj.optBoolean("enabled", false),
                        colorArgb = strokeObj.optInt("colorArgb", 0xFF000000.toInt()),
                        widthPx = strokeObj.optDouble("widthPx", 6.0).toFloat()
                    )
                } else StrokeStyle()

                val shadowObj = obj.optJSONObject("shadow")
                val shadow = if (shadowObj != null) {
                    ShadowStyle(
                        enabled = shadowObj.optBoolean("enabled", false),
                        colorArgb = shadowObj.optInt("colorArgb", 0x99000000.toInt()),
                        blurRadiusPx = shadowObj.optDouble("blurRadiusPx", 14.0).toFloat(),
                        offsetXPx = shadowObj.optDouble("offsetXPx", 0.0).toFloat(),
                        offsetYPx = shadowObj.optDouble("offsetYPx", 8.0).toFloat()
                    )
                } else ShadowStyle()

                val otMap = linkedMapOf<String, Int>()
                val otObj = obj.optJSONObject("openTypeFeatures")
                if (otObj != null) {
                    val keys = otObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        otMap[k] = otObj.optInt(k, 0)
                    }
                }

                val spansArr = obj.optJSONArray("spans")
                val spans = mutableListOf<TextStyleSpan>()
                if (spansArr != null) {
                    for (i in 0 until spansArr.length()) {
                        val so = spansArr.optJSONObject(i) ?: continue
                        spans.add(
                            TextStyleSpan(
                                start = so.optInt("start", 0),
                                end = so.optInt("end", 0),
                                fontId = if (so.has("fontId")) so.optString("fontId") else null,
                                fontSizePx = if (so.has("fontSizePx")) so.optDouble("fontSizePx").toFloat() else null,
                                colorArgb = if (so.has("colorArgb")) so.optInt("colorArgb") else null,
                                isBold = if (so.has("isBold")) so.optBoolean("isBold") else null,
                                isItalic = if (so.has("isItalic")) so.optBoolean("isItalic") else null,
                                isUnderline = if (so.has("isUnderline")) so.optBoolean("isUnderline") else null
                            )
                        )
                    }
                }

                TextLayer(
                    id = id,
                    name = name,
                    xPx = xPx,
                    yPx = yPx,
                    rotationDeg = rotationDeg,
                    scaleX = scaleX,
                    scaleY = scaleY,
                    opacity = opacity,
                    blendMode = blendMode,
                    isVisible = isVisible,
                    isLocked = isLocked,
                    text = obj.optString("text", ""),
                    fontId = obj.optString("fontId", "asset:IBMPlexSansArabic-Regular.otf"),
                    fontSizePx = obj.optDouble("fontSizePx", 96.0).toFloat(),
                    boxWidthPx = obj.optDouble("boxWidthPx", 860.0).toFloat(),
                    colorArgb = obj.optInt("colorArgb", 0xFFFFFFFF.toInt()),
                    alignMode = runCatching {
                        TextAlignMode.valueOf(obj.optString("alignMode", "CENTER"))
                    }.getOrDefault(TextAlignMode.CENTER),
                    directionMode = runCatching {
                        TextDirectionMode.valueOf(obj.optString("directionMode", "AUTO"))
                    }.getOrDefault(TextDirectionMode.AUTO),
                    lineHeightMultiplier = obj.optDouble("lineHeightMultiplier", 1.2).toFloat(),
                    letterSpacingEm = obj.optDouble("letterSpacingEm", 0.0).toFloat(),
                    preserveArabicJoining = obj.optBoolean("preserveArabicJoining", true),
                    stroke = stroke,
                    shadow = shadow,
                    openTypeFeatures = otMap,
                    spans = spans
                )
            }
            "shape" -> {
                ShapeLayer(
                    id = id,
                    name = name,
                    xPx = xPx,
                    yPx = yPx,
                    rotationDeg = rotationDeg,
                    scaleX = scaleX,
                    scaleY = scaleY,
                    opacity = opacity,
                    blendMode = blendMode,
                    isVisible = isVisible,
                    isLocked = isLocked,
                    shapeKind = runCatching {
                        ShapeKind.valueOf(obj.optString("shapeKind", "RECTANGLE"))
                    }.getOrDefault(ShapeKind.RECTANGLE),
                    widthPx = obj.optDouble("widthPx", 360.0).toFloat(),
                    heightPx = obj.optDouble("heightPx", 220.0).toFloat(),
                    strokeWidthPx = obj.optDouble("strokeWidthPx", 8.0).toFloat(),
                    cornerRadiusPx = obj.optDouble("cornerRadiusPx", 24.0).toFloat(),
                    fillEnabled = obj.optBoolean("fillEnabled", false),
                    fillColorArgb = obj.optInt("fillColorArgb", 0x33FF5A36),
                    strokeEnabled = obj.optBoolean("strokeEnabled", true),
                    strokeColorArgb = obj.optInt("strokeColorArgb", 0xFFFF5A36.toInt())
                )
            }
            "paint" -> {
                PaintLayer(
                    id = id,
                    name = name,
                    xPx = xPx,
                    yPx = yPx,
                    rotationDeg = rotationDeg,
                    scaleX = scaleX,
                    scaleY = scaleY,
                    opacity = opacity,
                    blendMode = blendMode,
                    isVisible = isVisible,
                    isLocked = isLocked,
                    canvasWidthPx = obj.optInt("canvasWidthPx", 1080),
                    canvasHeightPx = obj.optInt("canvasHeightPx", 1350),
                    strokes = strokesFromJson(obj.optJSONArray("strokes"))
                )
            }
            else -> null
        }
    }
}
