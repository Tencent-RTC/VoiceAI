// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAI Demo - TTS 音色设置页 (对齐 Android ui/pages/TtsVoiceSettingPage.kt)

import SwiftUI

struct TtsVoiceSettingPage: View {
    @EnvironmentObject private var router: Router
    @StateObject private var vm = TtsVoiceSettingViewModel()

    var body: some View {
        PageScaffold(title: "选择音色", onBack: { router.pop() }) {
            ScrollView {
                VStack(spacing: 0) {
                    ForEach(vm.voices) { voice in
                        Button(action: { vm.select(voice) }) {
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(voice.displayName)
                                        .font(.system(size: 16))
                                        .foregroundColor(Theme.textPrimary)
                                    Text(voice.voiceId)
                                        .font(.system(size: 12))
                                        .foregroundColor(Theme.textSecondary)
                                }
                                Spacer()
                                if vm.selectedVoiceId == voice.voiceId {
                                    Image(systemName: "checkmark.circle.fill")
                                        .foregroundColor(Theme.brandBlue)
                                }
                            }
                            .padding(.horizontal, 16).frame(height: 60)
                        }
                        .buttonStyle(.plain)
                        if voice.id != vm.voices.last?.id {
                            Divider().padding(.leading, 16)
                        }
                    }
                }
                .background(Color.white)
                .cornerRadius(12)
                .padding(16)
            }
        }
    }
}
