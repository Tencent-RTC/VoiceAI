// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAI Demo - 设置页 ViewModel (对齐 Android SettingViewModel.kt)

import Foundation

final class SettingViewModel: ObservableObject {
    @Published var voiceDisplayName: String = ""
    @Published var denoiseMode: DenoiseMode = VoiceAIPrefs.shared.realtimeDenoiseMode
    @Published var hasVoiceprint: Bool = false

    func refresh() {
        let voiceId = VoiceAIKit.shared.getParams()?.ttsVoiceId ?? VoiceAIKit.defaultTtsVoiceId
        voiceDisplayName = TtsVoiceCatalog.displayName(of: voiceId)
        denoiseMode = VoiceAIPrefs.shared.realtimeDenoiseMode
        hasVoiceprint = VoiceprintStore.hasVoiceprint
    }

    func setDenoise(_ mode: DenoiseMode) {
        denoiseMode = mode
        VoiceAIPrefs.shared.realtimeDenoiseMode = mode
    }

    /// App 版本号。
    var appVersion: String {
        let v = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"
        let b = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "1"
        return "\(v) (\(b))"
    }
}
