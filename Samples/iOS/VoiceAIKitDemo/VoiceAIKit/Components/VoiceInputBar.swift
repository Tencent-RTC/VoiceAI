// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAIKit - 语音输入条 (对齐 Android view/component/VoiceInputBar.kt)
//
// 支持「键盘输入」与「按住说话」两种模式：
//   - 键盘模式：文本框 + 发送按钮，右侧可切到语音模式
//   - 语音模式：按住底部按钮实时识别，上滑取消，松手后回调识别文本
// 组件自持 AsrEngine（内置麦克风采集），不依赖外部录音逻辑。

import SwiftUI
import AVFoundation

// MARK: - Model

final class VoiceInputBarModel: ObservableObject {

    /// 是否语音模式（true=语音，false=键盘），初始沿用 Kit 偏好。
    @Published var voiceMode: Bool = VoiceAIPrefs.shared.chatInputMode {
        didSet { VoiceAIPrefs.shared.chatInputMode = voiceMode }
    }
    @Published var recording = false
    @Published var willCancel = false          // 上滑取消预备态
    @Published var partialText = ""            // 实时识别中间结果

    private let asr = AsrEngine()
    private var finalBuffer = ""

    /// 松手且未取消时回调最终识别文本。
    var onRecognized: ((String) -> Void)?

    init() {
        asr.onPartial = { [weak self] text in
            self?.partialText = text
        }
        asr.onFinal = { [weak self] text in
            guard let self = self else { return }
            self.finalBuffer += text
            self.partialText = ""
        }
        asr.onError = { [weak self] _, msg in
            self?.recording = false
            self?.partialText = ""
            NSLog("[VoiceInputBar] ASR error: %@", msg)
        }
        asr.onStopped = { [weak self] in
            guard let self = self else { return }
            self.recording = false
            let result = (self.finalBuffer + self.partialText).trimmingCharacters(in: .whitespacesAndNewlines)
            self.partialText = ""
            if !self.willCancel, !result.isEmpty {
                self.onRecognized?(result)
            }
            self.willCancel = false
        }
    }

    func beginRecord() {
        requestMic { [weak self] granted in
            guard let self = self else { return }
            guard granted else { return }
            self.finalBuffer = ""
            self.partialText = ""
            self.willCancel = false
            self.recording = true
            self.asr.start(sourceLanguage: "")
        }
    }

    func endRecord(cancel: Bool) {
        guard recording else { return }
        willCancel = cancel
        asr.stop()
    }

    private func requestMic(_ completion: @escaping (Bool) -> Void) {
        let session = AVAudioSession.sharedInstance()
        switch session.recordPermission {
        case .granted:
            completion(true)
        case .denied:
            completion(false)
        default:
            session.requestRecordPermission { granted in
                DispatchQueue.main.async { completion(granted) }
            }
        }
    }
}

// MARK: - View

struct VoiceInputBar: View {
    @Binding var text: String
    @StateObject private var model = VoiceInputBarModel()
    /// 发送文本（键盘发送 或 语音识别完成）。
    var onSend: (String) -> Void

    var body: some View {
        VStack(spacing: 0) {
            // 语音模式下松手前的浮动提示
            if model.recording {
                recordingHint
            }
            HStack(spacing: 10) {
                Button(action: { model.voiceMode.toggle() }) {
                    Image(systemName: model.voiceMode ? "keyboard" : "mic")
                        .font(.system(size: 22))
                        .foregroundColor(Theme.textSecondary)
                        .frame(width: 40, height: 40)
                }

                if model.voiceMode {
                    voiceButton
                } else {
                    keyboardField
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
        }
        .background(Color.white)
        .onAppear {
            model.onRecognized = { recognized in onSend(recognized) }
        }
    }

    // 键盘模式：输入框 + 发送
    private var keyboardField: some View {
        HStack(spacing: 8) {
            TextField("发消息…", text: $text)
                .textFieldStyle(.plain)
                .padding(.horizontal, 12)
                .frame(height: 40)
                .background(Theme.background)
                .cornerRadius(20)
            Button(action: sendTyped) {
                Image(systemName: "arrow.up.circle.fill")
                    .font(.system(size: 32))
                    .foregroundColor(text.trimmingCharacters(in: .whitespaces).isEmpty
                                     ? Theme.textSecondary : Theme.brandBlue)
            }
            .disabled(text.trimmingCharacters(in: .whitespaces).isEmpty)
        }
    }

    // 语音模式：按住说话
    private var voiceButton: some View {
        Text(model.recording ? (model.willCancel ? "松开 取消" : "松开 发送") : "按住 说话")
            .font(.system(size: 16, weight: .medium))
            .foregroundColor(model.willCancel ? .red : Theme.textPrimary)
            .frame(maxWidth: .infinity)
            .frame(height: 40)
            .background(model.recording ? Theme.background : Color.white)
            .overlay(RoundedRectangle(cornerRadius: 20).stroke(Theme.textSecondary.opacity(0.3)))
            .cornerRadius(20)
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        if !model.recording {
                            model.beginRecord()
                        }
                        // 上滑超过 60pt 视为取消
                        model.willCancel = value.translation.height < -60
                    }
                    .onEnded { _ in
                        model.endRecord(cancel: model.willCancel)
                    }
            )
    }

    private var recordingHint: some View {
        VStack(spacing: 6) {
            Image(systemName: model.willCancel ? "xmark.circle.fill" : "waveform")
                .font(.system(size: 28))
                .foregroundColor(model.willCancel ? .red : Theme.brandBlue)
            Text(model.partialText.isEmpty ? (model.willCancel ? "松开手指，取消发送" : "正在聆听…")
                                          : model.partialText)
                .font(.system(size: 14))
                .foregroundColor(Theme.textPrimary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 20)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 16)
        .background(Color.white)
    }

    private func sendTyped() {
        let t = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !t.isEmpty else { return }
        onSend(t)
        text = ""
    }
}
