// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAI Demo - 账号与 LLM 服务配置（Demo 的唯一配置入口）
//
// 注意：本文件中的 SDKAPPID / SECRETKEY / OPEN_AIP_KEY 均为敏感信息，
// 开源或外发前请先执行仓库根目录的 sanitize.ps1（Windows）或 sanitize.sh（macOS/Linux）一键脱敏。

package com.tencent.voiceai.demo.util;

import android.text.TextUtils;
import android.util.Base64;

import com.tencent.voiceai.kit.debug.GenerateTestUserSig;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.zip.Deflater;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class Config {
    /**
     * 大模型（LLM）对话服务地址，经 VoiceAIKit.VoiceAIKitParams 透传给 VoiceAIKit 的 AI
     * 对话（TXAIChatClient）使用。 统一抽到此处便于后期集中管理，请替换为您自己的服务地址。
     */
    public static final String OPEN_AIP_URL = "";
    public static final String OPEN_AIP_KEY = "";

    /**
     * 对话使用的模型名（与参考工程保持一致，已验证可跑通）。
     * 请求体遵循 OpenAI 兼容的 /v1/chat/completions 协议（stream=true）。
     */
    public static final String OPEN_AIP_MODEL = "hy3-preview";

    /**
     * Tencent Cloud SDKAppID. Set it to the SDKAppID of your account.
     * <p>
     * You can view your `SDKAppId` after creating an application in the [Tencent Cloud TRTC
     * console](https://console.cloud.tencent.com/trtc). SDKAppID uniquely identifies a Tencent
     * Cloud account.
     */
    public static final int SDKAPPID = 0;
    public static final String SECRETKEY = "";

    /**
     * 演示用的用户 ID。ASR 与 TTS 统一使用该 ID（见 VoiceAIKit.VoiceAIKitParams.userId）。
     * 真实业务中应为登录用户的唯一标识。
     */
    public static final String USERID = "Test_userID";

    private Config() {}

    /**
     * 便捷方法：使用本类中的 SDKAPPID / SECRETKEY 计算 UserSig。
     * 真正的签名算法在 GenerateTestUserSig 中，本类只负责提供账号配置。
     */
    public static String genTestUserSig(String userId) {
        return GenerateTestUserSig.genTestUserSig(SDKAPPID, userId, SECRETKEY);
    }
}
