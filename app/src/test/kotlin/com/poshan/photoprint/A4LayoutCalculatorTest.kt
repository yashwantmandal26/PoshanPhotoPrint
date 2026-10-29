package com.poshan.photoprint

import com.poshan.photoprint.core.layout.A4LayoutCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class A4LayoutCalculatorTest {

    @Test
    fun testGridDimensions_AlwaysTwoColumns() {
        // Strict 2-column grid: 1-4 photos -> 2x2, 5-6 photos -> 3x2, 7-8 photos -> 4x2
        assertEquals(Pair(2, 2), A4LayoutCalculator.determineGrid(1))
        assertEquals(Pair(2, 2), A4LayoutCalculator.determineGrid(2))
        assertEquals(Pair(2, 2), A4LayoutCalculator.determineGrid(3))
        assertEquals(Pair(2, 2), A4LayoutCalculator.determineGrid(4))
        assertEquals(Pair(3, 2), A4LayoutCalculator.determineGrid(5))
        assertEquals(Pair(3, 2), A4LayoutCalculator.determineGrid(6))
        assertEquals(Pair(4, 2), A4LayoutCalculator.determineGrid(7))
        assertEquals(Pair(4, 2), A4LayoutCalculator.determineGrid(8))
    }

    @Test
    fun testThreePhotosLayout_R1C1_R1C2_R2C1() {
        val aspectRatios = listOf(1.33f, 0.75f, 1.33f)
        val plan = A4LayoutCalculator.computeLayout(
            photoAspectRatios = aspectRatios,
            pageWidth = 1240,
            pageHeight = 1754,
            marginPx = 35f,
            gapPx = 14f
        )

        assertEquals(2, plan.rows)
        assertEquals(2, plan.cols)
        assertEquals(3, plan.cells.size)

        val cell0 = plan.cells[0] // R1C1
        val cell1 = plan.cells[1] // R1C2
        val cell2 = plan.cells[2] // R2C1

        // R1C1 and R1C2 are on Row 1 (same top coordinate)
        assertEquals(cell0.cellBounds.top, cell1.cellBounds.top, 0.01f)
        // R1C2 is to the right of R1C1
        assertTrue(cell0.cellBounds.right < cell1.cellBounds.left)

        // R1C1 and R2C1 are on Column 1 (same left coordinate)
        assertEquals(cell0.cellBounds.left, cell2.cellBounds.left, 0.01f)
        // R2C1 is below R1C1
        assertTrue(cell0.cellBounds.bottom < cell2.cellBounds.top)
    }

    @Test
    fun testFourPhotosMixedOrientation_ZeroCropping() {
        // 2 Portrait (aspect 0.75) and 2 Landscape (aspect 1.333)
        val aspectRatios = listOf(0.75f, 0.75f, 1.333f, 1.333f)
        val plan = A4LayoutCalculator.computeLayout(
            photoAspectRatios = aspectRatios,
            pageWidth = 1240,
            pageHeight = 1754,
            marginPx = 35f,
            gapPx = 14f
        )

        assertEquals(2, plan.rows)
        assertEquals(2, plan.cols)
        assertEquals(4, plan.cells.size)

        for (cell in plan.cells) {
            val cellBounds = cell.cellBounds
            val contentBounds = cell.contentBounds

            // Content must be entirely inside cell bounds (Zero Cropping)
            assertTrue(
                "Content left (${contentBounds.left}) must be >= cell left (${cellBounds.left})",
                contentBounds.left >= cellBounds.left - 0.01f
            )
            assertTrue(
                "Content right (${contentBounds.right}) must be <= cell right (${cellBounds.right})",
                contentBounds.right <= cellBounds.right + 0.01f
            )
            assertTrue(
                "Content top (${contentBounds.top}) must be >= cell top (${cellBounds.top})",
                contentBounds.top >= cellBounds.top - 0.01f
            )
            assertTrue(
                "Content bottom (${contentBounds.bottom}) must be <= cell bottom (${cellBounds.bottom})",
                contentBounds.bottom <= cellBounds.bottom + 0.01f
            )
        }
    }

    @Test
    fun testSixPhotosA4Layout() {
        // 6 photos in 3x2 grid
        val aspectRatios = listOf(1.333f, 0.75f, 1.333f, 1.333f, 0.75f, 0.75f)
        val plan = A4LayoutCalculator.computeLayout(
            photoAspectRatios = aspectRatios,
            pageWidth = 1240,
            pageHeight = 1754,
            marginPx = 35f,
            gapPx = 14f
        )

        assertEquals(3, plan.rows)
        assertEquals(2, plan.cols)
        assertEquals(6, plan.cells.size)

        // Verify no overlap between cell 0 and cell 1 (adjacent in row 0)
        assertTrue(plan.cells[0].cellBounds.right < plan.cells[1].cellBounds.left)

        // Verify no overlap between cell 0 and cell 2 (adjacent in col 0)
        assertTrue(plan.cells[0].cellBounds.bottom < plan.cells[2].cellBounds.top)
    }

    @Test
    fun testTwoPhotosCompactLayout_CenteredInMiddle() {
        val aspectRatios = listOf(0.75f, 0.75f) // 2 portrait photos
        val plan = A4LayoutCalculator.computeLayout(
            photoAspectRatios = aspectRatios,
            pageWidth = 2480,
            pageHeight = 3508,
            marginPx = 60f,
            gapPx = 24f,
            preset = com.poshan.photoprint.core.layout.LayoutPreset.COMPACT
        )

        assertEquals(2, plan.cells.size)
        val p0 = plan.cells[0].contentBounds
        val p1 = plan.cells[1].contentBounds

        // Both photos must be in the same row (same top)
        assertEquals(p0.top, p1.top, 0.1f)
        assertEquals(p0.bottom, p1.bottom, 0.1f)

        // They must sit right next to each other separated by gapPx
        assertEquals(p0.right + 24f, p1.left, 0.1f)

        // The whole row must be centered vertically in the middle of 3508 page
        assertTrue("Photos must not be glued to the top margin in Compact mode", p0.top > 500f)
        assertTrue("Photos must not be glued to the bottom margin in Compact mode", p0.bottom < 3000f)

        // Equal top and bottom spacing on the A4 page
        val topSpace = p0.top
        val bottomSpace = 3508f - p0.bottom
        assertEquals(topSpace, bottomSpace, 1.0f)

        // Equal left and right spacing on the A4 page
        val leftSpace = p0.left
        val rightSpace = 2480f - p1.right
        assertEquals(leftSpace, rightSpace, 1.0f)
    }
}
