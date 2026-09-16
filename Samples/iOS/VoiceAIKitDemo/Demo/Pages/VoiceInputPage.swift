// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAI Demo - 语音输入页 (对齐 Android ui/pages/VoiceInputPage.kt)
//
// 顶部标题栏 + 消息列表（回显识别结果）+ 底部 VoiceInputBar。

import SwiftUI

struct VoiceInputPage: View {
    @EnvironmentObject private var router: Router
    @StateObject private var vm = VoiceInputViewModel()

    var body: some View {
        PageScaffold(title: "语音输入", onBack: { router.pop() }, trailing: {
            Button(action: vm.clear) {
                Image(systemName: "trash")
                    .font(.system(size: 18))
                    .foregroundColor(Theme.textSecondary)
                    .frame(width: 40, height: 40)
            }
        }) {
            VStack(spacing: 0) {
                if vm.items.isEmpty {
                    EmptyChatHint(icon: "mic.circle", text: "切换到语音，按住底部按钮开始说话")
                } else {
                    ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(spacing: 12) {
                            ForEach(vm.items) { ChatBubbleView(bubble: $0) }
                            Color.clear.frame(height: 1).id("bottom")
                        }
                        .padding(16)
                    }
                    .scrollDismissesKeyboard(.immediately)
                    .onChange(of: vm.items.count) { _ in
                        withAnimation { proxy.scrollTo("bottom", anchor: .bottom) }
                    }
                    }
                    }
                    Divider()
                    VoiceInputBar(text: $vm.inputText, onSend: vm.onSend)
                    }
                    .hideKeyboardOnTap()
                    }
    }
}
