// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAI Demo - AI 对话页 (对齐 Android ui/pages/ChatPage.kt)
//
// 顶部：标题 + 自动朗读开关 + 实时通话入口(电话按钮)
// 中部：气泡列表（LLM 流式）
// 底部：VoiceInputBar（文字/语音输入）

import SwiftUI

struct ChatPage: View {
    @EnvironmentObject private var router: Router
    @StateObject private var controller = AIChatController()
    @State private var inputText = ""

    var body: some View {
        PageScaffold(title: "AI 对话", onBack: { router.pop() }, trailing: {
            HStack(spacing: 4) {
                // 自动朗读开关
                Button(action: controller.toggleAutoSpeak) {
                    Image(systemName: controller.autoSpeak ? "speaker.wave.2.fill" : "speaker.slash.fill")
                        .font(.system(size: 18))
                        .foregroundColor(controller.autoSpeak ? Theme.brandBlue : Theme.textSecondary)
                        .frame(width: 40, height: 40)
                }
                // 进入实时通话
                Button(action: { router.push(.realtimeChat) }) {
                    Image(systemName: "phone.fill")
                        .font(.system(size: 18))
                        .foregroundColor(Theme.brandBlue)
                        .frame(width: 40, height: 40)
                }
            }
        }) {
            VStack(spacing: 0) {
                if controller.messages.isEmpty {
                    EmptyChatHint(icon: "bubble.left.and.bubble.right",
                                  text: "开始和 AI 聊天吧，可打字或按住说话")
                } else {
                    ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(spacing: 12) {
                            ForEach(controller.messages) { ChatBubbleView(bubble: $0) }
                            Color.clear.frame(height: 1).id("bottom")
                        }
                        .padding(16)
                    }
                    .scrollDismissesKeyboard(.immediately)
                    .onChange(of: controller.messages) { _ in
                            withAnimation { proxy.scrollTo("bottom", anchor: .bottom) }
                        }
                    }
                }

                if controller.isGenerating {
                    Button(action: controller.stopGenerating) {
                        Label("停止生成", systemImage: "stop.circle")
                            .font(.system(size: 14))
                            .foregroundColor(Theme.brandBlue)
                    }
                    .padding(.vertical, 6)
                }

                Divider()
                VoiceInputBar(text: $inputText) { text in
                    controller.sendText(text)
                }
            }
            .hideKeyboardOnTap()
        }
        .toast($controller.toast)
        .onAppear { controller.onEnterPage() }
        .onDisappear { controller.onLeavePage() }
    }
}
