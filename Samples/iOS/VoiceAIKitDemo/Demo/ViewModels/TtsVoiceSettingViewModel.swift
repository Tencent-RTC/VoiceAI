// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAI Demo - TTS 音色设置 ViewModel (对齐 Android TtsVoiceSettingViewModel.kt)

import Foundation

final class TtsVoiceSettingViewModel: ObservableObject {
    @Published var voices: [TtsVoice] = TtsVoiceCatalog.online
    @Published var selectedVoiceId: String = ""

    init() {
        selectedVoiceId = VoiceAIKit.shared.getParams()?.ttsVoiceId ?? VoiceAIKit.defaultTtsVoiceId
    }

    /// 选中并持久化音色，同时同步到 Kit。
    func select(_ voice: TtsVoice) {
        selectedVoiceId = voice.voiceId
        VoiceAIPrefs.shared.ttsVoiceId = voice.voiceId
        VoiceAIKit.shared.setTtsVoiceId(voice.voiceId)
    }
}
