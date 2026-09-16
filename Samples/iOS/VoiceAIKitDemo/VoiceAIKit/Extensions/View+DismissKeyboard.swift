// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAIKit - 收起键盘工具
//
// SwiftUI 不像 UIKit 的 UIScrollView 那样默认支持「点击空白收起键盘」，
// 需显式处理（对齐 Android 点击空白隐藏软键盘的行为）。

import SwiftUI

extension View {
    /// 点击空白区域收起键盘。
    /// 子视图（输入框、按钮等）会优先消费点击，不影响其正常交互。
    func hideKeyboardOnTap() -> some View {
        contentShape(Rectangle())
            .onTapGesture { dismissKeyboard() }
    }
}

/// 让当前第一响应者放弃响应，从而收起键盘。
func dismissKeyboard() {
    UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder),
                                    to: nil, from: nil, for: nil)
}
