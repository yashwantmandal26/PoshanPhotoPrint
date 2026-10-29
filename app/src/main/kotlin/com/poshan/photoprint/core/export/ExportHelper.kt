package com.poshan.photoprint.core.export

import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.poshan.photoprint.core.storage.AppPreferenceManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object ExportHelper {

    /**
     * 1-Tap direct share of A4 PDF to WhatsApp (or WhatsApp Business) for CDPO/Supervisor.
     */
    fun shareToWhatsApp(context: Context, pdfFile: File) {
        if (!pdfFile.exists()) {
            Toast.makeText(context, "PDF फाइल नहीं मिली", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )

        // Try standard WhatsApp first
        val waIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "आँगनबाड़ी केंद्र मासिक A4 फोटो रिपोर्ट (Poshan Photo Print)")
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            context.startActivity(waIntent)
            return
        } catch (_: Exception) { }

        // Try WhatsApp Business next
        val waBusinessIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "आँगनबाड़ी केंद्र मासिक A4 फोटो रिपोर्ट (Poshan Photo Print)")
            setPackage("com.whatsapp.w4b")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            context.startActivity(waBusinessIntent)
            return
        } catch (_: Exception) { }

        // Fallback to standard Android Share Sheet
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "A4 PDF रिपोर्ट शेयर करें"))
    }

    /**
     * Native Android Print Spooler integration for direct wireless/OTG cable printing.
     */
    fun printDocument(context: Context, pdfFile: File, jobName: String = "Poshan_A4_Report") {
        if (!pdfFile.exists()) {
            Toast.makeText(context, "प्रिंट करने के लिए PDF नहीं मिली", Toast.LENGTH_SHORT).show()
            return
        }

        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            Toast.makeText(context, "फोन में प्रिंट सेवा उपलब्ध नहीं है", Toast.LENGTH_SHORT).show()
            return
        }

        val printAdapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder("$jobName.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(1)
                    .build()
                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                var input: FileInputStream? = null
                var output: FileOutputStream? = null
                try {
                    input = FileInputStream(pdfFile)
                    output = FileOutputStream(destination?.fileDescriptor)
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } >= 0) {
                        output.write(buffer, 0, bytesRead)
                    }
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.message)
                } finally {
                    input?.close()
                    output?.close()
                }
            }
        }

        val printAttributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .build()

        printManager.print(jobName, printAdapter, printAttributes)
    }

    /**
     * Saves PDF to public Downloads using MediaStore (Android 10+) or standard Downloads dir.
     */
    fun savePdfToPublicDownloads(context: Context, pdfFile: File): Uri? {
        if (!pdfFile.exists()) return null

        val fileName = pdfFile.name
        val resolver = context.contentResolver

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/PoshanReports")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val itemUri = resolver.insert(collection, contentValues) ?: return null

            resolver.openOutputStream(itemUri)?.use { out ->
                FileInputStream(pdfFile).use { it.copyTo(out) }
            }

            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(itemUri, contentValues, null, null)
            return itemUri
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val subDir = File(downloadsDir, "PoshanReports").apply { mkdirs() }
            val dest = File(subDir, fileName)
            pdfFile.copyTo(dest, overwrite = true)
            return Uri.fromFile(dest)
        }
    }

    /**
     * Saves high-res image to the phone's Gallery album ("Poshan Photo Print").
     */
    fun saveImageToGallery(context: Context, imageFile: File): Uri? {
        if (!imageFile.exists()) return null

        val fileName = imageFile.name
        val resolver = context.contentResolver

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/PoshanPhotoPrint")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val itemUri = resolver.insert(collection, contentValues) ?: return null

            resolver.openOutputStream(itemUri)?.use { out ->
                FileInputStream(imageFile).use { it.copyTo(out) }
            }

            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(itemUri, contentValues, null, null)
            return itemUri
        } else {
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val subDir = File(picturesDir, "PoshanPhotoPrint").apply { mkdirs() }
            val dest = File(subDir, fileName)
            imageFile.copyTo(dest, overwrite = true)
            return Uri.fromFile(dest)
        }
    }

    /**
     * Opens PDF in the device's default or saved preferred PDF viewer.
     * Uses saved preference to launch instantly without repeatedly prompting the user.
     */
    fun openPdf(context: Context, pdfFile: File) {
        if (!pdfFile.exists()) return
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // If user already picked and saved a preferred viewer, launch directly!
            val preferredPkg = AppPreferenceManager.getPreferredPdfPackage(context)
            if (preferredPkg != null) {
                intent.setPackage(preferredPkg)
                context.startActivity(intent)
                return
            }

            // First time: prompt with chooser and automatically remember the user's choice
            val receiverIntent = Intent(context, AppChooserReceiver::class.java).apply {
                action = AppChooserReceiver.ACTION_VIEWER_CHOSEN
                putExtra(AppChooserReceiver.EXTRA_MIME_TYPE, "application/pdf")
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                101,
                receiverIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            )
            val chooser = Intent.createChooser(intent, "Open PDF", pendingIntent.intentSender).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(chooser)
        } catch (_: Exception) {
            Toast.makeText(context, "No PDF viewer app found", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Opens Image in the device's default or saved preferred Gallery / Photo Viewer.
     * Uses saved preference to launch instantly without repeatedly prompting the user.
     */
    fun openImage(context: Context, imageFile: File) {
        if (!imageFile.exists()) return
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "image/jpeg")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // If user already picked and saved a preferred viewer, launch directly!
            val preferredPkg = AppPreferenceManager.getPreferredImagePackage(context)
            if (preferredPkg != null) {
                intent.setPackage(preferredPkg)
                context.startActivity(intent)
                return
            }

            // First time: prompt with chooser and automatically remember the user's choice
            val receiverIntent = Intent(context, AppChooserReceiver::class.java).apply {
                action = AppChooserReceiver.ACTION_VIEWER_CHOSEN
                putExtra(AppChooserReceiver.EXTRA_MIME_TYPE, "image/jpeg")
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                102,
                receiverIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            )
            val chooser = Intent.createChooser(intent, "Open Image", pendingIntent.intentSender).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(chooser)
        } catch (_: Exception) {
            Toast.makeText(context, "No photo viewer found", Toast.LENGTH_SHORT).show()
        }
    }
}
