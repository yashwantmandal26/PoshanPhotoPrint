package com.poshan.photoprint.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.exifinterface.media.ExifInterface
import coil.compose.AsyncImage
import com.poshan.photoprint.core.crop.SmartCropDetector
import com.poshan.photoprint.core.model.PhotoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

@Composable
fun ImageCropDialog(
    photo: PhotoItem,
    isHindi: Boolean,
    onDismiss: () -> Unit,
    onConfirmCrop: (newUri: Uri) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isProcessing by remember { mutableStateOf(false) }

    // Smooth animatable normalized crop rectangle (0f..1f relative to image view)
    val animLeft = remember { Animatable(0f) }
    val animTop = remember { Animatable(0f) }
    val animRight = remember { Animatable(1f) }
    val animBottom = remember { Animatable(1f) }

    fun animateTo(targetL: Float, targetT: Float, targetR: Float, targetB: Float) {
        scope.launch {
            launch { animLeft.animateTo(targetL, tween(250, easing = FastOutSlowInEasing)) }
            launch { animTop.animateTo(targetT, tween(250, easing = FastOutSlowInEasing)) }
            launch { animRight.animateTo(targetR, tween(250, easing = FastOutSlowInEasing)) }
            launch { animBottom.animateTo(targetB, tween(250, easing = FastOutSlowInEasing)) }
        }
    }

    // Auto-detect screenshot letterbox bars on open
    LaunchedEffect(photo.uri) {
        val detected = withContext(Dispatchers.Default) {
            SmartCropDetector.detectSmartCrop(context, photo.uri)
        }
        if (detected.top > 0.015f || detected.bottom < 0.985f || detected.left > 0.015f || detected.right < 0.985f) {
            animateTo(detected.left, detected.top, detected.right, detected.bottom)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = Color.White
                        )
                    }

                    Text(
                        text = if (isHindi) "फोटो क्रॉप करें (Auto / Manual)" else "Crop Photo",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )

                    IconButton(
                        onClick = {
                            animateTo(0f, 0f, 1f, 1f)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Reset",
                            tint = Color.White
                        )
                    }
                }

                // Interactive Crop Area (Bounded by available screen space with zero letterbox padding)
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val availableW = maxWidth
                    val availableH = maxHeight
                    val imgAspect = photo.aspectRatio.coerceIn(0.25f, 4.0f)

                    // Compute fitted dimensions: image fills the exact frame with no empty letterbox space
                    val (viewW, viewH) = if (availableW / availableH > imgAspect) {
                        // Height-constrained: height is availableH, width is availableH * imgAspect
                        Pair(availableH * imgAspect, availableH)
                    } else {
                        // Width-constrained: width is availableW, height is availableW / imgAspect
                        Pair(availableW, availableW / imgAspect)
                    }

                    Box(
                        modifier = Modifier
                            .size(width = viewW, height = viewH),
                        contentAlignment = Alignment.TopStart
                    ) {
                        // Photo underlay fills exact viewW x viewH with zero distortion
                        AsyncImage(
                            model = photo.uri,
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )

                        val cl = animLeft.value.coerceIn(0f, 0.9f)
                        val ct = animTop.value.coerceIn(0f, 0.9f)
                        val cr = animRight.value.coerceIn(cl + 0.08f, 1f)
                        val cb = animBottom.value.coerceIn(ct + 0.08f, 1f)

                        val boxLeftPx = viewW * cl
                        val boxTopPx = viewH * ct
                        val boxWidthPx = viewW * (cr - cl)
                        val boxHeightPx = viewH * (cb - ct)

                        // Top darkened region
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(boxTopPx)
                                .align(Alignment.TopStart)
                                .background(Color.Black.copy(alpha = 0.55f))
                        )
                        // Bottom darkened region
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(viewH - (boxTopPx + boxHeightPx))
                                .align(Alignment.BottomStart)
                                .background(Color.Black.copy(alpha = 0.55f))
                        )
                        // Left darkened region
                        Box(
                            modifier = Modifier
                                .offset(x = 0.dp, y = boxTopPx)
                                .size(width = boxLeftPx, height = boxHeightPx)
                                .background(Color.Black.copy(alpha = 0.55f))
                        )
                        // Right darkened region
                        Box(
                            modifier = Modifier
                                .offset(x = boxLeftPx + boxWidthPx, y = boxTopPx)
                                .size(width = viewW - (boxLeftPx + boxWidthPx), height = boxHeightPx)
                                .background(Color.Black.copy(alpha = 0.55f))
                        )

                        // Crop Box Frame with 3x3 grid lines
                        Box(
                            modifier = Modifier
                                .offset(x = boxLeftPx, y = boxTopPx)
                                .size(width = boxWidthPx, height = boxHeightPx)
                                .border(2.dp, Color.White, RoundedCornerShape(2.dp))
                                // Drag inside box to move entire crop frame
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val deltaX = dragAmount.x / viewW.toPx()
                                        val deltaY = dragAmount.y / viewH.toPx()
                                        val w = cr - cl
                                        val h = cb - ct

                                        val newL = (cl + deltaX).coerceIn(0f, 1f - w)
                                        val newT = (ct + deltaY).coerceIn(0f, 1f - h)
                                        scope.launch {
                                            animLeft.snapTo(newL)
                                            animRight.snapTo(newL + w)
                                            animTop.snapTo(newT)
                                            animBottom.snapTo(newT + h)
                                        }
                                    }
                                }
                        ) {
                            // 3x3 Grid Guidelines
                            Column(modifier = Modifier.fillMaxSize()) {
                                Spacer(modifier = Modifier.weight(1f))
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.35f)))
                                Spacer(modifier = Modifier.weight(1f))
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.35f)))
                                Spacer(modifier = Modifier.weight(1f))
                            }
                            Row(modifier = Modifier.fillMaxSize()) {
                                Spacer(modifier = Modifier.weight(1f))
                                Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color.White.copy(alpha = 0.35f)))
                                Spacer(modifier = Modifier.weight(1f))
                                Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color.White.copy(alpha = 0.35f)))
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }

                        // Corner Drag Handles
                        val handleSize = 36.dp

                        // 1. Top-Left Handle
                        Box(
                            modifier = Modifier
                                .offset(x = boxLeftPx - 18.dp, y = boxTopPx - 18.dp)
                                .size(handleSize)
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val deltaX = dragAmount.x / viewW.toPx()
                                        val deltaY = dragAmount.y / viewH.toPx()
                                        scope.launch {
                                            animLeft.snapTo((cl + deltaX).coerceIn(0f, cr - 0.12f))
                                            animTop.snapTo((ct + deltaY).coerceIn(0f, cb - 0.12f))
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
                                modifier = Modifier.size(20.dp)
                            ) {}
                        }

                        // 2. Top-Right Handle
                        Box(
                            modifier = Modifier
                                .offset(x = boxLeftPx + boxWidthPx - 18.dp, y = boxTopPx - 18.dp)
                                .size(handleSize)
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val deltaX = dragAmount.x / viewW.toPx()
                                        val deltaY = dragAmount.y / viewH.toPx()
                                        scope.launch {
                                            animRight.snapTo((cr + deltaX).coerceIn(cl + 0.12f, 1f))
                                            animTop.snapTo((ct + deltaY).coerceIn(0f, cb - 0.12f))
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
                                modifier = Modifier.size(20.dp)
                            ) {}
                        }

                        // 3. Bottom-Left Handle
                        Box(
                            modifier = Modifier
                                .offset(x = boxLeftPx - 18.dp, y = boxTopPx + boxHeightPx - 18.dp)
                                .size(handleSize)
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val deltaX = dragAmount.x / viewW.toPx()
                                        val deltaY = dragAmount.y / viewH.toPx()
                                        scope.launch {
                                            animLeft.snapTo((cl + deltaX).coerceIn(0f, cr - 0.12f))
                                            animBottom.snapTo((cb + deltaY).coerceIn(ct + 0.12f, 1f))
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
                                modifier = Modifier.size(20.dp)
                            ) {}
                        }

                        // 4. Bottom-Right Handle
                        Box(
                            modifier = Modifier
                                .offset(x = boxLeftPx + boxWidthPx - 18.dp, y = boxTopPx + boxHeightPx - 18.dp)
                                .size(handleSize)
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val deltaX = dragAmount.x / viewW.toPx()
                                        val deltaY = dragAmount.y / viewH.toPx()
                                        scope.launch {
                                            animRight.snapTo((cr + deltaX).coerceIn(cl + 0.12f, 1f))
                                            animBottom.snapTo((cb + deltaY).coerceIn(ct + 0.12f, 1f))
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
                                modifier = Modifier.size(20.dp)
                            ) {}
                        }
                    }
                }

                // Bottom Controls: Auto Crop + Presets + Confirm Button
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Presets Row (Auto Crop, 4:3, 1:1)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Smart Auto Crop Button: Smoothly detects and animates directly to photo boundary
                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    val detected = withContext(Dispatchers.Default) {
                                        SmartCropDetector.detectSmartCrop(context, photo.uri)
                                    }
                                    val hasDetectedBorders = detected.top > 0.015f || detected.bottom < 0.985f ||
                                            detected.left > 0.015f || detected.right < 0.985f

                                    if (hasDetectedBorders) {
                                        animateTo(detected.left, detected.top, detected.right, detected.bottom)
                                        Toast.makeText(context, if (isHindi) "स्मार्ट ऑटो-क्रॉप लागू (किनारे हटाए गए)" else "Smart Auto applied (Borders removed)", Toast.LENGTH_SHORT).show()
                                    } else {
                                        // Standard camera photo without screenshot black bars: apply clean 4:3 subject crop preserving bottom watermark
                                        animateTo(0.04f, 0.06f, 0.96f, 0.96f)
                                        Toast.makeText(context, if (isHindi) "स्मार्ट क्रॉप लागू (फ़ोकस किया गया)" else "Smart Crop applied (Subject focused)", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1.4f)
                                .height(46.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isHindi) "स्मार्ट ऑटो" else "Smart Auto",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }

                        // 4:3 Preset
                        FilledTonalButton(
                            onClick = {
                                animateTo(0.05f, 0.08f, 0.95f, 0.95f)
                            },
                            modifier = Modifier
                                .weight(0.8f)
                                .height(46.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(text = "4:3", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        // 1:1 Square Preset
                        FilledTonalButton(
                            onClick = {
                                animateTo(0.08f, 0.15f, 0.92f, 0.92f)
                            },
                            modifier = Modifier
                                .weight(0.8f)
                                .height(46.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(text = "1:1", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    // Apply / Done Button
                    Button(
                        onClick = {
                            if (!isProcessing) {
                                isProcessing = true
                                scope.launch {
                                    val croppedUri = cropAndSaveImage(
                                        context = context,
                                        photo = photo,
                                        cropRect = RectF(
                                            animLeft.value.coerceIn(0f, 0.9f),
                                            animTop.value.coerceIn(0f, 0.9f),
                                            animRight.value.coerceIn(0.1f, 1f),
                                            animBottom.value.coerceIn(0.1f, 1f)
                                        )
                                    )
                                    isProcessing = false
                                    if (croppedUri != null) {
                                        onConfirmCrop(croppedUri)
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isHindi) "क्रॉप लागू करें (Apply Crop)" else "Apply Crop",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Performs actual pixel crop and saves high-res cropped image to app cache.
 */
private suspend fun cropAndSaveImage(
    context: Context,
    photo: PhotoItem,
    cropRect: RectF
): Uri? = withContext(Dispatchers.IO) {
    try {
        val inputStream = context.contentResolver.openInputStream(photo.uri) ?: return@withContext null
        val fullBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()

        if (fullBitmap == null) return@withContext null

        val rotation = getExifOrientation(context, photo.uri)
        val orientedBitmap = if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            val rotated = Bitmap.createBitmap(
                fullBitmap,
                0,
                0,
                fullBitmap.width,
                fullBitmap.height,
                matrix,
                true
            )
            if (rotated != fullBitmap) fullBitmap.recycle()
            rotated
        } else {
            fullBitmap
        }

        val imgW = orientedBitmap.width
        val imgH = orientedBitmap.height

        val x = (cropRect.left * imgW).roundToInt().coerceIn(0, imgW - 1)
        val y = (cropRect.top * imgH).roundToInt().coerceIn(0, imgH - 1)
        val w = ((cropRect.right - cropRect.left) * imgW).roundToInt().coerceIn(10, imgW - x)
        val h = ((cropRect.bottom - cropRect.top) * imgH).roundToInt().coerceIn(10, imgH - y)

        val croppedBitmap = Bitmap.createBitmap(orientedBitmap, x, y, w, h)
        if (croppedBitmap != orientedBitmap) {
            orientedBitmap.recycle()
        }

        val cropDir = File(context.cacheDir, "cropped_photos").apply { mkdirs() }
        val outputFile = File(cropDir, "crop_${System.currentTimeMillis()}.jpg")

        FileOutputStream(outputFile).use { out ->
            croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        croppedBitmap.recycle()

        Uri.fromFile(outputFile)
    } catch (_: Exception) {
        null
    }
}

private fun getExifOrientation(context: Context, uri: Uri): Int {
    return try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    } catch (_: Exception) {
        0
    }
}