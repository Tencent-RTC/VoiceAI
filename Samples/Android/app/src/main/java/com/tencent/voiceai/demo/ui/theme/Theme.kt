// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAI Demo - Material3 Theme (light)

package com.tencent.voiceai.demo.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// --- 全局配色（亮色调） ---
val BackgroundColor = Color(0xFFF4F6FB)

val PurpleStart = Color(0xFFF3EAFF)
val PurpleEnd = Color(0xFFFBF8FF)

val BlueStart = Color(0xFFE6F0FF)
val BlueEnd = Color(0xFFF7FAFF)

val GreenStart = Color(0xFFE3F7F4)
val GreenEnd = Color(0xFFF6FCFB)

val TextPrimary = Color(0xFF1A1F36)
val TextSecondary = Color(0xFF6B7488)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2B6CF6),
    onPrimary = Color.White,
    background = BackgroundColor,
    onBackground = TextPrimary,
    surface = Color.White,
    onSurface = TextPrimary,
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF2B6CF6),
    onPrimary = Color.White,
    background = Color(0xFF030817),
    onBackground = Color(0xFFF5F7FF),
    surface = Color(0xFF0B1224),
    onSurface = Color(0xFFF5F7FF),
)

@Composable
fun VoiceAIDemoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // 当前 Demo 统一使用亮色调，不跟随系统暗色。
    val colorScheme = LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = BackgroundColor.toArgb()
            window.navigationBarColor = BackgroundColor.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = true
            controller.isAppearanceLightNavigationBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
