// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAI Demo - Navigation

package com.tencent.voiceai.demo

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.tencent.voiceai.demo.ui.pages.ChatPage
import com.tencent.voiceai.demo.ui.pages.HomePage
import com.tencent.voiceai.demo.ui.pages.SettingPage
import com.tencent.voiceai.demo.ui.pages.TtsVoiceSettingPage
import com.tencent.voiceai.demo.ui.pages.VoiceInputPage
import com.tencent.voiceai.demo.ui.pages.VoiceprintRegisterPage

/**
 * 单 Activity 下的页面路由。使用字符串状态机 + AnimatedContent 做页面切换，
 * 非首页时用 BackHandler 拦截系统返回回到首页。
 */
object Routes {
    const val HOME = "home"
    const val VOICE_INPUT = "voice_input"
    const val CHAT = "chat"
    const val SETTING = "setting"
    const val TTS_VOICE = "tts_voice"          // TTS 音色设置
    const val VOICEPRINT_REGISTER = "voiceprint_register"  // 声纹注册
}

@Composable
fun AppNavigation() {
    var screen by remember { mutableStateOf(Routes.HOME) }
    val context = LocalContext.current

    // 麦克风权限申请：点击「AI 对话」入口时触发，授权成功后进入对话界面。
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            screen = Routes.CHAT
        } else {
            Toast.makeText(context, "未授予麦克风权限，无法进行语音对话", Toast.LENGTH_SHORT).show()
        }
    }

    fun enterChat() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) screen = Routes.CHAT
        else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    AnimatedContent(
        targetState = screen,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "nav"
    ) { dest ->
        // 非首页时拦截系统返回（含侧滑返回）：子场景回到上级页，其余回到首页。
        BackHandler(enabled = dest != Routes.HOME) {
            screen = when (dest) {
                Routes.TTS_VOICE -> Routes.SETTING
                Routes.VOICEPRINT_REGISTER -> Routes.SETTING
                else -> Routes.HOME
            }
        }
        when (dest) {
            Routes.VOICE_INPUT -> VoiceInputPage(onBack = { screen = Routes.HOME })
            Routes.CHAT -> ChatPage(
                onBack = { screen = Routes.HOME }
            )
            Routes.SETTING -> SettingPage(
                onBack = { screen = Routes.HOME },
                onVoiceSetting = { screen = Routes.TTS_VOICE },
                onVoiceprintRegister = { screen = Routes.VOICEPRINT_REGISTER }
            )
            Routes.TTS_VOICE -> TtsVoiceSettingPage(onBack = { screen = Routes.SETTING })
            Routes.VOICEPRINT_REGISTER -> VoiceprintRegisterPage(onBack = { screen = Routes.SETTING })
            else -> HomePage(
                onVoiceInput = { screen = Routes.VOICE_INPUT },
                onChat = { enterChat() },
                onSettings = { screen = Routes.SETTING }
            )
        }
    }
}
