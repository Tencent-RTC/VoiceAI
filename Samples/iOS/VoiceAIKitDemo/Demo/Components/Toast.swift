// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAI Demo - 轻量 Toast (对齐 Android ui/components/Toast.kt)

import SwiftUI

/// 一个自动消失的浮层提示。绑定一个可空字符串，非空时展示，几秒后自动清空。
struct ToastModifier: ViewModifier {
    @Binding var message: String?
    var duration: TimeInterval = 2.0

    func body(content: Content) -> some View {
        ZStack {
            content
            if let msg = message, !msg.isEmpty {
                VStack {
                    Spacer()
                    Text(msg)
                        .font(.system(size: 14))
                        .foregroundColor(.white)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(Color.black.opacity(0.8))
                        .cornerRadius(10)
                        .padding(.bottom, 80)
                }
                .transition(.opacity)
                .onAppear {
                    DispatchQueue.main.asyncAfter(deadline: .now() + duration) {
                        withAnimation { message = nil }
                    }
                }
            }
        }
    }
}

extension View {
    func toast(_ message: Binding<String?>, duration: TimeInterval = 2.0) -> some View {
        modifier(ToastModifier(message: message, duration: duration))
    }
}
