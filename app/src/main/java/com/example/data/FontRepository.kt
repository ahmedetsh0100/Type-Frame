package com.example.data

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import com.example.model.FontItem
import com.example.typography.GlyphAlternateMatch
import com.example.typography.OpenTypeFontInspector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class FontRepository(
    private val context: Context,
    private val fontDao: FontDao
) {
    private val fontsDir = File(context.filesDir, "fonts").apply { mkdirs() }
    private val typefaceCache = ConcurrentHashMap<String, Typeface>()
    private val bytesCache = ConcurrentHashMap<String, ByteArray>()
    private val itemCache = ConcurrentHashMap<String, FontItem>()

    private val bundledSpecs = listOf(
        Triple("asset:IBMPlexSansArabic-Regular.otf", "IBM Plex Sans Arabic", "fonts/IBMPlexSansArabic-Regular.otf"),
        Triple("asset:Amiri-Regular.ttf", "Amiri Regular", "fonts/Amiri-Regular.ttf")
    )

    fun observeAllFonts(): Flow<List<FontItem>> {
        return fontDao.observeImportedFonts().map { entities ->
            val bundled = bundledSpecs.mapNotNull { (id, fallbackName, assetPath) ->
                getOrLoadBundledFont(id, fallbackName, assetPath)
            }
            val imported = entities.mapNotNull { entity ->
                getOrLoadImportedFont(entity)
            }
            imported + bundled
        }
    }

    suspend fun getBundledFontsImmediate(): List<FontItem> = withContext(Dispatchers.IO) {
        bundledSpecs.mapNotNull { (id, fallbackName, assetPath) ->
            getOrLoadBundledFont(id, fallbackName, assetPath)
        }
    }

    suspend fun importFontFromUri(uri: Uri): Result<FontItem> = withContext(Dispatchers.IO) {
        runCatching {
            val rawName = resolveDisplayName(uri) ?: "custom_font_${System.currentTimeMillis()}.otf"
            val lower = rawName.lowercase()
            require(lower.endsWith(".otf") || lower.endsWith(".ttf")) {
                "Only .otf and .ttf font files are supported."
            }
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Unable to read selected font file.")

            val metadata = OpenTypeFontInspector.inspect(bytes, rawName.substringBeforeLast('.'))
            val id = "file:${UUID.randomUUID()}"
            val ext = if (lower.endsWith(".ttf")) "ttf" else "otf"
            val destFile = File(fontsDir, "${id.removePrefix("file:")}.$ext")
            destFile.writeBytes(bytes)

            val typeface = Typeface.createFromFile(destFile)
            typefaceCache[id] = typeface
            bytesCache[id] = bytes

            val entity = ImportedFontEntity(
                id = id,
                displayName = metadata.fullName.ifBlank { rawName.substringBeforeLast('.') },
                fileName = rawName,
                filePath = destFile.absolutePath,
                importedAtEpochMs = System.currentTimeMillis()
            )
            fontDao.upsertFont(entity)

            FontItem(
                id = id,
                displayName = entity.displayName,
                fileName = entity.fileName,
                isBundled = false,
                filePath = entity.filePath,
                metadata = metadata
            ).also { itemCache[id] = it }
        }
    }

    suspend fun deleteImportedFont(fontItem: FontItem) = withContext(Dispatchers.IO) {
        if (fontItem.isBundled) return@withContext
        fontDao.deleteFontById(fontItem.id)
        fontItem.filePath?.let { File(it).delete() }
        typefaceCache.remove(fontItem.id)
        bytesCache.remove(fontItem.id)
        itemCache.remove(fontItem.id)
    }

    fun resolveTypeface(fontId: String): Typeface {
        typefaceCache[fontId]?.let { return it }
        val item = itemCache[fontId]
        if (item != null) {
            val tf = if (item.isBundled && item.assetPath != null) {
                Typeface.createFromAsset(context.assets, item.assetPath)
            } else if (item.filePath != null && File(item.filePath).exists()) {
                Typeface.createFromFile(item.filePath)
            } else {
                Typeface.DEFAULT
            }
            typefaceCache[fontId] = tf
            return tf
        }
        // Fallback for bundled asset IDs before flow emits
        val bundled = bundledSpecs.firstOrNull { it.first == fontId }
        if (bundled != null) {
            return Typeface.createFromAsset(context.assets, bundled.third).also {
                typefaceCache[fontId] = it
            }
        }
        return Typeface.DEFAULT
    }

    fun findAlternatesForText(fontId: String, text: String, tags: Set<String>): List<GlyphAlternateMatch> {
        val bytes = bytesCache[fontId] ?: return emptyList()
        return OpenTypeFontInspector.findAlternatesForText(bytes, text, tags)
    }

    private fun getOrLoadBundledFont(id: String, fallbackName: String, assetPath: String): FontItem? {
        itemCache[id]?.let { return it }
        return runCatching {
            val bytes = bytesCache[id] ?: context.assets.open(assetPath).use { it.readBytes() }.also {
                bytesCache[id] = it
            }
            val metadata = OpenTypeFontInspector.inspect(bytes, fallbackName)
            typefaceCache.putIfAbsent(id, Typeface.createFromAsset(context.assets, assetPath))
            FontItem(
                id = id,
                displayName = metadata.fullName.ifBlank { fallbackName },
                fileName = assetPath.substringAfterLast('/'),
                isBundled = true,
                assetPath = assetPath,
                metadata = metadata
            ).also { itemCache[id] = it }
        }.getOrNull()
    }

    private fun getOrLoadImportedFont(entity: ImportedFontEntity): FontItem? {
        itemCache[entity.id]?.let { return it }
        val file = File(entity.filePath)
        if (!file.exists()) return null
        return runCatching {
            val bytes = bytesCache[entity.id] ?: file.readBytes().also { bytesCache[entity.id] = it }
            val metadata = OpenTypeFontInspector.inspect(bytes, entity.displayName)
            typefaceCache.putIfAbsent(entity.id, Typeface.createFromFile(file))
            FontItem(
                id = entity.id,
                displayName = entity.displayName,
                fileName = entity.fileName,
                isBundled = false,
                filePath = entity.filePath,
                metadata = metadata
            ).also { itemCache[entity.id] = it }
        }.getOrNull()
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            } ?: uri.lastPathSegment?.substringAfterLast('/')
    }
}
