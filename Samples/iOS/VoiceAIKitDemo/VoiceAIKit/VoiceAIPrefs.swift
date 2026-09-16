// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAIKit - 本地偏好存储 (对齐 Android VoiceAIPrefs.kt)
//
// 使用 UserDefaults 持久化 Kit 级别的用户偏好。宿主与 Kit 共享同一份偏好。

import Foundation

/// 实时对话降噪算法。
enum DenoiseMode: String {
    case none = "none"           // 关闭
    case farField = "farField"   // 远场降噪
    case voiceprint = "voiceprint" // 声纹降噪
}

final class VoiceAIPrefs {

    static let shared = VoiceAIPrefs()
    private init() {}

    private let defaults = UserDefaults.standard

    private enum Keys {
        static let chatInputMode = "voiceai_kit_chat_input_mode"
        static let realtimeDenoiseMode = "voiceai_kit_realtime_denoise_mode"
        static let voiceprintFileName = "voiceai_kit_voiceprint_file_name"
        static let ttsVoiceId = "voiceai_kit_tts_voice_id"
    }

    /// 已选 TTS 音色 voiceId（未选返回空串，由 Kit 使用默认音色）。
    var ttsVoiceId: String {
        get { defaults.string(forKey: Keys.ttsVoiceId) ?? "" }
        set { defaults.set(newValue, forKey: Keys.ttsVoiceId) }
    }

    /// AI 对话输入栏模式（true=语音模式，false=键盘模式）。
    var chatInputMode: Bool {
        get { defaults.bool(forKey: Keys.chatInputMode) }
        set { defaults.set(newValue, forKey: Keys.chatInputMode) }
    }

    /// 实时对话降噪算法。
    var realtimeDenoiseMode: DenoiseMode {
        get { DenoiseMode(rawValue: defaults.string(forKey: Keys.realtimeDenoiseMode) ?? "") ?? .none }
        set { defaults.set(newValue.rawValue, forKey: Keys.realtimeDenoiseMode) }
    }

    /// 最近注册声纹 WAV 文件名（存于沙盒 Documents/voiceprint 下）。
    var voiceprintFileName: String {
        get { defaults.string(forKey: Keys.voiceprintFileName) ?? "" }
        set { defaults.set(newValue, forKey: Keys.voiceprintFileName) }
    }
}
