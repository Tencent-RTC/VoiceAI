// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAI Demo - 配置（★ 本 Demo 唯一需要你修改的文件）
//
// 跑通步骤：
//   1. 打开腾讯云 TRTC 控制台创建应用，拿到 SDKAppID 与 SecretKey
//      https://console.cloud.tencent.com/trtc
//   2. 填入下面的 sdkAppId / secretKey —— 语音识别（ASR）与语音播报（TTS）都依赖它
//   3.（可选）想体验「AI 对话 / 实时对话」时，再填 openApiUrl / openApiKey / openApiModel
//   4. 重新编译运行即可
//
// 安全提示：SecretKey 硬编码在客户端仅供调试。正式版本请在服务端计算 UserSig、
//           LLM Key 走后端代理，切勿打进 App。

import Foundation

enum Config {

    // MARK: - 必填：腾讯云账号（ASR / TTS 鉴权）

    /// 腾讯云 SDKAppID，在 TRTC 控制台创建应用后获取（纯数字）。
    static let sdkAppId: Int = 0

    /// 腾讯云 SecretKey，仅用于本地生成测试 UserSig。
    static let secretKey: String = ""

    // MARK: - 选填：大模型对话（AI 对话 / 实时对话需要；只体验语音输入可不填）

    /// 大模型对话服务地址（OpenAI 兼容 /v1/chat/completions）。
    static let openApiUrl: String = "https://tokenhub.tencentmaas.com/v1/chat/completions"

    /// 大模型对话服务鉴权 Key（Authorization: Bearer）。
    static let openApiKey: String = ""

    /// 使用的模型名。
    static let openApiModel: String = "hy3-preview"

    // MARK: - 其他

    /// 本次使用的用户 ID；ASR 与 TTS 均使用该 ID。
    static let userId: String = "Test_userID"

    /// 生成当前用户的测试 UserSig。
    static func genUserSig() -> String {
        return GenerateTestUserSig.genTestUserSig(identifier: userId)
    }

    /// 必填项是否已填写（未填写时首页会显示提示条）。
    static var isConfigured: Bool {
        return sdkAppId > 0 && !secretKey.trimmingCharacters(in: .whitespaces).isEmpty
    }
}
