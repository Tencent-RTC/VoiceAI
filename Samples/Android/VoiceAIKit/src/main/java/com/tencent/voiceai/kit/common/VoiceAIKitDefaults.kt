// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAIKit - 内置默认值（不对外开放配置）

package com.tencent.voiceai.kit.common

import com.tencent.voiceai.kit.VoiceAIKit

/**
 * VoiceAIKit 的内置默认值。
 *
 * 与 [VoiceAIKit.VoiceAIKitParams] 的分工：
 * - [VoiceAIKit.VoiceAIKitParams]：由接入方在启动时注入，是**外部输入**；
 * - 本类：Kit 内置的固定行为与默认取值，**不随参数开放配置**。
 */
internal object VoiceAIKitDefaults {

    /** 默认模型名（[VoiceAIKit.LLMParams] 的 model 未指定时使用）。 */
    const val DEFAULT_LLM_MODEL = "default"

    /** 对话系统提示词。 */
    const val SYSTEM_PROMPT =
        "你是一个友好的中文语音助手，回答要简洁、口语化，适合被朗读出来。"
}
