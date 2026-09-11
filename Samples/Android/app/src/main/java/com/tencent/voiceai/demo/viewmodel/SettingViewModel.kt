// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAI Demo - 设置 ViewModel

package com.tencent.voiceai.demo.viewmodel

import androidx.lifecycle.ViewModel

/**
 * 设置页面的 ViewModel。
 *
 * 设置页目前仅保留「默认音色」「声纹注册」「版本」等展示 / 跳转项，
 * 其状态由 TtsVoiceSettingViewModel 提供，本类暂不持有开关状态。
 * 后续若新增设置项，可在此以 mutableStateOf + private set 对外只读发布。
 */
class SettingViewModel : ViewModel()
