package com.poshan.photoprint

import com.poshan.photoprint.core.model.PhotoItem
import com.poshan.photoprint.core.sorter.ChronologicalSorter
import org.junit.Assert.assertEquals
import org.junit.Test

class ChronologicalSorterTest {

    @Test
    fun testAscendingDateSorting() {
        val dummyUriString = "content://dummy/1"

        // Simulate 4 dates matching October 2026 samples:
        // 29 Oct, 05 Oct, 23 Oct, 10 Oct (unsorted input)
        val p29 = PhotoItem(
            id = "p29",
            uriString = dummyUriString,
            dateMillis = 1793262867000L, // 29 Oct
            orderIndex = 0
        )
        val p05 = PhotoItem(
            id = "p05",
            uriString = dummyUriString,
            dateMillis = 1791189720000L, // 05 Oct
            orderIndex = 1
        )
        val p23 = PhotoItem(
            id = "p23",
            uriString = dummyUriString,
            dateMillis = 1792746180000L, // 23 Oct
            orderIndex = 2
        )
        val p10 = PhotoItem(
            id = "p10",
            uriString = dummyUriString,
            dateMillis = 1791621720000L, // 10 Oct
            orderIndex = 3
        )

        val unsorted = listOf(p29, p05, p23, p10)
        val sorted = ChronologicalSorter.sortByAscendingDate(unsorted)

        // Expected order: p05 (05 Oct) -> p10 (10 Oct) -> p23 (23 Oct) -> p29 (29 Oct)
        assertEquals("p05", sorted[0].id)
        assertEquals("p10", sorted[1].id)
        assertEquals("p23", sorted[2].id)
        assertEquals("p29", sorted[3].id)

        // Verify orderIndex is sequential
        assertEquals(0, sorted[0].orderIndex)
        assertEquals(1, sorted[1].orderIndex)
        assertEquals(2, sorted[2].orderIndex)
        assertEquals(3, sorted[3].orderIndex)
    }

    @Test
    fun testManualReorder() {
        val dummyUriString = "content://dummy/1"
        val items = (0..3).map { i ->
            PhotoItem(id = "item_$i", uriString = dummyUriString, orderIndex = i)
        }

        // Drag item at index 0 to index 2
        val reordered = ChronologicalSorter.reorder(items, fromIndex = 0, toIndex = 2)

        assertEquals("item_1", reordered[0].id)
        assertEquals("item_2", reordered[1].id)
        assertEquals("item_0", reordered[2].id)
        assertEquals("item_3", reordered[3].id)

        // Check indices re-assigned
        assertEquals(0, reordered[0].orderIndex)
        assertEquals(1, reordered[1].orderIndex)
        assertEquals(2, reordered[2].orderIndex)
        assertEquals(3, reordered[3].orderIndex)
    }
}
