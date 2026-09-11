// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAIKit - 模块全局入口

package com.tencent.voiceai.kit

import com.tencent.voiceai.kit.common.VoiceAIKitDefaults

/**
 * VoiceAIKit 的全局入口，承载模块级的公共配置。
 *
 * 接入方在应用启动时注入一次即可：
 * ```kotlin
 * VoiceAIKit.init(
 *     VoiceAIKit.VoiceAIKitParams(
 *         appId = appId,
 *         userId = userId,
 *         userSig = userSig,
 *         llm = VoiceAIKit.LLMParams(apiUrl = llmApiUrl, apiKey = llmApiKey),
 *     )
 * )
 * ```
 *
 * 必填项/可选项见 [VoiceAIKitParams]；对外方法均为 `@JvmStatic` 静态方法，Java 可直接调用。
 */
object VoiceAIKit {

    @Volatile
    private var params: VoiceAIKitParams? = null

    /** 注入初始化参数（唯一入口），覆盖上一次注入的值。 */
    @JvmStatic
    fun init(value: VoiceAIKitParams) {
        params = value
    }

    /** 当前注入的初始化参数，未注入时返回 null。 */
    @JvmStatic
    fun getParams(): VoiceAIKitParams? = params

    /**
     * 运行期更新 TTS 音色。
     *
     * 只更新 Kit 持有的参数，不会重建已创建的 TTS 引擎，新音色在下次进入对话页后生效。
     *
     * @throws IllegalStateException 尚未调用 [init]
     */
    @JvmStatic
    fun setTtsVoiceId(voiceId: String) {
        init(requireParams().copy(ttsVoiceId = voiceId))
    }

    /**
     * 获取初始化参数，未注入时抛出明确异常。
     *
     * @throws IllegalStateException 参数未注入
     */
    @JvmStatic
    fun requireParams(): VoiceAIKitParams =
        params ?: throw IllegalStateException(
            "VoiceAIKitParams 未注入，请在使用 AIChat / RealtimeChat 前调用 VoiceAIKit.init(...)"
        )

    /**
     * 大模型（LLM）对话服务的连接参数。
     *
     * 仅在使用 AI 对话 / 实时对话 时需要，单独使用 VoiceInputBar 时无需配置。
     * 密钥建议从服务端下发或 BuildConfig/env 读取，避免随 aar 分发。
     *
     * @param apiUrl OpenAI 兼容协议的对话服务地址（/v1/chat/completions）；留空表示未配置。
     * @param apiKey 对话服务鉴权 Key（Authorization: Bearer）；留空表示未配置。
     * @param model  使用的模型名。
     */
    data class LLMParams @JvmOverloads constructor(
        val apiUrl: String = "",
        val apiKey: String = "",
        val model: String = VoiceAIKitDefaults.DEFAULT_LLM_MODEL,
    ) {

        /** 配置是否完整（[apiUrl] 与 [apiKey] 均非空）。 */
        val isConfigured: Boolean
            get() = apiUrl.isNotBlank() && apiKey.isNotBlank()
    }

    /**
     * VoiceAIKit 的初始化参数。
     *
     * **必填**：[appId] / [userId] / [userSig] —— 语音能力（ASR）鉴权，
     * 即使只单独使用 VoiceInputBar 也必须提供。
     *
     * **可选**：[llm] / [ttsVoiceId] —— [llm] 仅在使用 AI 对话 / 实时对话 时需要，
     * 是否配置完整见 [LLMParams.isConfigured]。
     *
     * @param appId      腾讯云 SDKAppID。
     * @param userId     本次使用的用户 ID；ASR 与 TTS 均使用该 ID。
     * @param userSig    [userId] 对应的 UserSig。
     * @param llm        大模型对话服务参数；不使用 AI 对话时留空即可。
     * @param ttsVoiceId 使用的在线 TTS 音色 voiceId（云端音色 ID，默认 [DEFAULT_TTS_VOICE_ID]）。
     */
    data class VoiceAIKitParams @JvmOverloads constructor(
        val appId: Int,
        val userId: String,
        val userSig: String,
        val llm: LLMParams = LLMParams(),
        val ttsVoiceId: String = DEFAULT_TTS_VOICE_ID,
    ) {

        companion object {
            /**
             * 默认 TTS 音色 voiceId（在线音色「温柔姐姐」）。
             *
             * Kit 只走在线合成，取值须为云端在线音色 ID，
             * 完整清单见 <https://cloud.tencent.com/document/product/647/131300>。
             *
             * 不放在 [VoiceAIKitDefaults]：宿主在 init 前需要用它做兜底，
             * 而 VoiceAIKitDefaults 是 internal，宿主访问不到。
             */
            const val DEFAULT_TTS_VOICE_ID = "v-female-R2s4N9qJ"
        }
    }
}
