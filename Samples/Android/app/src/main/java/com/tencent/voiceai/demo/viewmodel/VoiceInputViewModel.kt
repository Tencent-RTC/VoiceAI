// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAI Demo - 语音输入 ViewModel

package com.tencent.voiceai.demo.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/**
 * 语音输入场景中的一条消息。
 */
data class VoiceInputMessage(
    val fromUser: Boolean,
    val text: String
)

/**
 * 语音输入场景 ViewModel：
 * - 维护会话历史，发送消息时默认以 echo 方式自动回复一条。
 *
 * 语音识别（含输入框内联识别、按住说话、识别结果确认弹窗）全部由原生
 * [com.tencent.voiceai.kit.widget.VoiceInputBar] 自持，本 ViewModel 不再涉及 ASR，
 * 只负责接收最终文本并追加到会话。
 */
class VoiceInputViewModel : ViewModel() {

    // 会话历史
    val messages = mutableStateListOf<VoiceInputMessage>()

    // 输入框文本
    var inputText by mutableStateOf("")
        private set

    // 是否正在模拟回复
    var replying by mutableStateOf(false)
        private set

    init {
        messages.add(
            VoiceInputMessage(
                fromUser = false,
                text = "你好呀，在下方输入文字，或切到语音模式按住说话发条消息吧。"
            )
        )
    }

    fun onInputChange(text: String) {
        inputText = text
    }

    /** 发送文本消息，并自动以 echo 方式回一条。 */
    fun sendText() {
        val t = inputText.trim()
        if (t.isEmpty()) return
        messages.add(VoiceInputMessage(fromUser = true, text = t))
        inputText = ""
        echo(t)
    }

    /**
     * 发送一条指定文本的消息，并自动以 echo 方式回一条。
     *
     * 供原生 VoiceInputBar 使用：输入框与识别文本都由 View 自身维护，
     * View 只把最终要发送的文本回调出来，因此这里不读取 [inputText]。
     */
    fun sendMessage(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        messages.add(VoiceInputMessage(fromUser = true, text = t))
        inputText = ""
        echo(t)
    }

    /** echo：把用户的内容原样回显为一条新消息。 */
    private fun echo(userText: String) {
        replying = true
        messages.add(VoiceInputMessage(fromUser = false, text = userText))
        replying = false
    }
}
