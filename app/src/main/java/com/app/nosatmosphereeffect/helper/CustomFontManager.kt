package com.app.nosatmosphereeffect.helper

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.FileOutputStream

data class CustomFontInfo(
    val id: String,
    val name: String,
    val file: File
)

object CustomFontManager {
    private const val TAG = "CustomFontManager"
    private const val FONTS_DIR = "custom_fonts"
    private val typefaceCache = mutableMapOf<String, Typeface>()

    private fun getFontsDirectory(context: Context): File {
        val dir = File(context.filesDir, FONTS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getCustomFonts(context: Context): List<CustomFontInfo> {
        val dir = getFontsDirectory(context)
        val files = dir.listFiles { f ->
            f.isFile && (f.name.endsWith(".ttf", ignoreCase = true) || f.name.endsWith(".otf", ignoreCase = true))
        } ?: return emptyList()

        return files.map { file ->
            val cleanName = file.nameWithoutExtension.replace('_', ' ')
            CustomFontInfo(
                id = "custom_${file.name}",
                name = cleanName,
                file = file
            )
        }.sortedBy { it.name }
    }

    fun importFontFromUri(context: Context, uri: Uri): CustomFontInfo? {
        val contentResolver = context.contentResolver
        var originalFileName: String? = null

        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) originalFileName = cursor.getString(nameIndex)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not resolve file name for font uri: $uri", e)
        }

        val baseName = originalFileName ?: "font_${System.currentTimeMillis()}.ttf"
        val extension = when {
            baseName.endsWith(".otf", ignoreCase = true) -> ".otf"
            else -> ".ttf"
        }
        val safeFileName = baseName.substringBeforeLast('.')
            .replace("[^a-zA-Z0-9_-]".toRegex(), "_") + extension

        val targetFile = File(getFontsDirectory(context), safeFileName)

        try {
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            // Validate that Android can load the font
            val typeface = Typeface.createFromFile(targetFile)
            if (typeface == null) {
                targetFile.delete()
                return null
            }

            val fontInfo = CustomFontInfo(
                id = "custom_${targetFile.name}",
                name = targetFile.nameWithoutExtension.replace('_', ' '),
                file = targetFile
            )
            typefaceCache[fontInfo.id] = typeface
            return fontInfo
        } catch (error: Exception) {
            Log.e(TAG, "Failed to import custom font from uri: $uri", error)
            if (targetFile.exists()) targetFile.delete()
            return null
        }
    }

    fun loadTypeface(context: Context, fontId: String): Typeface? = getTypeface(context, fontId)

    fun getTypeface(context: Context, fontId: String): Typeface? {
        if (!fontId.startsWith("custom_")) return null
        typefaceCache[fontId]?.let { return it }

        val fontName = fontId.removePrefix("custom_")
        val file = File(getFontsDirectory(context), fontName)
        if (!file.exists()) return null

        return try {
            val typeface = Typeface.createFromFile(file)
            if (typeface != null) {
                typefaceCache[fontId] = typeface
            }
            typeface
        } catch (e: Exception) {
            Log.e(TAG, "Could not load custom typeface $fontId", e)
            null
        }
    }

    fun deleteFont(context: Context, fontId: String): Boolean {
        if (!fontId.startsWith("custom_")) return false
        val fontName = fontId.removePrefix("custom_")
        val file = File(getFontsDirectory(context), fontName)
        typefaceCache.remove(fontId)
        return if (file.exists()) file.delete() else false
    }
}
