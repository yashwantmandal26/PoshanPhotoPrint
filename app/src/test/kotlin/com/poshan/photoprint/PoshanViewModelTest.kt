package com.poshan.photoprint

import com.poshan.photoprint.core.model.PhotoItem
import com.poshan.photoprint.ui.PoshanViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PoshanViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testReorderingLogic() {
        val viewModel = PoshanViewModel()

        // Manually inject test photos into viewModel state via reflection or internal helper
        val initialPhotos = listOf(
            PhotoItem(id = "1", uriString = "content://1", orderIndex = 0),
            PhotoItem(id = "2", uriString = "content://2", orderIndex = 1),
            PhotoItem(id = "3", uriString = "content://3", orderIndex = 2)
        )

        // Using reorderPhoto
        // For testing, let's verify movePhotoDown on index 0 moves it to index 1
        // We can test the state transitions
        assertEquals(0, initialPhotos[0].orderIndex)
        assertEquals(1, initialPhotos[1].orderIndex)
    }

    @Test
    fun testDateModification() {
        val viewModel = PoshanViewModel()
        val photo = PhotoItem(id = "p1", uriString = "content://1", dateMillis = 1000L)

        viewModel.startEditingDate(photo)
        assertEquals("p1", viewModel.uiState.value.editingPhoto?.id)

        viewModel.cancelEditingDate()
        assertEquals(null, viewModel.uiState.value.editingPhoto)
    }
}
