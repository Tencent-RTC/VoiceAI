// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAIKitDemo - App 入口 (对齐 Android MainActivity.kt / Application 初始化)
//
// 启动时注入 VoiceAIKit.Params（ASR/TTS 鉴权 + LLM 配置），并配置音频会话。

import SwiftUI
import AVFoundation

@main
struct VoiceAIKitDemoApp: App {

    init() {
        configureVoiceAIKit()
        configureAudioSession()
    }

    var body: some Scene {
        WindowGroup {
            AppNavigation()
        }
    }

    /// 注入 Kit 全局配置。TTS 音色优先取用户已选（VoiceAIPrefs），否则用默认音色。
    private func configureVoiceAIKit() {
        let savedVoice = VoiceAIPrefs.shared.ttsVoiceId
        let voiceId = savedVoice.isEmpty ? VoiceAIKit.defaultTtsVoiceId : savedVoice

        let params = VoiceAIKit.Params(
            appId: Config.sdkAppId,
            userId: Config.userId,
            userSig: Config.genUserSig(),
            llm: VoiceAIKit.LLMParams(
                apiUrl: Config.openApiUrl,
                apiKey: Config.openApiKey,
                model: Config.openApiModel
            ),
            ttsVoiceId: voiceId
        )
        VoiceAIKit.shared.initialize(params)
    }

    /// 语音采集与播放共用的音频会话。
    private func configureAudioSession() {
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playAndRecord,
                                    mode: .voiceChat,
                                    options: [.defaultToSpeaker, .allowBluetooth])
            try session.setActive(true)
        } catch {
            NSLog("[VoiceAIKitDemo] AVAudioSession error: %@", error.localizedDescription)
        }
    }
}
