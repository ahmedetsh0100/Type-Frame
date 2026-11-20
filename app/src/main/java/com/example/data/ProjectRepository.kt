package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.model.ImageLayer
import com.example.model.ProjectDocument
import com.example.model.TextLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class ProjectRepository(
    private val context: Context,
    private val projectDao: ProjectDao
) {
    private val projectsRoot = File(context.filesDir, "projects").apply { mkdirs() }

    fun observeAllProjects(): Flow<List<ProjectEntity>> = projectDao.observeAllProjects()

    suspend fun getDocument(projectId: String): ProjectDocument? = withContext(Dispatchers.IO) {
        projectDao.getProjectById(projectId)?.let { entity ->
            ProjectSerializer.fromJson(entity.documentJson)
        }
    }

    suspend fun saveDocument(
        doc: ProjectDocument,
        thumbnailBitmap: Bitmap? = null
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val updatedDoc = doc.copy(updatedAtEpochMs = now)
        val existing = projectDao.getProjectById(doc.id)
        val projectDir = File(projectsRoot, doc.id).apply { mkdirs() }

        val thumbPath = if (thumbnailBitmap != null) {
            val thumbFile = File(projectDir, "thumb.png")
            FileOutputStream(thumbFile).use { out ->
                thumbnailBitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            thumbFile.absolutePath
        } else {
            existing?.thumbnailPath
        }

        val entity = ProjectEntity(
            id = updatedDoc.id,
            title = updatedDoc.title,
            canvasWidthPx = updatedDoc.canvasWidthPx,
            canvasHeightPx = updatedDoc.canvasHeightPx,
            layerCount = updatedDoc.layers.size,
            thumbnailPath = thumbPath,
            documentJson = ProjectSerializer.toJson(updatedDoc),
            createdAtEpochMs = existing?.createdAtEpochMs ?: now,
            updatedAtEpochMs = now
        )
        projectDao.upsertProject(entity)
    }

    suspend fun createBlankProject(
        title: String,
        widthPx: Int,
        heightPx: Int,
        transparentBg: Boolean = false
    ): ProjectDocument = withContext(Dispatchers.IO) {
        val doc = ProjectDocument(
            title = title,
            canvasWidthPx = widthPx,
            canvasHeightPx = heightPx,
            isTransparentBackground = transparentBg,
            layers = listOf(
                TextLayer(
                    name = "Arabic Headline",
                    xPx = widthPx / 2f,
                    yPx = heightPx / 2f,
                    text = "بِسْمِ الْإِبْدَاعِ\nHTYPE STUDIO",
                    fontSizePx = (widthPx * 0.085f).coerceIn(48f, 140f),
                    boxWidthPx = widthPx * 0.85f
                )
            )
        )
        saveDocument(doc)
        doc
    }

    suspend fun createProjectFromImageUri(uri: Uri): Result<ProjectDocument> = withContext(Dispatchers.IO) {
        runCatching {
            val projectId = UUID.randomUUID().toString()
            val mediaResult = copyImageToProjectMedia(projectId, uri).getOrThrow()
            val canvasW = mediaResult.naturalWidth.coerceIn(720, 2400)
            val canvasH = ((canvasW.toFloat() / mediaResult.naturalWidth) * mediaResult.naturalHeight)
                .toInt().coerceIn(720, 2400)

            val imageLayer = ImageLayer(
                name = "Photo",
                xPx = canvasW / 2f,
                yPx = canvasH / 2f,
                imagePath = mediaResult.path,
                widthPx = canvasW.toFloat(),
                heightPx = canvasH.toFloat(),
                naturalWidthPx = mediaResult.naturalWidth,
                naturalHeightPx = mediaResult.naturalHeight
            )
            val doc = ProjectDocument(
                id = projectId,
                title = "Photo Frame",
                canvasWidthPx = canvasW,
                canvasHeightPx = canvasH,
                layers = listOf(imageLayer)
            )
            saveDocument(doc)
            doc
        }
    }

    data class CopiedMedia(val path: String, val naturalWidth: Int, val naturalHeight: Int)

    suspend fun copyImageToProjectMedia(projectId: String, uri: Uri): Result<CopiedMedia> =
        withContext(Dispatchers.IO) {
            runCatching {
                val mediaDir = File(projectsRoot, "$projectId/media").apply { mkdirs() }
                val destFile = File(mediaDir, "img_${UUID.randomUUID()}.bin")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output -> input.copyTo(output) }
                } ?: error("Could not read selected image.")

                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(destFile.absolutePath, opts)
                val w = opts.outWidth.coerceAtLeast(1)
                val h = opts.outHeight.coerceAtLeast(1)
                CopiedMedia(destFile.absolutePath, w, h)
            }
        }

    suspend fun deleteProject(projectId: String) = withContext(Dispatchers.IO) {
        projectDao.deleteProjectById(projectId)
        File(projectsRoot, projectId).deleteRecursively()
    }
}
