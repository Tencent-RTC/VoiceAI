// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAIKit - 模块内偏好存储（SharedPreferences）

package com.tencent.voiceai.kit.common

import android.content.Context

/**
 * VoiceAIKit 的轻量偏好存储，持久化 AI 对话相关的界面状态与开关。
 *
 * 使用独立的 SharedPreferences 文件（voiceai_kit_prefs）与宿主应用自身的偏好隔离，避免键名冲突；
 * 但本类对宿主可见，且设计为宿主与 Kit 共享同一份状态：
 * Kit 内部（AIChat / RealtimeChat）读取 [realtimeDenoiseMode]、[voiceprintFileName]、
 * [chatInputMode] 时，读的就是宿主写入的值。
 *
 * 因此宿主若提供「声纹注册」等入口，应通过本类写回结果，
 * 不要另建一份偏好存储，否则会出现「设置写了但 Kit 读不到」的问题。
 *
 * 注意：TTS 音色不再由偏好存储决定，统一来自 VoiceAIKitParams.ttsVoiceId（见 AIChatController）。
 */
class VoiceAIPrefs private constructor(context: Context) {

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /** AI 对话页底部输入栏模式：true = 语音输入（按住说话），false = 文字输入。 */
    var chatInputMode: Boolean
        get() = prefs.getBoolean(KEY_CHAT_INPUT_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_CHAT_INPUT_MODE, value).apply()

    /**
     * 实时 AI 对话的降噪算法：
     * "none"（关闭）/ "farField"（远场降噪）/ "voiceprint"（声纹降噪）。
     */
    var realtimeDenoiseMode: String
        get() = prefs.getString(KEY_REALTIME_DENOISE_MODE, DEFAULT_REALTIME_DENOISE_MODE)
            ?: DEFAULT_REALTIME_DENOISE_MODE
        set(value) = prefs.edit().putString(KEY_REALTIME_DENOISE_MODE, value).apply()

    /**
     * 最近一次成功注册的声纹 WAV 文件名（文件保存在 filesDir 下）。
     * 声纹降噪时读取；无注册结果时为空串。可由接入方通过其它入口写入。
     */
    var voiceprintFileName: String
        get() = prefs.getString(KEY_VOICEPRINT_FILE_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_VOICEPRINT_FILE_NAME, value).apply()

    companion object {
        private const val PREF_NAME = "voiceai_kit_prefs"

        private const val KEY_CHAT_INPUT_MODE = "chat_input_mode"

        private const val KEY_REALTIME_DENOISE_MODE = "realtime_denoise_mode"
        private const val DEFAULT_REALTIME_DENOISE_MODE = "none"

        private const val KEY_VOICEPRINT_FILE_NAME = "voiceprint_file_name"

        @Volatile
        private var instance: VoiceAIPrefs? = null

        /** 进程内单例；固定使用 Application 上下文，避免持有 Activity 引用。 */
        fun getInstance(context: Context): VoiceAIPrefs =
            instance ?: synchronized(this) {
                instance ?: VoiceAIPrefs(context.applicationContext).also { instance = it }
            }
    }
}
