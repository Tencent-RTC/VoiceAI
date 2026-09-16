// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAIKit - 在线 TTS 音色目录 (对齐 Android 音色清单)

import Foundation

/// 一个 TTS 音色。
struct TtsVoice: Identifiable, Equatable {
    var id: String { voiceId }
    let displayName: String
    let voiceId: String
}

enum TtsVoiceCatalog {
    /// 在线音色清单（voiceId 为云端在线音色 ID）。
    static let online: [TtsVoice] = [
        TtsVoice(displayName: "温柔姐姐", voiceId: "v-female-R2s4N9qJ"),
        TtsVoice(displayName: "威严霸总", voiceId: "v-male-Bk7vD3xP"),
        TtsVoice(displayName: "傲娇学姐", voiceId: "v-female-m1KpW7zE"),
        TtsVoice(displayName: "夹子女生", voiceId: "v-female-U8aT2yLf"),
        TtsVoice(displayName: "闲聊男声", voiceId: "v-male-s5NqE0rZ"),
        TtsVoice(displayName: "自然男声", voiceId: "v-male-W1tH9jVc"),
        TtsVoice(displayName: "客服小美", voiceId: "female-kefu-xiaomei"),
        TtsVoice(displayName: "客服小心", voiceId: "female-kefu-xiaoxin"),
        TtsVoice(displayName: "客服小悦", voiceId: "female-kefu-xiaoyue"),
        TtsVoice(displayName: "客服小徐", voiceId: "male-kefu-xiaoxu"),
        TtsVoice(displayName: "客服右琪", voiceId: "v-female-S6n2JxR5"),
        TtsVoice(displayName: "客服小羊", voiceId: "v-female-S6p4LxQ8"),
        TtsVoice(displayName: "客服小丁", voiceId: "v-female-H6p3LxP8"),
        TtsVoice(displayName: "客服小柒", voiceId: "v-male-S6m3LxP8"),
    ]

    /// 根据 voiceId 找展示名，找不到返回 voiceId 本身。
    static func displayName(of voiceId: String) -> String {
        online.first(where: { $0.voiceId == voiceId })?.displayName ?? voiceId
    }
}
