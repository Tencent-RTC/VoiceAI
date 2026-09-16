// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAIKit - 在线实时语音合成引擎封装
//
// 对 TXRealtimeTTS 的轻量封装（仅在线模式），供 AIChat / RealtimeChat 朗读 LLM 回复。
//
// 会话模型（对齐 Android 的常驻引擎）：
//   - start()：建立会话，幂等。已建立则直接复用，不重复 start（SDK 重复 start 会返回
//     TXRealtimeTTSErrorInvalidState）
//   - append(text)：逐句喂入，isEnd = false，会话保持
//   - clear()：丢弃待合成/待播放文本用于打断，会话保持，可继续 append
//   - stop()：结束会话（离开页面 / 释放时调用）
//   - end()：仅在需要「一段播完」回调的场景使用（发 isEnd=true，播完触发 onCompleted）

import Foundation
import TXLiteAVSDK_VoiceAI_iOS

final class TtsEngine: NSObject {

    /// 合成播放全部完成（正常或失败）。
    var onCompleted: ((Int32) -> Void)?

    private let tts = TXRealtimeTTS()
    private var textIdSeq = 0
    /// 会话是否已建立（start 成功 = true，直到 stop 或 end 播完回调 onCompleted）。
    private(set) var isStarted = false
    private(set) var isSpeaking = false

    override init() {
        super.init()
        tts.setListener(self)
    }

    deinit {
        tts.stop()
        tts.setListener(nil)
    }

    /// 建立合成会话（幂等，使用 Kit 当前配置的音色）。
    /// 已建立会话时直接返回 true，不会重复 start / stop。
    @discardableResult
    func start() -> Bool {
        if isStarted { return true }
        guard let params = VoiceAIKit.shared.getParams() else { return false }

        let ttsParams = TXRealtimeTTSParams()
        ttsParams.mode = .online
        ttsParams.audioOutputMode = .playbackOnly
        ttsParams.voiceName = params.ttsVoiceId

        let online = TXRealtimeTTSOnlineCredential()
        online.appId = String(params.appId)
        online.userId = params.userId
        online.userSig = params.userSig
        ttsParams.online = online

        let code = tts.start(ttsParams)
        if code != 0 {
            NSLog("[TtsEngine] start failed code=%d", code)
            return false
        }
        isStarted = true
        isSpeaking = true
        return true
    }

    /// 追加一段待合成文本（不结束会话）。
    func append(_ text: String) {
        guard isStarted, !text.isEmpty else { return }
        let textId = "tts_\(textIdSeq)"
        textIdSeq += 1
        tts.appendText(textId, text: text, isEnd: false)
    }

    /// 打断：清空待合成/待播放文本，会话保持，可继续 append。
    func clear() {
        guard isStarted else { return }
        tts.clear()
    }

    /// 结束会话并释放播放资源（离开页面 / 释放时调用）。
    func stop() {
        guard isStarted else { return }
        tts.clear()
        tts.stop()
        isStarted = false
        isSpeaking = false
    }

    /// 标记本段文本输入结束，播完后触发 onCompleted 并结束会话。
    /// 仅用于依赖「播完回调」推进状态机的场景（如实时对话回到聆听）；
    /// 常驻朗读场景（AI 对话）不需要调用，直接 append 即可。
    func end() {
        guard isStarted else { return }
        let textId = "tts_\(textIdSeq)"
        textIdSeq += 1
        tts.appendText(textId, text: "", isEnd: true)
    }

    /// 设置语速 [1.0, 3.0]。
    func setSpeed(_ speed: Float) {
        tts.setSpeed(speed)
    }
}

// MARK: - TXRealtimeTTSListener

extension TtsEngine: TXRealtimeTTSListener {

    func onStarted() {}

    func onPlaybackProgress(_ textId: String, textSlice: String) {}

    func onSynthesizedAudioFrame(_ audioFrame: TXSynthesizeAudioFrame) {}

    func onCompleted(_ code: Int32, msg: String?) {
        DispatchQueue.main.async { [weak self] in
            self?.isSpeaking = false
            // 收到结束回调意味着本次会话已结束，下次需重新 start
            self?.isStarted = false
            self?.onCompleted?(code)
        }
    }
}
