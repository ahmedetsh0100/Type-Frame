package com.example.render

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.FontRepository
import com.example.model.FontItem
import com.example.model.ProjectDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

enum class ExportMode {
    FULL_IMAGE_PNG,
    TRANSPARENT_PNG,
    HIGH_RES_JPEG,
    PDF_DOCUMENT
}

enum class ExportScaleOption(val label: String) {
    X1("1×"),
    X2("2×"),
    X3("3×"),
    MAX("Max")
}

data class ExportResult(
    val shareUri: Uri,
    val galleryUri: Uri?,
    val mimeType: String,
    val widthPx: Int,
    val heightPx: Int,
    val fileSizeBytes: Long,
    val mode: ExportMode
)

object ExportEngine {

    fun computeExportDimensions(
        doc: ProjectDocument,
        scaleOption: ExportScaleOption
    ): Pair<Int, Int> {
        val maxEdgeCap = 4096f
        val requestedScale = when (scaleOption) {
            ExportScaleOption.X1 -> 1f
            ExportScaleOption.X2 -> 2f
            ExportScaleOption.X3 -> 3f
            ExportScaleOption.MAX -> min(
                maxEdgeCap / doc.canvasWidthPx.coerceAtLeast(1),
                maxEdgeCap / doc.canvasHeightPx.coerceAtLeast(1)
            ).coerceAtLeast(1f)
        }
        val safeScale = min(
            requestedScale,
            min(
                maxEdgeCap / doc.canvasWidthPx.coerceAtLeast(1),
                maxEdgeCap / doc.canvasHeightPx.coerceAtLeast(1)
            )
        ).coerceAtLeast(0.5f)
        val w = max(1, (doc.canvasWidthPx * safeScale).toInt())
        val h = max(1, (doc.canvasHeightPx * safeScale).toInt())
        return w to h
    }

    fun renderPreviewThumbnail(
        doc: ProjectDocument,
        fontRepository: FontRepository,
        fontsById: Map<String, FontItem>,
        mode: ExportMode,
        textOnly: Boolean
    ): Bitmap {
        val maxPreview = 420f
        val scale = min(
            maxPreview / doc.canvasWidthPx.coerceAtLeast(1),
            maxPreview / doc.canvasHeightPx.coerceAtLeast(1)
        ).coerceAtMost(1f)
        return CanvasRenderer.renderDocumentToBitmap(
            doc = doc,
            fontRepository = fontRepository,
            fontsById = fontsById,
            forceTransparentBackground = (mode == ExportMode.TRANSPARENT_PNG),
            textOnlyMode = textOnly && mode == ExportMode.TRANSPARENT_PNG,
            scaleFactor = scale,
            isExportMode = false
        )
    }

    suspend fun export(
        context: Context,
        doc: ProjectDocument,
        fontRepository: FontRepository,
        fontsById: Map<String, FontItem>,
        mode: ExportMode,
        scaleOption: ExportScaleOption = ExportScaleOption.X1,
        textOnly: Boolean = false
    ): Result<ExportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val (targetW, targetH) = computeExportDimensions(doc, scaleOption)
            val scaleFactor = targetW.toFloat() / doc.canvasWidthPx.coerceAtLeast(1)
            val safeName = doc.title
                .replace(Regex("[^A-Za-z0-9_\\-]"), "_")
                .ifBlank { "HTYPE" }

            val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val timestamp = System.currentTimeMillis()

            val (cacheFile, mimeType) = when (mode) {
                ExportMode.PDF_DOCUMENT -> {
                    val fileName = "${safeName}_${timestamp}.pdf"
                    val file = File(exportsDir, fileName)
                    writePdfDocument(file, doc, fontRepository, fontsById, targetW, targetH, scaleFactor)
                    file to "application/pdf"
                }
                ExportMode.HIGH_RES_JPEG -> {
                    val fileName = "${safeName}_${timestamp}.jpg"
                    val file = File(exportsDir, fileName)
                    val bitmap = CanvasRenderer.renderDocumentToBitmap(
                        doc = doc,
                        fontRepository = fontRepository,
                        fontsById = fontsById,
                        forceTransparentBackground = false,
                        textOnlyMode = false,
                        scaleFactor = scaleFactor,
                        isExportMode = true
                    )
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 96, out)
                    }
                    bitmap.recycle()
                    file to "image/jpeg"
                }
                ExportMode.FULL_IMAGE_PNG, ExportMode.TRANSPARENT_PNG -> {
                    val suffix = if (mode == ExportMode.TRANSPARENT_PNG) {
                        if (textOnly) "text_transparent" else "transparent"
                    } else "full"
                    val fileName = "${safeName}_${suffix}_${timestamp}.png"
                    val file = File(exportsDir, fileName)
                    val bitmap = CanvasRenderer.renderDocumentToBitmap(
                        doc = doc,
                        fontRepository = fontRepository,
                        fontsById = fontsById,
                        forceTransparentBackground = (mode == ExportMode.TRANSPARENT_PNG),
                        textOnlyMode = textOnly && mode == ExportMode.TRANSPARENT_PNG,
                        scaleFactor = scaleFactor,
                        isExportMode = true
                    )
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    bitmap.recycle()
                    file to "image/png"
                }
            }

            val shareUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                cacheFile
            )

            val galleryUri = if (mode == ExportMode.PDF_DOCUMENT) {
                saveDocumentToMediaStore(context, cacheFile, mimeType)
            } else {
                saveImageToMediaStore(context, cacheFile, mimeType)
            }

            ExportResult(
                shareUri = shareUri,
                galleryUri = galleryUri,
                mimeType = mimeType,
                widthPx = targetW,
                heightPx = targetH,
                fileSizeBytes = cacheFile.length(),
                mode = mode
            )
        }
    }

    suspend fun writeExportToUri(
        context: Context,
        targetUri: Uri,
        doc: ProjectDocument,
        fontRepository: FontRepository,
        fontsById: Map<String, FontItem>,
        mode: ExportMode,
        scaleOption: ExportScaleOption = ExportScaleOption.X1,
        textOnly: Boolean = false
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val (targetW, targetH) = computeExportDimensions(doc, scaleOption)
            val scaleFactor = targetW.toFloat() / doc.canvasWidthPx.coerceAtLeast(1)
            context.contentResolver.openOutputStream(targetUri)?.use { out ->
                if (mode == ExportMode.PDF_DOCUMENT) {
                    writePdfToStream(out, doc, fontRepository, fontsById, targetW, targetH, scaleFactor)
                } else {
                    val bitmap = CanvasRenderer.renderDocumentToBitmap(
                        doc = doc,
                        fontRepository = fontRepository,
                        fontsById = fontsById,
                        forceTransparentBackground = (mode == ExportMode.TRANSPARENT_PNG),
                        textOnlyMode = textOnly && mode == ExportMode.TRANSPARENT_PNG,
                        scaleFactor = scaleFactor,
                        isExportMode = true
                    )
                    val format = if (mode == ExportMode.HIGH_RES_JPEG) {
                        Bitmap.CompressFormat.JPEG
                    } else {
                        Bitmap.CompressFormat.PNG
                    }
                    val quality = if (mode == ExportMode.HIGH_RES_JPEG) 96 else 100
                    bitmap.compress(format, quality, out)
                    bitmap.recycle()
                }
            } ?: error("Unable to open destination file.")
        }
    }

    private fun writePdfDocument(
        destFile: File,
        doc: ProjectDocument,
        fontRepository: FontRepository,
        fontsById: Map<String, FontItem>,
        targetW: Int,
        targetH: Int,
        scaleFactor: Float
    ) {
        FileOutputStream(destFile).use { out ->
            writePdfToStream(out, doc, fontRepository, fontsById, targetW, targetH, scaleFactor)
        }
    }

    private fun writePdfToStream(
        out: java.io.OutputStream,
        doc: ProjectDocument,
        fontRepository: FontRepository,
        fontsById: Map<String, FontItem>,
        targetW: Int,
        targetH: Int,
        scaleFactor: Float
    ) {
        val nativeSuccess = runCatching {
            val pdf = PdfDocument()
            try {
                val pageInfo = PdfDocument.PageInfo.Builder(targetW, targetH, 1).create()
                val page = pdf.startPage(pageInfo)
                page.canvas.scale(scaleFactor, scaleFactor)
                CanvasRenderer.renderDocumentToCanvas(
                    canvas = page.canvas,
                    doc = doc,
                    fontRepository = fontRepository,
                    fontsById = fontsById,
                    forceTransparentBackground = false,
                    textOnlyMode = false,
                    isExportMode = true
                )
                pdf.finishPage(page)
                pdf.writeTo(out)
            } finally {
                runCatching { pdf.close() }
            }
        }.isSuccess

        if (!nativeSuccess) {
            // Fallback valid PDF 1.4 container with embedded high-resolution JPEG stream
            val bitmap = CanvasRenderer.renderDocumentToBitmap(
                doc = doc,
                fontRepository = fontRepository,
                fontsById = fontsById,
                forceTransparentBackground = false,
                textOnlyMode = false,
                scaleFactor = scaleFactor,
                isExportMode = true
            )
            val jpgOut = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 96, jpgOut)
            val jpgBytes = jpgOut.toByteArray()
            bitmap.recycle()
            writeSingleImagePdf14(out, targetW, targetH, jpgBytes)
        }
    }

    private fun writeSingleImagePdf14(
        out: java.io.OutputStream,
        widthPt: Int,
        heightPt: Int,
        jpegBytes: ByteArray
    ) {
        val offsets = mutableListOf<Int>()
        val baos = java.io.ByteArrayOutputStream()
        fun emit(s: String) {
            baos.write(s.toByteArray(Charsets.US_ASCII))
        }

        emit("%PDF-1.4\n")
        offsets.add(baos.size())
        emit("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")

        offsets.add(baos.size())
        emit("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n")

        offsets.add(baos.size())
        emit("3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $widthPt $heightPt] /Resources << /XObject << /Im0 4 0 R >> >> /Contents 5 0 R >>\nendobj\n")

        offsets.add(baos.size())
        emit("4 0 obj\n<< /Type /XObject /Subtype /Image /Width $widthPt /Height $heightPt /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpegBytes.size} >>\nstream\n")
        baos.write(jpegBytes)
        emit("\nendstream\nendobj\n")

        val contentStream = "q\n$widthPt 0 0 $heightPt 0 0 cm\n/Im0 Do\nQ\n"
        offsets.add(baos.size())
        emit("5 0 obj\n<< /Length ${contentStream.length} >>\nstream\n$contentStream\nendstream\nendobj\n")

        val xrefPos = baos.size()
        emit("xref\n0 ${offsets.size + 1}\n0000000000 65535 f \n")
        for (off in offsets) {
            emit(String.format(java.util.Locale.US, "%010d 00000 n \n", off))
        }
        emit("trailer\n<< /Size ${offsets.size + 1} /Root 1 0 R >>\nstartxref\n$xrefPos\n%%EOF\n")
        out.write(baos.toByteArray())
    }

    private fun saveImageToMediaStore(context: Context, file: File, mimeType: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/HTYPE")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return runCatching {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        }.getOrNull()
    }

    private fun saveDocumentToMediaStore(context: Context, file: File, mimeType: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/HTYPE")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        return runCatching {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        }.getOrNull()
    }
}
