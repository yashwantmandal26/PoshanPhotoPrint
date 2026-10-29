package com.poshan.photoprint.core.layout

import android.graphics.RectF
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class LayoutPreset(
    val id: String,
    val labelEn: String,
    val labelHi: String
) {
    JUSTIFIED("justified", "Grid", "ग्रिड"),
    COMPACT("compact", "Compact", "सटा हुआ"),
    LEFT("left", "Left", "बायाँ"),
    CENTER("center", "Center", "मध्य"),
    RIGHT("right", "Right", "दायाँ")
}

data class PageRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    fun toAndroidRectF(): android.graphics.RectF = android.graphics.RectF(left, top, right, bottom)
}

data class CellRects(
    val index: Int,
    val cellBounds: PageRect,     // Total grid cell area including white space
    val contentBounds: PageRect  // Exact scaled photo placement (FIT_CENTER, zero crop)
)

data class A4PagePlan(
    val pageWidth: Int,
    val pageHeight: Int,
    val rows: Int,
    val cols: Int,
    val marginPx: Float,
    val gapPx: Float,
    val cells: List<CellRects>
)

object A4LayoutCalculator {

    // Optimized Compressed A4 Dimensions (Fast WhatsApp sharing, crisp print quality ~150-180 DPI)
    const val A4_WIDTH_COMPRESSED = 1240
    const val A4_HEIGHT_COMPRESSED = 1754

    // Full 300 DPI A4 Dimensions
    const val A4_WIDTH_300_DPI = 2480
    const val A4_HEIGHT_300_DPI = 3508

    // Standard 72 DPI A4 Dimensions (PostScript PDF points)
    const val A4_WIDTH_PDF_POINTS = 595
    const val A4_HEIGHT_PDF_POINTS = 842

    /**
     * Strict 2-column grid:
     * - Always 2 columns (unless 1 photo).
     * - Rows determined by ceil(count / 2), at least 2 rows for full grid.
     */
    fun determineGrid(count: Int): Pair<Int, Int> {
        val cols = 2
        val rows = when {
            count <= 4 -> 2
            count <= 6 -> 3
            count <= 8 -> 4
            else -> maxOf(2, ceil(count / 2.0).toInt())
        }
        return Pair(rows, cols)
    }

    /**
     * Compute exact positions and bounding boxes for N photos.
     * Guarantees zero cropping (FIT_CENTER) and uniform spacing.
     */
    fun computeLayout(
        photoAspectRatios: List<Float>,
        pageWidth: Int = A4_WIDTH_300_DPI,
        pageHeight: Int = A4_HEIGHT_300_DPI,
        marginPx: Float = 60f,
        gapPx: Float = 24f,
        preset: LayoutPreset = LayoutPreset.JUSTIFIED
    ): A4PagePlan {
        val count = photoAspectRatios.size
        if (count == 0) {
            return A4PagePlan(pageWidth, pageHeight, 1, 1, marginPx, gapPx, emptyList())
        }

        val cols = 2
        val gridRows = when {
            count <= 4 -> 2
            count <= 6 -> 3
            count <= 8 -> 4
            else -> maxOf(2, ceil(count / 2.0).toInt())
        }

        // How many rows are actually populated with photos
        val usedRows = ceil(count.toFloat() / cols).toInt()

        val printableWidth = pageWidth - (2 * marginPx) - ((cols - 1) * gapPx)
        val printableHeight = pageHeight - (2 * marginPx) - ((gridRows - 1) * gapPx)

        val cellWidth = printableWidth / cols
        val cellHeight = printableHeight / gridRows

        val isCompact = preset == LayoutPreset.COMPACT
        val isCenter = preset == LayoutPreset.CENTER
        val isLeft = preset == LayoutPreset.LEFT
        val isRight = preset == LayoutPreset.RIGHT

        val cells = mutableListOf<CellRects>()

        if (isCompact) {
            // COMPACT PRESET:
            // All photos cluster tightly together in the exact middle of the A4 page (both vertically and horizontally).
            // No empty row pushing photos to the top.
            val maxHPerRow = (pageHeight - (2 * marginPx) - ((usedRows - 1) * gapPx)) / usedRows
            val rowHeights = FloatArray(usedRows)
            val rowPhotos = mutableListOf<List<Float>>()

            for (r in 0 until usedRows) {
                val startIdx = r * cols
                val endIdx = minOf(count, startIdx + cols)
                val rowAspects = photoAspectRatios.subList(startIdx, endIdx)
                rowPhotos.add(rowAspects)

                val availW = printableWidth - ((rowAspects.size - 1) * gapPx)
                val sumAsp = rowAspects.sumOf { it.toDouble().coerceAtLeast(0.1) }.toFloat()
                // Natural height capped so photos fit both page width and page height comfortably
                val naturalH = minOf(cellHeight * 1.12f, availW / sumAsp, maxHPerRow)
                rowHeights[r] = naturalH
            }

            val totalClusterH = rowHeights.sum() + (usedRows - 1) * gapPx
            val clusterStartY = marginPx + ((pageHeight - (2 * marginPx) - totalClusterH).coerceAtLeast(0f) / 2f)

            var curY = clusterStartY
            for (r in 0 until usedRows) {
                val rowAspects = rowPhotos[r]
                val h = rowHeights[r]
                val widths = rowAspects.map { (h * it.coerceAtLeast(0.1f)) }
                val totalRowW = widths.sum() + ((widths.size - 1) * gapPx)
                val rowStartX = (pageWidth - totalRowW) / 2f

                var curX = rowStartX
                for (c in widths.indices) {
                    val idx = r * cols + c
                    val w = widths[c]
                    val contentBounds = PageRect(curX, curY, curX + w, curY + h)
                    val cellBounds = PageRect(curX, curY, curX + w, curY + h)
                    cells.add(CellRects(idx, cellBounds, contentBounds))
                    curX += w + gapPx
                }
                curY += h + gapPx
            }
        } else if (isCenter) {
            // CENTER PRESET:
            // Photos in each row are centered horizontally, and used rows are centered vertically
            val rowHeights = FloatArray(usedRows) { cellHeight }
            val totalClusterH = usedRows * cellHeight + (usedRows - 1) * gapPx
            val clusterStartY = marginPx + ((pageHeight - (2 * marginPx) - totalClusterH).coerceAtLeast(0f) / 2f)

            var curY = clusterStartY
            for (r in 0 until usedRows) {
                val startIdx = r * cols
                val endIdx = minOf(count, startIdx + cols)
                val rowCount = endIdx - startIdx
                val isOddLast = (rowCount == 1 && cols > 1)

                val actualCellLeft = if (isOddLast) marginPx + (printableWidth - cellWidth) / 2f else marginPx
                val rowW = if (isOddLast) cellWidth else printableWidth

                for (c in 0 until rowCount) {
                    val idx = startIdx + c
                    val aspect = photoAspectRatios[idx].coerceAtLeast(0.1f)
                    val cLeft = if (isOddLast) actualCellLeft else (marginPx + c * (cellWidth + gapPx))

                    val (cw, ch) = if (aspect >= (cellWidth / cellHeight)) {
                        Pair(cellWidth, cellWidth / aspect)
                    } else {
                        Pair(cellHeight * aspect, cellHeight)
                    }

                    val posX = cLeft + (cellWidth - cw) / 2f
                    val posY = curY + (cellHeight - ch) / 2f

                    val contentBounds = PageRect(posX, posY, posX + cw, posY + ch)
                    val cellBounds = PageRect(cLeft, curY, cLeft + cellWidth, curY + cellHeight)
                    cells.add(CellRects(idx, cellBounds, contentBounds))
                }
                curY += cellHeight + gapPx
            }
        } else {
            // JUSTIFIED (GRID) / LEFT / RIGHT PRESETS:
            val rowHeights = FloatArray(usedRows) { cellHeight }
            // For Left / Right / Justified, center vertically if fewer than full grid rows are used
            val totalClusterH = usedRows * cellHeight + (usedRows - 1) * gapPx
            val clusterStartY = if (usedRows < gridRows) {
                marginPx + ((pageHeight - (2 * marginPx) - totalClusterH).coerceAtLeast(0f) / 2f)
            } else {
                marginPx
            }

            var curY = clusterStartY
            for (r in 0 until usedRows) {
                val startIdx = r * cols
                val endIdx = minOf(count, startIdx + cols)
                val rowCount = endIdx - startIdx
                val isOddLast = (rowCount == 1 && cols > 1)

                for (c in 0 until rowCount) {
                    val idx = startIdx + c
                    val aspect = photoAspectRatios[idx].coerceAtLeast(0.1f)
                    val cLeft = marginPx + c * (cellWidth + gapPx)

                    val (cw, ch) = if (aspect >= (cellWidth / cellHeight)) {
                        Pair(cellWidth, cellWidth / aspect)
                    } else {
                        Pair(cellHeight * aspect, cellHeight)
                    }

                    val posX = when {
                        isLeft -> cLeft
                        isRight -> cLeft + (cellWidth - cw)
                        isOddLast -> (pageWidth - cw) / 2f
                        else -> cLeft + (cellWidth - cw) / 2f
                    }
                    val posY = curY + (cellHeight - ch) / 2f

                    val contentBounds = PageRect(posX, posY, posX + cw, posY + ch)
                    val cellBounds = PageRect(cLeft, curY, cLeft + cellWidth, curY + cellHeight)
                    cells.add(CellRects(idx, cellBounds, contentBounds))
                }
                curY += cellHeight + gapPx
            }
        }

        return A4PagePlan(
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            rows = if (isCompact) usedRows else gridRows,
            cols = cols,
            marginPx = marginPx,
            gapPx = gapPx,
            cells = cells
        )
    }
}