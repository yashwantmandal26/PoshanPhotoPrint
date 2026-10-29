package com.poshan.photoprint

import com.poshan.photoprint.core.generator.A4PdfGenerator
import com.poshan.photoprint.core.generator.CompressedJpegImage
import com.poshan.photoprint.core.layout.A4LayoutCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class A4PdfGeneratorTest {

    @Test
    fun testDirectPdfStreamGeneration() {
        val bos = ByteArrayOutputStream()

        // Create 4 dummy JPEG byte payloads
        val dummyJpeg = ByteArray(500) { 0xFF.toByte() }
        val images = listOf(
            CompressedJpegImage(600, 800, dummyJpeg),
            CompressedJpegImage(600, 800, dummyJpeg),
            CompressedJpegImage(600, 800, dummyJpeg),
            CompressedJpegImage(600, 800, dummyJpeg)
        )

        val plan = A4LayoutCalculator.computeLayout(
            photoAspectRatios = listOf(0.75f, 0.75f, 0.75f, 0.75f),
            pageWidth = 595,
            pageHeight = 842,
            marginPx = 28f,
            gapPx = 10f
        )

        A4PdfGenerator.writePdfStream(
            pageWidthPt = 595.28f,
            pageHeightPt = 841.89f,
            images = images,
            cells = plan.cells,
            outputStream = bos
        )

        val pdfBytes = bos.toByteArray()
        val pdfString = String(pdfBytes, Charsets.US_ASCII)

        // 1. PDF Header verification
        assertTrue("PDF must start with %PDF-1.4", pdfString.startsWith("%PDF-1.4"))

        // 2. Objects verification
        assertTrue("Must contain Catalog", pdfString.contains("/Type /Catalog"))
        assertTrue("Must contain Pages", pdfString.contains("/Type /Pages"))
        assertTrue("Must contain Page", pdfString.contains("/Type /Page"))
        assertTrue("Must contain DCTDecode", pdfString.contains("/Filter /DCTDecode"))
        assertTrue("Must contain 4 Image references", pdfString.contains("/Im1") && pdfString.contains("/Im4"))

        // 3. Trailer verification
        assertTrue("Must contain xref table", pdfString.contains("xref"))
        assertTrue("Must contain trailer", pdfString.contains("trailer"))
        assertTrue("Must end with %%EOF", pdfString.trim().endsWith("%%EOF"))

        // 4. File size: for 4 x 500 byte images, file size should be small (~3-4 KB)
        assertTrue("Size should be under 10 KB for test", pdfBytes.size < 10000)
    }
}
