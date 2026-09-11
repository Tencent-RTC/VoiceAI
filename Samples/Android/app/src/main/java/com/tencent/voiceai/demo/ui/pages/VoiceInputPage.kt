// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAI Demo - 语音输入页面（聊天界面 + 文本/语音输入 + echo 回复）

package com.tencent.voiceai.demo.ui.pages

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tencent.voiceai.demo.ui.components.PageScaffold
import com.tencent.voiceai.demo.ui.theme.TextPrimary
import com.tencent.voiceai.demo.viewmodel.VoiceInputMessage
import com.tencent.voiceai.demo.viewmodel.VoiceInputViewModel
import com.tencent.voiceai.kit.widget.VoiceInputBar

@Composable
fun VoiceInputPage(onBack: () -> Unit, viewModel: VoiceInputViewModel = viewModel()) {
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current

    // 新消息时自动滚动到底部
    LaunchedEffect(viewModel.messages.size) {
        if (viewModel.messages.isNotEmpty() && listState.layoutInfo.totalItemsCount > 0) {
            listState.scrollToItem(viewModel.messages.lastIndex)
        }
    }

    PageScaffold(title = "语音输入", onBack = onBack) {
        Column(modifier = Modifier.fillMaxSize()) {

            // 会话历史（点击空白处收起输入法）
            LazyColumn(
                    state = listState,
                    modifier =
                            Modifier.weight(1f)
                                    .fillMaxWidth()
                                    .clickable(
                                            interactionSource =
                                                    remember { MutableInteractionSource() },
                                            indication = null
                                    ) { keyboardController?.hide() }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(count = viewModel.messages.size, key = { index -> "im-$index" }) { index ->
                    ChatBubble(msg = viewModel.messages[index])
                }
            }

            // 输入栏：由原生 VoiceInputBar（View + XML）承载
            // 内部已自持 ASR 状态机与「识别结果确认」弹窗，这里只需接收最终文本
            VoiceInputBarHost(onSendMessage = viewModel::sendMessage)
        }
    }
}

/**
 * 以 [AndroidView] 承载原生 [VoiceInputBar]。
 *
 * - 发送文本：文本输入发送 与 语音确认发送 统一走 [onSendMessage]；
 * - 麦克风权限：由本 Composable 申请，结果回传给 View 以继续待处理的语音输入；
 * - 生命周期：离开页面时释放 ASR 资源。
 */
@Composable
private fun VoiceInputBarHost(onSendMessage: (String) -> Unit) {
    val context = LocalContext.current
    var bar: VoiceInputBar? by remember { mutableStateOf(null) }

    // 麦克风权限：申请结果回传给 VoiceInputBar
    val recordPermission =
            rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted
                ->
                bar?.onRecordAudioPermissionResult(granted)
            }

    AndroidView(
            factory = { ctx ->
                VoiceInputBar(ctx).apply {
                    setOnVoiceInputListener(
                            object : VoiceInputBar.OnVoiceInputListener {
                                override fun onSendMessage(text: String) {
                                    onSendMessage(text)
                                }

                                override fun onRequestRecordAudioPermission() {
                                    recordPermission.launch(
                                            android.Manifest.permission.RECORD_AUDIO
                                    )
                                }

                                override fun onToast(message: String) {
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                }
                            }
                    )
                    bar = this
                }
            },
            modifier = Modifier.fillMaxWidth(),
            update = {}
    )

    // 退出页面时释放 ASR 资源
    DisposableEffect(Unit) { onDispose { bar?.release() } }
}

/** 单条聊天气泡：用户靠右（蓝色渐变），助手靠左（白底）。 */
@Composable
private fun ChatBubble(msg: VoiceInputMessage) {
    val isUser = msg.fromUser
    Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                    modifier = Modifier.size(34.dp).clip(CircleShape).background(Color(0xFFE7ECF7)),
                    contentAlignment = Alignment.Center
            ) {
                Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "AI 头像",
                        tint = Color(0xFF4B5468),
                        modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        Box(
                modifier =
                        Modifier.widthIn(max = 260.dp)
                                .shadow(2.dp, RoundedCornerShape(14.dp))
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                        if (isUser)
                                                Brush.linearGradient(
                                                        listOf(Color(0xFF2B6CF6), Color(0xFF5B8DEF))
                                                )
                                        else Brush.linearGradient(listOf(Color.White, Color.White))
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                    text = msg.text,
                    fontSize = 15.sp,
                    color = if (isUser) Color.White else TextPrimary,
                    lineHeight = 21.sp
            )
        }
    }
}
