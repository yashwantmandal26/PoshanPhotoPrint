package com.poshan.photoprint.core.date

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.poshan.photoprint.core.crop.SmartCropDetector
import com.poshan.photoprint.core.model.DateSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ExtractedDateResult(
    val dateMillis: Long?,
    val source: DateSource,
    val width: Int,
    val height: Int
)

object DateExtractor {

    private const val TAG = "DateExtractor"

    private val EXIF_DATE_FORMAT = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
    private val EXIF_GPS_DATE_FORMAT = SimpleDateFormat("yyyy:MM:dd", Locale.US)

    // Regex for file names like IMG_20261005_104200.jpg or 20261005_104200.jpg
    private val FILE_NAME_PATTERN_FULL = Pattern.compile(
        "\\b(20\\d{2})[-_.]?([01]\\d)[-_.]?([0-3]\\d)[-_ ]?([0-2]\\d)[-_.]?([0-5]\\d)[-_.]?([0-5]\\d)?\\b"
    )
    private val FILE_NAME_PATTERN_DATE_ONLY = Pattern.compile(
        "\\b(20\\d{2})[-_.]?([01]\\d)[-_.]?([0-3]\\d)\\b"
    )

    // 1. Standard dd/MM/yyyy or dd-MM-yyyy or dd.MM.yyyy (supports 4-digit or 2-digit year, optional spaces)
    private val OCR_DATE_DMY = Pattern.compile(
        "\\b([0-3]?\\d)\\s*[/.-]\\s*([0-1]?\\d)\\s*[/.-]\\s*(20\\d{2}|\\d{2})\\b"
    )
    // 2. yyyy/MM/dd or yyyy-MM-dd
    private val OCR_DATE_YMD = Pattern.compile(
        "\\b(20\\d{2})\\s*[/.-]\\s*([0-1]?\\d)\\s*[/.-]\\s*([0-3]?\\d)\\b"
    )
    // 3. Textual month (e.g. 16 Mar 2026 or 16 March 2026 or 16-Oct-26)
    private val OCR_DATE_TEXTUAL = Pattern.compile(
        "\\b([0-3]?\\d)[\\s,-]+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec|January|February|March|April|May|June|July|August|September|October|November|December)[\\s,-]+(20\\d{2}|\\d{2})\\b",
        Pattern.CASE_INSENSITIVE
    )
    // 4. Month first (e.g. Mar 16, 2026)
    private val OCR_DATE_MONTH_FIRST = Pattern.compile(
        "\\b(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec|January|February|March|April|May|June|July|August|September|October|November|December)[\\s,-]+([0-3]?\\d)[\\s,]+(20\\d{2}|\\d{2})\\b",
        Pattern.CASE_INSENSITIVE
    )

    // Time pattern: 12-hour (10:55 AM, 10:55:23 AM) and 24-hour (14:35, 14:35:10)
    private val OCR_TIME_PATTERN = Pattern.compile(
        "\\b([0-2]?\\d):([0-5]\\d)(?::([0-5]\\d))?\\s*(AM|PM|am|pm)?\\b"
    )

    private val MONTH_MAP = mapOf(
        "jan" to 0, "january" to 0,
        "feb" to 1, "february" to 1,
        "mar" to 2, "march" to 2,
        "apr" to 3, "april" to 3,
        "may" to 4,
        "jun" to 5, "june" to 5,
        "jul" to 6, "july" to 6,
        "aug" to 7, "august" to 7,
        "sep" to 8, "september" to 8,
        "oct" to 9, "october" to 9,
        "nov" to 10, "november" to 10,
        "dec" to 11, "december" to 11
    )

    /**
     * Extracts date + time and image dimensions using Anganwadi proof hierarchy:
     *
     * Tier 1 (Supreme Ground Truth): ML Kit OCR on the photo's visible GPS Map Camera banner.
     * Tier 2: EXIF TAG_DATETIME_ORIGINAL (Physical camera shutter time from sensor).
     * Tier 3: File Name Pattern (e.g. IMG_20260316_105600.jpg, WhatsApp Image...).
     * Tier 4: EXIF GPS Datestamp + Timestamp.
     * Tier 5: Fallback EXIF TAG_DATETIME (only if not a screenshot/forwarded image).
     */
    suspend fun extract(
        context: Context,
        uri: Uri,
        fileName: String? = null
    ): ExtractedDateResult = withContext(Dispatchers.IO) {
        var detectedDate: Long? = null
        var source = DateSource.UNKNOWN
        var width = 0
        var height = 0
        val rotation = getExifOrientation(context, uri)

        // 1. Determine image dimensions with rotation
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, options)
                width = options.outWidth
                height = options.outHeight

                if (rotation == 90 || rotation == 270) {
                    val temp = width
                    width = height
                    height = temp
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode bounds: ${e.message}")
        }

        // TIER 1: Unconditional OCR on the photo's visual stamped GPS watermark!
        // In Anganwadi reporting, the stamped watermark is the official proof of the event.
        if (width > 0 && height > 0) {
            try {
                val ocrDate = runOcrOnBottomBanner(context, uri, width, height, rotation)
                if (ocrDate != null) {
                    detectedDate = ocrDate
                    source = DateSource.OCR
                    Log.i(TAG, "Tier 1 OCR matched from bottom banner: $ocrDate (${Date(ocrDate)})")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Bottom banner OCR error: ${e.message}")
            }

            // Fallback: If bottom crop missed it (e.g. watermark placed at top or center), scan full image
            if (detectedDate == null) {
                try {
                    val ocrFullDate = runOcrOnFullPhoto(context, uri, rotation)
                    if (ocrFullDate != null) {
                        detectedDate = ocrFullDate
                        source = DateSource.OCR
                        Log.i(TAG, "Tier 1 OCR matched from full photo: $ocrFullDate (${Date(ocrFullDate)})")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Full photo OCR error: ${e.message}")
                }
            }
        }

        // TIER 2: EXIF TAG_DATETIME_ORIGINAL (Hardware camera shutter time)
        // (Do NOT use TAG_DATETIME here because screenshots and photo editors overwrite it)
        if (detectedDate == null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    val dateOriginal = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    if (!dateOriginal.isNullOrBlank()) {
                        val parsed = EXIF_DATE_FORMAT.parse(dateOriginal)
                        if (parsed != null && parsed.time > 0) {
                            detectedDate = parsed.time
                            source = DateSource.EXIF
                            Log.i(TAG, "Tier 2 EXIF TAG_DATETIME_ORIGINAL matched: ${parsed.time} (${Date(parsed.time)})")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "EXIF TAG_DATETIME_ORIGINAL read error: ${e.message}")
            }
        }

        // TIER 3: File Name Regex (e.g. IMG_20260316_105600.jpg, WhatsApp Image 2026-03-16...)
        if (detectedDate == null && !fileName.isNullOrBlank()) {
            val fileDate = parseDateFromFileName(fileName)
            if (fileDate != null) {
                detectedDate = fileDate
                source = DateSource.FILE_NAME
                Log.i(TAG, "Tier 3 File name pattern matched: $fileDate (${Date(fileDate)})")
            }
        }

        // TIER 4: EXIF GPS datestamp + timestamp
        if (detectedDate == null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    val gpsDate = exif.getAttribute(ExifInterface.TAG_GPS_DATESTAMP)
                    val gpsTime = exif.getAttribute(ExifInterface.TAG_GPS_TIMESTAMP)
                    if (!gpsDate.isNullOrBlank()) {
                        val parsed = EXIF_GPS_DATE_FORMAT.parse(gpsDate)
                        if (parsed != null && parsed.time > 0) {
                            val cal = Calendar.getInstance()
                            cal.time = parsed
                            if (!gpsTime.isNullOrBlank()) {
                                val timeParts = gpsTime.split(":")
                                if (timeParts.isNotEmpty()) cal.set(Calendar.HOUR_OF_DAY, timeParts[0].toIntOrNull() ?: 10)
                                if (timeParts.size > 1) cal.set(Calendar.MINUTE, timeParts[1].toIntOrNull() ?: 0)
                                if (timeParts.size > 2) cal.set(Calendar.SECOND, timeParts[2].toIntOrNull() ?: 0)
                            }
                            detectedDate = cal.timeInMillis
                            source = DateSource.EXIF
                            Log.i(TAG, "Tier 4 EXIF GPS datestamp matched: ${cal.timeInMillis}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "EXIF GPS read error: ${e.message}")
            }
        }

        // TIER 5: Fallback EXIF TAG_DATETIME (only if not screenshot / forwarded)
        if (detectedDate == null && !isScreenshotOrForwarded(fileName)) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    val dateStr = exif.getAttribute(ExifInterface.TAG_DATETIME)
                    if (!dateStr.isNullOrBlank()) {
                        val parsed = EXIF_DATE_FORMAT.parse(dateStr)
                        if (parsed != null && parsed.time > 0) {
                            detectedDate = parsed.time
                            source = DateSource.EXIF
                            Log.i(TAG, "Tier 5 EXIF TAG_DATETIME matched: ${parsed.time}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "EXIF TAG_DATETIME read error: ${e.message}")
            }
        }

        ExtractedDateResult(
            dateMillis = detectedDate,
            source = source,
            width = width,
            height = height
        )
    }

    private fun parseDateFromFileName(name: String): Long? {
        val fullMatcher = FILE_NAME_PATTERN_FULL.matcher(name)
        if (fullMatcher.find()) {
            try {
                val year = fullMatcher.group(1)?.toIntOrNull() ?: return null
                val month = (fullMatcher.group(2)?.toIntOrNull() ?: 1) - 1
                val day = fullMatcher.group(3)?.toIntOrNull() ?: 1
                val hour = fullMatcher.group(4)?.toIntOrNull() ?: 10
                val min = fullMatcher.group(5)?.toIntOrNull() ?: 0
                val sec = fullMatcher.group(6)?.toIntOrNull() ?: 0

                val cal = Calendar.getInstance()
                cal.set(year, month, day, hour, min, sec)
                cal.set(Calendar.MILLISECOND, 0)
                return cal.timeInMillis
            } catch (_: Exception) { }
        }

        val dateMatcher = FILE_NAME_PATTERN_DATE_ONLY.matcher(name)
        if (dateMatcher.find()) {
            try {
                val year = dateMatcher.group(1)?.toIntOrNull() ?: return null
                val month = (dateMatcher.group(2)?.toIntOrNull() ?: 1) - 1
                val day = dateMatcher.group(3)?.toIntOrNull() ?: 1

                val cal = Calendar.getInstance()
                cal.set(year, month, day, 10, 0, 0)
                cal.set(Calendar.MILLISECOND, 0)
                return cal.timeInMillis
            } catch (_: Exception) { }
        }
        return null
    }

    private suspend fun runOcrOnBottomBanner(
        context: Context,
        uri: Uri,
        fullWidth: Int,
        fullHeight: Int,
        rotation: Int
    ): Long? {
        return try {
            val croppedBitmap = loadBottomCrop(context, uri, fullWidth, fullHeight, rotation) ?: return null
            val image = InputImage.fromBitmap(croppedBitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val visionText = suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        if (cont.isActive) cont.resume(result)
                    }
                    .addOnFailureListener { error ->
                        if (cont.isActive) cont.resumeWithException(error)
                    }
            }
            croppedBitmap.recycle()

            val rawText = visionText.text
            if (rawText.isBlank()) return null

            Log.d(TAG, "OCR raw text from banner:\n$rawText")
            val normalizedText = normalizeHindiDigits(rawText)
            parseDateAndTimeFromText(normalizedText)
        } catch (e: Exception) {
            Log.e(TAG, "runOcrOnBottomBanner failed: ${e.message}")
            null
        }
    }

    private suspend fun runOcrOnFullPhoto(
        context: Context,
        uri: Uri,
        rotation: Int
    ): Long? {
        return try {
            val options = BitmapFactory.Options().apply {
                inSampleSize = 2 // downsampled for fast ML Kit recognition
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val rawBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return null

            val orientedBitmap = if (rotation != 0) {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                if (rotated != rawBitmap) rawBitmap.recycle()
                rotated
            } else {
                rawBitmap
            }

            val image = InputImage.fromBitmap(orientedBitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val visionText = suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        if (cont.isActive) cont.resume(result)
                    }
                    .addOnFailureListener { error ->
                        if (cont.isActive) cont.resumeWithException(error)
                    }
            }
            orientedBitmap.recycle()

            val rawText = visionText.text
            if (rawText.isBlank()) return null

            Log.d(TAG, "OCR raw text from full photo:\n$rawText")
            val normalizedText = normalizeHindiDigits(rawText)
            parseDateAndTimeFromText(normalizedText)
        } catch (e: Exception) {
            Log.e(TAG, "runOcrOnFullPhoto failed: ${e.message}")
            null
        }
    }

    /**
     * Extracts full date and exact time (HH:mm:ss) from OCR recognized text.
     * Uses line-by-line analysis first to keep date and time pairs together.
     */
    fun parseDateAndTimeFromText(text: String): Long? {
        // Line-by-line first: if a single line contains both a date and a time (standard GPS Camera banner format),
        // extract both from that exact line!
        for (line in text.lines()) {
            val trimmed = line.trim()
            if (trimmed.length < 8) continue
            val singleLineResult = extractFromSingleLine(trimmed)
            if (singleLineResult != null) {
                return singleLineResult
            }
        }

        // Global search across entire text
        return extractFromEntireText(text)
    }

    private fun extractFromSingleLine(line: String): Long? {
        var day: Int? = null
        var month: Int? = null
        var year: Int? = null

        // 1. Try DD/MM/YYYY or DD-MM-YYYY
        val dmyMatcher = OCR_DATE_DMY.matcher(line)
        if (dmyMatcher.find()) {
            day = dmyMatcher.group(1)?.toIntOrNull()
            month = (dmyMatcher.group(2)?.toIntOrNull() ?: 1) - 1
            year = dmyMatcher.group(3)?.toIntOrNull()
        }

        // 2. Try YYYY/MM/DD
        if (year == null) {
            val ymdMatcher = OCR_DATE_YMD.matcher(line)
            if (ymdMatcher.find()) {
                year = ymdMatcher.group(1)?.toIntOrNull()
                month = (ymdMatcher.group(2)?.toIntOrNull() ?: 1) - 1
                day = ymdMatcher.group(3)?.toIntOrNull()
            }
        }

        // 3. Try textual month (e.g. 16 Mar 2026 or 16-March-2026)
        if (year == null) {
            val textMatcher = OCR_DATE_TEXTUAL.matcher(line)
            if (textMatcher.find()) {
                day = textMatcher.group(1)?.toIntOrNull()
                val monthStr = textMatcher.group(2)?.lowercase(Locale.US) ?: ""
                month = MONTH_MAP[monthStr] ?: 0
                year = textMatcher.group(3)?.toIntOrNull()
            }
        }

        // 4. Try month first (e.g. Mar 16, 2026)
        if (year == null) {
            val monthFirstMatcher = OCR_DATE_MONTH_FIRST.matcher(line)
            if (monthFirstMatcher.find()) {
                val monthStr = monthFirstMatcher.group(1)?.lowercase(Locale.US) ?: ""
                month = MONTH_MAP[monthStr] ?: 0
                day = monthFirstMatcher.group(2)?.toIntOrNull()
                year = monthFirstMatcher.group(3)?.toIntOrNull()
            }
        }

        if (year != null && year < 100) year += 2000
        if (day == null || month == null || year == null) return null
        if (day !in 1..31 || month !in 0..11 || year !in 2018..2035) return null

        // Look for exact time in the same line
        var hour = 10
        var minute = 0
        var second = 0

        val timeMatcher = OCR_TIME_PATTERN.matcher(line)
        if (timeMatcher.find()) {
            var parsedHour = timeMatcher.group(1)?.toIntOrNull() ?: 10
            val parsedMinute = timeMatcher.group(2)?.toIntOrNull() ?: 0
            val parsedSecond = timeMatcher.group(3)?.toIntOrNull() ?: 0
            val ampm = timeMatcher.group(4)?.uppercase(Locale.US)

            if (ampm == "PM" && parsedHour < 12) parsedHour += 12
            if (ampm == "AM" && parsedHour == 12) parsedHour = 0

            hour = parsedHour.coerceIn(0, 23)
            minute = parsedMinute.coerceIn(0, 59)
            second = parsedSecond.coerceIn(0, 59)
        }

        val cal = Calendar.getInstance()
        cal.set(year, month, day, hour, minute, second)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun extractFromEntireText(text: String): Long? {
        var day: Int? = null
        var month: Int? = null
        var year: Int? = null

        val dmyMatcher = OCR_DATE_DMY.matcher(text)
        if (dmyMatcher.find()) {
            day = dmyMatcher.group(1)?.toIntOrNull()
            month = (dmyMatcher.group(2)?.toIntOrNull() ?: 1) - 1
            year = dmyMatcher.group(3)?.toIntOrNull()
        }

        if (year == null) {
            val ymdMatcher = OCR_DATE_YMD.matcher(text)
            if (ymdMatcher.find()) {
                year = ymdMatcher.group(1)?.toIntOrNull()
                month = (ymdMatcher.group(2)?.toIntOrNull() ?: 1) - 1
                day = ymdMatcher.group(3)?.toIntOrNull()
            }
        }

        if (year == null) {
            val textMatcher = OCR_DATE_TEXTUAL.matcher(text)
            if (textMatcher.find()) {
                day = textMatcher.group(1)?.toIntOrNull()
                val monthStr = textMatcher.group(2)?.lowercase(Locale.US) ?: ""
                month = MONTH_MAP[monthStr] ?: 0
                year = textMatcher.group(3)?.toIntOrNull()
            }
        }

        if (year == null) {
            val monthFirstMatcher = OCR_DATE_MONTH_FIRST.matcher(text)
            if (monthFirstMatcher.find()) {
                val monthStr = monthFirstMatcher.group(1)?.lowercase(Locale.US) ?: ""
                month = MONTH_MAP[monthStr] ?: 0
                day = monthFirstMatcher.group(2)?.toIntOrNull()
                year = monthFirstMatcher.group(3)?.toIntOrNull()
            }
        }

        if (year != null && year < 100) year += 2000
        if (day == null || month == null || year == null) return null
        if (day !in 1..31 || month !in 0..11 || year !in 2018..2035) return null

        var hour = 10
        var minute = 0
        var second = 0

        val timeMatcher = OCR_TIME_PATTERN.matcher(text)
        if (timeMatcher.find()) {
            var parsedHour = timeMatcher.group(1)?.toIntOrNull() ?: 10
            val parsedMinute = timeMatcher.group(2)?.toIntOrNull() ?: 0
            val parsedSecond = timeMatcher.group(3)?.toIntOrNull() ?: 0
            val ampm = timeMatcher.group(4)?.uppercase(Locale.US)

            if (ampm == "PM" && parsedHour < 12) parsedHour += 12
            if (ampm == "AM" && parsedHour == 12) parsedHour = 0

            hour = parsedHour.coerceIn(0, 23)
            minute = parsedMinute.coerceIn(0, 59)
            second = parsedSecond.coerceIn(0, 59)
        }

        val cal = Calendar.getInstance()
        cal.set(year, month, day, hour, minute, second)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * Converts Devanagari numerals (०, १, २, ३, ४, ५, ६, ७, ८, ९) to ASCII (0-9).
     */
    fun normalizeHindiDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            if (ch in '०'..'९') {
                sb.append((ch - '०' + '0'.code).toChar())
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun isScreenshotOrForwarded(name: String?): Boolean {
        if (name == null) return false
        val lower = name.lowercase(Locale.US)
        return lower.contains("screenshot") || lower.contains("screen_") ||
               lower.contains("-wa") || lower.contains("whatsapp") ||
               lower.contains("telegram") || lower.contains("received")
    }

    private fun loadBottomCrop(
        context: Context,
        uri: Uri,
        width: Int,
        height: Int,
        rotation: Int
    ): Bitmap? {
        return try {
            val photoBounds = SmartCropDetector.detectSmartCrop(context, uri)

            var sampleSize = 1
            while (width / sampleSize > 1400) {
                sampleSize *= 2
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val rawBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return null

            // Correct EXIF orientation before calculating crop coordinates
            val orientedBitmap = if (rotation != 0) {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                if (rotated != rawBitmap) rawBitmap.recycle()
                rotated
            } else {
                rawBitmap
            }

            val bw = orientedBitmap.width
            val bh = orientedBitmap.height

            // Map photoBounds to downsampled coordinates
            val realLeft = (photoBounds.left * bw).toInt().coerceIn(0, bw - 1)
            val realRight = (photoBounds.right * bw).toInt().coerceIn(realLeft + 10, bw)
            val realTop = (photoBounds.top * bh).toInt().coerceIn(0, bh - 1)
            val realBottom = (photoBounds.bottom * bh).toInt().coerceIn(realTop + 10, bh)

            val realH = realBottom - realTop
            val realW = realRight - realLeft

            // Target bottom 45% of the true photograph where GPS watermark is located
            val bannerH = (realH * 0.45f).toInt().coerceIn(10, realH)
            val startY = (realBottom - bannerH).coerceIn(0, bh - bannerH)

            val cropped = Bitmap.createBitmap(
                orientedBitmap,
                realLeft,
                startY,
                realW,
                bannerH
            )
            if (cropped != orientedBitmap) {
                orientedBitmap.recycle()
            }
            cropped
        } catch (e: Exception) {
            Log.e(TAG, "loadBottomCrop error: ${e.message}")
            null
        }
    }

    fun getExifOrientation(context: Context, uri: Uri): Int {
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