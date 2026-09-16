// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAIKit - 会话消息模型 (对齐 Android 气泡数据结构)

import Foundation

/// 一条聊天气泡。
struct ChatBubble: Identifiable, Equatable {
    enum Role { case user, assistant }
    let id = UUID()
    let role: Role
    var text: String
    var isStreaming: Bool = false
}

/// 从流式文本中按句末标点切分出「完整句子」，用于逐句喂给 TTS。
struct SentenceSplitter {
    private var buffer = ""
    private let terminators: Set<Character> = ["。", "！", "？", "；", "\n", ".", "!", "?", ";"]

    /// 追加增量文本，返回其中已成句的片段（可能为空或多句）。
    mutating func feed(_ delta: String) -> [String] {
        buffer += delta
        var sentences: [String] = []
        var current = ""
        for ch in buffer {
            current.append(ch)
            if terminators.contains(ch) {
                let trimmed = current.trimmingCharacters(in: .whitespacesAndNewlines)
                if !trimmed.isEmpty { sentences.append(trimmed) }
                current = ""
            }
        }
        // current 里是尚未成句的残留，留待下次拼接
        buffer = current
        return sentences
    }

    /// 结束时取出剩余不足一句的文本。
    mutating func flush() -> String {
        let rest = buffer.trimmingCharacters(in: .whitespacesAndNewlines)
        buffer = ""
        return rest
    }
}
