// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAI Demo - 语音输入页 ViewModel (对齐 Android VoiceInputViewModel.kt)
//
// 该页仅演示 VoiceInputBar 的识别能力：把识别到的文本作为一条消息回显到列表。

import Foundation

final class VoiceInputViewModel: ObservableObject {
    @Published var items: [ChatBubble] = []
    @Published var inputText = ""

    /// 收到识别/输入文本，回显为一条用户消息。
    func onSend(_ text: String) {
        let t = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !t.isEmpty else { return }
        items.append(ChatBubble(role: .user, text: t))
    }

    func clear() { items.removeAll() }
}
