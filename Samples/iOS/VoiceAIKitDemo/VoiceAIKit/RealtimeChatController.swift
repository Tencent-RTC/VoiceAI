// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAIKit - 实时对话控制器 (对齐 Android view/RealtimeChatController.kt)
//
// 全双工连续语音对话：麦克风常开识别 → LLM 流式回复 → TTS 朗读，循环进行。
//   - 状态机：listening(聆听) → thinking(思考) → speaking(说话) → listening
//   - 打断：说话中检测到用户开口(partial)则打断 TTS 与生成，回到聆听
//   - 降噪：读取 VoiceAIPrefs.realtimeDenoiseMode（关闭 / 远场 / 声纹）

import Foundation
import AVFoundation

final class RealtimeChatController: NSObject, ObservableObject {

    enum State { case idle, listening, thinking, speaking }

    @Published var state: State = .idle
    @Published var messages: [ChatBubble] = []
    @Published var micEnabled = true
    @Published var ttsEnabled = true
    @Published var toast: String?

    let waveLevel = WaveLevel()

    private let asr = AsrEngine()
    private let tts = TtsEngine()
    private var llm: LlmClient?
    private var history: [ChatMessage] = []
    private var splitter = SentenceSplitter()
    private var streamingIndex: Int?
    private var started = false

    override init() {
        super.init()
        history.append(ChatMessage(role: "system", content: VoiceAIKitDefaults.systemPrompt))
        wireEngines()
    }

    deinit {
        asr.stop()
        tts.stop()
        llm?.cancel()
    }

    // MARK: - 生命周期

    func start() {
        requestMic { [weak self] granted in
            guard let self = self else { return }
            guard granted else { self.toast = "请在系统设置中开启麦克风权限"; return }
            self.started = true
            self.startListening()
        }
    }

    func stop() {
        started = false
        asr.stop()
        tts.stop()
        llm?.cancel()
        waveLevel.reset()
        state = .idle
    }

    /// 切换麦克风（暂停/恢复聆听）。
    func toggleMic() {
        micEnabled.toggle()
        if micEnabled {
            if state == .idle || state == .listening { startListening() }
        } else {
            asr.stop()
            waveLevel.reset()
            if state == .listening { state = .idle }
        }
    }

    /// 切换 TTS 朗读。
    func toggleTts() {
        ttsEnabled.toggle()
        if !ttsEnabled { tts.clear() }
    }

    // MARK: - 内部流转

    private func wireEngines() {
        asr.onVolume = { [weak self] v in self?.waveLevel.setVolume(v) }
        asr.onPartial = { [weak self] text in self?.handlePartial(text) }
        asr.onFinal = { [weak self] text in self?.handleFinal(text) }
        asr.onError = { [weak self] code, msg in
            self?.toast = "识别错误 \(code): \(msg)"
        }
        asr.onStopped = { [weak self] in
            guard let self = self, self.started, self.micEnabled else { return }
            // 连续对话：非主动停止时自动重新聆听
            if self.state == .listening { self.startListening() }
        }
        tts.onCompleted = { [weak self] _ in
            guard let self = self, self.started else { return }
            if self.state == .speaking { self.startListening() }
        }
    }

    private func startListening() {
        guard started, micEnabled, !asr.isRunning else { return }
        state = .listening
        let mode = VoiceAIPrefs.shared.realtimeDenoiseMode
        asr.start(sourceLanguage: "",
                  denoiseMode: mode,
                  voiceprintPath: VoiceprintStore.currentPath())
    }

    private func handlePartial(_ text: String) {
        // 说话中检测到用户开口 → 打断（barge-in）
        if state == .speaking, !text.trimmingCharacters(in: .whitespaces).isEmpty {
            interruptResponse()
        }
    }

    private func handleFinal(_ text: String) {
        let utterance = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !utterance.isEmpty else { return }
        guard state == .listening || state == .idle else { return }
        submit(utterance)
    }

    private func submit(_ utterance: String) {
        let params = VoiceAIKit.shared.requireParams()
        guard params.llm.isConfigured else { toast = "未配置 LLM"; return }

        messages.append(ChatBubble(role: .user, text: utterance))
        history.append(ChatMessage(role: "user", content: utterance))
        trimHistory()

        messages.append(ChatBubble(role: .assistant, text: "", isStreaming: true))
        streamingIndex = messages.count - 1
        state = .thinking

        splitter = SentenceSplitter()
        if ttsEnabled {
            tts.clear()
            tts.start()
        }

        let client = LlmClient(apiUrl: params.llm.apiUrl, apiKey: params.llm.apiKey, model: params.llm.model)
        llm = client
        let ctx = history
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            guard let self = self else { return }
            client.streamChat(messages: ctx, callback: self)
        }
    }

    /// 打断当前回复（对齐 Android RealtimeChatController.interruptResponse）：
    /// 取消 LLM 请求、清空 TTS 待播报文本并结束流式气泡，回到聆听态。
    func interruptResponse() {
        tts.clear()
        llm?.cancel()
        finalizeStreaming(interrupted: true)
        // ASR 仍在运行，回到聆听态以便接收本次开口的完整结果
        state = .listening
    }

    private func trimHistory() {
        let maxCount = VoiceAIKitDefaults.maxHistory + 1
        if history.count > maxCount {
            let system = history.first!
            history = [system] + history.suffix(maxCount - 1)
        }
    }

    private func finalizeStreaming(interrupted: Bool) {
        if let idx = streamingIndex, messages.indices.contains(idx) {
            messages[idx].isStreaming = false
            let full = messages[idx].text
            if full.isEmpty {
                messages[idx].text = interrupted ? VoiceAIKitDefaults.pausedHint : ""
            } else {
                history.append(ChatMessage(role: "assistant", content: full))
            }
        }
        streamingIndex = nil
    }

    private func requestMic(_ completion: @escaping (Bool) -> Void) {
        let session = AVAudioSession.sharedInstance()
        switch session.recordPermission {
        case .granted: completion(true)
        case .denied: completion(false)
        default:
            session.requestRecordPermission { granted in
                DispatchQueue.main.async { completion(granted) }
            }
        }
    }
}

// MARK: - LlmStreamCallback

extension RealtimeChatController: LlmStreamCallback {

    func onDelta(_ delta: String) {
        DispatchQueue.main.async { [weak self] in
            guard let self = self, self.state == .thinking || self.state == .speaking else { return }
            if let idx = self.streamingIndex, self.messages.indices.contains(idx) {
                self.messages[idx].text += delta
            }
            if self.ttsEnabled {
                if self.state == .thinking { self.state = .speaking }
                for sentence in self.splitter.feed(delta) { self.tts.append(sentence) }
            }
        }
    }

    func onCompleted(_ fullText: String) {
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            if self.ttsEnabled {
                let rest = self.splitter.flush()
                if !rest.isEmpty { self.tts.append(rest) }
                self.tts.end()
                self.finalizeStreaming(interrupted: false)
                // 等待 tts.onCompleted 再回到聆听；若无内容立即聆听
                if self.state != .speaking { self.startListening() }
            } else {
                self.finalizeStreaming(interrupted: false)
                self.startListening()
            }
        }
    }

    func onError(_ message: String) {
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.toast = "对话失败: \(message)"
            self.tts.clear()
            self.finalizeStreaming(interrupted: true)
            self.startListening()
        }
    }
}
