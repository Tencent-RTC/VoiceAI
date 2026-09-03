# 实时语音合成（TTS）接入文档 —— HarmonyOS (ArkTS)

> 模块：`TXRealtimeTTS @ TXLiteAVSDK`
>
> 本文档面向 HarmonyOS（ArkTS）平台，介绍腾讯实时语音合成（Text-To-Speech，TTS）SDK 的接入方式与完整 API 说明。

---

## 一、功能简介

`TXRealtimeTTS` 是实时流式语音合成引擎：

- **在线合成**：`TXRealtimeTTSMode.Online`，通过云端引擎合成，音色丰富、免本地资源，需联网并进行 UserSig 鉴权。
- **离线合成**：`TXRealtimeTTSMode.Offline`，使用本地引擎，无需联网即可合成。
- **流式文本输入**：通过 `appendText` 边输入边合成边播放。
- **灵活的音频输出**：仅播放、仅回调 PCM 音频帧、播放并回调。
- **实时控制**：停止 / 清空、音量调节。（暂停 / 恢复、语速调节暂未支持）
- **多监听器**：支持 `addListener` / `removeListener` 注册多个监听器。

> 注意：`TXRealtimeTTSMode` 已支持 `Online`（在线）与 `Offline`（离线）；`Mix`（混合）仍为预留，暂未开放。

---

## 二、接入准备

### 2.1 环境要求

| 项目 | 要求 |
|---|---|
| 系统 | HarmonyOS NEXT / API 对应版本 |
| 语言 | ArkTS |
| 依赖 | `libliteavsdk_voiceai.so` 及配套ArkTS API |

### 2.2 引入 SDK

1. 将 SDK 提供的 HAR 包（含 `libliteavsdk_voiceai.so` 与 ArkTS 声明）加入工程依赖。
2. 在 `oh-package.json5` 中声明依赖后执行同步。
3. 导入 API：

```typescript
import { TXRealtimeTTS, TXRealtimeTTSMode, TXRealtimeTTSError,
         TXRealtimeTTSAudioOutputMode, TXRealtimeTTSParams,
         TXSynthesizeAudioFrame, ITXRealtimeTTSListener } from 'path/to/tx_realtime_tts';
```

### 2.3 鉴权信息

不同引擎模式使用不同的鉴权方式，请按所选 `mode` 准备对应的鉴权信息。

#### 2.3.1 离线 TTS 鉴权

离线模式（`Offline`）使用 License 鉴权，通过 `TXRealtimeTTSParams.offline` 传入：

- `offline.licenseUrl`：离线授权文件下载 / 校验地址。
- `offline.licenseKey`：离线授权 key。

`licenseUrl` 与 `licenseKey` 由控制台申请获得，须在 `start` 之前填入 `TXRealtimeTTSParams.offline`。

#### 2.3.2 在线 TTS 鉴权

在线模式（`Online`）使用 UserSig 鉴权，通过 `TXRealtimeTTSParams.online` 传入：

- `online.appId`：应用的 SDKAppID。
- `online.userId`：用户 id。
- `online.userSig`：由 `appId` / `userId` 等信息加密生成的签名。

UserSig 是用户身份的加密凭证。**请勿在客户端硬编码密钥（SECRETKEY）计算 UserSig**，密钥一旦泄露会导致云资源被盗用；生产环境应在业务服务端计算 UserSig，App 通过接口动态获取。UserSig 无效（过期 / 解析失败 / 校验失败）时 `start` 或 `onCompleted` 会返回 `OnlineInvalidUserSig`。

> UserSig 的定义与客户端 / 服务端计算方法参见：<https://cloud.tencent.com/document/product/647/117218#.E5.AE.A2.E6.88.B7.E7.AB.AF.E8.AE.A1.E7.AE.97-usersig>

### 2.4 权限声明

在线模式需要网络访问，请在 `module.json5` 中声明 `ohos.permission.INTERNET`。离线模式无需网络权限；如需读取应用沙箱外的离线资源，请按需申请对应权限。

---

## 三、快速开始

```typescript
import { TXRealtimeTTS, TXRealtimeTTSMode, TXRealtimeTTSError,
         TXRealtimeTTSAudioOutputMode, TXRealtimeTTSParams,
         TXSynthesizeAudioFrame, ITXRealtimeTTSListener } from 'path/to/tx_realtime_tts';

class TTSDemo {
  private tts: TXRealtimeTTS | null = null;

  private listener: ITXRealtimeTTSListener = {
    onStarted: () => {
      // 会话已建立
    },
    onPlaybackProgress: (textId: string, textSlice: string) => {
      // 逐片播放进度
    },
    onSynthesizedAudioFrame: (frame: TXSynthesizeAudioFrame) => {
      // 仅 CallbackOnly / PlaybackAndCallback 模式触发
    },
    onCompleted: (code: number, msg: string | null) => {
      if (code === TXRealtimeTTSError.Ok) {
        // 成功完成
      } else {
        // 失败
      }
    }
  };

  startTTS(): void {
    // 1. 创建实例
    this.tts = new TXRealtimeTTS();

    // 2. 添加监听器
    this.tts.addListener(this.listener);

    // 3. 配置参数并启动
    // —— 在线模式 ——
    const params = new TXRealtimeTTSParams();
    params.mode = TXRealtimeTTSMode.Online;
    params.online.appId = 'your_app_id';
    params.online.userId = 'your_user_id';
    params.online.userSig = 'your_user_sig';
    params.voiceName = 'your_voice_name';
    params.audioOutputMode = TXRealtimeTTSAudioOutputMode.PlaybackOnly;
    this.tts.start(params);

    // —— 离线模式 ——
    // 离线模式须在 start 之前配置离线资源路径
    // this.tts.callExperimentalAPI(JSON.stringify({
    //   api: 'setOfflineCommonResourcePath',
    //   params: { path: '/data/storage/tts/common' }
    // }));
    // this.tts.callExperimentalAPI(JSON.stringify({
    //   api: 'setOfflineVoiceResourcePath',
    //   params: { path: '/data/storage/tts/voice' }
    // }));
    // const params = new TXRealtimeTTSParams();
    // params.mode = TXRealtimeTTSMode.Offline;
    // params.offline.licenseUrl = 'your_license_url';
    // params.offline.licenseKey = 'your_license_key';
    // params.voiceName = 'your_voice_name';
    // params.audioOutputMode = TXRealtimeTTSAudioOutputMode.PlaybackOnly;
    // this.tts.start(params);

    // 4. 流式追加文本
    this.tts.appendText('text_1', '你好，', false);
    this.tts.appendText('text_1', '欢迎使用实时语音合成。', true);
  }

  release(): void {
    if (this.tts) {
      this.tts.stop();
      this.tts.removeListener(this.listener);
      this.tts = null;
    }
  }
}
```

---

## 四、在线音色支持

在线模式（`mode = Online`）使用云端引擎合成，音色由服务端提供，支持中文、英文、日语、粤语四种语言，覆盖角色配音、有声书、客服、播报等多种场景。当前推荐模型为 `flow_02_turbo`。

完整的精品音色清单（含音色 ID、性别、语气、推荐场景）以腾讯云官方文档为准：

<https://cloud.tencent.com/document/product/647/131300#a0e5ec7a-cdc4-43d1-b63e-bbb9e8a0597c>

使用时，请将目标音色的**音色 ID** 填入 `TXRealtimeTTSParams.voiceName`，例如中文音色 `v-male-W1tH9jVc`（自然男声）、客服音色 `female-kefu-xiaomei`（客服小美）等。

说明：

- 在线模式的 `voiceName` 取值与官方音色清单中的「音色 ID」保持一致，请以文档最新清单为准。
- 在线音色与离线模式使用的本地音色包相互独立，二者 ID 体系不同。

## 五、API 参考

### 4.1 类 `TXRealtimeTTS`

#### 构造

| 方法 | 说明 |
|---|---|
| `constructor()` | 创建新实例，构造函数内完成原生资源分配。 |

#### 监听器管理

> 与其他平台不同，ArkTS 支持注册**多个**监听器。

| 方法 | 说明 |
|---|---|
| `addListener(listener: ITXRealtimeTTSListener): void` | 添加回调监听器。 |
| `removeListener(listener: ITXRealtimeTTSListener): void` | 移除之前添加的监听器。 |

#### 核心方法

除监听器管理外均返回 `number`，取值见 [`TXRealtimeTTSError`](#45-错误码-txrealtimettserror)。

| 方法 | 说明 |
|---|---|
| `start(param: TXRealtimeTTSParams): number` | 启动合成与播放。 |
| `stop(): number` | 停止合成与播放。 |
| `pause(): number` | 暂停合成与播放。**（暂未支持）** |
| `resume(): number` | 恢复合成与播放。**（暂未支持）** |
| `clear(): number` | 清空待播放与待合成文本；调用后立即丢弃所有排队文本，且不再触发 clear 前 `textId` 的进度回调。 |
| `appendText(textId: string, text: string, isEnd?: boolean): number` | 流式追加待合成文本。`textId`：文本标识（UTF-8）；`text`：文本内容（UTF-8，必填）；`isEnd`：是否为本段最后一片，默认 `false`。 |
| `setVolume(volume: number): number` | 设置音量，取值 `[0, 200]`，默认 `100` = 正常。 |
| `setSpeed(speed: number): number` | 设置语速，取值 `[1, 3]`，默认 `1` = 正常。**（暂未支持）** |
| `callExperimentalAPI(jsonParams: string): string \| null` | 实验性 API 调用，入参为 JSON 字符串，返回 JSON 字符串。见[第五章](#五实验性-api)。 |

### 4.2 监听器 `ITXRealtimeTTSListener`

> 所有方法均为可选，按需实现。所有回调均已切换到 UI 线程触发，调用方无需关心线程安全。

| 回调 | 说明 |
|---|---|
| `onStarted?(): void` | 合成开始（鉴权通过 / 会话已建立）。 |
| `onPlaybackProgress?(textId: string, textSlice: string): void` | 文本播放进度，逐片流式回调。 |
| `onSynthesizedAudioFrame?(audioFrame: TXSynthesizeAudioFrame): void` | 合成音频帧回调。仅当 `audioOutputMode` 为 `CallbackOnly` 或 `PlaybackAndCallback` 时触发。 |
| `onCompleted?(code: number, msg: string \| null): void` | 合成结束（成功或失败）。`code === Ok` 表示成功；否则表示任意阶段失败。`msg` 可为 `null`。 |

### 4.3 参数与数据结构

#### `TXRealtimeTTSParams`

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `mode` | `TXRealtimeTTSMode` | `Online` | 引擎模式，支持 `Online` / `Offline`。 |
| `online` | `TXRealtimeTTSOnlineCredential` | 空对象 | `mode=Online` 必填。 |
| `offline` | `TXRealtimeTTSOfflineLicense` | 空对象 | `mode=Offline` 必填。 |
| `voiceName` | `string` | `undefined` | 音色名，**必填**。 |
| `audioOutputMode` | `TXRealtimeTTSAudioOutputMode` | `PlaybackOnly` | 音频输出模式。 |

#### `TXRealtimeTTSOnlineCredential`（在线鉴权，`mode=Online` 必填）

| 字段 | 类型 | 说明 |
|---|---|---|
| `appId` | `string` | 应用 appid。 |
| `userId` | `string` | 用户 id。 |
| `userSig` | `string` | 用户签名（服务端下发，避免密钥打包进客户端）。 |

#### `TXRealtimeTTSOfflineLicense`（离线鉴权）

| 字段 | 类型 | 说明 |
|---|---|---|
| `licenseUrl` | `string` | 离线授权文件下载 / 校验地址。 |
| `licenseKey` | `string` | 离线授权 key。 |

#### `TXSynthesizeAudioFrame`（音频输出帧）

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `pcmData` | `Uint8Array` | `undefined` | 本次音频 PCM 字节；数据仅在回调内有效，跨线程 / 异步使用须深拷贝（如 `slice()`）。 |
| `pcmDataSize` | `number` | 0 | `pcmData` 字节数。 |
| `sampleRate` | `number` | 16000 | 采样率（Hz），如 16000/48000。 |
| `channelCount` | `number` | 1 | 通道数，1=单声道，2=双声道。 |
| `bitWidth` | `number` | 16 | 采样位宽（bit），固定 16。 |
| `textId` | `string` | `undefined` | 文本标识（对应 `appendText` 的 `textId`）。 |
| `textSlice` | `string` | `undefined` | 当前合成数据所属的文本片段。 |
| `progress` | `number` | 0 | `textSlice` 在 `textId` 文本段中的百分比 `[0.0, 1.0]`。 |

### 4.4 枚举

#### `TXRealtimeTTSMode`（引擎模式）

| 值 | 数值 | 说明 |
|---|---|---|
| `Online` | 0 | 纯在线（云端引擎，需联网与 UserSig 鉴权）。 |
| `Offline` | 1 | 纯离线（本地引擎，无网可用）。 |
| `Mix` | 2 | 预留，暂未开放。 |

#### `TXRealtimeTTSAudioOutputMode`（音频输出模式）

| 值 | 数值 | 说明 |
|---|---|---|
| `PlaybackOnly` | 0 | 仅播放音频，不回调音频帧（默认）。 |
| `CallbackOnly` | 1 | 仅回调音频帧，不播放。 |
| `PlaybackAndCallback` | 2 | 既播放又回调音频帧。 |

### 4.5 错误码 `TXRealtimeTTSError`

| 常量 | 值 | 说明 |
|---|---|---|
| `Ok` | 0 | 成功。 |
| `InvalidState` | 1 | 状态不允许（如已处于 Started 状态时再次 Start）。 |
| `InvalidParam` | 2 | 配置字段非法。 |
| `NotImplement` | 3 | 功能尚未实现（如 pause/resume/setSpeed/在线模式等）。 |
| `OfflineUnknownError` | 100 | 未识别的离线引擎状态（防御性兜底）。 |
| `OfflineInvalidState` | 101 | 离线引擎内部状态非法。 |
| `OfflineResourceError` | 102 | 通用/音色资源加载失败。 |
| `OfflineModelError` | 103 | 模型加载或推理失败。 |
| `OfflineLicenseInvalid` | 104 | 授权文件无效。 |
| `OfflineLicenseExpired` | 105 | 授权文件已过期。 |
| `OnlineUnknownError` | 200 | 未识别的在线引擎状态（防御性兜底）。 |
| `OnlineNetworkError` | 201 | 网络错误。 |
| `OnlineServerError` | 202 | 服务端错误。 |
| `OnlineInvalidUserSig` | 203 | UserSig 无效（过期/解析失败/校验失败等）。 |

> 说明：错误码分段规划，基础错误码位于 `[1, 100)`，离线 TTS 错误码位于 `[100, 200)`，在线 TTS 错误码位于 `[200, 300)`。`onCompleted` 的 `code` 使用 `TXRealtimeTTSError` 中的整型值。

---

## 六、实验性 API

`callExperimentalAPI(jsonParams: string)` 用于设置资源路径等可选项。**离线模式下 `setOfflineCommonResourcePath` 与 `setOfflineVoiceResourcePath` 为必需**，须在 `start` 之前调用；在线模式无需配置离线资源路径。入参为 JSON 字符串，格式：

```json
{ "api": "<接口名>", "params": { ... } }
```

支持的接口：

| api | params | 说明 |
|---|---|---|
| `setOfflineCommonResourcePath` | `{"path": "/path/to/common"}` | 设置离线通用资源路径。 |
| `setOfflineVoiceResourcePath` | `{"path": "/path/to/voiceresource"}` | 设置离线音色资源路径。 |
| `setTTSAudioDumpDir` | `{"path": "/path/to/dump"}` | 设置合成音频 WAV 落盘目录；`path` 为空则关闭落盘。 |

示例：

```typescript
const json = JSON.stringify({
  api: 'setOfflineCommonResourcePath',
  params: { path: '/data/storage/tts/common' }
});
this.tts?.callExperimentalAPI(json);
```

---

## 七、最佳实践与注意事项

1. **模式选择**：在线用 `TXRealtimeTTSMode.Online`（需联网 + UserSig 鉴权），离线用 `TXRealtimeTTSMode.Offline`（本地资源，无网可用）；`Mix` 暂未开放。
2. **音色必填**：`voiceName` 未设置会导致 `start` 返回 `InvalidParam`。
3. **在线鉴权必填**：`Online` 模式须填 `online.appId` / `online.userId` / `online.userSig`；UserSig 无效会返回 `OnlineInvalidUserSig`。
4. **离线鉴权必填**：`Offline` 模式须填 `offline.licenseUrl` 与 `offline.licenseKey`。
5. **离线资源路径必配**：`Offline` 模式须在 `start` 之前通过实验性 API 配置好通用资源与音色资源路径；在线模式无需配置。
6. **流式结束标记**：一段文本输入完成后，最后一片须置 `isEnd = true`。
7. **音频帧深拷贝**：`onSynthesizedAudioFrame` 中的 `pcmData` 仅在回调内有效，如需异步处理请立即 `slice()` 深拷贝。
8. **监听器管理**：注册的 `listener` 对象引用须保持稳定，`removeListener` 需传入 `addListener` 时的同一对象引用。
