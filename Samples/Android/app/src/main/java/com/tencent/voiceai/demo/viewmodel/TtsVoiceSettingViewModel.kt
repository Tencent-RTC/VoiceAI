// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAI Demo - TTS 音色设置 ViewModel

package com.tencent.voiceai.demo.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.tencent.voiceai.demo.util.AppPrefs
import com.tencent.voiceai.kit.VoiceAIKit

/**
 * 一个可选的 TTS 音色：展示名 + voiceId。
 *
 * Kit 只支持在线合成（TXRealtimeTTSMode.ONLINE），
 * 因此 voiceId 必须取云端音色 ID，
 * 完整清单见 <https://cloud.tencent.com/document/product/647/131300>。
 */
data class TtsVoice(
    val displayName: String,
    val voiceId: String,
)

/** 目前支持的在线音色列表。 */
val TTS_VOICES = listOf(
    TtsVoice("威严霸总", "v-male-Bk7vD3xP"),
    TtsVoice("温柔姐姐", "v-female-R2s4N9qJ"),
    TtsVoice("傲娇学姐", "v-female-m1KpW7zE"),
    TtsVoice("夹子女生", "v-female-U8aT2yLf"),
    TtsVoice("闲聊男声", "v-male-s5NqE0rZ"),
    TtsVoice("自然男声", "v-male-W1tH9jVc"),
    TtsVoice("客服小美", "female-kefu-xiaomei"),
    TtsVoice("客服小心", "female-kefu-xiaoxin"),
    TtsVoice("客服小悦", "female-kefu-xiaoyue"),
    TtsVoice("客服小徐", "male-kefu-xiaoxu"),
    TtsVoice("客服右琪", "v-female-S6n2JxR5"),
    TtsVoice("客服小羊", "v-female-S6p4LxQ8"),
    TtsVoice("客服小丁", "v-female-H6p3LxP8"),
    TtsVoice("客服小柒", "v-male-S6m3LxP8"),
)

/**
 * TTS 音色设置页面的 ViewModel。
 *
 * 音色由 [VoiceAIKit.VoiceAIKitParams.ttsVoiceId][com.tencent.voiceai.kit.VoiceAIKit.VoiceAIKitParams] 决定，
 * 因此选择音色时做两件事：
 * 1. 调用 [VoiceAIKit.setTtsVoiceId] 更新 Kit 持有的音色；
 * 2. 写入 [AppPrefs] —— 由 MainActivity 在下一次启动 init 时读回并注入，
 *    从而实现「记住用户选择」。
 */
class TtsVoiceSettingViewModel(application: Application) : AndroidViewModel(application) {

    private val appPrefs = AppPrefs.getInstance(application)

    val voices: List<TtsVoice> = TTS_VOICES

    /** 当前选中的 voiceId（进入页面时从 Kit 初始化参数读取）。 */
    var selectedVoiceId by mutableStateOf(VoiceAIKit.requireParams().ttsVoiceId)
        private set

    /** 当前选中的音色，未知时返回 null。 */
    val selectedVoice: TtsVoice?
        get() = voices.firstOrNull { it.voiceId == selectedVoiceId }

    /** 选择音色：更新 Kit 持有的音色 + 持久化（下次启动回填）。 */
    fun selectVoice(voiceId: String) {
        if (voices.none { it.voiceId == voiceId }) return
        selectedVoiceId = voiceId
        VoiceAIKit.setTtsVoiceId(voiceId)
        appPrefs.selectedVoiceId = voiceId
    }
}
