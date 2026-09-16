// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAI Demo - 声纹注册页 (对齐 Android ui/pages/VoiceprintRegisterPage.kt)
//
// 朗读提示文本 → 录制 ≥10s → 试听 → 注册。

import SwiftUI

struct VoiceprintRegisterPage: View {
    @EnvironmentObject private var router: Router
    @StateObject private var vm = VoiceprintRegisterViewModel()

    var body: some View {
        PageScaffold(title: "声纹注册", onBack: { router.pop() }) {
            VStack(spacing: 20) {
                // 提示文本卡片
                VStack(alignment: .leading, spacing: 8) {
                    Text("请朗读以下文本（不少于 10 秒）")
                        .font(.system(size: 13))
                        .foregroundColor(Theme.textSecondary)
                    Text(vm.promptText)
                        .font(.system(size: 17))
                        .foregroundColor(Theme.textPrimary)
                        .lineSpacing(6)
                }
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.white)
                .cornerRadius(12)

                // 进度
                VStack(spacing: 8) {
                    Text(String(format: "%.1fs / %.0fs", vm.elapsed, VoiceprintRegisterViewModel.minDuration))
                        .font(.system(size: 15, weight: .medium))
                        .foregroundColor(vm.elapsed >= VoiceprintRegisterViewModel.minDuration
                                         ? Theme.brandBlue : Theme.textSecondary)
                    ProgressView(value: vm.progress)
                        .tint(Theme.brandBlue)
                }
                .padding(.horizontal, 8)

                Spacer()

                // 录制按钮
                Button(action: vm.toggleRecord) {
                    ZStack {
                        Circle()
                            .fill(vm.recording ? Color.red : Theme.brandBlue)
                            .frame(width: 96, height: 96)
                        Image(systemName: vm.recording ? "stop.fill" : "mic.fill")
                            .font(.system(size: 36))
                            .foregroundColor(.white)
                    }
                }
                Text(vm.recording ? "点击停止" : (vm.hasRecorded ? "点击重新录制" : "点击开始录制"))
                    .font(.system(size: 13))
                    .foregroundColor(Theme.textSecondary)

                // 试听 + 注册
                HStack(spacing: 12) {
                    Button(action: vm.togglePlay) {
                        Label(vm.playing ? "停止" : "试听",
                              systemImage: vm.playing ? "stop.circle" : "play.circle")
                            .frame(maxWidth: .infinity).frame(height: 48)
                            .foregroundColor(Theme.brandBlue)
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.brandBlue))
                    }
                    .disabled(!vm.hasRecorded)
                    .opacity(vm.hasRecorded ? 1 : 0.4)

                    Button(action: vm.register) {
                        Text("注册声纹")
                            .frame(maxWidth: .infinity).frame(height: 48)
                            .foregroundColor(.white)
                            .background(vm.canRegister ? Theme.brandBlue : Theme.textSecondary)
                            .cornerRadius(12)
                    }
                    .disabled(!vm.canRegister)
                }
                .padding(.bottom, 20)
            }
            .padding(16)
        }
        .toast($vm.toast)
    }
}
