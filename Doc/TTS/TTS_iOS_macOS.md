# 实时语音合成（TTS）接入文档 —— iOS / macOS (Objective-C)

> 模块：`TXRealtimeTTS @ TXLiteAVSDK`
>
> 本文档面向 iOS / macOS 平台，介绍腾讯实时语音合成（Text-To-Speech，TTS）SDK 的接入方式与完整 API 说明。

---

## 一、功能简介

`TXRealtimeTTS` 是实时流式语音合成引擎：

- **在线合成**：`TXRealtimeTTSModeOnline`，通过云端引擎合成，音色丰富、免本地资源，需联网并进行 UserSig 鉴权。
- **离线合成**：`TXRealtimeTTSModeOffline`，使用本地引擎，无需联网即可合成。
- **流式文本输入**：通过 `appendText` 边输入边合成边播放。
- **灵活的音频输出**：仅播放、仅回调 PCM 音频帧、播放并回调。
- **实时控制**：停止 / 清空、音量与语速调节。（暂停 / 恢复暂未支持）

> 注意：`TXRealtimeTTSMode` 已支持 `Online`（在线）与 `Offline`（离线）；`Mix`（混合）仍为预留，暂未开放。

---

## 二、接入准备

### 2.1 环境要求

| 项目 | 要求 |
|---|---|
| iOS 最低版本 | iOS 11.0 及以上 |
| macOS 最低版本 | macOS 10.13 及以上 |
| 架构 | arm64 / x86_64 |
| 语言 | Objective-C / Swift（通过桥接） |

### 2.2 引入 SDK

将 VoiceAI TTS SDK 引入项目工程，支持以下两种方式：

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
#import "TXRealtimeTTS.h"
```

### 2.3 鉴权信息

不同引擎模式使用不同的鉴权方式，请按所选 `mode` 准备对应的鉴权信息。

#### 2.3.1 离线 TTS 鉴权

离线模式（`Offline`）使用 License 鉴权，通过 `TXRealtimeTTSParams.offline` 传入：

- `offline.licenseUrl`：离线授权文件下载 / 校验地址。
- `offline.licenseKey`：离线授权 key。

`licenseUrl` 与 `licenseKey` 由控制台申请获得，须在 `start` 之前填入 `TXRealtimeTTSParams.offline`。

**内置 License 文件【可选】**

离线鉴权除在初始化时传入正式的 License URL 与 License Key 外，还可以将 License 文件内置到宿主App 中，SDK内部优先使用离线 License 文件，如果离线 License 文件不存在或者无效，则从License URL 与 License Key里面获取鉴权信息。

将下载得到的 License 文件重命名为 `TXLiveSDK.licence`（注意后缀是 `.licence`，不是 `.license`，文件内容无需修改），添加到宿主 App Target，并确认：

1. 勾选宿主 App 的 Target Membership。
2. 文件已加入 `Build Phases → Copy Bundle Resources`。
3. 最终文件位于 `YourApp.app/TXLiveSDK.licence`。

注意：

- 不要只放入 VoiceAI Framework 或独立资源 Bundle。
- 文件名区分大小写，请固定使用 `TXLiveSDK.licence`。
- 当前 SDK 不会自动将 License 文件打入 Framework，需由宿主 App 自行配置。
- 即使已内置 License 文件，初始化时仍需传入正式的 License URL 与对应的 License Key。

#### 2.3.2 在线 TTS 鉴权

在线模式（`Online`）使用 UserSig 鉴权，通过 `TXRealtimeTTSParams.online` 传入：

- `online.appId`：应用的 SDKAppID。
- `online.userId`：用户 id。
- `online.userSig`：由 `appId` / `userId` 等信息加密生成的签名。

UserSig 是用户身份的加密凭证。**请勿在客户端硬编码密钥（SECRETKEY）计算 UserSig**，密钥一旦泄露会导致云资源被盗用；生产环境应在业务服务端计算 UserSig，App 通过接口动态获取。UserSig 无效（过期 / 解析失败 / 校验失败）时 `start` 或 `onCompleted` 会返回 `TXRealtimeTTSErrorOnlineInvalidUserSig`。

> UserSig 的定义与客户端 / 服务端计算方法参见：<https://cloud.tencent.com/document/product/647/117218#.E5.AE.A2.E6.88.B7.E7.AB.AF.E8.AE.A1.E7.AE.97-usersig>

### 2.4 权限声明

在线模式需要网络访问，请确保应用具备网络访问能力。离线模式无需网络访问，请确保应用具备对离线资源目录的读权限。

---

## 三、快速开始

```objc
#import "TXRealtimeTTS.h"

@interface TTSDemo ()<TXRealtimeTTSListener>
@property (nonatomic, strong) TXRealtimeTTS *tts;
@end

@implementation TTSDemo

- (void)startTTS {
    // 1. 创建实例
    self.tts = [[TXRealtimeTTS alloc] init];

    // 2. 设置监听器
    [self.tts setListener:self];

    // 3. 配置参数并启动
    // —— 在线模式 ——
    TXRealtimeTTSParams *params = [[TXRealtimeTTSParams alloc] init];
    params.mode = TXRealtimeTTSModeOnline;
    params.online = [[TXRealtimeTTSOnlineCredential alloc] init];
    params.online.appId = @"your_app_id";
    params.online.userId = @"your_user_id";
    params.online.userSig = @"your_user_sig";
    params.voiceName = @"your_voice_name";
    params.audioOutputMode = TXRealtimeTTSAudioOutputModePlaybackOnly;
    [self.tts start:params];

    // —— 离线模式 ——
    // 离线模式须在 start 之前配置离线资源包（path 指向具体的 zip 文件）
    // NSString *commonJson = @"{\"api\":\"setOfflineCommonResourcePath\","
    //                         @"\"params\":{\"path\":\"/var/mobile/tts/tts_resource/common_resources_x.x.x.zip\"}}";
    // [self.tts callExperimentalAPI:commonJson];
    // NSString *voiceJson = @"{\"api\":\"setOfflineVoiceResourcePath\","
    //                        @"\"params\":{\"path\":\"/var/mobile/tts/tts_resource/voice/voice_xxxxxx.zip\"}}";
    // [self.tts callExperimentalAPI:voiceJson];
    // TXRealtimeTTSParams *params = [[TXRealtimeTTSParams alloc] init];
    // params.mode = TXRealtimeTTSModeOffline;
    // params.offline = [[TXRealtimeTTSOfflineLicense alloc] init];
    // params.offline.licenseUrl = @"your_license_url";
    // params.offline.licenseKey = @"your_license_key";
    // params.voiceName = @"your_voice_name";
    // params.audioOutputMode = TXRealtimeTTSAudioOutputModePlaybackOnly;
    // [self.tts start:params];

    // 4. 流式追加文本
    [self.tts appendText:@"text_1" text:@"你好，" isEnd:NO];
    [self.tts appendText:@"text_2" text:@"欢迎使用实时语音合成。" isEnd:NO];

    // 5. 或者一次性输入文本
    [self.tts appendText:@"text_1" text:@"你好，欢迎使用实时语音合成。" isEnd:YES];
}

- (void)release {
    [self.tts stop];
    self.tts = nil; // ARC 释放
}

#pragma mark - TXRealtimeTTSListener

- (void)onStarted {
    // 会话已建立
}

- (void)onPlaybackProgress:(NSString *)textId textSlice:(NSString *)textSlice {
    // 逐片播放进度
}

- (void)onSynthesizedAudioFrame:(TXSynthesizeAudioFrame *)audioFrame {
    // 仅 CallbackOnly / PlaybackAndCallback 模式触发
}

- (void)onCompleted:(int32_t)code msg:(NSString *)msg {
    if (code == TXRealtimeTTSErrorOk) {
        // 成功完成
    } else {
        // 失败
    }
}

@end
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

#### 创建

| 方法 | 说明 |
|---|---|
| `- (instancetype)init` | 创建新实例，调用方通过 ARC 管理生命周期。 |

#### 核心方法

除`callExperimentalAPI` 外均返回 `int`，取值见 [`TXRealtimeTTSError`](#45-错误码-txrealtimettserror)。

| 方法 | 说明 |
|---|---|
| `- (int)setListener:(nullable id<TXRealtimeTTSListener>)listener` | 设置事件回调监听器；传 `nil` 取消监听。 |
| `- (int)start:(TXRealtimeTTSParams *)param` | 启动合成与播放。 |
| `- (int)stop` | 停止合成与播放。 |
| `- (int)pause` | 暂停合成与播放。**（暂未支持）** |
| `- (int)resume` | 恢复合成与播放。**（暂未支持）** |
| `- (int)clear` | 清空待播放与待合成文本；调用后立即丢弃所有排队文本，且不再触发 clear 前 `textId` 的进度回调。 |
| `- (int)appendText:(NSString *)textId text:(NSString *)text isEnd:(BOOL)isEnd` | 流式追加待合成文本。`textId`：文本标识（UTF-8）；`text`：文本内容（UTF-8，必填）；`isEnd`：是否为本段最后一片。 |
| `- (int)setVolume:(float)volume` | 设置音量，取值 `[0, 200]`，默认 `100` = 正常。 |
| `- (int)setSpeed:(float)speed` | 设置语速，取值 `[1, 3]`，默认 `1` = 正常。 |
| `- (NSString *)callExperimentalAPI:(NSString *)jsonParams` | 实验性 API 调用，入参为 JSON 字符串，返回 JSON 字符串。见[第五章](#五实验性-api)。 |

### 4.2 监听器协议 `TXRealtimeTTSListener`

> 所有方法均为 `@optional`，按需实现。回调在 SDK 内部线程触发，请勿在回调中执行耗时操作；更新 UI 需自行切主线程。

| 回调 | 说明 |
|---|---|
| `- (void)onStarted` | 合成开始（鉴权通过 / 会话已建立）。 |
| `- (void)onPlaybackProgress:(NSString *)textId textSlice:(NSString *)textSlice` | 文本播放进度，逐片流式回调。 |
| `- (void)onSynthesizedAudioFrame:(TXSynthesizeAudioFrame *)audioFrame` | 合成音频帧回调。仅当 `audioOutputMode` 为 `CallbackOnly` 或 `PlaybackAndCallback` 时触发。 |
| `- (void)onCompleted:(int32_t)code msg:(nullable NSString *)msg` | 合成结束（成功或失败）。`code == Ok` 表示成功；否则表示任意阶段失败。`msg` 可为 `nil`。 |

### 4.3 参数与数据结构

#### `TXRealtimeTTSParams`

| 属性 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `mode` | `TXRealtimeTTSMode` | `TXRealtimeTTSModeOnline` | 引擎模式，支持 `Online` / `Offline`。 |
| `online` | `TXRealtimeTTSOnlineCredential *` | `nil` | `mode=Online` 必填。 |
| `offline` | `TXRealtimeTTSOfflineLicense *` | `nil` | `mode=Offline` 必填。 |
| `voiceName` | `NSString *` | `nil` | 音色名，**必填**。 |
| `audioOutputMode` | `TXRealtimeTTSAudioOutputMode` | `PlaybackOnly` | 音频输出模式。 |

#### `TXRealtimeTTSOnlineCredential`（在线鉴权，`mode=Online` 必填）

| 属性 | 类型 | 说明 |
|---|---|---|
| `appId` | `NSString *` | 应用 appid。 |
| `userId` | `NSString *` | 用户 id。 |
| `userSig` | `NSString *` | 用户签名（服务端下发，避免密钥打包进客户端）。 |

#### `TXRealtimeTTSOfflineLicense`（离线鉴权）

| 属性 | 类型 | 说明 |
|---|---|---|
| `licenseUrl` | `NSString *` | 离线授权文件下载 / 校验地址。 |
| `licenseKey` | `NSString *` | 离线授权 key。 |

#### `TXSynthesizeAudioFrame`（音频输出帧）

| 属性 | 类型 | 说明 |
|---|---|---|
| `pcmData` | `NSData *` | 本次音频 PCM 字节；对象仅在回调内有效，跨线程 / 异步使用须深拷贝（如 `copy`）。 |
| `pcmDataSize` | `uint32_t` | `pcmData` 字节数。 |
| `sampleRate` | `uint32_t` | 采样率（Hz），如 16000/48000。 |
| `channelCount` | `uint32_t` | 通道数，1=单声道，2=双声道。 |
| `bitWidth` | `uint32_t` | 采样位宽（bit），固定 16。 |
| `textId` | `NSString *` | 文本标识（对应 `appendText` 的 `textId`）。 |
| `textSlice` | `NSString *` | 当前合成数据所属的文本片段。 |
| `progress` | `double` | `textSlice` 在 `textId` 文本段中的百分比 `[0.0, 1.0]`。 |

### 4.4 枚举

#### `TXRealtimeTTSMode`（引擎模式）

| 值 | 说明 |
|---|---|
| `TXRealtimeTTSModeOnline` | 纯在线（云端引擎，需联网与 UserSig 鉴权）。 |
| `TXRealtimeTTSModeOffline` | 纯离线（本地引擎，无网可用）。 |
| `TXRealtimeTTSModeMix` | 预留，暂未开放。 |

#### `TXRealtimeTTSAudioOutputMode`（音频输出模式）

| 值 | 说明 |
|---|---|
| `TXRealtimeTTSAudioOutputModePlaybackOnly` | 仅播放音频，不回调音频帧（默认）。 |
| `TXRealtimeTTSAudioOutputModeCallbackOnly` | 仅回调音频帧，不播放。 |
| `TXRealtimeTTSAudioOutputModePlaybackAndCallback` | 既播放又回调音频帧。 |

### 4.5 错误码 `TXRealtimeTTSError`

| 常量 | 值 | 说明 |
|---|---|---|
| `TXRealtimeTTSErrorOk` | 0 | 成功。 |
| `TXRealtimeTTSErrorInvalidState` | 1 | 状态不允许（如已处于 Started 状态时再次调用 Start）。 |
| `TXRealtimeTTSErrorInvalidParam` | 2 | 配置字段非法。 |
| `TXRealtimeTTSErrorNotImplement` | 3 | 功能尚未实现（如 pause/resume/在线模式等）。 |
| `TXRealtimeTTSErrorOfflineUnknownError` | 100 | 未识别的离线引擎状态（防御性兜底）。 |
| `TXRealtimeTTSErrorOfflineInvalidState` | 101 | 离线引擎内部状态非法。 |
| `TXRealtimeTTSErrorOfflineResourceError` | 102 | 通用/音色资源加载失败。 |
| `TXRealtimeTTSErrorOfflineModelError` | 103 | 模型加载或推理失败。 |
| `TXRealtimeTTSErrorOfflineLicenseInvalid` | 104 | 授权文件无效。 |
| `TXRealtimeTTSErrorOfflineLicenseExpired` | 105 | 授权文件已过期。 |
| `TXRealtimeTTSErrorOnlineUnknownError` | 200 | 未识别的在线引擎状态（防御性兜底）。 |
| `TXRealtimeTTSErrorOnlineNetworkError` | 201 | 网络错误。 |
| `TXRealtimeTTSErrorOnlineServerError` | 202 | 服务端错误。 |
| `TXRealtimeTTSErrorOnlineInvalidUserSig` | 203 | UserSig 无效（过期/解析失败/校验失败等）。 |

> 说明：错误码分段规划，基础错误码位于 `[1, 100)`，离线 TTS 错误码位于 `[100, 200)`，在线 TTS 错误码位于 `[200, 300)`。`onCompleted` 的 `code` 使用 `TXRealtimeTTSError` 中的整型值。

---

## 六、实验性 API

`callExperimentalAPI:` 用于设置离线资源包路径等可选项。**离线模式下 `setOfflineCommonResourcePath` 与 `setOfflineVoiceResourcePath` 为必需**，须在 `start` 之前调用；`path` 须指向具体的资源 zip 文件（而非解压后的目录），SDK 内部会自行解压。在线模式无需配置离线资源包。入参为 JSON 字符串，格式：

```json
{ "api": "<接口名>", "params": { ... } }
```

支持的接口：

| api | params | 说明 |
|---|---|---|
| `setOfflineCommonResourcePath` | `{"path": "/path/to/common_resources_x.x.x.zip"}` | 设置离线通用资源包路径，`path` 须指向具体的 zip 文件（非目录）。 |
| `setOfflineVoiceResourcePath` | `{"path": "/path/to/voice_xxxxxx.zip"}` | 设置离线音色资源包路径，`path` 须指向具体的 zip 文件（非目录）。 |

示例：

```objc
NSString *json = @"{\"api\":\"setOfflineCommonResourcePath\","
                  @"\"params\":{\"path\":\"/var/mobile/tts/tts_resource/common_resources_x.x.x.zip\"}}";
[self.tts callExperimentalAPI:json];
```

---

## 七、最佳实践与注意事项

1. **模式选择**：在线用 `TXRealtimeTTSModeOnline`（需联网 + UserSig 鉴权），离线用 `TXRealtimeTTSModeOffline`（本地资源，无网可用）；`Mix` 暂未开放。
2. **音色必填**：`voiceName` 为 `nil` 会导致 `start` 返回 `TXRealtimeTTSErrorInvalidParam`。
3. **在线鉴权必填**：`Online` 模式须填 `online.appId` / `online.userId` / `online.userSig`；UserSig 无效会返回 `TXRealtimeTTSErrorOnlineInvalidUserSig`。
4. **离线鉴权必填**：`Offline` 模式须填 `offline.licenseUrl` 与 `offline.licenseKey`。
5. **离线资源包必配**：`Offline` 模式须在 `start` 之前通过实验性 API 配置好通用资源包与音色资源包的 zip 文件路径（`path` 指向具体的 zip 文件，非解压后的目录）；在线模式无需配置。
6. **流式结束标记**：一段文本输入完成后，最后一片须置 `isEnd:YES`。
7. **音频帧深拷贝**：`onSynthesizedAudioFrame:` 中的 `pcmData` 仅在回调内有效，如需异步处理请立即 `copy`。
8. **生命周期**：使用完毕先 `stop`，再释放对象（ARC）。
