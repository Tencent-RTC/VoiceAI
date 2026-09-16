// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAIKit - 模块内部默认值 (对齐 Android VoiceAIKitDefaults.kt)

import Foundation

enum VoiceAIKitDefaults {
    /// 默认 LLM 模型名。
    static let defaultLlmModel = "default"

    /// 语音助手 system prompt：要求回答简洁、口语化，适合被朗读。
    static let systemPrompt = "你是一个友好的中文语音助手，回答要简洁、口语化，适合被朗读出来。"

    /// LLM 回复被打断且尚无内容时，气泡展示的占位提示。
    static let pausedHint = "已暂停生成"

    /// 会话历史最多保留条数（不含 system）。
    static let maxHistory = 20
}
