package com.example.model

import com.example.typography.FontMetadata
import com.example.typography.TextAlignMode
import com.example.typography.TextDirectionMode
import java.util.UUID

enum class ShapeKind {
    LINE,
    RECTANGLE,
    CIRCLE
}

enum class LayerBlendMode(val label: String) {
    NORMAL("Normal"),
    MULTIPLY("Multiply"),
    SCREEN("Screen"),
    OVERLAY("Overlay"),
    DARKEN("Darken"),
    LIGHTEN("Lighten"),
    ADD("Add"),
    COLOR_DODGE("Dodge"),
    COLOR_BURN("Burn"),
    SOFT_LIGHT("Soft Light"),
    DIFFERENCE("Difference")
}

data class CropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
) {
    val isFull: Boolean
        get() = left <= 0.001f && top <= 0.001f && right >= 0.999f && bottom >= 0.999f
}

data class StrokeStyle(
    val enabled: Boolean = false,
    val colorArgb: Int = 0xFF000000.toInt(),
    val widthPx: Float = 6f
)

data class ShadowStyle(
    val enabled: Boolean = false,
    val colorArgb: Int = 0x99000000.toInt(),
    val blurRadiusPx: Float = 14f,
    val offsetXPx: Float = 0f,
    val offsetYPx: Float = 8f
)

data class TextStyleSpan(
    val start: Int,
    val end: Int,
    val fontId: String? = null,
    val fontSizePx: Float? = null,
    val colorArgb: Int? = null,
    val isBold: Boolean? = null,
    val isItalic: Boolean? = null,
    val isUnderline: Boolean? = null
)

data class StrokePoint(
    val x: Float,
    val y: Float
)

data class PaintStroke(
    val id: String = UUID.randomUUID().toString(),
    val points: List<StrokePoint>,
    val colorArgb: Int = 0xFFFF5A36.toInt(),
    val sizePx: Float = 36f,
    val hardness: Float = 0.85f,
    val opacity: Float = 1f,
    val flow: Float = 1f,
    val isEraser: Boolean = false
)

sealed interface CanvasLayer {
    val id: String
    val name: String
    val xPx: Float
    val yPx: Float
    val rotationDeg: Float
    val scaleX: Float
    val scaleY: Float
    val opacity: Float
    val blendMode: LayerBlendMode
    val isVisible: Boolean
    val isLocked: Boolean

    fun withBaseTransform(
        xPx: Float = this.xPx,
        yPx: Float = this.yPx,
        rotationDeg: Float = this.rotationDeg,
        scaleX: Float = this.scaleX,
        scaleY: Float = this.scaleY,
        opacity: Float = this.opacity,
        blendMode: LayerBlendMode = this.blendMode,
        isVisible: Boolean = this.isVisible,
        isLocked: Boolean = this.isLocked,
        name: String = this.name
    ): CanvasLayer
}

data class ImageLayer(
    override val id: String = UUID.randomUUID().toString(),
    override val name: String = "Image",
    override val xPx: Float,
    override val yPx: Float,
    override val rotationDeg: Float = 0f,
    override val scaleX: Float = 1f,
    override val scaleY: Float = 1f,
    override val opacity: Float = 1f,
    override val blendMode: LayerBlendMode = LayerBlendMode.NORMAL,
    override val isVisible: Boolean = true,
    override val isLocked: Boolean = false,
    val imagePath: String,
    val widthPx: Float,
    val heightPx: Float,
    val naturalWidthPx: Int,
    val naturalHeightPx: Int,
    val cropRect: CropRect = CropRect(),
    val eraseMaskStrokes: List<PaintStroke> = emptyList()
) : CanvasLayer {
    override fun withBaseTransform(
        xPx: Float,
        yPx: Float,
        rotationDeg: Float,
        scaleX: Float,
        scaleY: Float,
        opacity: Float,
        blendMode: LayerBlendMode,
        isVisible: Boolean,
        isLocked: Boolean,
        name: String
    ): ImageLayer = copy(
        xPx = xPx,
        yPx = yPx,
        rotationDeg = rotationDeg,
        scaleX = scaleX,
        scaleY = scaleY,
        opacity = opacity,
        blendMode = blendMode,
        isVisible = isVisible,
        isLocked = isLocked,
        name = name
    )
}

data class TextLayer(
    override val id: String = UUID.randomUUID().toString(),
    override val name: String = "Text",
    override val xPx: Float,
    override val yPx: Float,
    override val rotationDeg: Float = 0f,
    override val scaleX: Float = 1f,
    override val scaleY: Float = 1f,
    override val opacity: Float = 1f,
    override val blendMode: LayerBlendMode = LayerBlendMode.NORMAL,
    override val isVisible: Boolean = true,
    override val isLocked: Boolean = false,
    val text: String = "تصميم HTYPE",
    val fontId: String = "asset:IBMPlexSansArabic-Regular.otf",
    val fontSizePx: Float = 96f,
    val boxWidthPx: Float = 860f,
    val colorArgb: Int = 0xFFFFFFFF.toInt(),
    val alignMode: TextAlignMode = TextAlignMode.CENTER,
    val directionMode: TextDirectionMode = TextDirectionMode.AUTO,
    val lineHeightMultiplier: Float = 1.2f,
    val letterSpacingEm: Float = 0f,
    val preserveArabicJoining: Boolean = true,
    val stroke: StrokeStyle = StrokeStyle(),
    val shadow: ShadowStyle = ShadowStyle(),
    val openTypeFeatures: Map<String, Int> = mapOf("liga" to 1, "calt" to 1, "kern" to 1),
    val spans: List<TextStyleSpan> = emptyList()
) : CanvasLayer {
    override fun withBaseTransform(
        xPx: Float,
        yPx: Float,
        rotationDeg: Float,
        scaleX: Float,
        scaleY: Float,
        opacity: Float,
        blendMode: LayerBlendMode,
        isVisible: Boolean,
        isLocked: Boolean,
        name: String
    ): TextLayer = copy(
        xPx = xPx,
        yPx = yPx,
        rotationDeg = rotationDeg,
        scaleX = scaleX,
        scaleY = scaleY,
        opacity = opacity,
        blendMode = blendMode,
        isVisible = isVisible,
        isLocked = isLocked,
        name = name
    )
}

data class ShapeLayer(
    override val id: String = UUID.randomUUID().toString(),
    override val name: String = "Shape",
    override val xPx: Float,
    override val yPx: Float,
    override val rotationDeg: Float = 0f,
    override val scaleX: Float = 1f,
    override val scaleY: Float = 1f,
    override val opacity: Float = 1f,
    override val blendMode: LayerBlendMode = LayerBlendMode.NORMAL,
    override val isVisible: Boolean = true,
    override val isLocked: Boolean = false,
    val shapeKind: ShapeKind = ShapeKind.RECTANGLE,
    val widthPx: Float = 360f,
    val heightPx: Float = 220f,
    val strokeWidthPx: Float = 8f,
    val cornerRadiusPx: Float = 24f,
    val fillEnabled: Boolean = false,
    val fillColorArgb: Int = 0x33FF5A36,
    val strokeEnabled: Boolean = true,
    val strokeColorArgb: Int = 0xFFFF5A36.toInt()
) : CanvasLayer {
    override fun withBaseTransform(
        xPx: Float,
        yPx: Float,
        rotationDeg: Float,
        scaleX: Float,
        scaleY: Float,
        opacity: Float,
        blendMode: LayerBlendMode,
        isVisible: Boolean,
        isLocked: Boolean,
        name: String
    ): ShapeLayer = copy(
        xPx = xPx,
        yPx = yPx,
        rotationDeg = rotationDeg,
        scaleX = scaleX,
        scaleY = scaleY,
        opacity = opacity,
        blendMode = blendMode,
        isVisible = isVisible,
        isLocked = isLocked,
        name = name
    )
}

data class PaintLayer(
    override val id: String = UUID.randomUUID().toString(),
    override val name: String = "Paint Layer",
    override val xPx: Float = 0f,
    override val yPx: Float = 0f,
    override val rotationDeg: Float = 0f,
    override val scaleX: Float = 1f,
    override val scaleY: Float = 1f,
    override val opacity: Float = 1f,
    override val blendMode: LayerBlendMode = LayerBlendMode.NORMAL,
    override val isVisible: Boolean = true,
    override val isLocked: Boolean = false,
    val canvasWidthPx: Int = 1080,
    val canvasHeightPx: Int = 1350,
    val strokes: List<PaintStroke> = emptyList()
) : CanvasLayer {
    override fun withBaseTransform(
        xPx: Float,
        yPx: Float,
        rotationDeg: Float,
        scaleX: Float,
        scaleY: Float,
        opacity: Float,
        blendMode: LayerBlendMode,
        isVisible: Boolean,
        isLocked: Boolean,
        name: String
    ): PaintLayer = copy(
        xPx = xPx,
        yPx = yPx,
        rotationDeg = rotationDeg,
        scaleX = scaleX,
        scaleY = scaleY,
        opacity = opacity,
        blendMode = blendMode,
        isVisible = isVisible,
        isLocked = isLocked,
        name = name
    )
}

data class ProjectDocument(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Untitled Frame",
    val canvasWidthPx: Int = 1080,
    val canvasHeightPx: Int = 1350,
    val backgroundColorArgb: Int = 0xFF14161D.toInt(),
    val isTransparentBackground: Boolean = false,
    val layers: List<CanvasLayer> = emptyList(),
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

data class FontItem(
    val id: String,
    val displayName: String,
    val fileName: String,
    val isBundled: Boolean,
    val assetPath: String? = null,
    val filePath: String? = null,
    val metadata: FontMetadata
)

data class FontFamilyGroup(
    val familyKey: String,
    val familyName: String,
    val weights: List<FontItem>,
    val isFavorite: Boolean,
    val supportsArabic: Boolean
)

data class CanvasPreset(
    val label: String,
    val ratioLabel: String,
    val widthPx: Int,
    val heightPx: Int
)

val DefaultCanvasPresets = listOf(
    CanvasPreset("Square Post", "1:1", 1080, 1080),
    CanvasPreset("Portrait", "4:5", 1080, 1350),
    CanvasPreset("Story / Reel", "9:16", 1080, 1920),
    CanvasPreset("Widescreen", "16:9", 1920, 1080),
    CanvasPreset("HD Poster", "3:4", 1200, 1600)
)
