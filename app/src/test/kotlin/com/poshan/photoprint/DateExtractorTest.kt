package com.poshan.photoprint

import com.poshan.photoprint.core.date.DateExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DateExtractorTest {

    @Test
    fun testGpsBannerDateAndTimeExtraction() {
        // Typical GPS Map Camera stamp from Jharkhand
        val sampleText = """
            Chandipur, Jonka, Jharkhand 816116, भारत
            Lat 24.079308° Long 87.773035°
            शुक्रवार, 29/10/2026 10:55 AM GMT +05:30
            Note : Captured by GPS Map Camera
        """.trimIndent()

        val millis = DateExtractor.parseDateAndTimeFromText(sampleText)
        assertNotNull("Should extract timestamp from GPS banner", millis)

        val cal = Calendar.getInstance()
        cal.timeInMillis = millis!!

        assertEquals(2026, cal.get(Calendar.YEAR))
        assertEquals(Calendar.OCTOBER, cal.get(Calendar.MONTH))
        assertEquals(29, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(10, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(55, cal.get(Calendar.MINUTE))
    }

    @Test
    fun testAfternoonPmTimeSorting() {
        val morningText = "29/10/2026 10:15 AM"
        val afternoonText = "29/10/2026 01:30 PM"

        val morningMillis = DateExtractor.parseDateAndTimeFromText(morningText)!!
        val afternoonMillis = DateExtractor.parseDateAndTimeFromText(afternoonText)!!

        assertTrue("Morning photo must precede afternoon photo", morningMillis < afternoonMillis)

        val calAfternoon = Calendar.getInstance()
        calAfternoon.timeInMillis = afternoonMillis
        assertEquals(13, calAfternoon.get(Calendar.HOUR_OF_DAY)) // 1:30 PM = 13:30
        assertEquals(30, calAfternoon.get(Calendar.MINUTE))
    }

    @Test
    fun testHindiDigitsNormalization() {
        val hindiInput = "शुक्रवार, २९/१०/२०२६ १०:५५ AM"
        val normalized = DateExtractor.normalizeHindiDigits(hindiInput)
        assertEquals("शुक्रवार, 29/10/2026 10:55 AM", normalized)

        val millis = DateExtractor.parseDateAndTimeFromText(normalized)
        assertNotNull(millis)
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis!!
        assertEquals(29, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.OCTOBER, cal.get(Calendar.MONTH))
    }

    @Test
    fun testTextualMonthFormat() {
        val text = "Captured on 15 Oct 2026 11:45 AM at Anganwadi Center"
        val millis = DateExtractor.parseDateAndTimeFromText(text)
        assertNotNull(millis)

        val cal = Calendar.getInstance()
        cal.timeInMillis = millis!!
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.OCTOBER, cal.get(Calendar.MONTH))
        assertEquals(11, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(45, cal.get(Calendar.MINUTE))
    }

    @Test
    fun testUserReportPhotosWatermarkExtraction() {
        // Photo #1 from user's live test
        val photo1Text = """
            Jonka, Jharkhand, India
            Xqhc+jwh, Jonka, Jharkhand 816116, India
            Lat 24.979253° Long 87.772901°
            Monday, 16/03/2026 10:56 AM GMT +05:30
        """.trimIndent()

        val millis1 = DateExtractor.parseDateAndTimeFromText(photo1Text)
        assertNotNull("Photo 1 watermark must be extracted", millis1)
        val cal1 = Calendar.getInstance()
        cal1.timeInMillis = millis1!!
        assertEquals(2026, cal1.get(Calendar.YEAR))
        assertEquals(Calendar.MARCH, cal1.get(Calendar.MONTH))
        assertEquals(16, cal1.get(Calendar.DAY_OF_MONTH))
        assertEquals(10, cal1.get(Calendar.HOUR_OF_DAY))
        assertEquals(56, cal1.get(Calendar.MINUTE))

        // Photo #2 from user's live test
        val photo2Text = """
            Jonka, Jharkhand, India
            Xqhc+jwh, Jonka, Jharkhand 816116, India
            Lat 24.979286° Long 87.772938°
            Saturday, 06/06/2026 09:18 AM GMT +05:30
        """.trimIndent()

        val millis2 = DateExtractor.parseDateAndTimeFromText(photo2Text)
        assertNotNull("Photo 2 watermark must be extracted", millis2)
        val cal2 = Calendar.getInstance()
        cal2.timeInMillis = millis2!!
        assertEquals(2026, cal2.get(Calendar.YEAR))
        assertEquals(Calendar.JUNE, cal2.get(Calendar.MONTH))
        assertEquals(6, cal2.get(Calendar.DAY_OF_MONTH))
        assertEquals(9, cal2.get(Calendar.HOUR_OF_DAY))
        assertEquals(18, cal2.get(Calendar.MINUTE))
    }
}
