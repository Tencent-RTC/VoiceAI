// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAI Demo - 聊天气泡 (对齐 Android ChatBubbleAdapter / RealtimeMessageAdapter)

import SwiftUI

struct ChatBubbleView: View {
    let bubble: ChatBubble

    var body: some View {
        HStack {
            if bubble.role == .user { Spacer(minLength: 40) }
            Text(displayText)
                .font(.system(size: 16))
                .foregroundColor(bubble.role == .user ? .white : Theme.textPrimary)
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(bubble.role == .user ? Theme.brandBlue : Color.white)
                .cornerRadius(16)
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(bubble.role == .user ? Color.clear : Theme.textSecondary.opacity(0.15))
                )
            if bubble.role == .assistant { Spacer(minLength: 40) }
        }
    }

    private var displayText: String {
        if bubble.role == .assistant, bubble.isStreaming, bubble.text.isEmpty {
            return "思考中…"
        }
        return bubble.text
    }
}

/// 空态占位。
struct EmptyChatHint: View {
    let icon: String
    let text: String
    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 40))
                .foregroundColor(Theme.textSecondary.opacity(0.5))
            Text(text)
                .font(.system(size: 14))
                .foregroundColor(Theme.textSecondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
