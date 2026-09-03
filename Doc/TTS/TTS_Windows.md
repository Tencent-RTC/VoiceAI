# 实时语音合成（TTS）接入文档 —— Windows /桌面 (C++)

> 模块：`TXRealtimeTTS @ TXLiteAVSDK`
>
> 本文档面向 Windows 及桌面 C++ 平台，介绍腾讯实时语音合成（Text-To-Speech，TTS）SDK 的接入方式与完整 API 说明。

---

## 一、功能简介

`ITXRealtimeTTS` 是实时流式语音合成引擎：

- **在线合成**：`TXRealtimeTTSMode::kOnline`，通过云端引擎合成，音色丰富、免本地资源，需联网并进行 UserSig 鉴权。
- **离线合成**：`TXRealtimeTTSMode::kOffline`，使用本地引擎，无需联网即可合成。
- **流式文本输入**：通过 `appendText` 边输入边合成边播放。
- **灵活的音频输出**：仅播放、仅回调 PCM 音频帧、播放并回调。
- **实时控制**：停止 / 清空、音量调节。（暂停 / 恢复、语速调节暂未支持）

> 注意：`TXRealtimeTTSMode` 已支持 `kOnline`（在线）与 `kOffline`（离线）；`kMix`（混合）仍为预留，暂未开放。

---

## 二、接入准备

### 2.1 环境要求

| 项目 | 要求 |
|---|---|
| 操作系统 | Windows 7 及以上（x86 / x64） |
| 编译器 | 支持 C++14 的MSVC / Clang |
| 依赖 | `TXLiteAVSDK` 动态库（`.dll` + `.lib` 导入库） |

### 2.2 引入 SDK

1. 头文件：将 SDK 的 `include` 目录加入工程**附加包含目录**，引入：

```cpp
#include "tx_realtime_tts.h"
```

2. 链接库：将 `TXLiteAVSDK.lib`（导入库）加入 **附加依赖项**，并确保运行时 `TXLiteAVSDK.dll` 位于可执行文件目录或系统 PATH。

3. 导出符号：API 通过 `LITEAVSDK_TTS_API` 导出，工程无需定义 `LITEAV_EXPORTS`（该宏仅 SDK 内部构建使用）。

### 2.3 鉴权信息

不同引擎模式使用不同的鉴权方式，请按所选 `mode` 准备对应的鉴权信息。

#### 2.3.1 离线 TTS 鉴权

离线模式（`kOffline`）使用 License 鉴权，通过 `TXRealtimeTTSParams.offline` 传入：

- `offline.licenseUrl`：离线授权文件下载 / 校验地址。
- `offline.licenseKey`：离线授权 key。

`licenseUrl` 与 `licenseKey` 由控制台申请获得，须在 `start` 之前填入 `TXRealtimeTTSParams.offline`。

#### 2.3.2 在线 TTS 鉴权

在线模式（`kOnline`）使用 UserSig 鉴权，通过 `TXRealtimeTTSParams.online` 传入：

- `online.appId`：应用的 SDKAppID。
- `online.userId`：用户 id。
- `online.userSig`：由 `appId` / `userId` 等信息加密生成的签名。

UserSig 是用户身份的加密凭证。**请勿在客户端硬编码密钥（SECRETKEY）计算 UserSig**，密钥一旦泄露会导致云资源被盗用；生产环境应在业务服务端计算 UserSig，App 通过接口动态获取。UserSig 无效（过期 / 解析失败 / 校验失败）时 `start` 或 `onCompleted` 会返回 `kOnlineInvalidUserSig`。

> UserSig 的定义与客户端 / 服务端计算方法参见：<https://cloud.tencent.com/document/product/647/117218#.E5.AE.A2.E6.88.B7.E7.AB.AF.E8.AE.A1.E7.AE.97-usersig>

### 2.4 实例创建与销毁

SDK 通过 C 风格工厂函数创建 / 销毁实例：

```cpp
extern "C" liteav::ITXRealtimeTTS* TXRealtimeTTSCreate();
extern "C" void TXRealtimeTTSDestroy(liteav::ITXRealtimeTTS* instance);
```

---

## 三、快速开始

```cpp
#include "tx_realtime_tts.h"
#include <cstring>

using namespace liteav;

// 1. 实现监听器
class MyTTSListener : public ITXRealtimeTTSListener {
 public:
  void onStarted() override {
    // 会话已建立
  }
  void onPlaybackProgress(const char* textId, const char* textSlice) override {
    // 逐片播放进度
  }
  void onSynthesizedAudioFrame(const TXSynthesizeAudioFrame& frame) override {
    // 仅 kCallbackOnly / kPlaybackAndCallback 模式触发
    // 注意：frame.pcmData 仅在回调内有效，异步使用须深拷贝
  }
  void onCompleted(int32_t code, const char* msg) override {
    if (code == static_cast<int32_t>(TXRealtimeTTSError::kOk)) {
      // 成功完成
    } else {
      // 失败
    }
  }
};

void RunTTS() {
  // 2. 创建实例
  ITXRealtimeTTS* tts = TXRealtimeTTSCreate();

  // 3. 设置监听器
  static MyTTSListener listener;
  tts->setListener(&listener);

  // 4. 配置参数并启动
  // —— 在线模式 ——
  TXRealtimeTTSParams params;
  params.mode = TXRealtimeTTSMode::kOnline;
  params.online.appId = "your_app_id";
  params.online.userId = "your_user_id";
  params.online.userSig = "your_user_sig";
  params.voiceName = "your_voice_name";
  params.audioOutputMode = TXRealtimeTTSAudioOutputMode::kPlaybackOnly;
  tts->start(params);

  // —— 离线模式 ——
  // 离线模式须在 start 之前配置离线资源路径
  // tts->callExperimentalAPI(
  //     R"({"api":"setOfflineCommonResourcePath","params":{"path":"C:/tts/common"}})");
  // tts->callExperimentalAPI(
  //     R"({"api":"setOfflineVoiceResourcePath","params":{"path":"C:/tts/voice"}})");
  // TXRealtimeTTSParams params;
  // params.mode = TXRealtimeTTSMode::kOffline;
  // params.offline.licenseUrl = "your_license_url";
  // params.offline.licenseKey = "your_license_key";
  // params.voiceName = "your_voice_name";
  // params.audioOutputMode = TXRealtimeTTSAudioOutputMode::kPlaybackOnly;
  // tts->start(params);

  // 5. 流式追加文本
  tts->appendText("text_1", "你好，", false);
  tts->appendText("text_1", "欢迎使用实时语音合成。", true);

  // ... 播放结束后 ...

  // 6. 停止并销毁
  tts->stop();
  tts->setListener(nullptr);
  TXRealtimeTTSDestroy(tts);
}
```

---

## 四、在线音色支持

在线模式（`mode = kOnline`）使用云端引擎合成，音色由服务端提供，支持中文、英文、日语、粤语四种语言，覆盖角色配音、有声书、客服、播报等多种场景。当前推荐模型为 `flow_02_turbo`。

完整的精品音色清单（含音色 ID、性别、语气、推荐场景）以腾讯云官方文档为准：

<https://cloud.tencent.com/document/product/647/131300#a0e5ec7a-cdc4-43d1-b63e-bbb9e8a0597c>

使用时，请将目标音色的**音色 ID** 填入 `TXRealtimeTTSParams.voiceName`，例如中文音色 `v-male-W1tH9jVc`（自然男声）、客服音色 `female-kefu-xiaomei`（客服小美）等。

说明：

- 在线模式的 `voiceName` 取值与官方音色清单中的「音色 ID」保持一致，请以文档最新清单为准。
- 在线音色与离线模式使用的本地音色包相互独立，二者 ID 体系不同。

## 五、API 参考

### 4.1 工厂函数

| 函数 | 说明 |
|---|---|
| `ITXRealtimeTTS* TXRealtimeTTSCreate()` | 创建实例。 |
| `void TXRealtimeTTSDestroy(ITXRealtimeTTS* instance)` | 销毁实例，释放资源。 |

### 4.2 接口 `ITXRealtimeTTS`

除 `callExperimentalAPI` 外均返回 `int32_t`，取值见 [`TXRealtimeTTSError`](#45-错误码-txrealtimettserror)。

| 方法 | 说明 |
|---|---|
| `int32_t setListener(ITXRealtimeTTSListener* listener)` | 设置事件回调监听器；传`nullptr` 取消监听。 |
| `int32_t start(const TXRealtimeTTSParams& param)` | 启动合成与播放。 |
| `int32_t stop()` | 停止合成与播放。 |
| `int32_t pause()` | 暂停合成与播放。**（暂未支持）** |
| `int32_t resume()` | 恢复合成与播放。**（暂未支持）** |
| `int32_t clear()` | 清空待播放与待合成文本；调用后立即丢弃所有排队文本，且不再触发 clear 前 `textId` 的进度回调。 |
| `int32_t appendText(const char* textId, const char* text, bool isEnd = false)` | 流式追加待合成文本。`textId`：文本标识（UTF-8）；`text`：文本内容（UTF-8，必填）；`isEnd`：是否为本段最后一片。 |
| `int32_t setVolume(float volume)` | 设置音量，取值 `[0, 200]`，默认 `100` = 正常。 |
| `int32_t setSpeed(float speed)` | 设置语速，取值 `[1, 3]`，默认 `1` = 正常。**（暂未支持）** |
| `const char* callExperimentalAPI(const char* jsonParams)` | 实验性API 调用，入参为 JSON 字符串，返回 JSON 字符串。见[第五章](#五实验性-api)。 |

### 4.3 监听器 `ITXRealtimeTTSListener`

> 所有回调均已切换到 UI 线程，调用方无需关心线程安全。

| 回调 | 说明 |
|---|---|
| `void onStarted()` | 合成开始（鉴权通过 / 会话已建立）。 |
| `void onPlaybackProgress(const char* textId, const char* textSlice)` | 文本播放进度，逐片流式回调。 |
| `void onSynthesizedAudioFrame(const TXSynthesizeAudioFrame& audioFrame)` | 合成音频帧回调。仅当 `audioOutputMode` 为 `kCallbackOnly` 或 `kPlaybackAndCallback` 时触发。 |
| `void onCompleted(int32_t code, const char* msg)` | 合成结束（成功或失败）。`code == kOk` 表示成功；否则表示任意阶段失败。`msg` 可为 `nullptr`。 |

### 4.4 参数与数据结构

#### `TXRealtimeTTSParams`

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `mode` | `TXRealtimeTTSMode` | `kOnline` | 引擎模式，支持 `kOnline` / `kOffline`。 |
| `online` | `TXRealtimeTTSOnlineCredential` | — | `mode=kOnline` 必填。 |
| `offline` | `TXRealtimeTTSOfflineLicense` | — | `mode=kOffline` 必填。 |
| `voiceName` | `const char*` | `nullptr` | 音色名，**必填**。 |
| `audioOutputMode` | `TXRealtimeTTSAudioOutputMode` | `kPlaybackOnly` | 音频输出模式。 |

#### `TXRealtimeTTSOnlineCredential`（在线鉴权，`mode=kOnline` 必填）

| 字段 | 类型 | 说明 |
|---|---|---|
| `appId` | `const char*` | 应用 appid。 |
| `userId` | `const char*` | 用户 id。 |
| `userSig` | `const char*` | 用户签名（服务端下发，避免密钥打包进客户端）。 |

#### `TXRealtimeTTSOfflineLicense`（离线鉴权）

| 字段 | 类型 | 说明 |
|---|---|---|
| `licenseUrl` | `const char*` | 离线授权文件下载 / 校验地址。 |
| `licenseKey` | `const char*` | 离线授权 key。 |

#### `TXSynthesizeAudioFrame`（音频输出帧）

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `pcmData` | `const uint8_t*` | `nullptr` | 本次音频 PCM 字节；指针仅在回调内有效，跨线程 / 异步使用须深拷贝。 |
| `pcmDataSize` | `uint32_t` | 0 | `pcmData` 字节数。 |
| `sampleRate` | `uint32_t` | 16000 | 采样率（Hz），如 16000/48000。 |
| `channelCount` | `uint32_t` | 1 | 通道数，1=单声道，2=双声道。 |
| `bitWidth` | `uint32_t` | 16 | 采样位宽（bit），固定 16。 |
| `textId` | `const char*` | `nullptr` | 文本标识（对应 `appendText` 的 `textId`）。 |
| `textSlice` | `const char*` | `nullptr` | 当前合成数据所属的文本片段。 |
| `progress` | `double` | 0.0 | `textSlice` 在 `textId` 文本段中的百分比 `[0.0, 1.0]`。 |

### 4.5 枚举

#### `TXRealtimeTTSMode`（引擎模式）

| 值 | 说明 |
|---|---|
| `kOnline` | 纯在线（云端引擎，需联网与 UserSig 鉴权）。 |
| `kOffline` | 纯离线（本地引擎，无网可用）。 |
| `kMix` | 预留，暂未开放。 |

#### `TXRealtimeTTSAudioOutputMode`（音频输出模式）

| 值 | 说明 |
|---|---|
| `kPlaybackOnly` | 仅播放音频，不回调音频帧（默认）。 |
| `kCallbackOnly` | 仅回调音频帧，不播放。 |
| `kPlaybackAndCallback` | 既播放又回调音频帧。 |

### 4.6 错误码 `TXRealtimeTTSError`

| 常量 | 值 | 说明 |
|---|---|---|
| `kOk` | 0 | 成功。 |
| `kInvalidState` | 1 | 状态不允许（如已处于 started 状态时再次调用 start）。 |
| `kInvalidParam` | 2 | 配置字段非法。 |
| `kNotImplement` | 3 | 功能尚未实现（如 pause/resume/setSpeed/在线模式等）。 |
| `kOfflineUnknownError` | 100 | 未识别的离线引擎状态（防御性兜底）。 |
| `kOfflineInvalidState` | 101 | 离线引擎内部状态非法。 |
| `kOfflineResourceError` | 102 | 通用/音色资源加载失败。 |
| `kOfflineModelError` | 103 | 模型加载或推理失败。 |
| `kOfflineLicenseInvalid` | 104 | 授权文件无效。 |
| `kOfflineLicenseExpired` | 105 | 授权文件已过期。 |
| `kOnlineUnknownError` | 200 | 未识别的在线引擎状态（防御性兜底）。 |
| `kOnlineNetworkError` | 201 | 网络错误。 |
| `kOnlineServerError` | 202 | 服务端错误。 |
| `kOnlineInvalidUserSig` | 203 | UserSig 无效（过期/解析失败/校验失败等）。 |

> 说明：错误码分段规划，基础错误码位于 `[1, 100)`，离线 TTS 错误码位于 `[100, 200)`，在线 TTS 错误码位于 `[200, 300)`。`onCompleted` 的 `code` 使用 `TXRealtimeTTSError` 中的整型值。

---

## 六、实验性 API

`callExperimentalAPI(const char* jsonParams)` 用于设置资源路径等可选项。**离线模式下 `setOfflineCommonResourcePath` 与 `setOfflineVoiceResourcePath` 为必需**，须在 `start` 之前调用；在线模式无需配置离线资源路径。入参为 JSON 字符串，格式：

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

```cpp
const char* json =
    R"({"api":"setOfflineCommonResourcePath","params":{"path":"C:/tts/common"}})";
const char* result = tts->callExperimentalAPI(json);
// result 为 JSON 字符串，指针在下次调用前有效
```

---

## 七、最佳实践与注意事项

1. **模式选择**：在线用 `kOnline`（需联网 + UserSig 鉴权），离线用 `kOffline`（本地资源，无网可用）；`kMix` 暂未开放。
2. **字符串编码**：`textId` / `text` 等均为 UTF-8。
3. **音色必填**：`voiceName` 为 `nullptr` 会导致 `start` 返回 `kInvalidParam`。
4. **在线鉴权必填**：`kOnline` 模式须填 `online.appId` / `online.userId` / `online.userSig`；UserSig 无效会返回 `kOnlineInvalidUserSig`。
5. **离线鉴权必填**：`kOffline` 模式须填 `offline.licenseUrl` 与 `offline.licenseKey`。
6. **离线资源路径必配**：`kOffline` 模式须在 `start` 之前通过实验性 API 配置好通用资源与音色资源路径；在线模式无需配置。
7. **流式结束标记**：一段文本输入完成后，最后一片须置 `isEnd=true`。
8. **指针生命周期**：`TXRealtimeTTSParams` 中的 `const char*` 字段在 `start` 调用期间须保持有效；`onSynthesizedAudioFrame` 的 `pcmData` 仅在回调内有效，异步使用请深拷贝。
9. **`callExperimentalAPI` 返回值**：返回的 `const char*` 由 SDK 内部持有，请在下次调用前使用或拷贝。
10. **生命周期**：销毁前先 `stop()` 并 `setListener(nullptr)`，再调用 `TXRealtimeTTSDestroy`。
