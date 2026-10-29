package com.poshan.photoprint

import com.poshan.photoprint.core.model.ReportRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportRecordTest {

    @Test
    fun testReportRecordMetadata() {
        val record = ReportRecord(
            id = "test_1",
            title = "अक्टूबर 2026 रिपोर्ट",
            createdAtMillis = 1793262867000L,
            photoCount = 4,
            pdfPath = "/data/user/0/app/cache/report.pdf",
            imagePath = "/data/user/0/app/cache/report.jpg"
        )

        assertEquals("test_1", record.id)
        assertEquals("अक्टूबर 2026 रिपोर्ट", record.title)
        assertEquals(4, record.photoCount)
        assertEquals("/data/user/0/app/cache/report.pdf", record.pdfPath)
        assertTrue(record.formattedDate.isNotBlank())
        assertTrue(record.defaultMonthTitle.contains("Report"))
    }
}
