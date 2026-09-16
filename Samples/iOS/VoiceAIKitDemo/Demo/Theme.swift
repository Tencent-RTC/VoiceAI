// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAI Demo - 主题配色 (对齐 Android ui/theme/Theme.kt)

import SwiftUI

enum Theme {
    static let background = Color(hex: 0xF4F6FB)
    static let textPrimary = Color(hex: 0x1A1F36)
    static let textSecondary = Color(hex: 0x6B7488)

    static let brandBlue = Color(hex: 0x2B6CF6)
    static let brandPurple = Color(hex: 0x9B4DEB)

    // 首页卡片渐变（对齐 Android Theme.kt）
    static let purpleStart = Color(hex: 0xF3EAFF)
    static let purpleEnd = Color(hex: 0xFBF8FF)
    static let greenStart = Color(hex: 0xE3F7F4)
    static let greenEnd = Color(hex: 0xF6FCFB)

    static let accentPurple = Color(hex: 0xB875FF)
    static let accentGreen = Color(hex: 0x35D7C6)

    // 首页标题渐变
    static let titleGradient = [Color(hex: 0x2B6CF6), Color(hex: 0x6E8BF5), Color(hex: 0x9B4DEB)]
    // 首页标题下方渐变短线
    static let underlineGradient = [Color(hex: 0x48E4FF), Color(hex: 0x8B5CFF)]
    // 设置按钮圆底 + 图标
    static let settingsCircle = Color(hex: 0xE7ECF7)
    static let settingsIcon = Color(hex: 0x4B5468)
    // 页脚
    static let footerText = Color(hex: 0x9AA2B4)
    static let footerLine = Color(hex: 0xC7CEDD)

    // 圆球/波形配色
    static let orbLight = Color(hex: 0x6E8BF5)
    static let orbBlue = Color(hex: 0x2B6CF6)
    static let orbPurple = Color(hex: 0x9B4DEB)
}

extension Color {
    init(hex: UInt32, alpha: Double = 1.0) {
        let r = Double((hex >> 16) & 0xFF) / 255.0
        let g = Double((hex >> 8) & 0xFF) / 255.0
        let b = Double(hex & 0xFF) / 255.0
        self.init(.sRGB, red: r, green: g, blue: b, opacity: alpha)
    }
}
