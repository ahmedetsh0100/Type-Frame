package com.example.data

import com.example.model.CanvasLayer
import com.example.model.CropRect
import com.example.model.ImageLayer
import com.example.model.ProjectDocument
import com.example.model.ShadowStyle
import com.example.model.ShapeKind
import com.example.model.ShapeLayer
import com.example.model.StrokeStyle
import com.example.model.TextLayer
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
            put("opacity", layer.opacity.toDouble())
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
        }
        return obj
    }

    private fun layerFromJson(obj: JSONObject): CanvasLayer? {
        val id = obj.optString("id")
        val name = obj.optString("name", "Layer")
        val xPx = obj.optDouble("xPx", 540.0).toFloat()
        val yPx = obj.optDouble("yPx", 540.0).toFloat()
        val rotationDeg = obj.optDouble("rotationDeg", 0.0).toFloat()
        val opacity = obj.optDouble("opacity", 1.0).toFloat()
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
                    opacity = opacity,
                    isVisible = isVisible,
                    isLocked = isLocked,
                    imagePath = obj.getString("imagePath"),
                    widthPx = obj.optDouble("widthPx", 600.0).toFloat(),
                    heightPx = obj.optDouble("heightPx", 600.0).toFloat(),
                    naturalWidthPx = obj.optInt("naturalWidthPx", 600),
                    naturalHeightPx = obj.optInt("naturalHeightPx", 600),
                    cropRect = crop
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

                TextLayer(
                    id = id,
                    name = name,
                    xPx = xPx,
                    yPx = yPx,
                    rotationDeg = rotationDeg,
                    opacity = opacity,
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
                    openTypeFeatures = otMap
                )
            }
            "shape" -> {
                ShapeLayer(
                    id = id,
                    name = name,
                    xPx = xPx,
                    yPx = yPx,
                    rotationDeg = rotationDeg,
                    opacity = opacity,
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
            else -> null
        }
    }
}
