package com.example.render

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
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

enum class ExportMode {
    FULL_IMAGE_PNG,
    TRANSPARENT_PNG
}

data class ExportResult(
    val shareUri: Uri,
    val galleryUri: Uri?,
    val widthPx: Int,
    val heightPx: Int,
    val fileSizeBytes: Long,
    val mode: ExportMode
)

object ExportEngine {

    suspend fun exportPng(
        context: Context,
        doc: ProjectDocument,
        fontRepository: FontRepository,
        fontsById: Map<String, FontItem>,
        mode: ExportMode,
        scaleMultiplier: Int = 1
    ): Result<ExportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val scale = scaleMultiplier.coerceIn(1, 2).toFloat()
            val bitmap = CanvasRenderer.renderDocumentToBitmap(
                doc = doc,
                fontRepository = fontRepository,
                fontsById = fontsById,
                forceTransparentBackground = (mode == ExportMode.TRANSPARENT_PNG),
                scaleFactor = scale,
                isExportMode = true
            )

            val safeName = doc.title
                .replace(Regex("[^A-Za-z0-9_\\-]"), "_")
                .ifBlank { "TYPEFRAME" }
            val suffix = if (mode == ExportMode.TRANSPARENT_PNG) "transparent" else "full"
            val fileName = "${safeName}_${suffix}_${System.currentTimeMillis()}.png"

            val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val cacheFile = File(exportsDir, fileName)
            FileOutputStream(cacheFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val shareUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                cacheFile
            )

            val galleryUri = saveToMediaStoreIfSupported(context, fileName, bitmap)
            val width = bitmap.width
            val height = bitmap.height
            bitmap.recycle()

            ExportResult(
                shareUri = shareUri,
                galleryUri = galleryUri,
                widthPx = width,
                heightPx = height,
                fileSizeBytes = cacheFile.length(),
                mode = mode
            )
        }
    }

    suspend fun writePngToUri(
        context: Context,
        targetUri: Uri,
        doc: ProjectDocument,
        fontRepository: FontRepository,
        fontsById: Map<String, FontItem>,
        mode: ExportMode,
        scaleMultiplier: Int = 1
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = CanvasRenderer.renderDocumentToBitmap(
                doc = doc,
                fontRepository = fontRepository,
                fontsById = fontsById,
                forceTransparentBackground = (mode == ExportMode.TRANSPARENT_PNG),
                scaleFactor = scaleMultiplier.coerceIn(1, 2).toFloat(),
                isExportMode = true
            )
            context.contentResolver.openOutputStream(targetUri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: error("Unable to open destination file.")
            bitmap.recycle()
        }
    }

    private fun saveToMediaStoreIfSupported(
        context: Context,
        fileName: String,
        bitmap: Bitmap
    ): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return null
        }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/TYPEFRAME")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return runCatching {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        }.getOrNull()
    }
}
