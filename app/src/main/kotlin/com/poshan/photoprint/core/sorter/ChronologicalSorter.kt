package com.poshan.photoprint.core.sorter

import com.poshan.photoprint.core.model.PhotoItem

object ChronologicalSorter {

    /**
     * Sorts photo items in strict ascending chronological order (earliest date first).
     * Items without dates are placed at the end while preserving their original order.
     */
    fun sortByAscendingDate(photos: List<PhotoItem>): List<PhotoItem> {
        val sorted = photos.sortedWith { a, b ->
            when {
                a.dateMillis != null && b.dateMillis != null -> a.dateMillis.compareTo(b.dateMillis)
                a.dateMillis != null && b.dateMillis == null -> -1
                a.dateMillis == null && b.dateMillis != null -> 1
                else -> a.orderIndex.compareTo(b.orderIndex)
            }
        }

        // Re-index slots from 0 to N-1
        return sorted.mapIndexed { index, item ->
            item.copy(orderIndex = index)
        }
    }

    /**
     * Reorders photos after a user drag-and-drop gesture, updating their orderIndex.
     */
    fun reorder(photos: List<PhotoItem>, fromIndex: Int, toIndex: Int): List<PhotoItem> {
        if (fromIndex !in photos.indices || toIndex !in photos.indices || fromIndex == toIndex) {
            return photos
        }

        val mutable = photos.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)

        return mutable.mapIndexed { index, photoItem ->
            photoItem.copy(orderIndex = index)
        }
    }
}
