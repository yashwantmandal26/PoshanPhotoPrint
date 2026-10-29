package com.poshan.photoprint.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ViewCompact
import com.poshan.photoprint.core.layout.LayoutPreset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.poshan.photoprint.core.export.ExportHelper
import com.poshan.photoprint.core.generator.ExportQuality
import com.poshan.photoprint.core.model.DateSource
import com.poshan.photoprint.core.model.PhotoItem
import com.poshan.photoprint.ui.PoshanStrings
import com.poshan.photoprint.ui.PoshanUiState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun A4PreviewScreen(
    uiState: PoshanUiState,
    onBack: () -> Unit,
    onResetSort: () -> Unit,
    onSwapPhotos: (indexA: Int, indexB: Int) -> Unit,
    onReplaceSinglePhoto: (index: Int, uri: Uri) -> Unit,
    onRemovePhoto: (index: Int) -> Unit,
    onAddPhotos: (List<Uri>) -> Unit,
    onStartCrop: (PhotoItem) -> Unit,
    onSelectExportQuality: (ExportQuality) -> Unit,
    onSelectLayoutPreset: (LayoutPreset) -> Unit,
    onStartEditDate: (PhotoItem) -> Unit,
    onClearSnackbar: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val isHindi = uiState.isHindi

    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // Spring-based Pan & Zoom with zero recomposition lag
    val animZoom = remember { Animatable(1f) }
    val animPanX = remember { Animatable(0f) }
    val animPanY = remember { Animatable(0f) }

    // Full Screen Photo Viewer State
    var fullScreenPhotoId by remember { mutableStateOf<String?>(null) }

    // Drag-and-Drop state
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var hoveredTargetIndex by remember { mutableIntStateOf(-1) }
    var isHoveringDeleteZone by remember { mutableStateOf(false) }

    // Single Photo Replacement State
    var targetReplaceIndex by remember { mutableIntStateOf(-1) }
    var photoIndexToDelete by remember { mutableIntStateOf(-1) }
    val singlePhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null && targetReplaceIndex != -1) {
            onReplaceSinglePhoto(targetReplaceIndex, uri)
            targetReplaceIndex = -1
        }
    }

    // Add Photos Picker Launcher (Multi-Photo)
    val addPhotosPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onAddPhotos(uris)
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            onClearSnackbar()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = PoshanStrings.a4Title(isHindi),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = PoshanStrings.a4Subtitle(isHindi, uiState.photos.size),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // Add Photo Button: Allows appending photos to current A4 sheet
                    IconButton(
                        onClick = { addPhotosPickerLauncher.launch("image/*") }
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = if (isHindi) "फोटो जोड़ें" else "Add Photo",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    // Reset Button: Restores original ascending date sort and smoothly resets zoom
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch {
                                launch { animZoom.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow)) }
                                launch { animPanX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                                launch { animPanY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                            }
                            draggedIndex = -1
                            hoveredTargetIndex = -1
                            isHoveringDeleteZone = false
                            onResetSort()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = PoshanStrings.resetTooltip(isHindi),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 6.dp,
                shadowElevation = 10.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Export Quality Mode Selector (WhatsApp Mode vs HQ Print Mode)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isHindi) "क्वालिटी:" else "Quality:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ExportQuality.entries.forEach { quality ->
                                val isSelected = (uiState.exportQuality == quality)
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.35f)),
                                    modifier = Modifier.clickable { onSelectExportQuality(quality) }
                                ) {
                                    Text(
                                        text = if (isHindi) quality.labelHi else quality.labelEn,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Primary Row: Download PDF & Save Image (Both prominent + Open after save)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. Download PDF Button (Saves and opens immediately)
                        Button(
                            onClick = {
                                val pdf = uiState.generatedPdfFile
                                if (pdf != null && pdf.exists()) {
                                    ExportHelper.savePdfToPublicDownloads(context, pdf)
                                    Toast.makeText(
                                        context,
                                        PoshanStrings.pdfSavedToast(isHindi),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    // Auto-open PDF in viewer
                                    ExportHelper.openPdf(context, pdf)
                                } else {
                                    Toast.makeText(context, PoshanStrings.preparingPdf(isHindi), Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = PoshanStrings.downloadPdf(isHindi),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // 2. Save Image Button (Saves directly to Gallery and opens immediately)
                        FilledTonalButton(
                            onClick = {
                                val img = uiState.generatedImageFile
                                if (img != null && img.exists()) {
                                    ExportHelper.saveImageToGallery(context, img)
                                    Toast.makeText(
                                        context,
                                        PoshanStrings.imageSavedToast(isHindi),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    // Auto-open Image in gallery
                                    ExportHelper.openImage(context, img)
                                } else {
                                    Toast.makeText(context, PoshanStrings.preparingImage(isHindi), Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = PoshanStrings.saveImage(isHindi),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Secondary Row: WhatsApp Share & Direct Print Spooler
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                val pdf = uiState.generatedPdfFile
                                if (pdf != null && pdf.exists()) {
                                    ExportHelper.shareToWhatsApp(context, pdf)
                                } else {
                                    Toast.makeText(context, PoshanStrings.preparingPdf(isHindi), Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF25D366).copy(alpha = 0.16f),
                                contentColor = Color(0xFF1B8A44)
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = PoshanStrings.whatsApp(isHindi), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        FilledTonalButton(
                            onClick = {
                                val pdf = uiState.generatedPdfFile
                                if (pdf != null && pdf.exists()) {
                                    ExportHelper.printDocument(context, pdf)
                                } else {
                                    Toast.makeText(context, PoshanStrings.preparingPdf(isHindi), Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = PoshanStrings.print(isHindi), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(if (uiState.isDarkTheme) Color(0xFF14181B) else Color(0xFFE2E7EB))
                // Smooth Spring Pinch-to-Zoom and Pan (Jitter-free, 120fps hardware vsync)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val pressedChanges = event.changes.filter { it.pressed }
                            if (pressedChanges.size >= 2) {
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                val oldScale = animZoom.value
                                // Allow elastic zoom-out down to 0.75f and zoom-in up to 4.5f
                                val newScale = (oldScale * zoomChange).coerceIn(0.75f, 4.5f)

                                scope.launch {
                                    animZoom.snapTo(newScale)
                                    if (newScale <= 1.15f) {
                                        val factor = ((newScale - 1f) / 0.15f).coerceIn(0f, 1f)
                                        animPanX.snapTo(animPanX.value * factor)
                                        animPanY.snapTo(animPanY.value * factor)
                                    } else {
                                        animPanX.snapTo(animPanX.value + panChange.x)
                                        animPanY.snapTo(animPanY.value + panChange.y)
                                    }
                                }
                                event.changes.forEach { it.consume() }
                            } else if (pressedChanges.size == 1 && animZoom.value > 1.05f) {
                                // 1-finger pan while zoomed into the document
                                val panChange = event.calculatePan()
                                scope.launch {
                                    animPanX.snapTo(animPanX.value + panChange.x)
                                    animPanY.snapTo(animPanY.value + panChange.y)
                                }
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })

                        // On finger release: snap with smooth spring back to 1.0f and center!
                        if (animZoom.value <= 1.15f) {
                            scope.launch {
                                animZoom.animateTo(
                                    1f,
                                    spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                                )
                            }
                            scope.launch {
                                animPanX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                animPanY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                            }
                        } else {
                            val maxPanX = (size.width * (animZoom.value - 1f)) / 2f
                            val maxPanY = (size.height * (animZoom.value - 1f)) / 2f
                            val clampedX = animPanX.value.coerceIn(-maxPanX, maxPanX)
                            val clampedY = animPanY.value.coerceIn(-maxPanY, maxPanY)
                            if (clampedX != animPanX.value || clampedY != animPanY.value) {
                                scope.launch {
                                    animPanX.animateTo(clampedX, spring(stiffness = Spring.StiffnessMediumLow))
                                    animPanY.animateTo(clampedY, spring(stiffness = Spring.StiffnessMediumLow))
                                }
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch {
                                if (animZoom.value > 1.1f) {
                                    launch { animZoom.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)) }
                                    launch { animPanX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                                    launch { animPanY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                                } else {
                                    launch { animZoom.animateTo(2.2f, spring(stiffness = Spring.StiffnessMediumLow)) }
                                }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (uiState.isExporting && uiState.generatedPdfFile == null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = if (isHindi) "A4 रिपोर्ट तैयार हो रही है..." else "Creating compressed A4 report...",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Floating Delete Drop Target Zone when dragging
                    AnimatedVisibility(
                        visible = draggedIndex != -1,
                        enter = fadeIn() + slideInVertically(),
                        exit = fadeOut() + slideOutVertically()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = if (isHoveringDeleteZone) Color(0xFFD32F2F) else Color(0xDD37474F),
                            border = BorderStroke(2.dp, if (isHoveringDeleteZone) Color.White else Color(0xFFE57373)),
                            shadowElevation = if (isHoveringDeleteZone) 14.dp else 4.dp,
                            modifier = Modifier
                                .padding(top = 8.dp, bottom = 4.dp)
                                .graphicsLayer {
                                    scaleX = if (isHoveringDeleteZone) 1.08f else 1f
                                    scaleY = if (isHoveringDeleteZone) 1.08f else 1f
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = if (isHoveringDeleteZone)
                                        (if (isHindi) "छोड़ें और हटाएं" else "Release to Remove")
                                    else
                                        (if (isHindi) "हटाने के लिए यहाँ लाएँ (Drop to Remove)" else "Drag here to Remove"),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    // Layout Presets Row (Justified / Left / Center / Right)
                    if (draggedIndex == -1) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (uiState.isDarkTheme) Color(0xFF1E2429) else Color(0xFFF0F4F8),
                            tonalElevation = 2.dp,
                            shadowElevation = 2.dp,
                            border = BorderStroke(1.dp, if (uiState.isDarkTheme) Color(0xFF333D45) else Color(0xFFCFD8DC)),
                            modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val presets = listOf(
                                    Triple(LayoutPreset.JUSTIFIED, if (isHindi) "ग्रिड" else "Grid", Icons.Default.GridView),
                                    Triple(LayoutPreset.COMPACT, if (isHindi) "सटा हुआ" else "Compact", Icons.Default.ViewCompact),
                                    Triple(LayoutPreset.LEFT, if (isHindi) "बाएं" else "Left", Icons.AutoMirrored.Filled.FormatAlignLeft),
                                    Triple(LayoutPreset.CENTER, if (isHindi) "मध्य" else "Center", Icons.Default.FormatAlignCenter),
                                    Triple(LayoutPreset.RIGHT, if (isHindi) "दाएं" else "Right", Icons.AutoMirrored.Filled.FormatAlignRight)
                                )

                                presets.forEach { (preset, label, icon) ->
                                    val isSelected = uiState.layoutPreset == preset
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            Color.Transparent
                                        },
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable { onSelectLayoutPreset(preset) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = label,
                                                tint = if (isSelected) Color.White else (if (uiState.isDarkTheme) Color.LightGray else Color(0xFF546E7A)),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = label,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color.White else (if (uiState.isDarkTheme) Color.LightGray else Color(0xFF546E7A))
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // A4 Paper Sheet (Canvas with true 1:1.414 ratio)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(0.94f)
                            .padding(vertical = 4.dp)
                            .graphicsLayer {
                                scaleX = animZoom.value
                                scaleY = animZoom.value
                                translationX = animPanX.value
                                translationY = animPanY.value
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f / 1.4142f)
                                .shadow(elevation = 12.dp, shape = RoundedCornerShape(4.dp))
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White)
                                .border(1.dp, Color(0xFFD0D7DE), RoundedCornerShape(4.dp))
                        ) {
                            val plan = uiState.a4Plan
                            if (plan != null && uiState.photos.isNotEmpty()) {
                                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                    val paperWidth = maxWidth
                                    val paperHeight = maxHeight

                                    // Render each photo slot
                                    uiState.photos.forEachIndexed { index, photo ->
                                        if (index < plan.cells.size) {
                                            val cell = plan.cells[index]
                                            val normLeft = cell.contentBounds.left / plan.pageWidth
                                            val normTop = cell.contentBounds.top / plan.pageHeight
                                            val normWidth = cell.contentBounds.width / plan.pageWidth
                                            val normHeight = cell.contentBounds.height / plan.pageHeight

                                            val slotX = paperWidth * normLeft
                                            val slotY = paperHeight * normTop
                                            val slotW = paperWidth * normWidth
                                            val slotH = paperHeight * normHeight

                                            val isBeingDragged = (draggedIndex == index)
                                            val isHoveredTarget = (hoveredTargetIndex == index && draggedIndex != -1 && draggedIndex != index)

                                            val slotBorderWidth by animateDpAsState(
                                                targetValue = when {
                                                    isHoveredTarget -> 3.dp
                                                    isBeingDragged -> 1.5.dp
                                                    else -> 1.dp
                                                },
                                                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                                label = "slotBorderWidth"
                                            )
                                            val slotBorderColor by animateColorAsState(
                                                targetValue = when {
                                                    isHoveredTarget -> Color(0xFF00C853)
                                                    isBeingDragged -> Color.Gray
                                                    else -> Color(0x33000000)
                                                },
                                                animationSpec = tween(120),
                                                label = "slotBorderColor"
                                            )

                                            Box(
                                                modifier = Modifier
                                                    .offset(x = slotX, y = slotY)
                                                    .size(width = slotW, height = slotH)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0xFFFAFAFA))
                                                    .border(
                                                        width = slotBorderWidth,
                                                        color = slotBorderColor,
                                                        shape = RoundedCornerShape(4.dp)
                                                    )
                                                    .graphicsLayer {
                                                        alpha = if (isBeingDragged) 0.3f else 1.0f
                                                    }
                                                    // Long Press + Drag to Move / Reorder / Delete
                                                    .pointerInput(photo.id) {
                                                        detectDragGesturesAfterLongPress(
                                                            onDragStart = {
                                                                if (animZoom.value <= 1.1f) {
                                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                    draggedIndex = index
                                                                    dragOffset = Offset.Zero
                                                                    hoveredTargetIndex = -1
                                                                    isHoveringDeleteZone = false
                                                                }
                                                            },
                                                            onDrag = { change, dragAmount ->
                                                                change.consume()
                                                                dragOffset += dragAmount

                                                                val currentCenterY = (slotY.toPx() + (slotH.toPx() / 2f)) + dragOffset.y
                                                                // If dragged near top edge of the canvas or dragged upwards towards delete zone
                                                                val nearTop = currentCenterY < 140f || dragOffset.y < -180f
                                                                isHoveringDeleteZone = nearTop

                                                                if (!nearTop) {
                                                                    val currentCenterX = (slotX.toPx() + (slotW.toPx() / 2f)) + dragOffset.x
                                                                    val paperWidthPx = paperWidth.toPx()
                                                                    val paperHeightPx = paperHeight.toPx()

                                                                    val hovered = plan.cells.indexOfFirst { candidateCell ->
                                                                        val cLeft = paperWidthPx * (candidateCell.cellBounds.left / plan.pageWidth)
                                                                        val cRight = paperWidthPx * (candidateCell.cellBounds.right / plan.pageWidth)
                                                                        val cTop = paperHeightPx * (candidateCell.cellBounds.top / plan.pageHeight)
                                                                        val cBottom = paperHeightPx * (candidateCell.cellBounds.bottom / plan.pageHeight)
                                                                        currentCenterX in cLeft..cRight && currentCenterY in cTop..cBottom
                                                                    }
                                                                    val newTarget = if (hovered != draggedIndex) hovered else -1
                                                                    if (newTarget != hoveredTargetIndex && newTarget != -1) {
                                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                    }
                                                                    hoveredTargetIndex = newTarget
                                                                } else {
                                                                    hoveredTargetIndex = -1
                                                                }
                                                            },
                                                            onDragEnd = {
                                                                if (isHoveringDeleteZone && draggedIndex != -1) {
                                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                    val toRemove = draggedIndex
                                                                    draggedIndex = -1
                                                                    isHoveringDeleteZone = false
                                                                    photoIndexToDelete = toRemove
                                                                } else {
                                                                    val target = hoveredTargetIndex
                                                                    val source = draggedIndex
                                                                    if (source != -1 && target != -1 && source != target) {
                                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                        onSwapPhotos(source, target)
                                                                    }
                                                                    draggedIndex = -1
                                                                    dragOffset = Offset.Zero
                                                                    hoveredTargetIndex = -1
                                                                    isHoveringDeleteZone = false
                                                                }
                                                            },
                                                            onDragCancel = {
                                                                draggedIndex = -1
                                                                dragOffset = Offset.Zero
                                                                hoveredTargetIndex = -1
                                                                isHoveringDeleteZone = false
                                                            }
                                                        )
                                                    }
                                                    // Tap photo to open in Full Screen Viewer
                                                    .pointerInput(photo.id) {
                                                        detectTapGestures(
                                                            onTap = {
                                                                if (draggedIndex == -1) {
                                                                    fullScreenPhotoId = photo.id
                                                                }
                                                            }
                                                        )
                                                    }
                                            ) {
                                                // Zero-crop photo with smooth crossfade
                                                AsyncImage(
                                                    model = ImageRequest.Builder(LocalContext.current)
                                                        .data(photo.uri)
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Fit,
                                                    modifier = Modifier.fillMaxSize()
                                                )

                                                // Clean Header Overlay: Slot Badge (#1) + Compact Date/Time + Quick Delete
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .align(Alignment.TopCenter)
                                                        .background(Color.Black.copy(alpha = 0.72f))
                                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                            modifier = Modifier.weight(1f, fill = false)
                                                        ) {
                                                            // Position Badge (#1, #2...)
                                                            Surface(
                                                                color = MaterialTheme.colorScheme.primary,
                                                                shape = RoundedCornerShape(3.dp)
                                                            ) {
                                                                Text(
                                                                    text = "#${index + 1}",
                                                                    color = Color.White,
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontSize = 9.sp,
                                                                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                                                )
                                                            }

                                                            // Compact Date + Time (Direct tap to edit date)
                                                            Surface(
                                                                color = Color(0x44000000),
                                                                shape = RoundedCornerShape(4.dp),
                                                                border = BorderStroke(0.5.dp, Color(0x66FFEB3B)),
                                                                modifier = Modifier
                                                                    .clip(RoundedCornerShape(4.dp))
                                                                    .clickable { onStartEditDate(photo) }
                                                            ) {
                                                                Row(
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                                ) {
                                                                    Text(
                                                                        text = PhotoItem.formatCompactDate(photo.dateMillis),
                                                                        color = Color(0xFFFFEB3B),
                                                                        fontWeight = FontWeight.Bold,
                                                                        fontSize = 9.sp,
                                                                        maxLines = 1
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        // Quick Delete (Circle Red Close button)
                                                        Surface(
                                                            shape = CircleShape,
                                                            color = Color(0xFFD32F2F),
                                                            modifier = Modifier
                                                                .size(16.dp)
                                                                .clickable { photoIndexToDelete = index }
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Close,
                                                                contentDescription = "Remove photo",
                                                                tint = Color.White,
                                                                modifier = Modifier
                                                                    .fillMaxSize()
                                                                    .padding(2.dp)
                                                                )
                                                        }
                                                    }

                                                // "Drop Here" overlay when dragging over this slot
                                                if (isHoveredTarget) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .background(Color(0xFF00C853).copy(alpha = 0.25f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Surface(
                                                            color = Color(0xFF00C853),
                                                            shape = RoundedCornerShape(12.dp)
                                                        ) {
                                                            Text(
                                                                text = PoshanStrings.dropHere(isHindi),
                                                                color = Color.White,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 12.sp,
                                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Elevated Floating Dragged Item Preview
                                    val activeDraggedPhoto = uiState.photos.getOrNull(draggedIndex)
                                    if (draggedIndex != -1 && activeDraggedPhoto != null && draggedIndex < plan.cells.size) {
                                        val dCell = plan.cells[draggedIndex]
                                        val dNormLeft = dCell.contentBounds.left / plan.pageWidth
                                        val dNormTop = dCell.contentBounds.top / plan.pageHeight
                                        val dNormWidth = dCell.contentBounds.width / plan.pageWidth
                                        val dNormHeight = dCell.contentBounds.height / plan.pageHeight

                                        val origSlotX = paperWidth * dNormLeft
                                        val origSlotY = paperHeight * dNormTop
                                        val slotW = paperWidth * dNormWidth
                                        val slotH = paperHeight * dNormHeight

                                        Box(
                                            modifier = Modifier
                                                .offset {
                                                    IntOffset(
                                                        x = (origSlotX.toPx() + dragOffset.x).roundToInt(),
                                                        y = (origSlotY.toPx() + dragOffset.y).roundToInt()
                                                    )
                                                }
                                                .size(width = slotW, height = slotH)
                                                .zIndex(100f)
                                                .graphicsLayer {
                                                    scaleX = if (isHoveringDeleteZone) 0.85f else 1.08f
                                                    scaleY = if (isHoveringDeleteZone) 0.85f else 1.08f
                                                    alpha = if (isHoveringDeleteZone) 0.7f else 1.0f
                                                }
                                                .shadow(elevation = 16.dp, shape = RoundedCornerShape(6.dp))
                                                .border(
                                                    2.5.dp,
                                                    if (isHoveringDeleteZone) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary,
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color.White)
                                        ) {
                                            AsyncImage(
                                                model = activeDraggedPhoto.uri,
                                                contentDescription = null,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            // Top Badge
                                            Surface(
                                                color = if (isHoveringDeleteZone) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(bottomEnd = 6.dp),
                                                modifier = Modifier.align(Alignment.TopStart)
                                            ) {
                                                Text(
                                                    text = if (isHoveringDeleteZone) "Delete" else "#${draggedIndex + 1}",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirmation Dialog before removing a photo from A4 layout
    if (photoIndexToDelete in uiState.photos.indices) {
        val photo = uiState.photos[photoIndexToDelete]
        val photoNum = photoIndexToDelete + 1
        AlertDialog(
            onDismissRequest = { photoIndexToDelete = -1 },
            title = {
                Text(
                    text = if (isHindi) "फोटो हटाएं?" else "Remove Photo?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (isHindi)
                        "क्या आप वाकई फोटो #${photoNum} (${photo.dateDisplay}) को इस A4 रिपोर्ट से हटाना चाहते हैं?"
                    else
                        "Are you sure you want to remove photo #${photoNum} (${photo.dateDisplay}) from this A4 report?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val idx = photoIndexToDelete
                        photoIndexToDelete = -1
                        onRemovePhoto(idx)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(if (isHindi) "हाँ, हटाएं" else "Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { photoIndexToDelete = -1 }) {
                    Text(if (isHindi) "रद्द करें" else "Cancel")
                }
            }
        )
    }

    // Full Screen Photo Viewer Modal
    val currentFullScreenPhoto = uiState.photos.find { it.id == fullScreenPhotoId }
    if (currentFullScreenPhoto != null) {
        val currIdx = uiState.photos.indexOf(currentFullScreenPhoto)
        FullScreenPhotoViewerDialog(
            photo = currentFullScreenPhoto,
            photoIndex = currIdx,
            totalPhotos = uiState.photos.size,
            isHindi = isHindi,
            onDismiss = { fullScreenPhotoId = null },
            onCrop = {
                val photoToCrop = currentFullScreenPhoto
                fullScreenPhotoId = null
                onStartCrop(photoToCrop)
            },
            onReplace = {
                val idx = currIdx
                fullScreenPhotoId = null
                targetReplaceIndex = idx
                singlePhotoPickerLauncher.launch("image/*")
            },
            onEditDate = {
                val photoToEdit = currentFullScreenPhoto
                fullScreenPhotoId = null
                onStartEditDate(photoToEdit)
            },
            onNavigatePhoto = { nextIdx ->
                if (nextIdx in uiState.photos.indices) {
                    fullScreenPhotoId = uiState.photos[nextIdx].id
                }
            }
        )
    }
}

/**
 * Immersive Full Screen Photo Viewer with Zoom, Pan, Date info and prominent Crop action.
 */
@Composable
fun FullScreenPhotoViewerDialog(
    photo: PhotoItem,
    photoIndex: Int,
    totalPhotos: Int,
    isHindi: Boolean,
    onDismiss: () -> Unit,
    onCrop: () -> Unit,
    onReplace: () -> Unit,
    onEditDate: () -> Unit,
    onNavigatePhoto: ((Int) -> Unit)? = null
) {
    val animScale = remember(photo.id) { Animatable(1f) }
    val animOffsetX = remember(photo.id) { Animatable(0f) }
    val animOffsetY = remember(photo.id) { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Interactive Zoom & Pan Photo (Spring-Physics & zero-recomposition)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(photo.id) {
                        detectTapGestures(
                            onDoubleTap = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                coroutineScope.launch {
                                    if (animScale.value > 1.1f) {
                                        launch {
                                            animScale.animateTo(
                                                1f,
                                                spring(
                                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                                    stiffness = Spring.StiffnessMediumLow
                                                )
                                            )
                                        }
                                        launch { animOffsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                                        launch { animOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                                    } else {
                                        launch { animScale.animateTo(2.5f, spring(stiffness = Spring.StiffnessMediumLow)) }
                                    }
                                }
                            }
                        )
                    }
                    .pointerInput(photo.id) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                val pressedChanges = event.changes.filter { it.pressed }
                                if (pressedChanges.size >= 2) {
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    val oldScale = animScale.value
                                    val newScale = (oldScale * zoomChange).coerceIn(0.75f, 5f)

                                    coroutineScope.launch {
                                        animScale.snapTo(newScale)
                                        if (newScale <= 1.1f) {
                                            val factor = ((newScale - 1f) / 0.1f).coerceIn(0f, 1f)
                                            animOffsetX.snapTo(animOffsetX.value * factor)
                                            animOffsetY.snapTo(animOffsetY.value * factor)
                                        } else {
                                            animOffsetX.snapTo(animOffsetX.value + panChange.x)
                                            animOffsetY.snapTo(animOffsetY.value + panChange.y)
                                        }
                                    }
                                    event.changes.forEach { it.consume() }
                                } else if (pressedChanges.size == 1 && animScale.value > 1.05f) {
                                    val panChange = event.calculatePan()
                                    coroutineScope.launch {
                                        animOffsetX.snapTo(animOffsetX.value + panChange.x)
                                        animOffsetY.snapTo(animOffsetY.value + panChange.y)
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })

                            // On finger release: snap with smooth spring back to 1.0f and center if zoomed out
                            if (animScale.value <= 1.1f) {
                                coroutineScope.launch {
                                    launch {
                                        animScale.animateTo(
                                            1f,
                                            spring(
                                                dampingRatio = Spring.DampingRatioLowBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    }
                                    launch { animOffsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                                    launch { animOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                                }
                            } else {
                                val maxOffsetX = (size.width * (animScale.value - 1f)) / 2f
                                val maxOffsetY = (size.height * (animScale.value - 1f)) / 2f
                                val clampedX = animOffsetX.value.coerceIn(-maxOffsetX, maxOffsetX)
                                val clampedY = animOffsetY.value.coerceIn(-maxOffsetY, maxOffsetY)
                                if (clampedX != animOffsetX.value || clampedY != animOffsetY.value) {
                                    coroutineScope.launch {
                                        launch { animOffsetX.animateTo(clampedX, spring(stiffness = Spring.StiffnessMediumLow)) }
                                        launch { animOffsetY.animateTo(clampedY, spring(stiffness = Spring.StiffnessMediumLow)) }
                                    }
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(photo.uri)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Full Screen Photo #${photoIndex + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = animScale.value
                            scaleY = animScale.value
                            translationX = animOffsetX.value
                            translationY = animOffsetY.value
                        }
                )
            }

            // Top Bar Overlay (with statusBarsPadding to avoid overlapping phone status bar)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                color = Color.Black.copy(alpha = 0.85f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }
                        Column {
                            Text(
                                text = if (isHindi) "फोटो #${photoIndex + 1} / $totalPhotos" else "Photo #${photoIndex + 1} of $totalPhotos",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = photo.dateDisplay,
                                color = Color(0xFFFFEB3B),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Edit Date Action in Full Screen
                    TextButton(
                        onClick = onEditDate,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFFD54F))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Date",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isHindi) "तारीख बदलें" else "Edit Date",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Navigation Arrows (Left / Right) if multiple photos
            if (totalPhotos > 1 && onNavigatePhoto != null) {
                if (photoIndex > 0) {
                    IconButton(
                        onClick = { onNavigatePhoto(photoIndex - 1) },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 12.dp)
                            .size(44.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous",
                            tint = Color.White
                        )
                    }
                }
                if (photoIndex < totalPhotos - 1) {
                    IconButton(
                        onClick = { onNavigatePhoto(photoIndex + 1) },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 12.dp)
                            .size(44.dp)
                            .graphicsLayer { rotationZ = 180f }
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Next",
                            tint = Color.White
                        )
                    }
                }
            }

            // Bottom Actions Bar (Crop Photo & Replace Photo)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                color = Color.Black.copy(alpha = 0.85f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Replace photo button
                    FilledTonalButton(
                        onClick = onReplace,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF263238),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "Replace",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isHindi) "फोटो बदलें" else "Replace",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    // CROP PHOTO BUTTON (Highlight primary action)
                    Button(
                        onClick = onCrop,
                        modifier = Modifier.weight(1.3f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Crop,
                            contentDescription = "Crop Photo",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isHindi) "फोटो क्रॉप करें" else "Crop Photo",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}
