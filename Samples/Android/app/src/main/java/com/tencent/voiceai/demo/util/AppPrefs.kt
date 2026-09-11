// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAI Demo - 宿主侧偏好存储（SharedPreferences）

package com.tencent.voiceai.demo.util

import android.content.Context

/**
 * Demo（宿主应用）自身的轻量偏好存储。
 *
 * 与 Kit 的 [com.tencent.voiceai.kit.common.VoiceAIPrefs] 相互隔离：
 * Kit 的偏好服务于 Kit 内部状态，而这里保存的是宿主自己的选择结果，
 * 用于在下次启动初始化 Kit 时回填到 [com.tencent.voiceai.kit.VoiceAIKit.VoiceAIKitParams]，
 * 从而实现「记住用户选择」。
 */
class AppPrefs private constructor(context: Context) {

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /**
     * 用户最近一次选择的 TTS 音色 voiceId；空串表示尚未选择。
     * 初始化 Kit 时应读取本值回填到配置，为空则使用 Kit 的默认音色。
     */
    var selectedVoiceId: String
        get() = prefs.getString(KEY_SELECTED_VOICE_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SELECTED_VOICE_ID, value).apply()

    companion object {
        private const val PREF_NAME = "voiceai_demo_prefs"
        private const val KEY_SELECTED_VOICE_ID = "selected_voice_id"

        @Volatile
        private var instance: AppPrefs? = null

        /** 进程内单例；固定使用 Application 上下文，避免持有 Activity 引用。 */
        fun getInstance(context: Context): AppPrefs =
            instance ?: synchronized(this) {
                instance ?: AppPrefs(context.applicationContext).also { instance = it }
            }
    }
}
