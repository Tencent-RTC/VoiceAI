// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAI Demo - 首页 (对齐 Android ui/pages/HomePage.kt)
//
// 顶部渐变标题 + 右上圆形设置入口；两张功能卡片（语音输入 / AI 对话）+ 页脚。

import SwiftUI

struct HomePage: View {
    @EnvironmentObject private var router: Router

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                header

                if !Config.isConfigured {
                    Spacer().frame(height: 16)
                    configBanner
                }

                Spacer().frame(height: 16)

                demoCard(
                    number: "01",
                    title: "语音输入",
                    subtitle: "体验实时语音识别",
                    description: "实时识别 · 一句话识别 · 语音文件识别",
                    imageName: "ic_voice_input",
                    imageSize: 96,
                    gradient: [Theme.purpleStart, Theme.purpleEnd],
                    accent: Theme.accentPurple
                ) { router.push(.voiceInput) }

                Spacer().frame(height: 12)

                demoCard(
                    number: "02",
                    title: "AI 对话",
                    subtitle: "与 AI 自然地说话",
                    description: "实时语音对话 · 智能理解 · 流畅回复",
                    imageName: "ic_ai_chat",
                    imageSize: 110,
                    gradient: [Theme.greenStart, Theme.greenEnd],
                    accent: Theme.accentGreen
                ) { router.push(.chat) }

                Spacer().frame(height: 20)

                footer

                Spacer().frame(height: 30)
            }
        }
        .background(Theme.background.ignoresSafeArea())
    }

    // MARK: - Header

    private var header: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 0) {
                Text("VoiceAI")
                    .font(.system(size: 46, weight: .bold))
                    .tracking(-1.5)
                    .foregroundStyle(
                        LinearGradient(colors: Theme.titleGradient,
                                       startPoint: .leading, endPoint: .trailing)
                    )

                Spacer().frame(height: 5)

                Text("探索声音的无限可能")
                    .font(.system(size: 15))
                    .foregroundColor(Theme.textSecondary)

                Spacer().frame(height: 10)

                RoundedRectangle(cornerRadius: 10)
                    .fill(
                        LinearGradient(colors: Theme.underlineGradient,
                                       startPoint: .leading, endPoint: .trailing)
                    )
                    .frame(width: 60, height: 3)
            }

            Spacer()

            Button(action: { router.push(.setting) }) {
                ZStack {
                    Circle().fill(Theme.settingsCircle).frame(width: 48, height: 48)
                    Image(systemName: "gearshape.fill")
                        .font(.system(size: 24))
                        .foregroundColor(Theme.settingsIcon)
                }
            }
            .buttonStyle(.plain)
        }
        .padding(.leading, 28)
        .padding(.trailing, 20)
        .padding(.top, 28)
    }

    /// 未填写 SDKAppID / SecretKey 时的提示条。
    private var configBanner: some View {
        HStack(alignment: .top, spacing: 8) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 14))
                .foregroundColor(Color(hex: 0xFF9500))
            Text("尚未配置 SDKAppID / SecretKey，请在 VoiceAIKitDemo/Demo/Config.swift 中填写后重新运行。语音识别与语音播报均依赖该配置。")
                .font(.system(size: 12))
                .foregroundColor(Theme.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(hex: 0xFFF4E5))
        .cornerRadius(10)
        .padding(.horizontal, 20)
    }

    // MARK: - Demo Card

    private func demoCard(number: String, title: String, subtitle: String,
                          description: String, imageName: String, imageSize: CGFloat,
                          gradient: [Color], accent: Color,
                          action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(alignment: .center, spacing: 12) {
                VStack(alignment: .leading, spacing: 0) {
                    Text(number)
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(accent)

                    Spacer().frame(height: 3)

                    Text(title)
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(Theme.textPrimary)

                    Spacer().frame(height: 2)

                    Text(subtitle)
                        .font(.system(size: 12))
                        .foregroundColor(accent)

                    Spacer().frame(height: 3)

                    Text(description)
                        .font(.system(size: 9))
                        .foregroundColor(Theme.textSecondary)

                    Spacer().frame(height: 8)

                    HStack(spacing: 6) {
                        Text("立即体验")
                            .font(.system(size: 11, weight: .medium))
                            .foregroundColor(.white)
                        Text("›")
                            .font(.system(size: 18))
                            .foregroundColor(.white)
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 5)
                    .background(accent.opacity(0.85))
                    .clipShape(Capsule())
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                Image(imageName)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(width: imageSize, height: imageSize)
            }
            .padding(.leading, 24)
            .padding(.top, 10)
            .padding(.bottom, 10)
            .padding(.trailing, 18)
            .frame(maxWidth: .infinity)
            .background(
                LinearGradient(colors: gradient, startPoint: .topLeading, endPoint: .bottomTrailing)
            )
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .shadow(color: accent.opacity(0.25), radius: 12, x: 0, y: 6)
        }
        .buttonStyle(.plain)
        .padding(.horizontal, 20)
    }

    // MARK: - Footer

    private var footer: some View {
        HStack(spacing: 12) {
            Rectangle().fill(Theme.footerLine).frame(width: 35, height: 1)
            Text("技术驱动 · 声音未来")
                .font(.system(size: 11))
                .foregroundColor(Theme.footerText)
            Rectangle().fill(Theme.footerLine).frame(width: 35, height: 1)
        }
    }
}
