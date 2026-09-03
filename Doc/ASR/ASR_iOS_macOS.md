本文档面向 iOS & macOS 平台，介绍 VoiceAI ASR SDK 的接入方式与完整 API 说明

## 一、功能简介

`TXRealtimeASR` 是实时流式语音转写引擎：

- **实时流式识别**：边说边出字，逐句返回识别结果。
- **中间结果与稳态结果**：通过 `isCompleted` 区分「进行中（中间结果）」与「已说完（稳态结果）」。
- **自动 / 指定识别语言**：支持 `"zh"`（中文）、`"en"`（英文）等，留空即为自动识别。
- **说话人区分**：结果携带 `speakerId`，`-1` 表示未知。
- **时间戳定位**：结果携带 `startTime` / `endTime`，标识该消息在整个音频流中的起止位置（毫秒），`-1` 表示未知。
- **实时音量回调**：通过 `onRealtimeASRVolume` 获取当前音量等级 `[0, 100]`，可用于绘制音量波动效果。
- **自定义音频采集**：可通过 `enableCustomCapture` 开启，自行采集 PCM 后调用 `feedPcmData` 送入引擎。
- **多监听器**：通过 `addListener:` / `removeListener:` 注册多个回调监听器。

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
| iOS 最低版本 | iOS 11.0 及以上 |
| macOS 最低版本 | macOS 10.13 及以上 |
| 架构 | arm64 / x86_64 |
| 语言 | Objective-C / Swift（通过桥接） |

### 2.3 引入 SDK

将 VoiceAI ASR SDK 引入项目工程，支持以下两种方式：

#### 方式一：CocoaPods 引入（推荐）

在 `Podfile` 中声明依赖，然后执行 `pod install`：

```ruby
platform :ios, '11.0'  # iOS
# platform :osx, '10.13'  # macOS

target 'YourApp' do
  # iOS
  pod 'TXLiteAVSDK_VoiceAI_iOS', '13.5.0.223'

  # macOS
  # pod 'TXLiteAVSDK_VoiceAI_Mac', '13.5.0.223'
end
```

#### 方式二：手动引入

1. 下载 SDK 压缩包：
   - iOS：[iOS SDK 下载](https://dl.gmertc.com/voiceai/13.5.0/VoiceAI_iOS_sdk_13.5.0.223.zip)
   - macOS： [Mac SDK 下载](https://dl.gmertc.com/voiceai/13.5.0/VoiceAI_Mac_sdk_13.5.0.223.zip)
2. 解压后将 `TXLiteAVSDK_VoiceAI_iOS.xcframework`（macOS 为 `TXLiteAVSDK_VoiceAI_Mac.xcframework`）拖入工程。
3. 在 `Target > General > Frameworks, Libraries, and Embedded Content` 中将 framework 设为 **Embed & Sign**。

#### 导入头文件

两种方式均需导入头文件：

```objc
#import "TXRealtimeASR.h"
```

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

### 2.5 权限声明

实时转写为在线服务，需要网络访问；同时**必须**在 `Info.plist` 中声明麦克风权限：

```xml
<key>NSMicrophoneUsageDescription</key>
<string>需要使用麦克风进行语音转写</string>
```


> 首次采集时系统会自动弹出授权弹窗；若用户拒绝授权，需引导其前往「设置」中开启。
>
> macOS 额外要求：若开启了 Hardened Runtime，请在 `Target > Signing & Capabilities` 中勾选 **Audio Input**，否则无法访问麦克风。

---

## 三、快速开始

```objc
#import "TXRealtimeASR.h"

@interface ASRDemo ()<TXRealtimeASRListener>
@property (nonatomic, strong) TXRealtimeASR *asr;
@end

@implementation ASRDemo

- (void)startASR {
    // 1. 创建实例
    self.asr = [[TXRealtimeASR alloc] init];

    // 2. 设置监听器
    [self.asr addListener:self];

    // 3. 配置参数并启动
    TXRealtimeASRParams *params = [[TXRealtimeASRParams alloc] init];
    params.sdkAppId = @"1400000000";          // TRTC 应用 ID
    params.userSig = @"your_user_sig";        // 服务端下发的用户签名
    params.voiceId = @"uuid-xxxx-xxxx-xxxx";  // 识别会话 ID，建议每次调用重新生成 UUID
    params.sourceLanguage = @"zh";            // 识别语言，"zh"/"en"，留空为自动识别
    params.enableCustomCapture = NO;          // 是否启用自定义音频采集
    [self.asr startRealtimeASR:params];

    // 4.（可选）自定义采集模式：开启后自行采集并送入 PCM 音频
    // NSData *pcm = ...; // 16k、单声道、16bit 采样
    // [self.asr feedPcmData:pcm sampleRate:16000 channels:1];
}

- (void)release {
    [self.asr stopRealtimeASR];
    [self.asr removeListener:self];
    self.asr = nil; // ARC 释放
}

#pragma mark - TXRealtimeASRListener

- (void)onRealtimeASRStarted:(NSString *)voiceId {
    // 转录会话已开启
}

- (void)onReceiveRealtimeASRMessage:(TXRealtimeASRMessage *)message {
    if (message.isCompleted) {
        // 稳态结果：某句话已说完
    } else {
        // 中间结果：正在识别中
    }
    // 可获取该消息在音频流中的时间区间（毫秒），-1 表示未知
    int start = message.startTime;
    int end = message.endTime;
}

- (void)onRealtimeASRStopped {
    // 转录正常停止
}

- (void)onRealtimeASRError:(int)errorCode errorMsg:(NSString *)errorMsg {
    // 转录出错，errorCode 非 0 表示异常
}

- (void)onRealtimeASRVolume:(int)volume {
    // 实时音量，volume 取值 [0, 100]
}

@end
```

---

## 四、API 参考

### 4.1 类 `TXRealtimeASR`

#### 创建

| 方法 | 说明 |
|---|---|
| `- (instancetype)init` | 创建新实例，调用方通过 ARC 管理生命周期。 |

#### 监听器管理

| 方法 | 说明 |
|---|---|
| `- (void)addListener:(id<TXRealtimeASRListener>)listener` | 添加回调监听器。 |
| `- (void)removeListener:(id<TXRealtimeASRListener>)listener` | 移除回调监听器。 |

#### 核心方法

| 方法 | 说明 |
|---|---|
| `- (void)startRealtimeASR:(TXRealtimeASRParams *)params` | 开始实时语音转写会话。 |
| `- (void)stopRealtimeASR` | 停止实时转录，等后端算完后回调 `onRealtimeASRStopped` 或 `onRealtimeASRError:errorMsg:`。 |
| `- (void)feedPcmData:(NSData *)data sampleRate:(uint32_t)sampleRate channels:(uint32_t)channels` | 自定义采集时送入 PCM 音频数据。`data`：16bit PCM 字节数据；`sampleRate`：采样率（Hz）；`channels`：通道数。 |
| `- (NSString *)callExperimentalAPI:(NSString *)jsonStr` | 实验性 API 调用，入参为 JSON 字符串，返回字符串。 |

### 4.2 监听器协议 `TXRealtimeASRListener`

> 所有方法均为 `@optional`，按需实现。所有回调均已切换到主线程，调用方无需关心线程安全。

| 回调 | 说明 |
|---|---|
| `- (void)onRealtimeASRStarted:(NSString *)voiceId` | 实时转录开启成功的回调。`voiceId` 为识别会话 ID。 |
| `- (void)onReceiveRealtimeASRMessage:(TXRealtimeASRMessage *)message` | 收到实时转录消息的回调，`message` 包含转录文本和完成状态。 |
| `- (void)onRealtimeASRStopped` | 实时转录停止的回调（正常停止）。 |
| `- (void)onRealtimeASRError:(int)errorCode errorMsg:(NSString *)errorMsg` | 实时转录出错的回调。`errorCode` 非 0 表示异常。 |
| `- (void)onRealtimeASRVolume:(int)volume` | 实时音量回调。`volume` 为音量等级，取值范围 `[0, 100]`。 |

### 4.3 参数与数据结构

#### `TXRealtimeASRParams`（启动参数）

| 属性 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `sdkAppId` | `NSString *` | `nil` | TRTC 应用 ID。 |
| `userSig` | `NSString *` | `nil` | TRTC 用户签名。 |
| `voiceId` | `NSString *` | `nil` | 识别会话 ID，建议每次调用时重新生成 UUID 传入。 |
| `sourceLanguage` | `NSString *` | `nil` | 识别语言，如 `"zh"`、`"en"`，留空即为自动识别。 |
| `enableCustomCapture` | `BOOL` | `NO` | 是否启用自定义音频采集；为 `YES` 时须通过 `feedPcmData` 送入音频。 |

#### `TXRealtimeASRMessage`（转录消息）

| 属性 | 类型 | 说明 |
|---|---|---|
| `segmentId` | `NSString *` | 消息段的唯一标识 ID。 |
| `sourceText` | `NSString *` | 识别出的源语言文本（对应协议 `voice_text_str`）。 |
| `isCompleted` | `BOOL` | 转录是否结束：`NO`=进行中（中间结果），`YES`=已说完（稳态结果）。 |
| `speakerId` | `int` | 说话人 ID，`-1` 表示未知。 |
| `startTime` | `int` | 本消息在整个音频流中的**起始时间**（毫秒），`-1` 表示未知。 |
| `endTime` | `int` | 本消息在整个音频流中的**结束时间**（毫秒），`-1` 表示未知。 |

---

## 五、实验性 API

`callExperimentalAPI:` 用于调用实验性 / 扩展能力。入参为 JSON 字符串，返回字符串。格式：

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

```objc
NSString *json = @"{\"api\":\"setExtraParams\",\"params\":{"
                  @"\"extraRequestParams\":\"engine_model_type=bigmodel\","
                  @"\"clientDenoiseStrategy\":1}}";
[self.asr callExperimentalAPI:json];
```

---

## 六、最佳实践与注意事项

1. **鉴权信息**：`userSig` 应由服务端生成后下发，切勿将生成密钥硬编码到客户端。
2. **voiceId 唯一性**：每次调用 `startRealtimeASR:` 建议重新生成新的 UUID 作为 `voiceId`。
3. **自定义采集**：`enableCustomCapture = YES` 时须主动调用 `feedPcmData:sampleRate:channels:` 送入 16bit PCM 数据，并保证 `sampleRate`、`channels` 与数据一致。
4. **中间结果与稳态结果**：`isCompleted = NO` 的文本会持续更新，仅在 `isCompleted = YES` 时该段识别才最终确定。
5. **监听器生命周期**：监听器被 `removeListener:` 移除前请保持其引用有效。
6. **生命周期**：使用完毕先 `stopRealtimeASR`，再 `removeListener:` 并释放对象（ARC）。
