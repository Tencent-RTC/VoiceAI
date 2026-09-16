// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAIKit - AI 对话控制器 (对齐 Android view/AIChatController.kt)
//
// 职责：串联「文本/语音输入 → LLM 流式回复 → 逐句 TTS 朗读」。
//   - sendText(text)：追加用户气泡，发起 LLM 流式请求，实时更新 assistant 气泡
//   - autoSpeak 打开时，回复按句喂给 TtsEngine 朗读
//   - stopGenerating()：取消 LLM 与 TTS
// ASR 由界面上的 VoiceInputBar 组件自行完成，识别文本通过 sendText 进入本控制器。

import Foundation

final class AIChatController: ObservableObject {

    @Published var messages: [ChatBubble] = []
    @Published var isGenerating = false
    /// 自动朗读开关（对齐 Android「自动朗读」）。
    @Published var autoSpeak = true
    @Published var toast: String?

    private let tts = TtsEngine()
    private var llm: LlmClient?
    private var history: [ChatMessage] = []
    private var splitter = SentenceSplitter()
    private var streamingIndex: Int?

    init() {
        history.append(ChatMessage(role: "system", content: VoiceAIKitDefaults.systemPrompt))
    }

    deinit {
        llm?.cancel()
        tts.stop()
    }

    // MARK: - 生命周期

    /// 进入对话页：预启动 TTS 会话（幂等），避免首句朗读有启动延迟。
    func onEnterPage() {
        if autoSpeak { tts.start() }
    }

    /// 离开对话页：停止 TTS 会话并取消在途请求。
    func onLeavePage() {
        llm?.cancel()
        llm = nil
        tts.stop()
        isGenerating = false
        streamingIndex = nil
    }

    /// 发送一条用户消息并请求回复。
    func sendText(_ raw: String) {
        let text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }

        let params = VoiceAIKit.shared.requireParams()
        guard params.llm.isConfigured else {
            toast = "未配置 LLM，请检查 Config"
            return
        }
        if isGenerating { stopGenerating() }

        messages.append(ChatBubble(role: .user, text: text))
        history.append(ChatMessage(role: "user", content: text))
        trimHistory()

        // 追加一个流式 assistant 气泡
        messages.append(ChatBubble(role: .assistant, text: "", isStreaming: true))
        streamingIndex = messages.count - 1
        isGenerating = true

        if autoSpeak {
            // 常驻会话：只清空上一轮残留，不 stop；start 幂等（首次才真正 start）
            tts.clear()
            tts.start()
        }
        splitter = SentenceSplitter()

        let client = LlmClient(apiUrl: params.llm.apiUrl, apiKey: params.llm.apiKey, model: params.llm.model)
        llm = client
        let ctx = history
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            guard let self = self else { return }
            client.streamChat(messages: ctx, callback: self)
        }
    }

    /// 停止生成：取消 LLM 请求并清空待播报文本（TTS 会话保持，不重复 start）。
    func stopGenerating() {
        llm?.cancel()
        llm = nil
        tts.clear()
        finalizeStreaming(interrupted: true)
    }

    /// 切换自动朗读：关闭时立即清空待播报文本，且后续不再向 TTS 追加文本。
    func toggleAutoSpeak() {
        autoSpeak.toggle()
        guard !autoSpeak else { return }
        // 立即停止正在播报/排队的内容；TTS 会话保持，重新开启后可继续 append
        tts.clear()
        // 丢弃当前未成句的分片，避免重新开启后补播已跳过的内容
        splitter = SentenceSplitter()
    }

    func clear() {
        stopGenerating()
        messages.removeAll()
        history = [ChatMessage(role: "system", content: VoiceAIKitDefaults.systemPrompt)]
    }

    // MARK: - Private

    private func trimHistory() {
        // 保留 system + 最近 maxHistory 条
        let maxCount = VoiceAIKitDefaults.maxHistory + 1
        if history.count > maxCount {
            let system = history.first!
            history = [system] + history.suffix(maxCount - 1)
        }
    }

    private func appendToStreaming(_ delta: String) {
        guard let idx = streamingIndex, messages.indices.contains(idx) else { return }
        messages[idx].text += delta
    }

    private func finalizeStreaming(interrupted: Bool) {
        guard let idx = streamingIndex, messages.indices.contains(idx) else {
            isGenerating = false
            return
        }
        messages[idx].isStreaming = false
        if messages[idx].text.isEmpty {
            messages[idx].text = interrupted ? VoiceAIKitDefaults.pausedHint : ""
        }
        // 完整回复入历史
        let full = messages[idx].text
        if !full.isEmpty, full != VoiceAIKitDefaults.pausedHint {
            history.append(ChatMessage(role: "assistant", content: full))
        }
        streamingIndex = nil
        isGenerating = false
    }
}

// MARK: - LlmStreamCallback (后台线程 → 切主线程)

extension AIChatController: LlmStreamCallback {

    func onDelta(_ delta: String) {
        DispatchQueue.main.async { [weak self] in
            guard let self = self, self.isGenerating else { return }
            self.appendToStreaming(delta)
            if self.autoSpeak {
                for sentence in self.splitter.feed(delta) {
                    self.tts.append(sentence)
                }
            }
        }
    }

    func onCompleted(_ fullText: String) {
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            if self.autoSpeak {
                let rest = self.splitter.flush()
                if !rest.isEmpty { self.tts.append(rest) }
            }
            self.finalizeStreaming(interrupted: false)
        }
    }

    func onError(_ message: String) {
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.toast = "对话失败: \(message)"
            self.tts.clear()
            self.finalizeStreaming(interrupted: true)
        }
    }
}
