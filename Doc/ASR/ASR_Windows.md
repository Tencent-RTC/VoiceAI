本文档面向 Windows 平台，介绍 VoiceAI ASR SDK 的接入方式与完整 API 说明

## 一、功能简介

`ITXRealtimeASR` 是实时流式语音转写引擎：

- **实时流式识别**：边说边出字，逐句返回识别结果。
- **中间结果与稳态结果**：通过 `isCompleted` 区分「进行中（中间结果）」与「已说完（稳态结果）」。
- **自动 / 指定识别语言**：支持 `"zh"`（中文）、`"en"`（英文）等，留空即为自动识别。
- **说话人区分**：结果携带 `speakerId`，`-1` 表示未知。
- **时间戳定位**：结果携带 `startTime` / `endTime`，标识该消息在整个音频流中的起止位置（毫秒），`-1` 表示未知。
- **实时音量回调**：通过 `onRealtimeASRVolume` 获取当前音量等级 `[0, 100]`，可用于绘制音量波动效果。
- **自定义音频采集**：可通过 `enableCustomCapture` 开启，自行采集 PCM 后调用 `feedPcmData` 送入引擎。
- **多监听器**：通过 `addListener` / `removeListener` 注册多个回调监听器。

---

## 二、接入准备

### 2.1 开通服务

登录 [实时音视频控制台](https://console.cloud.tencent.com/trtc)，单击创建应用 。如果您已经完成创建，可以跳过该操作。

在创建应用成功后，您可以在应用管理中获取到您的 SDKAppID 和 SDK 密钥。

![](https://write-document-release-1258344699.cos.ap-guangzhou.tencentcos.cn/100027984178/3f1a16f79bb011f1b2a8525400074c32.png)

- SDKAppID： 控制台创建的实时音视频应用的唯一标识。

- SDK 密钥：生成鉴权信息（UserSig）时使用的密钥串。

### 2.2 环境要求

| 项目 | 要求 |
|---|---|
| 操作系统 | Windows 7 及以上（x86 / x64） |
| 编译器 | 支持 C++14 的 MSVC / Clang |
| 依赖 | `liteavsdk_voiceai` 动态库（`.dll` + `.lib` 导入库） |
| 网络 | 实时转写为在线服务，需要可访问腾讯云的网络环境 |

### 2.3 引入 SDK

1. [下载 Windows SDK](https://dl.gmertc.com/voiceai/13.6.0/VoiceAI_Win_sdk_13.6.0.237.zip)
1. 头文件：将 SDK 的 `include` 目录加入工程**附加包含目录**，引入：

```cpp
#include "tx_realtime_asr.h"
```

2. 链接库：将 `liteavsdk_voiceai.lib`（导入库）加入 **附加依赖项**，并确保运行时 `liteavsdk_voiceai.dll` 位于可执行文件目录或系统 PATH。

3. 导出符号：API 通过 `LITEAVSDK_API` 导出，工程无需定义 `LITEAV_EXPORTS`（该宏仅 SDK 内部构建使用）。

### 2.4 鉴权信息

启动转写前需要准备以下 TRTC 鉴权信息：

| 参数 | 说明 |
|---|---|
| `sdkAppId` | TRTC 应用 ID，可在腾讯云 TRTC 控制台获取。 |
| `voiceId` | 识别会话 ID，参与 `userSig` 计算，建议每次调用时重新生成 UUID 传入。 |
| `userSig` | TRTC 用户签名，由 `sdkAppId`、`sdkAppKey`、`voiceId` 三者计算得到。 |

> **关于 UserSig 的安全说明**
>
> UserSig 是用户身份的加密凭证。**请勿在客户端硬编码密钥（SECRETKEY）计算 UserSig**，密钥一旦泄露会导致云资源被盗用；生产环境应在业务服务端计算 UserSig，App 通过接口动态获取。
>
> UserSig 计算方式参考：
> - **调试阶段**：可参考 [调试跑通阶段如何计算 UserSig](https://cloud.tencent.com/document/product/647/17275#.E8.B0.83.E8.AF.95.E8.B7.91.E9.80.9A.E9.98.B6.E6.AE.B5.E5.A6.82.E4.BD.95.E8.AE.A1.E7.AE.97-UserSig.EF.BC.9F)，在本地临时计算 UserSig 用于联调。
> - **生产环境**：应在业务服务端计算 UserSig，App 通过接口动态获取，可参考 [UserSig 服务端计算指引](https://cloud.tencent.com/document/product/647/17275#formal)。

### 2.5 实例创建与销毁

SDK 通过 C 风格工厂函数创建 / 销毁实例：

```cpp
extern "C" liteav::ITXRealtimeASR* TXRealtimeASRCreate();
extern "C" void TXRealtimeASRDestroy(liteav::ITXRealtimeASR* instance);
```

---

## 三、快速开始

```cpp
#include "tx_realtime_asr.h"
#include <string>

using namespace liteav;

// 1. 实现监听器
class MyASRListener : public TXRealtimeASRListener {
 public:
  void onRealtimeASRStarted(const char* voiceId) override {
    // 转录会话已开启，voiceId 为识别会话 ID
  }

  void onReceiveRealtimeASRMessage(const TXRealtimeASRMessage& message) override {
    if (message.isCompleted) {
      // 稳态结果：某句话已说完，sourceText 为最终识别文本
    } else {
      // 中间结果：正在识别中，sourceText 为实时暂存文本
    }
    // 可获取该消息在音频流中的时间区间（毫秒），-1 表示未知
    int start = message.startTime;
    int end = message.endTime;
  }

  void onRealtimeASRStopped() override {
    // 转录正常停止
  }

  void onRealtimeASRError(int errorCode, const char* errorMsg) override {
    // 转录出错，errorCode 非 0 表示异常
  }

  void onRealtimeASRVolume(int volume) override {
    // 实时音量，volume 取值 [0, 100]
  }
};

void RunASR() {
  // 2. 创建实例
  ITXRealtimeASR* asr = TXRealtimeASRCreate();

  // 3. 设置监听器（注意保持监听器对象在会话期间有效）
  static MyASRListener listener;
  asr->addListener(&listener);

  // 4. 配置参数并启动
  TXRealtimeASRParams params;
  params.sdkAppId = "1400000000";               // TRTC 应用 ID
  params.userSig = "your_user_sig";             // 服务端下发的用户签名
  params.voiceId = "uuid-xxxx-xxxx-xxxx";       // 识别会话 ID，建议每次调用重新生成 UUID
  params.sourceLanguage = "zh";                 // 识别语言，"zh"/"en"，留空为自动识别
  params.enableCustomCapture = false;           // 是否启用自定义音频采集
  asr->startRealtimeASR(params);

  // 5.（可选）自定义采集模式：开启后自行采集并送入 PCM 音频
  // const int16_t pcm[160]; // 16k、单声道、16bit 采样
  // asr->feedPcmData(pcm, 160, 16000, 1);

  // 6. 停止转录（正常停止后回调 onRealtimeASRStopped）
  asr->stopRealtimeASR();

  // 7. 移除监听并销毁
  asr->removeListener(&listener);
  TXRealtimeASRDestroy(asr);
}
```

---

## 四、API 参考

### 4.1 工厂函数

| 函数 | 说明 |
|---|---|
| `liteav::ITXRealtimeASR* TXRealtimeASRCreate()` | 创建实例。 |
| `void TXRealtimeASRDestroy(liteav::ITXRealtimeASR* instance)` | 销毁实例，释放资源。 |

### 4.2 接口 `ITXRealtimeASR`

| 方法 | 说明 |
|---|---|
| `void addListener(TXRealtimeASRListener* listener)` | 添加回调监听器；同一监听器重复添加可能触发多次回调。 |
| `void removeListener(TXRealtimeASRListener* listener)` | 移除回调监听器。 |
| `void startRealtimeASR(const TXRealtimeASRParams& params)` | 启动实时语音转写会话。 |
| `void stopRealtimeASR()` | 停止实时转写，等后端算完后回调 `onRealtimeASRStopped` 或 `onRealtimeASRError`。 |
| `void feedPcmData(const int16_t* data, size_t length, uint32_t sampleRate, uint32_t channels)` | 自定义采集时送入 PCM 音频数据。`data`：16bit PCM 采样数据；`length`：采样点个数（`int16_t` 元素数量，非字节数）；`sampleRate`：采样率（Hz）；`channels`：通道数。 |
| `const char* callExperimentalAPI(const char* jsonStr)` | 实验性 API 调用，入参为 JSON 字符串，返回字符串。 |

### 4.3 监听器 `TXRealtimeASRListener`

> 所有回调均已切换到 UI 线程，调用方无需关心线程安全。

| 回调 | 说明 |
|---|---|
| `void onRealtimeASRStarted(const char* voiceId)` | 实时转录开启成功的回调。`voiceId` 为识别会话 ID。 |
| `void onReceiveRealtimeASRMessage(const TXRealtimeASRMessage& message)` | 收到实时转录消息的回调，`message` 包含转录文本和完成状态。 |
| `void onRealtimeASRStopped()` | 实时转录停止的回调（正常停止）。 |
| `void onRealtimeASRError(int errorCode, const char* errorMsg)` | 实时转录出错的回调。`errorCode` 非 0 表示异常，`errorMsg` 为错误信息。 |
| `void onRealtimeASRVolume(int volume)` | 实时音量回调。`volume` 为音量等级，取值范围 `[0, 100]`。 |

### 4.4 参数与数据结构

#### `TXRealtimeASRParams`（启动参数）

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `sdkAppId` | `const char*` | `nullptr` | TRTC 应用 ID。 |
| `userSig` | `const char*` | `nullptr` | TRTC 用户签名。 |
| `voiceId` | `const char*` | `nullptr` | 识别会话 ID，建议每次调用时重新生成 UUID 传入。 |
| `sourceLanguage` | `const char*` | `nullptr` | 识别语言，如 `"zh"`、`"en"`，留空即为自动识别。 |
| `enableCustomCapture` | `bool` | `false` | 是否启用自定义音频采集；为 `true` 时须通过 `feedPcmData` 送入音频。 |

#### `TXRealtimeASRMessage`（转录消息）

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `segmentId` | `const char*` | `nullptr` | 消息段的唯一标识 ID。 |
| `sourceText` | `const char*` | `nullptr` | 识别出的源语言文本（对应协议 `voice_text_str`）。 |
| `isCompleted` | `bool` | `false` | 转录是否结束：`false`=进行中（中间结果），`true`=已说完（稳态结果）。 |
| `speakerId` | `int` | `-1` | 说话人 ID，`-1` 表示未知。 |
| `startTime` | `int` | `-1` | 本消息在整个音频流中的**起始时间**（毫秒），`-1` 表示未知。 |
| `endTime` | `int` | `-1` | 本消息在整个音频流中的**结束时间**（毫秒），`-1` 表示未知。 |

> 注意：`TXRealtimeASRParams` 中的 `const char*` 字段在 `startRealtimeASR` 调用期间须保持有效；`TXRealtimeASRMessage` 中的字符串指针仅在回调内有效，异步使用请自行拷贝。

---

## 五、实验性 API

`callExperimentalAPI(const char* jsonStr)` 用于调用实验性 / 扩展能力。入参为 JSON 字符串，返回字符串。格式：

```json
{ "api": "<接口名>", "params": { ... } }
```

### 5.1 setExtraParams

引擎参数配置，用于追加自定义的 ASR 引擎参数与降噪策略。

输入参数（`params` 字段）：

| 参数 | 类型 | 说明 |
|---|---|---|
| `extraRequestParams` | `string` | Query String 格式的引擎参数，整体追加到 ASR URL 末尾。 |
| `clientDenoiseStrategy` | `int` | 降噪策略：`0`=关闭（默认），`1`=开启（Percepnet）。 |

`extraRequestParams` 可用键名：

| 键名 | 说明 | 默认值 |
|---|---|---|
| `engine_model_type` | 引擎模型类型，如 `"bigmodel"`、`"16k_zh_en"`。 | `bigmodel` |
| `needvad` | 是否开启 VAD：`0`=关闭，`1`=开启。 | `1` |
| `filter_dirty` | 脏词过滤：`0`=不过滤，`1`=过滤，`2`=替换为 `*`。 | `0` |
| `filter_modal` | 语气词过滤：`0`=不过滤，`1`=部分，`2`=严格。 | `0` |
| `filter_punc` | 句末标点过滤：`0`=不过滤，`1`=过滤。 | `0` |
| `convert_num_mode` | 数字转换：`0`=不转，`1`=智能转换，`3`=数学转换。 | `1` |
| `vad_silence_time` | VAD 静音断句阈值（ms），范围 240-1000。 | `1000` |
| `max_speak_time` | 强制断句时间（ms），范围 5000-90000。 | `60000` |

参数优先级规则：

1. SDK 内部生成的参数（`timestamp`、`expired`、`signature`、`nonce` 等）不可被覆盖。
2. 启动参数中的显性字段（`sdkAppId`、`userSig`、`voiceId`、`sourceLanguage`）不可被覆盖。
3. `extraRequestParams` 中的其余键——自由扩展，整体追加到 URL 末尾。

> - 若 `extraRequestParams` 中出现了第 1、2 类键，SDK 将忽略并输出 WARNING 日志。
> - 若 `extraRequestParams` 中的键与默认字段同名（如 `engine_model_type`），默认字段被移除，以 `extraRequestParams` 中的值为准。

示例：

```cpp
const char* json =
    R"({"api":"setExtraParams","params":{"extraRequestParams":"engine_model_type=bigmodel","clientDenoiseStrategy":1}})";
asr->callExperimentalAPI(json);
```

---

## 六、最佳实践与注意事项

1. **鉴权信息**：`userSig` 应由服务端生成后下发，切勿将生成密钥硬编码到客户端。
2. **voiceId 唯一性**：每次调用 `startRealtimeASR` 建议重新生成新的 UUID 作为 `voiceId`。
3. **字符串生命周期**：`TXRealtimeASRParams` 中的 `const char*` 字段在启动调用期间须保持有效；回调中的字符串指针仅在回调内有效。
4. **自定义采集**：`enableCustomCapture = true` 时须主动调用 `feedPcmData` 送入 16bit PCM 数据，并保证 `sampleRate`、`channels` 与数据一致。
5. **中间结果与稳态结果**：`isCompleted = false` 的文本会持续更新，仅在 `isCompleted = true` 时该段识别才最终确定。
6. **`callExperimentalAPI` 返回值**：返回的 `const char*` 由 SDK 内部持有，请在下次调用前使用或拷贝。
7. **生命周期**：销毁前先 `stopRealtimeASR()` 并 `removeListener`，再调用 `TXRealtimeASRDestroy`。
