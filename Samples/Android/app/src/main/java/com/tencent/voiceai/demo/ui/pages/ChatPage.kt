// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAI Demo - AI 对话页面
//
// 对话主体复用 VoiceAIKit 的 AIChatFragment（不含标题栏）。
// 标题栏由本页（宿主）通过 PageScaffold 提供：返回、进入实时对话、自动朗读开关。
// 「实时对话」直接启动 VoiceAIKit 的 RealtimeChatActivity；
// 「自动朗读」通过 AIChatFragment 暴露的 toggleAutoSpeak / onAutoSpeakChanged 联动。

package com.tencent.voiceai.demo.ui.pages

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import com.tencent.voiceai.demo.ui.components.PageScaffold
import com.tencent.voiceai.kit.view.AIChatFragment
import com.tencent.voiceai.kit.view.RealtimeChatActivity

private val IconButtonBg = Color(0xFFE7ECF7)
private val IconButtonBgActive = Color(0xFFDCE8FF)
private val BrandBlue = Color(0xFF2B6CF6)
private val NeutralIcon = Color(0xFF4B5468)

/** FragmentContainerView 的固定容器 id，供 FragmentManager 挂载 AIChatFragment。 */
private val CHAT_CONTAINER_ID = View.generateViewId()

/** 本页在 FragmentManager 中的 tag，用于查找 / 移除已添加的 Fragment。 */
private const val CHAT_FRAGMENT_TAG = "vak_ai_chat_fragment"

/**
 * AI 对话页：标题栏由本页提供，对话主体承载 VoiceAIKit 的 [AIChatFragment]。
 */
@Composable
fun ChatPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as FragmentActivity

    // 自动朗读开关状态：由 Fragment 通过 onAutoSpeakChanged 回调驱动
    var autoSpeak by remember { mutableStateOf(true) }
    // 持有 Fragment 引用，供标题栏喇叭按钮调用 toggleAutoSpeak()
    val fragmentHolder = remember { arrayOfNulls<AIChatFragment>(1) }

    DisposableEffect(Unit) {
        val fm = activity.supportFragmentManager
        val fragment = (fm.findFragmentByTag(CHAT_FRAGMENT_TAG) as? AIChatFragment)
            ?: AIChatFragment.newInstance().also {
                fm.beginTransaction()
                    .replace(CHAT_CONTAINER_ID, it, CHAT_FRAGMENT_TAG)
                    .commitNow()
            }
        fragment.onAutoSpeakChanged = { autoSpeak = it }
        autoSpeak = fragment.isAutoSpeak()
        fragmentHolder[0] = fragment

        onDispose {
            fragment.onAutoSpeakChanged = null
            fragmentHolder[0] = null
            if (!activity.isFinishing) {
                fm.findFragmentByTag(CHAT_FRAGMENT_TAG)?.let {
                    fm.beginTransaction().remove(it).commitNowAllowingStateLoss()
                }
            }
        }
    }

    PageScaffold(
        title = "AI 对话",
        onBack = onBack,
        actions = {
            // 电话：直接进入 VoiceAIKit 的实时对话整页
            TitleBarAction(
                imageVector = Icons.Default.Call,
                contentDescription = "实时对话",
                active = false,
                onClick = { RealtimeChatActivity.start(context) }
            )
            Spacer(modifier = Modifier.width(8.dp))
            // 喇叭：是否自动朗读 AI 回复（状态与 Fragment 联动）
            TitleBarAction(
                imageVector = if (autoSpeak) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                contentDescription = if (autoSpeak) "关闭自动朗读" else "开启自动朗读",
                active = autoSpeak,
                onClick = { fragmentHolder[0]?.toggleAutoSpeak() }
            )
        }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                FragmentContainerView(ctx).apply { id = CHAT_CONTAINER_ID }
            }
        )
    }
}

/** 标题栏右侧圆形图标按钮。 */
@Composable
private fun TitleBarAction(
    imageVector: ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (active) IconButtonBgActive else IconButtonBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = if (active) BrandBlue else NeutralIcon,
            modifier = Modifier.size(22.dp)
        )
    }
}
