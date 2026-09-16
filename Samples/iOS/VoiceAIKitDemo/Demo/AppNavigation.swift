// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAI Demo - 应用导航 (对齐 Android AppNavigation.kt)

import SwiftUI

/// 页面路由。
enum AppRoute: Hashable {
    case voiceInput
    case chat
    case realtimeChat
    case setting
    case ttsVoice
    case voiceprint
}

/// 全局导航器。
final class Router: ObservableObject {
    @Published var path = NavigationPath()

    func push(_ route: AppRoute) { path.append(route) }
    func pop() { if !path.isEmpty { path.removeLast() } }
    func popToRoot() { path = NavigationPath() }
}

struct AppNavigation: View {
    @StateObject private var router = Router()

    var body: some View {
        NavigationStack(path: $router.path) {
            HomePage()
                .navigationDestination(for: AppRoute.self) { route in
                    destination(for: route)
                        .navigationBarBackButtonHidden(true)
                }
                .navigationBarHidden(true)
        }
        .environmentObject(router)
        .tint(Theme.brandBlue)
    }

    @ViewBuilder
    private func destination(for route: AppRoute) -> some View {
        switch route {
        case .voiceInput:    VoiceInputPage()
        case .chat:          ChatPage()
        case .realtimeChat:  RealtimeChatPage()
        case .setting:       SettingPage()
        case .ttsVoice:      TtsVoiceSettingPage()
        case .voiceprint:    VoiceprintRegisterPage()
        }
    }
}
