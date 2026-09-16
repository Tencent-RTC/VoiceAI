// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAIKit - 实时语音识别引擎封装
//
// 对 TXRealtimeASR 的轻量封装，统一供 VoiceInputBar / RealtimeChatController 使用。
// 采用内置麦克风采集 (enableCustomCapture=false)，通过闭包回调向上层透出识别结果与音量。
//
// ASR 底层监听回调已在主线程触发。

import Foundation
import TXLiteAVSDK_VoiceAI_iOS

final class AsrEngine: NSObject {

    // MARK: - 回调闭包 (主线程)

    /// 中间（非稳态）结果，随说随出。
    var onPartial: ((String) -> Void)?
    /// 一句稳态结果（isCompleted=true）。
    var onFinal: ((String) -> Void)?
    /// 音量 0-100。
    var onVolume: ((Int32) -> Void)?
    var onStarted: (() -> Void)?
    var onStopped: (() -> Void)?
    var onError: ((Int32, String) -> Void)?

    private let engine = TXRealtimeASR()
    private(set) var isRunning = false

    override init() {
        super.init()
        engine.addListener(self)
    }

    deinit {
        engine.removeListener(self)
    }

    /// 开始识别。
    /// - Parameters:
    ///   - sourceLanguage: 识别语言，""=自动/中文
    ///   - denoiseMode: 降噪策略
    ///   - voiceprintPath: 声纹降噪所需 WAV 路径（仅 voiceprint 模式使用）
    func start(sourceLanguage: String = "",
               denoiseMode: DenoiseMode = .none,
               voiceprintPath: String = "") {
        guard !isRunning else { return }
        isRunning = true

        let voiceId = UUID().uuidString
        let params = TXRealtimeASRParams()
        params.sdkAppId = String(Config.sdkAppId)
        params.userSig = GenerateTestUserSig.genTestUserSig(identifier: voiceId)
        params.voiceId = voiceId
        params.sourceLanguage = sourceLanguage
        params.enableCustomCapture = false

        // clientDenoiseStrategy: 0=关闭 1=录音笔 2=AI对话 3=会议/远场(语音输入)
        // 参考 https://cloud.tencent.com/document/product/647/137680 (callExperimentalAPI/setExtraParams)
        let strategy: Int32 = (denoiseMode == .farField) ? 3 : 0
        let extra = "{\"api\":\"setExtraParams\",\"params\":{\"extraRequestParams\":\"engine_model_type=bigmodel\",\"clientDenoiseStrategy\":\(strategy)}}"
        engine.callExperimentalAPI(extra)

        // 声纹降噪
        if denoiseMode == .voiceprint, !voiceprintPath.isEmpty {
            let escaped = voiceprintPath.replacingOccurrences(of: "\\", with: "\\\\")
            let vp = "{\"api\":\"enableVoiceprintDenoise\",\"params\":{\"enable\":true,\"voiceprint_file_path\":\"\(escaped)\"}}"
            engine.callExperimentalAPI(vp)
        } else {
            engine.callExperimentalAPI("{\"api\":\"enableVoiceprintDenoise\",\"params\":{\"enable\":false}}")
        }

        engine.startRealtimeASR(params)
    }

    func stop() {
        guard isRunning else { return }
        engine.stopRealtimeASR()
    }
}

// MARK: - TXRealtimeASRListener

extension AsrEngine: TXRealtimeASRListener {

    func onRealtimeASRStarted(_ voiceId: String) {
        onStarted?()
    }

    func onReceiveRealtimeASRMessage(_ message: TXRealtimeASRMessage) {
        if message.isCompleted {
            onFinal?(message.sourceText)
        } else {
            onPartial?(message.sourceText)
        }
    }

    func onRealtimeASRStopped() {
        isRunning = false
        onStopped?()
    }

    func onRealtimeASRError(_ errorCode: Int32, errorMsg: String) {
        isRunning = false
        onError?(errorCode, errorMsg)
    }

    func onRealtimeASRVolume(_ volume: Int32) {
        onVolume?(volume)
    }
}
