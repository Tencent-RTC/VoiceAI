
本文档面向 Android 平台，介绍 VoiceAI ASR SDK 的接入方式与完整 API 说明

## 一、功能简介

`TXRealtimeASR` 是实时流式语音转写引擎：

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
| 最低系统版本 | Android 5.0 (API 21) 及以上 |
| ABI | armeabi-v7a / arm64-v8a / x86 / x86_64 |
| 语言 | Java 8+ |

### 2.3 引入 SDK

将 VoiceAI ASR SDK 引入项目工程，支持以下两种方式：

#### 方式一：Maven 引入（推荐）

在项目根目录的 `settings.gradle`（或 `build.gradle`）中确保已配置 Maven Central 仓库：

```gradle
repositories {
    mavenCentral()
}
```

在 `app/build.gradle` 中声明依赖（`x.x.x.x` 为具体版本号，比如 13.5.0.223，[SDK 插件地址](https://central.sonatype.com/artifact/com.tencent.liteav/LiteAVSDK_VoiceAI)）：

```gradle
dependencies {
    implementation 'com.tencent.liteav:LiteAVSDK_VoiceAI:x.x.x.x'
}
```

#### 方式二：本地 AAR 引入

1. [下载 SDK](https://dl.gmertc.com/voiceai/13.5.0/VoiceAI_Android_sdk_13.5.0.223.zip)，把 SDK 提供的 `LiteAVSDK_VoiceAI_x.x.x.x.aar`（或 `.so` + `jar`）放入 `app/libs`（`x.x.x.x` 为具体版本号）。
2. 在 `app/build.gradle` 中声明：

```gradle
dependencies {
    implementation fileTree(dir: 'libs', include: ['*.jar', '*.aar'])
}
```

#### ABI 架构过滤（两种方式通用，推荐）

SDK 的 AAR 内含多种架构的 `.so`，如不配置会默认将全部架构打进 APK，导致体积增大。建议在 `app/build.gradle` 中按需过滤：

```gradle
android {
    defaultConfig {
        // ...其他默认配置
        ndk {
            // 支持 armeabi-v7a 和 arm64-v8a 架构
            abiFilters "armeabi-v7a", "arm64-v8a"
        }
    }
}
```

> 说明：
> - 现代真机基本只保留 `arm64-v8a`；如需兼容 32 位设备，可同时保留 `armeabi-v7a`。
> - 若需在 x86 模拟器调试，可临时加入 `"x86_64"`。
> - 使用 App Bundle（`splits.abi`）按架构分包时，无需再配置 `ndk.abiFilters`。

API 入口类位于包 `com.tencent.voiceai`：

```java
import com.tencent.voiceai.TXRealtimeASR;
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

实时转写为在线服务，且必须采集麦克风音频，需要在 `AndroidManifest.xml` 中同时声明网络权限与麦克风录音权限：

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

> Android 6.0（API 23）及以上，`RECORD_AUDIO` 除清单声明外还须在运行时**动态申请**，用户授权后才能开始转写。

---

## 三、快速开始

```java
import com.tencent.voiceai.TXRealtimeASR;
import com.tencent.voiceai.TXRealtimeASR.*;

public class ASRDemo {
    private TXRealtimeASR mASR;

    public void startASR() {
        // 1. 创建实例
        mASR = new TXRealtimeASR();

        // 2. 设置监听器
        mASR.addListener(new Listener() {
            @Override
            public void onRealtimeASRStarted(String voiceId) {
                // 转录会话已开启
            }

            @Override
            public void onReceiveRealtimeASRMessage(Message message) {
                if (message.isCompleted) {
                    // 稳态结果：某句话已说完
                } else {
                    // 中间结果：正在识别中
                }
                // 可获取该消息在音频流中的时间区间（毫秒），-1 表示未知
                int start = message.startTime;
                int end = message.endTime;
            }

            @Override
            public void onRealtimeASRStopped() {
                // 转录正常停止
            }

            @Override
            public void onRealtimeASRError(int errorCode, String errorMsg) {
                // 转录出错
            }

            @Override
            public void onRealtimeASRVolume(int volume) {
                // 实时音量，volume 取值 [0, 100]
            }
        });

        // 3. 配置参数并启动
        Params params = new Params();
        params.sdkAppId = "1400000000";            // TRTC 应用 ID
        params.userSig = "your_user_sig";          // 服务端下发的用户签名
        params.voiceId = "uuid-xxxx-xxxx-xxxx";    // 识别会话 ID，建议每次调用重新生成 UUID
        params.sourceLanguage = "zh";              // 识别语言，"zh"/"en"，留空为自动识别
        params.enableCustomCapture = false;        // 是否启用自定义音频采集
        mASR.startRealtimeASR(params);

        // 4.（可选）自定义采集模式：开启后自行采集并送入 PCM 音频
        // byte[] pcm = ...; // 16k、单声道、16bit 采样
        // mASR.feedPcmData(pcm, 16000, 1);
    }

    public void release() {
        if (mASR != null) {
            mASR.stopRealtimeASR();
            mASR.destroy();
            mASR = null;
        }
    }
}
```

---

## 四、API 参考

### 4.1 类 `TXRealtimeASR`

#### 构造与销毁

| 方法 | 说明 |
|---|---|
| `TXRealtimeASR()` | 创建新实例，调用方通过 GC 管理生命周期。 |
| `void destroy()` | 销毁实例，释放原生资源。使用完毕务必调用。 |

#### 监听器管理

| 方法 | 说明 |
|---|---|
| `void addListener(Listener listener)` | 添加回调监听器（内部使用弱引用持有）。 |
| `void removeListener(Listener listener)` | 移除回调监听器。 |

#### 核心方法

| 方法 | 说明 |
|---|---|
| `void startRealtimeASR(Params params)` | 启动实时语音转写会话。 |
| `void stopRealtimeASR()` | 停止实时转写，等后端算完后回调 `onRealtimeASRStopped` 或 `onRealtimeASRError`。 |
| `void feedPcmData(byte[] data, int sampleRate, int channels)` | 自定义采集时送入 PCM 音频数据。`data`：16bit PCM 字节数据；`sampleRate`：采样率（Hz）；`channels`：通道数。 |
| `String callExperimentalAPI(String jsonStr)` | 实验性 API 调用，入参为 JSON 字符串，返回字符串。 |

### 4.2 监听器 `Listener`

> 接口中**所有方法均为 `default` 空实现**，按需重写感兴趣的方法即可，无需实现全部回调。后续新增的回调方法也会以 `default` 空实现形式添加，保证源码与二进制兼容。
>
> 回调在 SDK 内部线程触发，请勿在回调中执行耗时操作；更新 UI 需自行切回主线程。

| 回调 | 说明 |
|---|---|
| `void onRealtimeASRStarted(String voiceId)` | 实时转录开启成功的回调。`voiceId` 为识别会话 ID。 |
| `void onReceiveRealtimeASRMessage(Message message)` | 收到实时转录消息的回调，`message` 包含转录文本和完成状态。 |
| `void onRealtimeASRStopped()` | 实时转录停止的回调（正常停止）。 |
| `void onRealtimeASRError(int errorCode, String errorMsg)` | 实时转录出错的回调。`errorCode` 非 0 表示异常。 |
| `void onRealtimeASRVolume(int volume)` | 实时音量回调。`volume` 为音量等级，取值范围 `[0, 100]`。 |

### 4.3 参数与数据结构

#### `Params`（启动参数）

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `sdkAppId` | `String` | `null` | TRTC 应用 ID。 |
| `userSig` | `String` | `null` | TRTC 用户签名。 |
| `voiceId` | `String` | `null` | 识别会话 ID，建议每次调用时重新生成 UUID 传入。 |
| `sourceLanguage` | `String` | `null` | 识别语言，如 `"zh"`、`"en"`，留空即为自动识别。 |
| `enableCustomCapture` | `boolean` | `false` | 是否启用自定义音频采集；为 `true` 时须通过 `feedPcmData` 送入音频。 |

#### `Message`（转录消息）

| 字段 | 类型 | 说明 |
|---|---|---|
| `segmentId` | `String` | 消息段的唯一标识 ID。 |
| `sourceText` | `String` | 识别出的源语言文本（对应协议 `voice_text_str`）。 |
| `isCompleted` | `boolean` | 转录是否结束：`false`=进行中（中间结果），`true`=已说完（稳态结果）。 |
| `speakerId` | `int` | 说话人 ID，`-1` 表示未知。 |
| `startTime` | `int` | 本消息在整个音频流中的**起始时间**（毫秒），`-1` 表示未知。 |
| `endTime` | `int` | 本消息在整个音频流中的**结束时间**（毫秒），`-1` 表示未知。 |

---

## 五、实验性 API

`callExperimentalAPI(String jsonStr)` 用于调用实验性 / 扩展能力。入参为 JSON 字符串，返回字符串。格式：

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

```java
String json = "{\"api\":\"setExtraParams\",\"params\":{"
            + "\"extraRequestParams\":\"engine_model_type=bigmodel\","
            + "\"clientDenoiseStrategy\":1}}";
mASR.callExperimentalAPI(json);
```

---

## 六、最佳实践与注意事项

1. **鉴权信息**：`userSig` 应由服务端生成后下发，切勿将生成密钥硬编码到客户端。
2. **voiceId 唯一性**：每次调用 `startRealtimeASR` 建议重新生成新的 UUID 作为 `voiceId`。
3. **自定义采集**：`enableCustomCapture = true` 时须主动调用 `feedPcmData` 送入 16bit PCM 数据，并保证 `sampleRate`、`channels` 与数据一致。
4. **中间结果与稳态结果**：`isCompleted = false` 的文本会持续更新，仅在 `isCompleted = true` 时该段识别才最终确定。
5. **监听器弱引用**：监听器以弱引用持有，请自行保存监听器对象的强引用，避免被提前回收。
6. **生命周期**：使用完毕先 `stopRealtimeASR()` 再 `destroy()`，避免资源泄漏。
