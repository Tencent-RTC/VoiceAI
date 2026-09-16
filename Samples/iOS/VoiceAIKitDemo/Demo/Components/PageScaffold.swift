// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAI Demo - 通用页面脚手架 (对齐 Android ui/components/PageScaffold.kt)

import SwiftUI

/// 带标题栏的通用页面容器：左侧返回按钮 + 居中标题 + 右侧可选操作区。
struct PageScaffold<TrailingContent: View, Body: View>: View {
    let title: String
    let onBack: () -> Void
    @ViewBuilder let trailing: () -> TrailingContent
    @ViewBuilder let content: () -> Body

    init(title: String,
         onBack: @escaping () -> Void,
         @ViewBuilder trailing: @escaping () -> TrailingContent = { EmptyView() },
         @ViewBuilder content: @escaping () -> Body) {
        self.title = title
        self.onBack = onBack
        self.trailing = trailing
        self.content = content
    }

    var body: some View {
        VStack(spacing: 0) {
            ZStack {
                Text(title)
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundColor(Theme.textPrimary)
                HStack {
                    Button(action: onBack) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 20, weight: .semibold))
                            .foregroundColor(Theme.textPrimary)
                            .frame(width: 40, height: 40)
                    }
                    Spacer()
                    trailing()
                }
            }
            .padding(.horizontal, 8)
            .frame(height: 52)
            .background(Theme.background)

            content()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .background(Theme.background)
    }
}
