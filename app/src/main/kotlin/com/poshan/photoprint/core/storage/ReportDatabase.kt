package com.poshan.photoprint.core.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.poshan.photoprint.core.model.ReportRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

class ReportDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    companion object {
        private const val DATABASE_NAME = "poshan_reports.db"
        private const val DATABASE_VERSION = 2

        private const val TABLE_REPORTS = "reports"
        private const val COL_ID = "id"
        private const val COL_TITLE = "title"
        private const val COL_CREATED_AT = "created_at"
        private const val COL_PHOTO_COUNT = "photo_count"
        private const val COL_PDF_PATH = "pdf_path"
        private const val COL_IMAGE_PATH = "image_path"
        private const val COL_PHOTOS_JSON = "photos_json"

        @Volatile
        private var instance: ReportDatabase? = null

        fun getInstance(context: Context): ReportDatabase {
            return instance ?: synchronized(this) {
                instance ?: ReportDatabase(context).also { instance = it }
            }
        }
    }

    private val _reportsFlow = MutableStateFlow<List<ReportRecord>>(emptyList())
    val reportsFlow: Flow<List<ReportRecord>> = _reportsFlow.asStateFlow()

    init {
        refreshReportsCache()
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createSql = """
            CREATE TABLE $TABLE_REPORTS (
                $COL_ID TEXT PRIMARY KEY,
                $COL_TITLE TEXT NOT NULL,
                $COL_CREATED_AT INTEGER NOT NULL,
                $COL_PHOTO_COUNT INTEGER NOT NULL,
                $COL_PDF_PATH TEXT NOT NULL,
                $COL_IMAGE_PATH TEXT,
                $COL_PHOTOS_JSON TEXT
            )
        """.trimIndent()
        db.execSQL(createSql)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE $TABLE_REPORTS ADD COLUMN $COL_PHOTOS_JSON TEXT")
            } catch (_: Exception) {}
        }
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        try {
            db.execSQL("ALTER TABLE $TABLE_REPORTS ADD COLUMN $COL_PHOTOS_JSON TEXT")
        } catch (_: Exception) {}
    }

    suspend fun saveReport(report: ReportRecord) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_ID, report.id)
            put(COL_TITLE, report.title)
            put(COL_CREATED_AT, report.createdAtMillis)
            put(COL_PHOTO_COUNT, report.photoCount)
            put(COL_PDF_PATH, report.pdfPath)
            put(COL_IMAGE_PATH, report.imagePath)
            put(COL_PHOTOS_JSON, report.photosJson)
        }
        db.insertWithOnConflict(TABLE_REPORTS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        refreshReportsCache()
    }

    suspend fun getAllReports(): List<ReportRecord> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ReportRecord>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_REPORTS,
            null,
            null,
            null,
            null,
            null,
            "$COL_CREATED_AT DESC"
        )
        cursor.use {
            val idIdx = cursor.getColumnIndexOrThrow(COL_ID)
            val titleIdx = cursor.getColumnIndexOrThrow(COL_TITLE)
            val createdIdx = cursor.getColumnIndexOrThrow(COL_CREATED_AT)
            val countIdx = cursor.getColumnIndexOrThrow(COL_PHOTO_COUNT)
            val pdfIdx = cursor.getColumnIndexOrThrow(COL_PDF_PATH)
            val imgIdx = cursor.getColumnIndexOrThrow(COL_IMAGE_PATH)
            val jsonIdx = cursor.getColumnIndex(COL_PHOTOS_JSON)

            while (cursor.moveToNext()) {
                val photosJson = if (jsonIdx != -1) cursor.getString(jsonIdx) else null
                list.add(
                    ReportRecord(
                        id = cursor.getString(idIdx),
                        title = cursor.getString(titleIdx),
                        createdAtMillis = cursor.getLong(createdIdx),
                        photoCount = cursor.getInt(countIdx),
                        pdfPath = cursor.getString(pdfIdx),
                        imagePath = cursor.getString(imgIdx),
                        photosJson = photosJson
                    )
                )
            }
        }
        list
    }

    suspend fun updatePhotosJson(reportId: String, photosJson: String) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_PHOTOS_JSON, photosJson)
        }
        db.update(TABLE_REPORTS, values, "$COL_ID = ?", arrayOf(reportId))
        refreshReportsCache()
    }

    suspend fun deleteReport(reportId: String) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        // Also remove file if exists
        val cursor = db.query(TABLE_REPORTS, arrayOf(COL_PDF_PATH, COL_IMAGE_PATH), "$COL_ID = ?", arrayOf(reportId), null, null, null)
        cursor.use {
            if (cursor.moveToFirst()) {
                val pdfPath = cursor.getString(0)
                val imgPath = cursor.getString(1)
                pdfPath?.let { File(it).delete() }
                imgPath?.let { File(it).delete() }
            }
        }
        db.delete(TABLE_REPORTS, "$COL_ID = ?", arrayOf(reportId))
        refreshReportsCache()
    }

    private fun refreshReportsCache() {
        try {
            val db = readableDatabase
            val list = mutableListOf<ReportRecord>()
            val cursor = db.query(
                TABLE_REPORTS,
                null,
                null,
                null,
                null,
                null,
                "$COL_CREATED_AT DESC"
            )
            cursor.use {
                val idIdx = cursor.getColumnIndexOrThrow(COL_ID)
                val titleIdx = cursor.getColumnIndexOrThrow(COL_TITLE)
                val createdIdx = cursor.getColumnIndexOrThrow(COL_CREATED_AT)
                val countIdx = cursor.getColumnIndexOrThrow(COL_PHOTO_COUNT)
                val pdfIdx = cursor.getColumnIndexOrThrow(COL_PDF_PATH)
                val imgIdx = cursor.getColumnIndexOrThrow(COL_IMAGE_PATH)

                while (cursor.moveToNext()) {
                    list.add(
                        ReportRecord(
                            id = cursor.getString(idIdx),
                            title = cursor.getString(titleIdx),
                            createdAtMillis = cursor.getLong(createdIdx),
                            photoCount = cursor.getInt(countIdx),
                            pdfPath = cursor.getString(pdfIdx),
                            imagePath = cursor.getString(imgIdx)
                        )
                    )
                }
            }
            _reportsFlow.value = list
        } catch (_: Exception) { }
    }
}
