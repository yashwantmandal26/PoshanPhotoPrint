package com.poshan.photoprint.core.generator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import com.poshan.photoprint.core.layout.A4LayoutCalculator
import com.poshan.photoprint.core.layout.CellRects
import com.poshan.photoprint.core.layout.LayoutPreset
import com.poshan.photoprint.core.model.PhotoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

data class CompressedJpegImage(
    val width: Int,
    val height: Int,
    val jpegBytes: ByteArray
)

enum class ExportQuality(val labelEn: String, val labelHi: String, val multiplier: Float, val jpegQuality: Int) {
    WHATSAPP("WhatsApp (<400 KB)", "व्हाट्सएप (<400 KB)", 1.6f, 75),
    HQ_PRINT("HQ Print (300 DPI)", "प्रिंट क्वालिटी (HQ)", 2.8f, 88)
}

object A4PdfGenerator {

    // Standard ISO A4 dimensions in PDF points (72 points per inch)
    const val A4_WIDTH_POINTS = 595.28f
    const val A4_HEIGHT_POINTS = 841.89f

    /**
     * Generates an ultra-compact (<600 KB in WhatsApp mode, or high-res in HQ Print mode),
     * sharp A4 PDF document embedding JPEG streams directly via native PDF /DCTDecode compression (ISO 32000-1).
     */
    suspend fun generatePdf(
        context: Context,
        photos: List<PhotoItem>,
        outputFile: File,
        quality: ExportQuality = ExportQuality.WHATSAPP,
        preset: LayoutPreset = LayoutPreset.JUSTIFIED,
        pageWidthPt: Float = A4_WIDTH_POINTS,
        pageHeightPt: Float = A4_HEIGHT_POINTS,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): File = withContext(Dispatchers.IO) {
        if (outputFile.exists()) {
            outputFile.delete()
        }
        outputFile.parentFile?.mkdirs()

        val aspectRatios = photos.map { it.aspectRatio }
        // Compute layout in PDF points: margin 28 pt, gap 10 pt
        val layoutPlan = A4LayoutCalculator.computeLayout(
            photoAspectRatios = aspectRatios,
            pageWidth = pageWidthPt.roundToInt(),
            pageHeight = pageHeightPt.roundToInt(),
            marginPx = 28f,
            gapPx = 10f,
            preset = preset
        )

        val compressedImages = mutableListOf<CompressedJpegImage>()

        photos.forEachIndexed { index, photoItem ->
            val cell = if (index < layoutPlan.cells.size) layoutPlan.cells[index] else null
            val mult = quality.multiplier
            val targetW = cell?.let { (it.contentBounds.width * mult).roundToInt().coerceAtLeast(600) } ?: 800
            val targetH = cell?.let { (it.contentBounds.height * mult).roundToInt().coerceAtLeast(600) } ?: 800

            val bitmap = loadOptimizedBitmap(
                context = context,
                photo = photoItem,
                targetWidth = targetW,
                targetHeight = targetH
            )

            if (bitmap != null) {
                val bos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality.jpegQuality, bos)
                val jpegBytes = bos.toByteArray()
                compressedImages.add(
                    CompressedJpegImage(
                        width = bitmap.width,
                        height = bitmap.height,
                        jpegBytes = jpegBytes
                    )
                )
                bitmap.recycle()
            }
            onProgress?.invoke(index + 1, photos.size)
        }

        FileOutputStream(outputFile).use { fos ->
            writePdfStream(
                pageWidthPt = pageWidthPt,
                pageHeightPt = pageHeightPt,
                images = compressedImages,
                cells = layoutPlan.cells,
                outputStream = fos
            )
        }

        outputFile
    }

    /**
     * Direct PDF Writer writing standard ISO 32000-1 compliant PDF stream with /DCTDecode images.
     */
    fun writePdfStream(
        pageWidthPt: Float,
        pageHeightPt: Float,
        images: List<CompressedJpegImage>,
        cells: List<CellRects>,
        outputStream: OutputStream
    ) {
        val countingOut = CountingOutputStream(outputStream)
        val imageCount = images.size

        // Object IDs:
        // 1: Catalog
        // 2: Pages
        // 3: Page
        // 4 .. (3 + imageCount): Images
        // (4 + imageCount): Content stream
        val totalObjs = 4 + imageCount
        val contentObjId = totalObjs
        val offsets = LongArray(totalObjs + 1)

        // 1. PDF Header
        countingOut.write("%PDF-1.4\n%\u00E2\u00E3\u00CF\u00D3\n".toByteArray(Charsets.ISO_8859_1))

        // 2. Object 1: Catalog
        offsets[1] = countingOut.bytesWritten
        countingOut.write("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n".toByteArray(Charsets.US_ASCII))

        // 3. Object 2: Pages
        offsets[2] = countingOut.bytesWritten
        countingOut.write("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n".toByteArray(Charsets.US_ASCII))

        // 4. Object 3: Page
        offsets[3] = countingOut.bytesWritten
        val pageObjHeader = buildString {
            append("3 0 obj\n<<\n")
            append("  /Type /Page\n")
            append("  /Parent 2 0 R\n")
            append(String.format(Locale.US, "  /MediaBox [0 0 %.2f %.2f]\n", pageWidthPt, pageHeightPt))
            append("  /Resources <<\n")
            append("    /ProcSet [/PDF /ImageC]\n")
            append("    /XObject <<\n")
            for (i in 0 until imageCount) {
                append("      /Im${i + 1} ${4 + i} 0 R\n")
            }
            append("    >>\n")
            append("  >>\n")
            append("  /Contents $contentObjId 0 R\n")
            append(">>\nendobj\n")
        }
        countingOut.write(pageObjHeader.toByteArray(Charsets.US_ASCII))

        // 5. Image Objects (4 .. 3 + imageCount)
        for (i in 0 until imageCount) {
            val objId = 4 + i
            val img = images[i]
            offsets[objId] = countingOut.bytesWritten
            val imgHeader = buildString {
                append("$objId 0 obj\n<<\n")
                append("  /Type /XObject\n")
                append("  /Subtype /Image\n")
                append("  /Width ${img.width}\n")
                append("  /Height ${img.height}\n")
                append("  /ColorSpace /DeviceRGB\n")
                append("  /BitsPerComponent 8\n")
                append("  /Filter /DCTDecode\n")
                append("  /Length ${img.jpegBytes.size}\n")
                append(">>\nstream\r\n")
            }
            countingOut.write(imgHeader.toByteArray(Charsets.US_ASCII))
            countingOut.write(img.jpegBytes)
            countingOut.write("\r\nendstream\nendobj\n".toByteArray(Charsets.US_ASCII))
        }

        // 6. Object (4 + imageCount): Page Content Stream
        offsets[contentObjId] = countingOut.bytesWritten
        val contentBuilder = StringBuilder()
        // Fill clean white background
        contentBuilder.append(String.format(Locale.US, "q 1 1 1 rg 0 0 %.2f %.2f re f Q\n", pageWidthPt, pageHeightPt))

        for (i in 0 until minOf(imageCount, cells.size)) {
            val rect = cells[i].contentBounds
            val x = rect.left
            val y = pageHeightPt - rect.bottom
            val w = rect.width
            val h = rect.height

            // Draw Image
            contentBuilder.append(String.format(Locale.US, "q %.2f 0 0 %.2f %.2f %.2f cm /Im%d Do Q\n", w, h, x, y, i + 1))
            // Subtle 0.5pt border around photo
            contentBuilder.append(String.format(Locale.US, "q 0.85 0.85 0.85 RG 0.5 w %.2f %.2f %.2f %.2f re S Q\n", x, y, w, h))
        }

        val contentBytes = contentBuilder.toString().toByteArray(Charsets.US_ASCII)
        val contentHeader = "$contentObjId 0 obj\n<< /Length ${contentBytes.size} >>\nstream\r\n"
        countingOut.write(contentHeader.toByteArray(Charsets.US_ASCII))
        countingOut.write(contentBytes)
        countingOut.write("\r\nendstream\nendobj\n".toByteArray(Charsets.US_ASCII))

        // 7. XRef Table
        val startXref = countingOut.bytesWritten
        countingOut.write("xref\n0 ${totalObjs + 1}\n".toByteArray(Charsets.US_ASCII))
        countingOut.write("0000000000 65535 f \r\n".toByteArray(Charsets.US_ASCII))
        for (i in 1..totalObjs) {
            val entry = String.format(Locale.US, "%010d 00000 n \r\n", offsets[i])
            countingOut.write(entry.toByteArray(Charsets.US_ASCII))
        }

        // 8. Trailer & EOF
        val trailer = buildString {
            append("trailer\n<<\n")
            append("  /Size ${totalObjs + 1}\n")
            append("  /Root 1 0 R\n")
            append(">>\n")
            append("startxref\n")
            append("$startXref\n")
            append("%%EOF\r\n")
        }
        countingOut.write(trailer.toByteArray(Charsets.US_ASCII))
        countingOut.flush()
    }

    /**
     * Generates a high-res JPEG image of the A4 layout (for phone Gallery saving).
     */
    suspend fun generateImage(
        context: Context,
        photos: List<PhotoItem>,
        outputFile: File,
        pageWidth: Int = A4LayoutCalculator.A4_WIDTH_COMPRESSED,
        pageHeight: Int = A4LayoutCalculator.A4_HEIGHT_COMPRESSED,
        preset: LayoutPreset = LayoutPreset.JUSTIFIED
    ): File = withContext(Dispatchers.IO) {
        if (outputFile.exists()) {
            outputFile.delete()
        }
        outputFile.parentFile?.mkdirs()

        val aspectRatios = photos.map { it.aspectRatio }
        val layoutPlan = A4LayoutCalculator.computeLayout(
            photoAspectRatios = aspectRatios,
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            marginPx = 35f,
            gapPx = 14f,
            preset = preset
        )

        val bitmap = Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            isDither = true
        }

        val borderPaint = Paint().apply {
            color = Color.rgb(220, 224, 230)
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }

        photos.forEachIndexed { index, photoItem ->
            if (index < layoutPlan.cells.size) {
                val cell = layoutPlan.cells[index]
                val targetRect = cell.contentBounds.toAndroidRectF()

                val photoBmp = loadOptimizedBitmap(
                    context = context,
                    photo = photoItem,
                    targetWidth = targetRect.width().toInt(),
                    targetHeight = targetRect.height().toInt()
                )

                if (photoBmp != null) {
                    canvas.drawBitmap(photoBmp, null, targetRect, paint)
                    canvas.drawRect(targetRect, borderPaint)
                    photoBmp.recycle()
                }
            }
        }

        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        bitmap.recycle()

        outputFile
    }

    private fun loadOptimizedBitmap(
        context: Context,
        photo: PhotoItem,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? {
        return try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(photo.uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }

            val rawWidth = boundsOptions.outWidth
            val rawHeight = boundsOptions.outHeight
            if (rawWidth <= 0 || rawHeight <= 0) return null

            var inSampleSize = 1
            val maxTarget = max(targetWidth, targetHeight).coerceAtLeast(1)
            val maxRaw = max(rawWidth, rawHeight)
            while ((maxRaw / (inSampleSize * 2)) >= maxTarget) {
                inSampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }

            val decodedBitmap = context.contentResolver.openInputStream(photo.uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            val rotationAngle = getOrientationAngle(context, photo)
            if (rotationAngle != 0) {
                val matrix = Matrix().apply { postRotate(rotationAngle.toFloat()) }
                val rotated = Bitmap.createBitmap(
                    decodedBitmap,
                    0,
                    0,
                    decodedBitmap.width,
                    decodedBitmap.height,
                    matrix,
                    true
                )
                if (rotated != decodedBitmap) {
                    decodedBitmap.recycle()
                }
                rotated
            } else {
                decodedBitmap
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getOrientationAngle(context: Context, photo: PhotoItem): Int {
        return try {
            context.contentResolver.openInputStream(photo.uri)?.use { stream ->
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

    private class CountingOutputStream(private val out: OutputStream) : OutputStream() {
        var bytesWritten: Long = 0L
            private set

        override fun write(b: Int) {
            out.write(b)
            bytesWritten++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            bytesWritten += len
        }

        override fun flush() {
            out.flush()
        }

        override fun close() {
            out.close()
        }
    }
}
