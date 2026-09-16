// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAI Demo - 实时通话页 (对齐 Android vak_fragment_realtime_chat.xml)
//
// 顶部：居中标题 + 右侧「字」开关（圆球 / 文本）
// 中部：圆球 + 波形，或消息列表（点击可打断）
// 底部：状态提示 + 四枚白色圆按钮（麦克风 / 播报 / 打断 / 退出）

import SwiftUI

struct RealtimeChatPage: View {
    @EnvironmentObject private var router: Router
    @StateObject private var controller = RealtimeChatController()
    @State private var showMessages = false

    /// AI 是否正在产出（思考/回复），用于打断按钮的激活态。
    private var canInterrupt: Bool {
        controller.state == .thinking || controller.state == .speaking
    }

    var body: some View {
        VStack(spacing: 0) {
            topBar
            contentArea
            bottomBar
        }
        .background(Theme.background.ignoresSafeArea())
        .onAppear { controller.start() }
        .onDisappear { controller.stop() }
        .toast($controller.toast)
    }

    // MARK: - 顶部栏

    private var topBar: some View {
        ZStack {
            Text("AI 实时对话")
                .font(.system(size: 19, weight: .bold))
                .foregroundColor(Theme.textPrimary)

            HStack {
                Button(action: { router.pop() }) {
                    Image(systemName: "chevron.down")
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundColor(Theme.textPrimary)
                        .frame(width: 34, height: 34)
                }
                .buttonStyle(.plain)

                Spacer()

                // 「字」开关：圆球 / 文本模式
                Button(action: { showMessages.toggle() }) {
                    Text("字")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(showMessages ? Theme.brandBlue : Color(hex: 0x4B5468))
                        .frame(width: 34, height: 34)
                        .background(
                            Circle().fill(showMessages ? Color(hex: 0xDCE8FF) : Color(hex: 0xE7ECF7))
                        )
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 14)
    }

    // MARK: - 内容区

    /// 占满标题栏与底部栏之间的空间；点击可打断当前回复。
    private var contentArea: some View {
        Group {
            if showMessages { messageList } else { orbArea }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .contentShape(Rectangle())
        .onTapGesture {
            if canInterrupt { controller.interruptResponse() }
        }
    }

    private var orbArea: some View {
        VStack(spacing: 24) {
            VoiceOrbView(active: controller.state == .listening || controller.state == .speaking)
            VoiceWaveView(level: controller.waveLevel, color: Theme.brandBlue)
                .frame(height: 40)
                .padding(.horizontal, 60)
                .opacity(controller.state == .listening ? 1 : 0.35)
        }
    }

    /// 文本模式：左右 28pt 对齐 Android，上下留白收紧（Android 为 45/42，此处留 16）。
    private var messageList: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 12) {
                    ForEach(controller.messages) { ChatBubbleView(bubble: $0) }
                    Color.clear.frame(height: 1).id("bottom")
                }
                .padding(.horizontal, 28)
                .padding(.vertical, 16)
            }
            .onChange(of: controller.messages) { _ in
                withAnimation { proxy.scrollTo("bottom", anchor: .bottom) }
            }
        }
    }

    // MARK: - 底部控制区

    private var bottomBar: some View {
        VStack(spacing: 0) {
            Text(hintText)
                .font(.system(size: 15))
                .foregroundColor(Theme.textSecondary)
                .padding(.bottom, 24)

            HStack(spacing: 18) {
                circleButton(icon: controller.micEnabled ? "mic.fill" : "mic.slash.fill",
                             tint: controller.micEnabled ? Color(hex: 0x3AA655) : Color(hex: 0x4B4B4B),
                             action: controller.toggleMic)

                circleButton(icon: controller.ttsEnabled ? "speaker.wave.2.fill" : "speaker.slash.fill",
                             tint: controller.ttsEnabled ? Color(hex: 0x4B4B4B) : Color(hex: 0x9AA0A6),
                             action: controller.toggleTts)

                circleButton(icon: "stop.fill",
                             tint: canInterrupt ? Color(hex: 0xFF9500) : Color(hex: 0x4B4B4B),
                             action: controller.interruptResponse)

                circleButton(icon: "xmark", tint: Color(hex: 0xFF3B30)) {
                    controller.stop()
                    router.pop()
                }
            }
            .padding(.vertical, 8)
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 10)
    }

    /// 白色圆底 + 着色图标（对齐 Android vak_bg_circle_white + tint）。
    private func circleButton(icon: String, tint: Color,
                              action: @escaping () -> Void) -> some View {
        Button(action: action) {
            ZStack {
                Circle().fill(Color.white)
                Image(systemName: icon)
                    .font(.system(size: 22))
                    .foregroundColor(tint)
            }
            .frame(width: 56, height: 56)
        }
        .buttonStyle(.plain)
    }

    private var hintText: String {
        switch controller.state {
        case .idle: return "已停止，点击麦克风开启"
        case .listening: return "正在聆听，直接说话即可"
        case .thinking: return "AI 正在思考"
        case .speaking: return "AI 正在回复"
        }
    }
}
