// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAI Demo - 设置页 (对齐 Android ui/pages/SettingPage.kt)

import SwiftUI

struct SettingPage: View {
    @EnvironmentObject private var router: Router
    @StateObject private var vm = SettingViewModel()

    var body: some View {
        PageScaffold(title: "设置", onBack: { router.pop() }) {
            ScrollView {
                VStack(spacing: 16) {
                    // TTS 音色
                    section(title: "语音合成") {
                        navRow(title: "默认音色", value: vm.voiceDisplayName) {
                            router.push(.ttsVoice)
                        }
                    }

                    // 实时对话降噪
                    section(title: "实时对话降噪") {
                        VStack(spacing: 0) {
                            denoiseRow("关闭", .none)
                            divider
                            denoiseRow("远场降噪", .farField)
                            divider
                            denoiseRow(vm.hasVoiceprint ? "声纹降噪" : "声纹降噪（未注册）", .voiceprint)
                        }
                    }

                    // 声纹
                    section(title: "声纹") {
                        navRow(title: "声纹注册",
                               value: vm.hasVoiceprint ? "已注册" : "未注册") {
                            router.push(.voiceprint)
                        }
                    }

                    // 关于
                    section(title: "关于") {
                        HStack {
                            Text("版本").foregroundColor(Theme.textPrimary)
                            Spacer()
                            Text(vm.appVersion).foregroundColor(Theme.textSecondary)
                        }
                        .padding(.horizontal, 16).frame(height: 48)
                    }
                }
                .padding(16)
            }
        }
        .onAppear { vm.refresh() }
    }

    private var divider: some View {
        Divider().padding(.leading, 16)
    }

    private func denoiseRow(_ title: String, _ mode: DenoiseMode) -> some View {
        Button(action: { vm.setDenoise(mode) }) {
            HStack {
                Text(title).foregroundColor(Theme.textPrimary)
                Spacer()
                if vm.denoiseMode == mode {
                    Image(systemName: "checkmark").foregroundColor(Theme.brandBlue)
                }
            }
            .padding(.horizontal, 16).frame(height: 48)
        }
        .buttonStyle(.plain)
    }

    private func navRow(title: String, value: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                Text(title).foregroundColor(Theme.textPrimary)
                Spacer()
                Text(value).foregroundColor(Theme.textSecondary)
                Image(systemName: "chevron.right").foregroundColor(Theme.textSecondary)
            }
            .padding(.horizontal, 16).frame(height: 48)
        }
        .buttonStyle(.plain)
    }

    private func section<Content: View>(title: String,
                                        @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.system(size: 13))
                .foregroundColor(Theme.textSecondary)
                .padding(.leading, 4)
            VStack(spacing: 0) { content() }
                .background(Color.white)
                .cornerRadius(12)
        }
    }
}
