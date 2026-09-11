// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAI Demo - 单 Activity 入口

package com.tencent.voiceai.demo

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.tencent.voiceai.demo.ui.theme.BackgroundColor
import com.tencent.voiceai.demo.ui.theme.VoiceAIDemoTheme
import com.tencent.voiceai.demo.util.AppPrefs
import com.tencent.voiceai.demo.util.Config
import com.tencent.voiceai.kit.VoiceAIKit

/**
 * 全应用唯一的 Activity，仅作为 Compose 宿主。
 * 具体页面由 [AppNavigation] 以「单 Activity + 多 Page」的方式管理。
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 回填上次选择的 TTS 音色；未选择过则使用 Kit 的默认音色
        val savedVoiceId = AppPrefs.getInstance(this).selectedVoiceId

        // VoiceAIKit 不内置任何账号信息与密钥，由宿主一次性注入。
        // 生产环境建议：UserSig 由服务端下发，其余密钥从服务端或 BuildConfig 读取。
        VoiceAIKit.init(
                VoiceAIKit.VoiceAIKitParams(
                        appId = Config.SDKAPPID,
                        userId = Config.USERID,
                        userSig = Config.genTestUserSig(Config.USERID),
                        llm = VoiceAIKit.LLMParams(
                                apiUrl = Config.OPEN_AIP_URL,
                                apiKey = Config.OPEN_AIP_KEY,
                                model = Config.OPEN_AIP_MODEL,
                        ),
                        ttsVoiceId = savedVoiceId.ifBlank {
                            VoiceAIKit.VoiceAIKitParams.DEFAULT_TTS_VOICE_ID
                        },
                )
        )

        setContent {
            VoiceAIDemoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BackgroundColor
                ) {
                    AppNavigation()
                }
            }
        }
    }
}
