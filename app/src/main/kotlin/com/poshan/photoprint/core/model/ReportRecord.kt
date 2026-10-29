package com.poshan.photoprint.core.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class ReportRecord(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val photoCount: Int,
    val pdfPath: String,
    val imagePath: String? = null,
    val photosJson: String? = null
) {
    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("dd MMMM yyyy, hh:mm a", Locale.ENGLISH)
            return sdf.format(Date(createdAtMillis))
        }

    val displayTitle: String
        get() = toEnglishTitle(title, photoCount, createdAtMillis)

    val defaultMonthTitle: String
        get() = formatMonthTitle(createdAtMillis)

    companion object {
        fun formatMonthTitle(millis: Long): String {
            val sdf = SimpleDateFormat("MMMM yyyy", Locale.ENGLISH)
            return "${sdf.format(Date(millis))} Report"
        }

        fun toEnglishTitle(rawTitle: String, count: Int, millis: Long): String {
            val countSuffix = if (count == 1) "1 Photo" else "$count Photos"
            // If the title contains Hindi characters or keywords, convert cleanly to English
            if (rawTitle.contains("रिपोर्ट") || rawTitle.contains("फोटो") || rawTitle.any { it in '\u0900'..'\u097F' }) {
                val monthTitle = formatMonthTitle(millis)
                return "$monthTitle ($countSuffix)"
            }
            return rawTitle
        }
    }
}
