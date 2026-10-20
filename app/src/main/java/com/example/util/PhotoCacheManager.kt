package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object PhotoCacheManager {
    private const val CACHE_FOLDER = "report_photos"
    private const val MAX_CACHE_SIZE_BYTES = 250L * 1024 * 1024 // 250 MB for 30-day retention
    private const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000 // 30 days (1 Month persistent storage)

    fun getCacheDir(context: Context): File {
        // Use filesDir instead of cacheDir so Android OS does not prematurely evict photos within 30 days
        val dir = File(context.filesDir, CACHE_FOLDER)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun savePhotoToCache(
        context: Context,
        sourceUri: Uri,
        projectTitle: String = "",
        reportDate: String = "",
        reportNumber: Int = 100,
        kmInfo: String = "",
        applyWatermark: Boolean = true
    ): String? {
        try {
            val storageDir = getCacheDir(context)
            cleanUpCacheIfNeeded(storageDir)

            val inputStream = context.contentResolver.openInputStream(sourceUri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) return null

            // Resize if needed
            val maxWidth = 1280
            val maxHeight = 1280
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
                originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
            }

            // Apply automatic Construction Site Watermark (واترمارک کارگاهی)
            val watermarkedBitmap = if (applyWatermark) {
                applySiteWatermark(
                    bitmap = scaledBitmap,
                    projectTitle = projectTitle,
                    reportDate = reportDate,
                    reportNumber = reportNumber,
                    kmInfo = kmInfo
                )
            } else {
                scaledBitmap
            }

            val fileName = "photo_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg"
            val file = File(storageDir, fileName)

            val outputStream = FileOutputStream(file)
            watermarkedBitmap.compress(Bitmap.CompressFormat.JPEG, 78, outputStream)
            outputStream.flush()
            outputStream.close()

            return file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun applySiteWatermark(
        bitmap: Bitmap,
        projectTitle: String,
        reportDate: String,
        reportNumber: Int,
        kmInfo: String
    ): Bitmap {
        val mutableBitmap = if (bitmap.isMutable) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val width = mutableBitmap.width.toFloat()
        val height = mutableBitmap.height.toFloat()

        val barHeight = (height * 0.095f).coerceIn(54f, 110f)
        val barTop = height - barHeight

        // Semi-transparent dark engineering banner at bottom
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(205, 15, 23, 42) // #0f172a with ~80% opacity
            style = Paint.Style.FILL
        }
        canvas.drawRect(RectF(0f, barTop, width, height), bgPaint)

        // Amber safety accent strip at top of watermark bar
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(245, 158, 11) // #f59e0b Safety Amber
            style = Paint.Style.FILL
        }
        canvas.drawRect(RectF(0f, barTop, width, barTop + 3.5f), accentPaint)

        val timeStr = SimpleDateFormat("HH:mm", Locale.US).format(Date())
        val dateText = if (reportDate.isNotBlank()) reportDate else SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date())
        val kmText = if (kmInfo.isNotBlank()) " | KM: $kmInfo" else ""
        val projText = if (projectTitle.isNotBlank()) projectTitle else "گزارش‌یار کارگاه (BuildReport)"

        val primaryTextSize = (barHeight * 0.34f).coerceIn(16f, 32f)
        val secondaryTextSize = (barHeight * 0.28f).coerceIn(13f, 26f)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(251, 191, 36) // Amber gold
            textSize = primaryTextSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }

        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = secondaryTextSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
        }

        val paddingX = 18f
        val line1Y = barTop + barHeight * 0.44f
        val line2Y = barTop + barHeight * 0.82f

        val line1 = "$projText  •  گزارش #$reportNumber"
        val line2 = "تاریخ: $dateText  |  ساعت: $timeStr$kmText"

        canvas.drawText(line1, width - paddingX, line1Y, titlePaint)
        canvas.drawText(line2, width - paddingX, line2Y, metaPaint)

        return mutableBitmap
    }

    fun cleanUpExpiredPhotos(context: Context) {
        cleanUpCacheIfNeeded(getCacheDir(context))
        // Also clean old cacheDir folder if any legacy files remain
        val legacyDir = File(context.cacheDir, CACHE_FOLDER)
        if (legacyDir.exists()) {
            cleanUpCacheIfNeeded(legacyDir)
        }
    }

    private fun cleanUpCacheIfNeeded(storageDir: File) {
        val files = storageDir.listFiles()?.toList() ?: return
        val currentTime = System.currentTimeMillis()

        // 1. Delete files older than 30 days (1 month)
        files.forEach { file ->
            if (currentTime - file.lastModified() > MAX_AGE_MS) {
                file.delete()
            }
        }

        // 2. Ensure storage size is under MAX_CACHE_SIZE_BYTES
        val currentFiles = storageDir.listFiles()?.toList()?.sortedBy { it.lastModified() } ?: return
        var totalSize = currentFiles.sumOf { it.length() }

        for (file in currentFiles) {
            if (totalSize <= MAX_CACHE_SIZE_BYTES) break
            totalSize -= file.length()
            file.delete()
        }
    }
}
