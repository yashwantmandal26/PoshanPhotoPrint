package com.poshan.photoprint.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poshan.photoprint.core.date.DateExtractor
import com.poshan.photoprint.core.generator.A4PdfGenerator
import com.poshan.photoprint.core.generator.ExportQuality
import com.poshan.photoprint.core.layout.A4LayoutCalculator
import com.poshan.photoprint.core.layout.A4PagePlan
import com.poshan.photoprint.core.layout.LayoutPreset
import com.poshan.photoprint.core.model.DateSource
import com.poshan.photoprint.core.model.PhotoItem
import com.poshan.photoprint.core.model.ReportRecord
import com.poshan.photoprint.core.sorter.ChronologicalSorter
import com.poshan.photoprint.core.storage.ReportDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class PoshanUiState(
    val photos: List<PhotoItem> = emptyList(),
    val isLoading: Boolean = false,
    val loadingMessage: String = "",
    val a4Plan: A4PagePlan? = null,
    val generatedPdfFile: File? = null,
    val generatedImageFile: File? = null,
    val isExporting: Boolean = false,
    val editingPhoto: PhotoItem? = null,
    val replacingIndex: Int? = null,
    val snackbarMessage: String? = null,
    val savedReports: List<ReportRecord> = emptyList(),
    val isHindi: Boolean = false,
    val isDarkTheme: Boolean = false, // White theme mode by default
    val exportQuality: ExportQuality = ExportQuality.WHATSAPP,
    val layoutPreset: LayoutPreset = LayoutPreset.JUSTIFIED,
    val croppingPhoto: PhotoItem? = null
)

class PoshanViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PoshanUiState())
    val uiState: StateFlow<PoshanUiState> = _uiState.asStateFlow()

    fun initLanguage(context: Context) {
        val prefs = context.getSharedPreferences("poshan_prefs", Context.MODE_PRIVATE)
        val savedIsHindi = prefs.getBoolean("is_hindi", false) // English default
        val savedIsDark = prefs.getBoolean("is_dark_theme", false) // White theme default
        _uiState.update { it.copy(isHindi = savedIsHindi, isDarkTheme = savedIsDark) }
    }

    fun toggleLanguage(context: Context) {
        val next = !_uiState.value.isHindi
        val prefs = context.getSharedPreferences("poshan_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_hindi", next).apply()
        _uiState.update { it.copy(isHindi = next) }
    }

    fun toggleTheme(context: Context) {
        val next = !_uiState.value.isDarkTheme
        val prefs = context.getSharedPreferences("poshan_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_dark_theme", next).apply()
        _uiState.update { it.copy(isDarkTheme = next) }
    }

    fun loadSelectedPhotos(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    loadingMessage = "फोटो और तारीख पढ़ी जा रही है... (0/${uris.size})"
                )
            }

            val photoItems = mutableListOf<PhotoItem>()
            val appContext = context.applicationContext

            uris.forEachIndexed { index, uri ->
                _uiState.update {
                    it.copy(
                        loadingMessage = "फोटो ${index + 1}/${uris.size} की तारीख जांची जा रही है..."
                    )
                }

                val photoId = UUID.randomUUID().toString()
                val persistentUri = persistPhotoLocally(appContext, uri, photoId)
                val fileName = getFileName(appContext, persistentUri)
                val result = DateExtractor.extract(appContext, persistentUri, fileName)

                photoItems.add(
                    PhotoItem(
                        id = photoId,
                        uri = persistentUri,
                        dateMillis = result.dateMillis,
                        dateDisplay = PhotoItem.formatDateDisplay(result.dateMillis),
                        dateSource = result.source,
                        width = result.width,
                        height = result.height,
                        orderIndex = index
                    )
                )
            }

            // Auto-sort ascending by date
            val sortedPhotos = ChronologicalSorter.sortByAscendingDate(photoItems)
            val plan = A4LayoutCalculator.computeLayout(sortedPhotos.map { it.aspectRatio }, preset = _uiState.value.layoutPreset)

            _uiState.update {
                it.copy(
                    photos = sortedPhotos,
                    a4Plan = plan,
                    isLoading = false,
                    loadingMessage = ""
                )
            }
            generateA4Files(appContext)
        }
    }

    fun reorderPhoto(fromIndex: Int, toIndex: Int) {
        val current = _uiState.value.photos
        val reordered = ChronologicalSorter.reorder(current, fromIndex, toIndex)
        val plan = A4LayoutCalculator.computeLayout(reordered.map { it.aspectRatio }, preset = _uiState.value.layoutPreset)

        _uiState.update {
            it.copy(photos = reordered, a4Plan = plan)
        }
    }

    fun swapPhotos(indexA: Int, indexB: Int, context: Context) {
        val current = _uiState.value.photos.toMutableList()
        if (indexA in current.indices && indexB in current.indices && indexA != indexB) {
            val temp = current[indexA]
            current[indexA] = current[indexB].copy(orderIndex = indexA)
            current[indexB] = temp.copy(orderIndex = indexB)

            val plan = A4LayoutCalculator.computeLayout(current.map { it.aspectRatio }, preset = _uiState.value.layoutPreset)
            val isHindi = _uiState.value.isHindi
            _uiState.update {
                it.copy(
                    photos = current,
                    a4Plan = plan,
                    snackbarMessage = PoshanStrings.swapNotification(isHindi, indexA + 1, indexB + 1)
                )
            }
            generateA4Files(context)
        }
    }

    fun resetToDefaultSort(context: Context) {
        val current = _uiState.value.photos
        val sorted = ChronologicalSorter.sortByAscendingDate(current)
        val plan = A4LayoutCalculator.computeLayout(sorted.map { it.aspectRatio }, preset = _uiState.value.layoutPreset)
        val isHindi = _uiState.value.isHindi
        _uiState.update {
            it.copy(
                photos = sorted,
                a4Plan = plan,
                snackbarMessage = PoshanStrings.resetNotification(isHindi)
            )
        }
        generateA4Files(context)
    }

    fun movePhotoUp(index: Int) {
        if (index > 0) {
            reorderPhoto(index, index - 1)
        }
    }

    fun movePhotoDown(index: Int) {
        if (index < _uiState.value.photos.size - 1) {
            reorderPhoto(index, index + 1)
        }
    }

    fun startEditingDate(photo: PhotoItem) {
        _uiState.update { it.copy(editingPhoto = photo) }
    }

    fun cancelEditingDate() {
        _uiState.update { it.copy(editingPhoto = null) }
    }

    fun applyDateChange(photoId: String, newDateMillis: Long) {
        val updated = _uiState.value.photos.map { item ->
            if (item.id == photoId) {
                item.copy(
                    dateMillis = newDateMillis,
                    dateDisplay = PhotoItem.formatDateDisplay(newDateMillis),
                    dateSource = DateSource.MANUAL
                )
            } else {
                item
            }
        }

        _uiState.update {
            it.copy(
                photos = updated,
                editingPhoto = null,
                snackbarMessage = "तारीख सुरक्षित हो गई"
            )
        }
    }

    fun setExportQuality(quality: ExportQuality, context: Context) {
        if (_uiState.value.exportQuality != quality) {
            _uiState.update { it.copy(exportQuality = quality) }
            generateA4Files(context)
        }
    }

    fun setLayoutPreset(preset: LayoutPreset, context: Context) {
        if (_uiState.value.layoutPreset != preset) {
            _uiState.update { it.copy(layoutPreset = preset) }
            val photos = _uiState.value.photos
            if (photos.isNotEmpty()) {
                val plan = A4LayoutCalculator.computeLayout(
                    photos.map { it.aspectRatio },
                    preset = preset
                )
                _uiState.update { it.copy(a4Plan = plan) }
                generateA4Files(context)
            }
        }
    }

    fun replaceSinglePhoto(context: Context, index: Int, newUri: Uri) {
        if (index !in _uiState.value.photos.indices) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    loadingMessage = if (it.isHindi) "फोटो #${index + 1} बदली जा रही है..." else "Replacing photo #${index + 1}..."
                )
            }
            val appContext = context.applicationContext
            val photoId = UUID.randomUUID().toString()
            val persistentUri = persistPhotoLocally(appContext, newUri, photoId)
            val fileName = getFileName(appContext, persistentUri)
            val result = DateExtractor.extract(appContext, persistentUri, fileName)

            val updatedPhoto = PhotoItem(
                id = photoId,
                uri = persistentUri,
                dateMillis = result.dateMillis,
                dateDisplay = PhotoItem.formatDateDisplay(result.dateMillis),
                dateSource = result.source,
                width = result.width,
                height = result.height,
                orderIndex = index
            )

            val mutable = _uiState.value.photos.toMutableList()
            mutable[index] = updatedPhoto
            val plan = A4LayoutCalculator.computeLayout(mutable.map { it.aspectRatio }, preset = _uiState.value.layoutPreset)

            _uiState.update {
                it.copy(
                    photos = mutable,
                    a4Plan = plan,
                    isLoading = false,
                    snackbarMessage = if (it.isHindi) "फोटो #${index + 1} बदल दी गई" else "Photo #${index + 1} replaced successfully"
                )
            }
            generateA4Files(appContext)
        }
    }

    fun addPhotosToCurrentReport(context: Context, newUris: List<Uri>) {
        if (newUris.isEmpty()) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    loadingMessage = if (it.isHindi) "नई फोटो जोड़ी जा रही हैं..." else "Adding new photos..."
                )
            }
            val appContext = context.applicationContext
            val currentList = _uiState.value.photos.toMutableList()
            val startOrderIndex = currentList.size

            val newlyExtracted = newUris.mapIndexed { idx, uri ->
                val photoId = UUID.randomUUID().toString()
                val persistentUri = persistPhotoLocally(appContext, uri, photoId)
                val fileName = getFileName(appContext, persistentUri)
                val result = DateExtractor.extract(appContext, persistentUri, fileName)
                PhotoItem(
                    id = photoId,
                    uri = persistentUri,
                    dateMillis = result.dateMillis,
                    dateDisplay = PhotoItem.formatDateDisplay(result.dateMillis),
                    dateSource = result.source,
                    width = result.width,
                    height = result.height,
                    orderIndex = startOrderIndex + idx
                )
            }

            currentList.addAll(newlyExtracted)
            val finalPhotos = currentList.mapIndexed { i, p -> p.copy(orderIndex = i) }

            val plan = A4LayoutCalculator.computeLayout(
                finalPhotos.map { it.aspectRatio },
                preset = _uiState.value.layoutPreset
            )

            _uiState.update {
                it.copy(
                    photos = finalPhotos,
                    a4Plan = plan,
                    isLoading = false,
                    snackbarMessage = if (it.isHindi) "${newlyExtracted.size} नई फोटो जोड़ी गईं" else "${newlyExtracted.size} new photo(s) added"
                )
            }

            generateA4Files(appContext)
        }
    }

    fun startReplacingPhoto(index: Int) {
        _uiState.update { it.copy(replacingIndex = index) }
    }

    fun replacePhoto(context: Context, newUri: Uri) {
        val index = _uiState.value.replacingIndex ?: return
        if (index !in _uiState.value.photos.indices) return

        viewModelScope.launch {
            val appContext = context.applicationContext
            val photoId = UUID.randomUUID().toString()
            val persistentUri = persistPhotoLocally(appContext, newUri, photoId)
            val fileName = getFileName(appContext, persistentUri)
            val result = DateExtractor.extract(appContext, persistentUri, fileName)

            val updatedPhoto = PhotoItem(
                id = photoId,
                uri = persistentUri,
                dateMillis = result.dateMillis,
                dateDisplay = PhotoItem.formatDateDisplay(result.dateMillis),
                dateSource = result.source,
                width = result.width,
                height = result.height,
                orderIndex = index
            )

            val mutable = _uiState.value.photos.toMutableList()
            mutable[index] = updatedPhoto
            val plan = A4LayoutCalculator.computeLayout(mutable.map { it.aspectRatio }, preset = _uiState.value.layoutPreset)

            _uiState.update {
                it.copy(
                    photos = mutable,
                    a4Plan = plan,
                    replacingIndex = null,
                    snackbarMessage = "फोटो बदल दी गई"
                )
            }
        }
    }

    fun startCroppingPhoto(photo: PhotoItem) {
        _uiState.update { it.copy(croppingPhoto = photo) }
    }

    fun cancelCroppingPhoto() {
        _uiState.update { it.copy(croppingPhoto = null) }
    }

    fun applyCroppedPhoto(context: Context, photoId: String, newUri: Uri) {
        val currentPhotos = _uiState.value.photos
        val index = currentPhotos.indexOfFirst { it.id == photoId }
        if (index == -1) return

        viewModelScope.launch {
            val appContext = context.applicationContext
            val targetPhoto = currentPhotos[index]

            var w = targetPhoto.width
            var h = targetPhoto.height
            try {
                appContext.contentResolver.openInputStream(newUri)?.use { stream ->
                    val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeStream(stream, null, opts)
                    w = opts.outWidth
                    h = opts.outHeight
                }
            } catch (_: Exception) { }

            val updatedPhoto = targetPhoto.copy(
                uriString = newUri.toString(),
                width = w,
                height = h
            )

            val mutable = currentPhotos.toMutableList()
            mutable[index] = updatedPhoto
            val plan = A4LayoutCalculator.computeLayout(mutable.map { it.aspectRatio }, preset = _uiState.value.layoutPreset)

            val isHindi = _uiState.value.isHindi
            _uiState.update {
                it.copy(
                    photos = mutable,
                    a4Plan = plan,
                    croppingPhoto = null,
                    snackbarMessage = if (isHindi) "फोटो क्रॉप हो गई" else "Photo cropped successfully"
                )
            }
            generateA4Files(appContext)
        }
    }

    fun removePhoto(index: Int, context: Context) {
        val mutable = _uiState.value.photos.toMutableList()
        if (index in mutable.indices) {
            mutable.removeAt(index)
            val reIndexed = mutable.mapIndexed { idx, item -> item.copy(orderIndex = idx) }
            val plan = A4LayoutCalculator.computeLayout(reIndexed.map { it.aspectRatio }, preset = _uiState.value.layoutPreset)
            val isHindi = _uiState.value.isHindi
            _uiState.update {
                it.copy(
                    photos = reIndexed,
                    a4Plan = plan,
                    snackbarMessage = if (isHindi) "फोटो #${index + 1} हटा दी गई" else "Photo #${index + 1} removed"
                )
            }
            if (reIndexed.isNotEmpty()) {
                generateA4Files(context)
            }
        }
    }

    fun generateA4Files(context: Context, onComplete: ((pdf: File, img: File) -> Unit)? = null) {
        val photos = _uiState.value.photos
        if (photos.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }

            val appContext = context.applicationContext
            val outputDir = File(appContext.cacheDir, "generated_reports").apply { mkdirs() }
            val timestamp = System.currentTimeMillis()
            val pdfFile = File(outputDir, "Poshan_Report_$timestamp.pdf")
            val imgFile = File(outputDir, "Poshan_Report_$timestamp.jpg")

            val quality = _uiState.value.exportQuality
            val preset = _uiState.value.layoutPreset
            A4PdfGenerator.generatePdf(appContext, photos, pdfFile, quality = quality, preset = preset)
            A4PdfGenerator.generateImage(appContext, photos, imgFile, preset = preset)

            // Auto-archive into Saved Reports database (Strictly in English)
            val db = ReportDatabase.getInstance(appContext)
            val monthTitle = ReportRecord.formatMonthTitle(timestamp)
            val countSuffix = if (photos.size == 1) "1 Photo" else "${photos.size} Photos"
            val record = ReportRecord(
                title = "$monthTitle ($countSuffix)",
                createdAtMillis = timestamp,
                photoCount = photos.size,
                pdfPath = pdfFile.absolutePath,
                imagePath = imgFile.absolutePath,
                photosJson = serializePhotosToJson(photos)
            )
            db.saveReport(record)
            val allReports = db.getAllReports()

            _uiState.update {
                it.copy(
                    generatedPdfFile = pdfFile,
                    generatedImageFile = imgFile,
                    isExporting = false,
                    savedReports = allReports
                )
            }

            onComplete?.invoke(pdfFile, imgFile)
        }
    }

    private fun serializePhotosToJson(photos: List<PhotoItem>): String {
        val arr = JSONArray()
        photos.forEach { photo ->
            val obj = JSONObject().apply {
                put("id", photo.id)
                put("uri", photo.uriString)
                put("dateMillis", photo.dateMillis ?: JSONObject.NULL)
                put("dateDisplay", photo.dateDisplay)
                put("dateSource", photo.dateSource.name)
                put("width", photo.width)
                put("height", photo.height)
                put("orderIndex", photo.orderIndex)
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun canReadUri(context: Context, uri: Uri): Boolean {
        return try {
            if (uri.scheme == "file") {
                val f = File(uri.path ?: "")
                f.exists() && f.canRead() && f.length() > 0
            } else {
                context.contentResolver.openInputStream(uri)?.use { true } ?: false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun persistPhotoLocally(context: Context, sourceUri: Uri, photoId: String): Uri {
        return try {
            if (sourceUri.scheme == "file") {
                val f = File(sourceUri.path ?: "")
                if (f.exists() && f.absolutePath.startsWith(context.filesDir.absolutePath)) {
                    return sourceUri
                }
            }
            val photosDir = File(context.filesDir, "report_photos").apply { mkdirs() }
            val targetFile = File(photosDir, "${photoId}.jpg")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (targetFile.exists() && targetFile.length() > 0) {
                Uri.fromFile(targetFile)
            } else {
                sourceUri
            }
        } catch (e: Exception) {
            e.printStackTrace()
            sourceUri
        }
    }

    fun loadReportForEditing(context: Context, report: ReportRecord) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadingMessage = "Opening report in edit mode...") }

            val appContext = context.applicationContext
            val photosDir = File(appContext.filesDir, "report_photos").apply { mkdirs() }
            val loadedPhotos = mutableListOf<PhotoItem>()
            if (!report.photosJson.isNullOrBlank()) {
                try {
                    val jsonArr = JSONArray(report.photosJson)
                    for (i in 0 until jsonArr.length()) {
                        val obj = jsonArr.getJSONObject(i)
                        val id = obj.optString("id", UUID.randomUUID().toString())
                        val uriStr = obj.getString("uri")
                        val dateMillis = if (obj.has("dateMillis") && !obj.isNull("dateMillis")) obj.getLong("dateMillis") else null
                        val dateSourceStr = obj.optString("dateSource", "UNKNOWN")
                        val dateSource = try { DateSource.valueOf(dateSourceStr) } catch (_: Exception) { DateSource.UNKNOWN }
                        val width = obj.optInt("width", 0)
                        val height = obj.optInt("height", 0)
                        val orderIndex = obj.optInt("orderIndex", i)
                        val dateDisplay = if (obj.has("dateDisplay")) obj.getString("dateDisplay") else PhotoItem.formatDateDisplay(dateMillis)

                        var finalUriStr = uriStr
                        val parsedUri = Uri.parse(uriStr)
                        if (!canReadUri(appContext, parsedUri)) {
                            // Check if photo was already extracted or saved under filesDir
                            val candidate = File(photosDir, "${id}.jpg")
                            if (candidate.exists() && candidate.length() > 0) {
                                finalUriStr = Uri.fromFile(candidate).toString()
                            }
                        }

                        loadedPhotos.add(
                            PhotoItem(
                                id = id,
                                uriString = finalUriStr,
                                dateMillis = dateMillis,
                                dateDisplay = dateDisplay,
                                dateSource = dateSource,
                                width = width,
                                height = height,
                                orderIndex = orderIndex
                            )
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Fallback for legacy reports without serialized photo items
            if (loadedPhotos.isEmpty()) {
                if (report.imagePath != null && File(report.imagePath).exists()) {
                    val file = File(report.imagePath)
                    val uri = Uri.fromFile(file)
                    loadedPhotos.add(
                        PhotoItem(
                            uri = uri,
                            dateMillis = report.createdAtMillis,
                            dateDisplay = PhotoItem.formatDateDisplay(report.createdAtMillis),
                            dateSource = DateSource.MANUAL
                        )
                    )
                }
            }

            // Extract individual photo crops from composite A4 image if original URIs are unreadable
            val hasUnreadablePhotos = loadedPhotos.any { !canReadUri(appContext, it.uri) }
            if (hasUnreadablePhotos && report.imagePath != null && File(report.imagePath).exists()) {
                try {
                    val a4Bitmap = BitmapFactory.decodeFile(report.imagePath)
                    if (a4Bitmap != null) {
                        val aspectRatios = loadedPhotos.map { it.aspectRatio }
                        val plan = A4LayoutCalculator.computeLayout(
                            photoAspectRatios = aspectRatios,
                            pageWidth = a4Bitmap.width,
                            pageHeight = a4Bitmap.height,
                            preset = _uiState.value.layoutPreset
                        )

                        loadedPhotos.forEachIndexed { idx, photo ->
                            if (!canReadUri(appContext, photo.uri) && idx < plan.cells.size) {
                                val cell = plan.cells[idx]
                                val cx = cell.contentBounds.left.toInt().coerceIn(0, a4Bitmap.width - 1)
                                val cy = cell.contentBounds.top.toInt().coerceIn(0, a4Bitmap.height - 1)
                                val cw = cell.contentBounds.width.toInt().coerceIn(1, a4Bitmap.width - cx)
                                val ch = cell.contentBounds.height.toInt().coerceIn(1, a4Bitmap.height - cy)
                                val cellBitmap = Bitmap.createBitmap(a4Bitmap, cx, cy, cw, ch)
                                val targetFile = File(photosDir, "${photo.id}.jpg")
                                FileOutputStream(targetFile).use { out ->
                                    cellBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                                }
                                cellBitmap.recycle()
                                loadedPhotos[idx] = photo.copy(
                                    uriString = Uri.fromFile(targetFile).toString(),
                                    width = cw,
                                    height = ch
                                )
                            }
                        }
                        a4Bitmap.recycle()

                        // Update database with permanent local URIs
                        val updatedJson = serializePhotosToJson(loadedPhotos)
                        val db = ReportDatabase.getInstance(appContext)
                        db.updatePhotosJson(report.id, updatedJson)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (loadedPhotos.isNotEmpty()) {
                val plan = A4LayoutCalculator.computeLayout(
                    loadedPhotos.map { it.aspectRatio },
                    preset = _uiState.value.layoutPreset
                )
                val pdfFile = File(report.pdfPath).takeIf { it.exists() }
                val imgFile = report.imagePath?.let { File(it).takeIf { f -> f.exists() } }

                _uiState.update {
                    it.copy(
                        photos = loadedPhotos,
                        a4Plan = plan,
                        generatedPdfFile = pdfFile,
                        generatedImageFile = imgFile,
                        isLoading = false,
                        loadingMessage = ""
                    )
                }

                // If PDF was cleared from cache, regenerate in background
                if (pdfFile == null || !pdfFile.exists()) {
                    generateA4Files(context.applicationContext)
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadingMessage = "",
                        snackbarMessage = "No photos found in this report"
                    )
                }
            }
        }
    }

    fun loadSavedReports(context: Context) {
        viewModelScope.launch {
            val db = ReportDatabase.getInstance(context.applicationContext)
            val list = db.getAllReports()
            _uiState.update { it.copy(savedReports = list) }
        }
    }

    fun deleteSavedReport(context: Context, reportId: String) {
        viewModelScope.launch {
            val db = ReportDatabase.getInstance(context.applicationContext)
            db.deleteReport(reportId)
            val list = db.getAllReports()
            _uiState.update {
                it.copy(
                    savedReports = list,
                    snackbarMessage = "रिपोर्ट हटा दी गई"
                )
            }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        name = cursor.getString(nameIndex)
                    }
                }
            } catch (_: Exception) { }
        }
        if (name == null) {
            name = uri.lastPathSegment
        }
        return name
    }
}
