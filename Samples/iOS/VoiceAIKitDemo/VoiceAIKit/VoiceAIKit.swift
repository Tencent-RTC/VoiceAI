// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAIKit - 模块全局入口 (iOS, 对齐 Android VoiceAIKit.kt)

import Foundation

/// VoiceAIKit 的全局入口，承载模块级公共配置。
///
/// 接入方在应用启动时注入一次即可：
/// ```swift
/// VoiceAIKit.shared.initialize(
///     VoiceAIKit.Params(
///         appId: appId, userId: userId, userSig: userSig,
///         llm: VoiceAIKit.LLMParams(apiUrl: llmApiUrl, apiKey: llmApiKey)
///     )
/// )
/// ```
final class VoiceAIKit {

    static let shared = VoiceAIKit()
    private init() {}

    /// 默认 TTS 音色 voiceId（在线音色「温柔姐姐」）。
    /// Kit 只走在线合成，取值须为云端在线音色 ID。
    /// 完整清单见 https://cloud.tencent.com/document/product/647/131300
    static let defaultTtsVoiceId = "v-female-R2s4N9qJ"

    private let lock = NSLock()
    private var params: Params?

    /// 注入初始化参数（唯一入口），覆盖上一次注入的值。
    func initialize(_ value: Params) {
        lock.lock(); defer { lock.unlock() }
        params = value
    }

    /// 当前注入的初始化参数，未注入时返回 nil。
    func getParams() -> Params? {
        lock.lock(); defer { lock.unlock() }
        return params
    }

    /// 获取初始化参数，未注入时触发 fatalError（对齐 Android requireParams 抛异常语义）。
    func requireParams() -> Params {
        guard let p = getParams() else {
            fatalError("VoiceAIKit.Params 未注入，请在使用 AIChat / RealtimeChat 前调用 VoiceAIKit.shared.initialize(...)")
        }
        return p
    }

    /// 运行期更新 TTS 音色。
    /// 只更新 Kit 持有的参数，不会重建已创建的 TTS 引擎，新音色在下次进入对话页后生效。
    func setTtsVoiceId(_ voiceId: String) {
        var p = requireParams()
        p.ttsVoiceId = voiceId
        initialize(p)
    }

    // MARK: - 参数模型

    /// 大模型（LLM）对话服务的连接参数。
    /// 仅在使用 AI 对话 / 实时对话 时需要，单独使用 VoiceInputBar 时无需配置。
    struct LLMParams {
        var apiUrl: String = ""
        var apiKey: String = ""
        var model: String = VoiceAIKitDefaults.defaultLlmModel

        /// 配置是否完整（apiUrl 与 apiKey 均非空）。
        var isConfigured: Bool {
            return !apiUrl.trimmingCharacters(in: .whitespaces).isEmpty
                && !apiKey.trimmingCharacters(in: .whitespaces).isEmpty
        }
    }

    /// VoiceAIKit 的初始化参数。
    ///
    /// **必填**：appId / userId / userSig —— 语音能力（ASR）鉴权，即使只单独使用 VoiceInputBar 也必须提供。
    /// **可选**：llm / ttsVoiceId —— llm 仅在使用 AI 对话 / 实时对话 时需要。
    struct Params {
        let appId: Int
        let userId: String
        let userSig: String
        var llm: LLMParams = LLMParams()
        var ttsVoiceId: String = VoiceAIKit.defaultTtsVoiceId
    }
}
