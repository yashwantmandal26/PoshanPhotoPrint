package com.poshan.photoprint.core.crop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Intelligent Smart Crop Detector designed for Anganwadi Sevika reporting photos.
 *
 * Automatically detects and eliminates:
 * 1. Letterboxing/pillarboxing black bars from phone screenshots or WhatsApp forwarded images.
 * 2. Android status bars (time, wifi, battery) and gallery action bars (share, edit, delete).
 * 3. Light-theme white screenshot letterbox bars.
 * 4. Preserves 100% of the underlying photograph including the critical GPS Map Camera watermark stamp.
 */
object SmartCropDetector {

    private const val TAG = "SmartCropDetector"

    fun detectSmartCrop(context: Context, uri: Uri): RectF {
        return try {
            val rotation = getExifOrientation(context, uri)

            // 1. Decode bounds
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return RectF(0f, 0f, 1f, 1f)

            val rawW = options.outWidth
            val rawH = options.outHeight
            if (rawW <= 0 || rawH <= 0) return RectF(0f, 0f, 1f, 1f)

            // Account for orientation in bounds
            val isSwapped = rotation == 90 || rotation == 270
            val orientedW = if (isSwapped) rawH else rawW
            val orientedH = if (isSwapped) rawW else rawH

            // 2. Downsample for fast precision analysis
            val targetW = 360
            var sampleSize = 1
            var w = orientedW
            var h = orientedH
            while (w / 2 >= targetW && h / 2 >= targetW) {
                w /= 2
                h /= 2
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val rawBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return RectF(0f, 0f, 1f, 1f)

            // Rotate if needed so pixel coordinates match screen display
            val bitmap = if (rotation != 0) {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                if (rotated != rawBitmap) rawBitmap.recycle()
                rotated
            } else {
                rawBitmap
            }

            val bw = bitmap.width
            val bh = bitmap.height
            if (bw <= 0 || bh <= 0) {
                bitmap.recycle()
                return RectF(0f, 0f, 1f, 1f)
            }

            val pixels = IntArray(bw * bh)
            bitmap.getPixels(pixels, 0, bw, 0, 0, bw, bh)
            bitmap.recycle()

            // 3. Two-phase Precision Row and Column Border Detection
            fun isBorderRow(y: Int): Boolean {
                val rowOffset = y * bw
                var darkCount = 0
                var brightCount = 0
                var totalBrightness = 0L

                for (x in 0 until bw) {
                    val p = pixels[rowOffset + x]
                    val r = (p shr 16) and 0xFF
                    val g = (p shr 8) and 0xFF
                    val b = p and 0xFF
                    val maxCh = max(r, max(g, b))
                    val minCh = min(r, min(g, b))

                    if (maxCh < 35) darkCount++
                    if (minCh > 230) brightCount++
                    totalBrightness += (r + g + b) / 3
                }

                val darkRatio = darkCount.toFloat() / bw
                val brightRatio = brightCount.toFloat() / bw
                val meanBrightness = totalBrightness.toFloat() / bw

                // Dark letterbox or status bar with sparse white icons/time text
                if (darkRatio > 0.70f) return true
                // White screenshot letterbox
                if (brightRatio > 0.75f) return true
                // Uniform dark bar
                if (darkRatio > 0.50f && meanBrightness < 25f) return true

                return false
            }

            // Phase 1: Top and Bottom Scan (bypassing screenshot letterbox and gallery action bars)
            var top = 0
            val maxTop = (bh * 0.45f).toInt()
            while (top < maxTop && isBorderRow(top)) {
                top++
            }

            var bottom = bh - 1
            val minBottom = (bh * 0.55f).toInt()
            while (bottom > minBottom && isBorderRow(bottom)) {
                bottom--
            }

            // Phase 2: Pillarbox Column Scan evaluated within the photograph rows [top..bottom]
            val photoHeight = (bottom - top + 1).coerceAtLeast(1)
            fun isBorderCol(x: Int): Boolean {
                var darkCount = 0
                var brightCount = 0
                var totalBrightness = 0L

                for (y in top..bottom) {
                    val p = pixels[y * bw + x]
                    val r = (p shr 16) and 0xFF
                    val g = (p shr 8) and 0xFF
                    val b = p and 0xFF
                    val maxCh = max(r, max(g, b))
                    val minCh = min(r, min(g, b))

                    if (maxCh < 35) darkCount++
                    if (minCh > 230) brightCount++
                    totalBrightness += (r + g + b) / 3
                }

                val darkRatio = darkCount.toFloat() / photoHeight
                val brightRatio = brightCount.toFloat() / photoHeight
                val meanBrightness = totalBrightness.toFloat() / photoHeight

                if (darkRatio > 0.85f) return true
                if (brightRatio > 0.85f) return true
                if (darkRatio > 0.65f && meanBrightness < 20f) return true

                return false
            }

            var left = 0
            val maxLeft = (bw * 0.40f).toInt()
            while (left < maxLeft && isBorderCol(left)) {
                left++
            }

            var right = bw - 1
            val minRight = (bw * 0.60f).toInt()
            while (right > minRight && isBorderCol(right)) {
                right--
            }

            val topNorm = top.toFloat() / bh
            val bottomNorm = (bottom + 1).toFloat() / bh
            val leftNorm = left.toFloat() / bw
            val rightNorm = (right + 1).toFloat() / bw

            val hasBorders = topNorm > 0.015f || bottomNorm < 0.985f || leftNorm > 0.015f || rightNorm < 0.985f

            Log.d(TAG, "detectSmartCrop hasBorders=$hasBorders, L=$leftNorm, T=$topNorm, R=$rightNorm, B=$bottomNorm")

            if (hasBorders) {
                // Precision crop with buffer to cleanly clip border lines
                val cleanTop = (topNorm + 0.002f).coerceIn(0f, 0.45f)
                val cleanBottom = (bottomNorm - 0.002f).coerceIn(0.55f, 1f)
                val cleanLeft = (leftNorm + 0.002f).coerceIn(0f, 0.40f)
                val cleanRight = (rightNorm - 0.002f).coerceIn(0.60f, 1f)
                RectF(cleanLeft, cleanTop, cleanRight, cleanBottom)
            } else {
                // Standard photo without screenshot bars: check for blank white/dead top ceiling
                val topMean = calculateTopMeanVariance(pixels, bw, bh, 0.12f)
                val topTrim = if (topMean.second < 12.0f && topMean.first > 170f) 0.06f else 0f
                RectF(0f, topTrim, 1f, 1f)
            }
        } catch (e: Exception) {
            Log.e(TAG, "detectSmartCrop exception: ${e.message}")
            RectF(0f, 0f, 1f, 1f)
        }
    }

    private fun calculateTopMeanVariance(pixels: IntArray, w: Int, h: Int, fraction: Float): Pair<Float, Float> {
        val rows = (h * fraction).toInt().coerceAtLeast(1)
        var sum = 0.0
        val count = rows * w
        for (i in 0 until count) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            sum += (r + g + b) / 3.0
        }
        val mean = sum / count
        var varSum = 0.0
        for (i in 0 until count) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val gray = (r + g + b) / 3.0
            varSum += (gray - mean) * (gray - mean)
        }
        val std = sqrt(varSum / count).toFloat()
        return Pair(mean.toFloat(), std)
    }

    private fun getExifOrientation(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (_: Exception) {
            0
        }
    }
}