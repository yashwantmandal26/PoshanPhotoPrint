package com.poshan.photoprint

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.poshan.photoprint.ui.PoshanViewModel
import com.poshan.photoprint.ui.screens.A4PreviewScreen
import com.poshan.photoprint.ui.screens.DateEditDialog
import com.poshan.photoprint.ui.screens.HomeScreen
import com.poshan.photoprint.ui.screens.ImageCropDialog
import com.poshan.photoprint.ui.screens.SavedReportsScreen

enum class PoshanScreen {
    HOME,
    PREVIEW,
    SAVED_REPORTS
}

@Composable
fun MainNavigation(
    sharedUris: List<Uri> = emptyList(),
    viewModel: PoshanViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var currentScreen by rememberSaveable { mutableStateOf(PoshanScreen.HOME) }

    LaunchedEffect(Unit) {
        viewModel.initLanguage(context)
        viewModel.loadSavedReports(context)
    }

    // Direct launch into A4 Screen when photos are shared from phone Gallery
    LaunchedEffect(sharedUris) {
        if (sharedUris.isNotEmpty()) {
            viewModel.loadSelectedPhotos(context, sharedUris)
            currentScreen = PoshanScreen.PREVIEW
        }
    }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            if (targetState.ordinal > initialState.ordinal) {
                (slideInHorizontally(animationSpec = tween(300)) { width -> width / 3 } + fadeIn(animationSpec = tween(300)))
                    .togetherWith(slideOutHorizontally(animationSpec = tween(300)) { width -> -width / 3 } + fadeOut(animationSpec = tween(300)))
            } else {
                (slideInHorizontally(animationSpec = tween(300)) { width -> -width / 3 } + fadeIn(animationSpec = tween(300)))
                    .togetherWith(slideOutHorizontally(animationSpec = tween(300)) { width -> width / 3 } + fadeOut(animationSpec = tween(300)))
            }
        },
        label = "ScreenTransition"
    ) { screen ->
        when (screen) {
            PoshanScreen.HOME -> {
                HomeScreen(
                    uiState = uiState,
                    onPhotosSelected = { uris ->
                        // Direct sort & immediately display A4 Sheet!
                        viewModel.loadSelectedPhotos(context, uris)
                        currentScreen = PoshanScreen.PREVIEW
                    },
                    onNavigateToSavedReports = {
                        currentScreen = PoshanScreen.SAVED_REPORTS
                    },
                    onToggleLanguage = {
                        viewModel.toggleLanguage(context)
                    },
                    onToggleTheme = {
                        viewModel.toggleTheme(context)
                    }
                )
            }

            PoshanScreen.PREVIEW -> {
                BackHandler {
                    currentScreen = PoshanScreen.HOME
                }

                A4PreviewScreen(
                    uiState = uiState,
                    onBack = { currentScreen = PoshanScreen.HOME },
                    onResetSort = { viewModel.resetToDefaultSort(context) },
                    onSwapPhotos = { a, b -> viewModel.swapPhotos(a, b, context) },
                    onReplaceSinglePhoto = { index, uri -> viewModel.replaceSinglePhoto(context, index, uri) },
                    onRemovePhoto = { index -> viewModel.removePhoto(index, context) },
                    onAddPhotos = { uris -> viewModel.addPhotosToCurrentReport(context, uris) },
                    onStartCrop = { photo -> viewModel.startCroppingPhoto(photo) },
                    onSelectExportQuality = { quality -> viewModel.setExportQuality(quality, context) },
                    onSelectLayoutPreset = { preset -> viewModel.setLayoutPreset(preset, context) },
                    onStartEditDate = { photo -> viewModel.startEditingDate(photo) },
                    onClearSnackbar = { viewModel.clearSnackbar() }
                )
            }

            PoshanScreen.SAVED_REPORTS -> {
                BackHandler {
                    currentScreen = PoshanScreen.HOME
                }

                SavedReportsScreen(
                    reports = uiState.savedReports,
                    isHindi = uiState.isHindi,
                    onBack = { currentScreen = PoshanScreen.HOME },
                    onOpenReport = { report ->
                        viewModel.loadReportForEditing(context, report)
                        currentScreen = PoshanScreen.PREVIEW
                    },
                    onDeleteReport = { id -> viewModel.deleteSavedReport(context, id) }
                )
            }
        }
    }

    // Modal Date Editor Dialog
    uiState.editingPhoto?.let { photoToEdit ->
        DateEditDialog(
            photo = photoToEdit,
            onDismiss = { viewModel.cancelEditingDate() },
            onConfirm = { newDateMillis ->
                viewModel.applyDateChange(photoToEdit.id, newDateMillis)
                viewModel.generateA4Files(context)
            }
        )
    }

    // Modal Image Cropper Dialog
    uiState.croppingPhoto?.let { photoToCrop ->
        ImageCropDialog(
            photo = photoToCrop,
            isHindi = uiState.isHindi,
            onDismiss = { viewModel.cancelCroppingPhoto() },
            onConfirmCrop = { newUri ->
                viewModel.applyCroppedPhoto(context, photoToCrop.id, newUri)
            }
        )
    }
}
