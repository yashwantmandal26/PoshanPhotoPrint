package com.poshan.photoprint.core.model

import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class DateSource {
    EXIF,
    FILE_NAME,
    OCR,
    MANUAL,
    UNKNOWN
}

data class PhotoItem(
    val id: String = UUID.randomUUID().toString(),
    val uriString: String,
    val dateMillis: Long? = null,
    val dateDisplay: String = formatDateDisplay(dateMillis),
    val dateSource: DateSource = DateSource.UNKNOWN,
    val width: Int = 0,
    val height: Int = 0,
    val orderIndex: Int = 0
) {
    val uri: Uri
        get() = Uri.parse(uriString)

    constructor(
        uri: Uri,
        id: String = UUID.randomUUID().toString(),
        dateMillis: Long? = null,
        dateDisplay: String = formatDateDisplay(dateMillis),
        dateSource: DateSource = DateSource.UNKNOWN,
        width: Int = 0,
        height: Int = 0,
        orderIndex: Int = 0
    ) : this(
        id = id,
        uriString = uri.toString(),
        dateMillis = dateMillis,
        dateDisplay = dateDisplay,
        dateSource = dateSource,
        width = width,
        height = height,
        orderIndex = orderIndex
    )

    val isLandscape: Boolean
        get() = width > height

    val aspectRatio: Float
        get() = if (height > 0) width.toFloat() / height.toFloat() else 1f

    companion object {
        fun formatDateDisplay(millis: Long?): String {
            if (millis == null || millis <= 0) return "तारीख अज्ञात / Unknown Date"
            val sdf = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
            return sdf.format(Date(millis))
        }

        fun formatDateOnly(millis: Long?): String {
            if (millis == null || millis <= 0) return "तारीख अज्ञात"
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            return sdf.format(Date(millis))
        }

        fun formatCompactDate(millis: Long?): String {
            if (millis == null || millis <= 0) return "तारीख अज्ञात"
            val sdf = SimpleDateFormat("dd/MM/yy hh:mm a", Locale.getDefault())
            return sdf.format(Date(millis))
        }
    }
}
