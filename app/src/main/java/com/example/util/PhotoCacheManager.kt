package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object PhotoCacheManager {
    private const val CACHE_FOLDER = "report_photos"
    private const val MAX_CACHE_SIZE_BYTES = 100L * 1024 * 1024 // 100 MB
    private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000 // 7 days

    fun getCacheDir(context: Context): File {
        val dir = File(context.cacheDir, CACHE_FOLDER)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun savePhotoToCache(context: Context, sourceUri: Uri): String? {
        try {
            val cacheDir = getCacheDir(context)
            cleanUpCacheIfNeeded(cacheDir)

            val inputStream = context.contentResolver.openInputStream(sourceUri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) return null

            // Resize if needed
            val maxWidth = 1024
            val maxHeight = 1024
            val ratioBitmap = originalBitmap.width.toFloat() / originalBitmap.height.toFloat()
            var finalWidth = maxWidth
            var finalHeight = maxHeight
            if (ratioBitmap > 1) {
                finalWidth = maxWidth
                finalHeight = (maxWidth / ratioBitmap).toInt()
            } else {
                finalHeight = maxHeight
                finalWidth = (maxHeight * ratioBitmap).toInt()
            }

            val scaledBitmap = if (originalBitmap.width > maxWidth || originalBitmap.height > maxHeight) {
                Bitmap.createScaledBitmap(originalBitmap, finalWidth, finalHeight, true)
            } else {
                originalBitmap
            }

            val fileName = "photo_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg"
            val file = File(cacheDir, fileName)
            
            val outputStream = FileOutputStream(file)
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            outputStream.flush()
            outputStream.close()

            return file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun cleanUpCacheIfNeeded(cacheDir: File) {
        val files = cacheDir.listFiles()?.toList() ?: return
        
        val currentTime = System.currentTimeMillis()
        
        // 1. Delete files older than 7 days
        files.forEach { file ->
            if (currentTime - file.lastModified() > MAX_AGE_MS) {
                file.delete()
            }
        }

        // 2. Ensure cache size is under 100MB
        var currentFiles = cacheDir.listFiles()?.toList()?.sortedBy { it.lastModified() } ?: return
        var totalSize = currentFiles.sumOf { it.length() }

        for (file in currentFiles) {
            if (totalSize <= MAX_CACHE_SIZE_BYTES) break
            totalSize -= file.length()
            file.delete()
        }
    }
}
