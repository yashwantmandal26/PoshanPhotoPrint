/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.poshan.photoprint.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF87CFF9),
    onPrimary = Color(0xFF00344A),
    primaryContainer = Color(0xFF005C7D),
    onPrimaryContainer = Color(0xFFF4F9FF),
    secondary = Color(0xFFB0CADC),
    onSecondary = Color(0xFF193342),
    secondaryContainer = Color(0xFF294351),
    onSecondaryContainer = Color(0xFFBDD8EA),
    tertiary = Color(0xFFE6B5F8),
    onTertiary = Color(0xFF462058),
    background = Color(0xFF101416),
    onBackground = Color(0xFFE0E3E6),
    surface = Color(0xFF101416),
    onSurface = Color(0xFFE0E3E6),
    surfaceVariant = Color(0xFF40484E),
    onSurfaceVariant = Color(0xFFBFC8CF),
    outline = Color(0xFF899298),
    outlineVariant = Color(0xFF40484E)
)

// Pristine, high-contrast Material 3 White Theme (Light Mode)
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF00668B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF001E2B),
    secondary = Color(0xFF4A6267),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFEAFD),
    onSecondaryContainer = Color(0xFF051F23),
    tertiary = Color(0xFF5B5B7E),
    onTertiary = Color.White,
    background = Color(0xFFF8F9FA),
    onBackground = Color(0xFF1A1C1E),
    surface = Color.White,
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFECEFF2),
    onSurfaceVariant = Color(0xFF42474E),
    outline = Color(0xFFC4C7C5),
    outlineVariant = Color(0xFFE0E3E6)
)

@Composable
fun PoshanPhotoPrintTheme(
    darkTheme: Boolean = false, // White theme default!
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
