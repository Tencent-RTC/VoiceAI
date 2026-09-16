// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAI Demo - 中央动态圆球 (对齐 Android view/component/VoiceOrbView.kt)
//
// 呼吸缩放 (0.92~1.04, 1600ms 往返) + 两层外扩光晕 + 蓝紫径向渐变主体 + 左上高光。

import SwiftUI

struct VoiceOrbView: View {
    /// 是否处于活跃状态（聆听/说话），活跃时呼吸更明显。
    var active: Bool = true

    @State private var breathing = false

    var body: some View {
        ZStack {
            // 外层光晕
            Circle()
                .fill(
                    RadialGradient(
                        colors: [Theme.orbLight.opacity(0.35), Theme.orbPurple.opacity(0.0)],
                        center: .center, startRadius: 10, endRadius: 150
                    )
                )
                .frame(width: 260, height: 260)
                .scaleEffect(breathing ? 1.08 : 0.94)

            // 中层光晕
            Circle()
                .fill(
                    RadialGradient(
                        colors: [Theme.orbBlue.opacity(0.30), Theme.orbPurple.opacity(0.0)],
                        center: .center, startRadius: 10, endRadius: 110
                    )
                )
                .frame(width: 200, height: 200)
                .scaleEffect(breathing ? 1.04 : 0.96)

            // 主体
            Circle()
                .fill(
                    LinearGradient(
                        colors: [Theme.orbLight, Theme.orbBlue, Theme.orbPurple],
                        startPoint: .topLeading, endPoint: .bottomTrailing
                    )
                )
                .frame(width: 150, height: 150)
                .scaleEffect(breathing ? 1.04 : 0.92)
                .overlay(
                    // 左上高光
                    Circle()
                        .fill(Color.white.opacity(0.35))
                        .frame(width: 44, height: 44)
                        .offset(x: -30, y: -30)
                        .blur(radius: 8)
                )
                .shadow(color: Theme.orbPurple.opacity(0.35), radius: 24, x: 0, y: 10)
        }
        .onAppear { startAnimation() }
        .onChange(of: active) { _ in startAnimation() }
    }

    private func startAnimation() {
        withAnimation(.easeInOut(duration: 1.6).repeatForever(autoreverses: true)) {
            breathing = active
        }
    }
}
