# 实时语音合成（TTS）接入文档 —— Android (Java)

> 模块：`TXRealtimeTTS @ TXLiteAVSDK`
>
> 本文档面向 Android 平台，介绍腾讯实时语音合成（Text-To-Speech，TTS）SDK 的接入方式与完整 API 说明。

---

## 一、功能简介

`TXRealtimeTTS` 是实时流式语音合成引擎：

- **在线合成**：`ONLINE`，通过云端引擎合成，音色丰富、免本地资源，需联网并进行 UserSig 鉴权。
- **离线合成**：`OFFLINE`，使用本地引擎，无需联网即可合成。
- **流式文本输入**：通过 `appendText` 边输入边合成边播放。
- **灵活的音频输出**：仅播放、仅回调 PCM 音频帧、播放并回调。
- **实时控制**：停止 / 清空、音量与语速调节。（暂停 / 恢复暂未支持）

> 注意：`TXRealtimeTTSMode` 已支持 `ONLINE`（在线）与 `OFFLINE`（离线）；`MIX`（混合）仍为预留，暂未开放。

---

## 二、接入准备

### 2.1 环境要求

| 项目 | 要求 |
|---|---|
| 最低系统版本 | Android 5.0 (API 21) 及以上 |
| ABI | armeabi-v7a / arm64-v8a / x86 / x86_64 |
| 语言 | Java 8+ |

### 2.2 引入 SDK

将 VoiceAI TTS SDK 引入项目工程，支持以下两种方式：

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

1. [下载 SDK](https://dl.gmertc.com/voiceai/13.6.0/VoiceAI_Android_sdk_13.6.0.237.zip)，把 SDK 提供的 `LiteAVSDK_VoiceAI_x.x.x.x.aar`（或 `.so` + `jar`）放入 `app/libs`（`x.x.x.x` 为具体版本号）。
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
import com.tencent.voiceai.TXRealtimeTTS;
```

### 2.3 鉴权信息

不同引擎模式使用不同的鉴权方式，请按所选 `mode` 准备对应的鉴权信息。

#### 2.3.1 离线 TTS 鉴权

离线模式（`OFFLINE`）使用 License 鉴权，通过 `TXRealtimeTTSParams.offline` 传入：

- `offline.licenseUrl`：离线授权文件下载 / 校验地址。
- `offline.licenseKey`：离线授权 key。

`licenseUrl` 与 `licenseKey` 由控制台申请获得，须在 `start` 之前填入 `TXRealtimeTTSParams.offline`。

**内置 License 文件【可选】**

离线鉴权除在初始化时传入正式的 License URL 与 License Key 外，还可以将 License 文件内置到宿主App 中，SDK内部优先使用离线 License 文件，如果离线 License 文件不存在或者无效，则从License URL 与 License Key里面获取鉴权信息。

将下载得到的 License 文件重命名为 `TXLiveSDK.licence`（注意后缀是 `.licence`，不是 `.license`，文件内容无需修改），放到宿主 App 的以下目录：

```text
app/src/main/assets/TXLiveSDK.licence
```

构建后，文件在 APK/AAB 中的位置应为：

```text
assets/TXLiveSDK.licence
```

注意：

- 不要放入 `res/raw` 或 assets 子目录。
- 文件名区分大小写，请固定使用 `TXLiveSDK.licence`。
- 当前 SDK 不会自动将 License 文件打入 AAR，需由宿主 App 自行配置。
- 即使已内置 License 文件，初始化时仍需传入正式的 License URL 与对应的 License Key。

#### 2.3.2 在线 TTS 鉴权

在线模式（`ONLINE`）使用 UserSig 鉴权，通过 `TXRealtimeTTSParams.online` 传入：

- `online.appId`：应用的 SDKAppID。
- `online.userId`：用户 id。
- `online.userSig`：由 `appId` / `userId` 等信息加密生成的签名。

UserSig 是用户身份的加密凭证。**请勿在客户端硬编码密钥（SECRETKEY）计算 UserSig**，密钥一旦泄露会导致云资源被盗用；生产环境应在业务服务端计算 UserSig，App 通过接口动态获取。UserSig 无效（过期 / 解析失败 / 校验失败）时 `start` 或 `onCompleted` 会返回 `ONLINE_INVALID_USER_SIG`。

> UserSig 的定义与客户端 / 服务端计算方法参见：<https://cloud.tencent.com/document/product/647/117218#.E5.AE.A2.E6.88.B7.E7.AB.AF.E8.AE.A1.E7.AE.97-usersig>

### 2.4 权限声明

在线模式需要网络访问，请在 `AndroidManifest.xml` 中声明 `android.permission.INTERNET`。离线模式无需网络权限；如需读取外部存储中的离线资源，请按需申请存储读权限。

---

## 三、快速开始

```java
import com.tencent.voiceai.TXRealtimeTTS;
import com.tencent.voiceai.TXRealtimeTTS.*;

public class TTSDemo {
    private TXRealtimeTTS mTTS;

    public void startTTS() {
        // 1. 创建实例
        mTTS = new TXRealtimeTTS();

        // 2. 设置监听器
        mTTS.setListener(new TXRealtimeTTSListener() {
            @Override
            public void onStarted() {
                // 会话已建立，可切换 UI 状态
            }

            @Override
            public void onPlaybackProgress(String textId, String textSlice) {
                //逐片播放进度
            }

            @Override
            public void onSynthesizedAudioFrame(TXSynthesizeAudioFrame audioFrame) {
                //仅 CALLBACK_ONLY / PLAYBACK_AND_CALLBACK 模式触发
            }

            @Override
            public void onCompleted(int code, String msg) {
                if (code == TXRealtimeTTSError.OK) {
                    // 成功完成
                } else {
                    // 失败：鉴权 / 弱网 / 内部错误
                }
            }
        });

        // 3. 配置参数并启动
        // —— 在线模式 ——
        TXRealtimeTTSParams params = new TXRealtimeTTSParams();
        params.mode = TXRealtimeTTSMode.ONLINE;
        params.online.appId = "your_app_id";
        params.online.userId = "your_user_id";
        params.online.userSig = "your_user_sig";
        params.voiceName = "your_voice_name";
        params.audioOutputMode = TXRealtimeTTSAudioOutputMode.PLAYBACK_ONLY;
        mTTS.start(params);

        // —— 离线模式 ——
        // 离线模式须在 start 之前配置离线资源包（path 指向具体的 zip 文件）
        // String commonJson = "{\"api\":\"setOfflineCommonResourcePath\","
        //         + "\"params\":{\"path\":\"/storage/emulated/0/Android/data/com.tencent.voiceai.demo.android/files/tts_resource/common_resources_x.x.x.zip\"}}";
        // mTTS.callExperimentalAPI(commonJson);
        // String voiceJson = "{\"api\":\"setOfflineVoiceResourcePath\","
        //         + "\"params\":{\"path\":\"/storage/emulated/0/Android/data/com.tencent.voiceai.demo.android/files/tts_resource/voice/voice_xxxxxx.zip\"}}";
        // mTTS.callExperimentalAPI(voiceJson);
        // TXRealtimeTTSParams params = new TXRealtimeTTSParams();
        // params.mode = TXRealtimeTTSMode.OFFLINE;
        // params.offline.licenseUrl = "your_license_url";
        // params.offline.licenseKey = "your_license_key";
        // params.voiceName = "your_voice_name";
        // params.audioOutputMode = TXRealtimeTTSAudioOutputMode.PLAYBACK_ONLY;
        // mTTS.start(params);

        // 4. 流式追加文本
        mTTS.appendText("text_1", "你好，", false);
        mTTS.appendText("text_2", "欢迎使用实时语音合成。", false);

        // 5. 或者一次性输入文本
        mTTS.appendText("text_1", "你好，欢迎使用实时语音合成。", true);
    }

    public void release() {
        if (mTTS != null) {
            mTTS.stop();
            mTTS.destroy();
            mTTS = null;
        }
    }
}
```

---

## 四、在线音色支持

在线模式（`mode = ONLINE`）使用云端引擎合成，音色由服务端提供，支持中文、英文、日语、粤语四种语言，覆盖角色配音、有声书、客服、播报等多种场景。当前推荐模型为 `flow_02_turbo`。

完整的精品音色清单（含音色 ID、性别、语气、推荐场景）以腾讯云官方文档为准：

<https://cloud.tencent.com/document/product/647/131300#a0e5ec7a-cdc4-43d1-b63e-bbb9e8a0597c>

使用时，请将目标音色的**音色 ID** 填入 `TXRealtimeTTSParams.voiceName`，例如中文音色 `v-male-W1tH9jVc`（自然男声）、客服音色 `female-kefu-xiaomei`（客服小美）等。

说明：

- 在线模式的 `voiceName` 取值与官方音色清单中的「音色 ID」保持一致，请以文档最新清单为准。
- 在线音色与离线模式使用的本地音色包相互独立，二者 ID 体系不同。

## 五、API 参考

### 4.1 类 `TXRealtimeTTS`

#### 构造与销毁

| 方法 | 说明 |
|---|---|
| `TXRealtimeTTS()` | 创建新实例（可多次创建，每个实例独立运行）。 |
| `void destroy()` | 销毁实例，释放原生资源。使用完毕务必调用。 |

#### 核心方法

所有方法返回 `int`，取值见 [`TXRealtimeTTSError`](#53-错误码-txrealtimettserror)。

| 方法 | 说明 |
|---|---|
| `int setListener(TXRealtimeTTSListener listener)` | 设置事件回调监听器；传 `null` 取消监听。 |
| `int start(TXRealtimeTTSParams param)` | 启动合成与播放。 |
| `int stop()` | 停止合成与播放。 |
| `int pause()` | 暂停合成与播放。**（暂未支持）** |
| `int resume()` | 恢复合成与播放。**（暂未支持）** |
| `int clear()` | 清空待播放与待合成文本；调用后立即丢弃所有排队文本，且不再触发 clear 前`textId` 的进度回调。 |
| `int appendText(String textId, String text, boolean isEnd)` | 流式追加待合成文本。`textId`：文本标识（UTF-8）；`text`：文本内容（UTF-8，必填）；`isEnd`：是否为本段最后一片。 |
| `int setVolume(float volume)` | 设置音量，取值 `[0, 200]`，默认 `100` = 正常。 |
| `int setSpeed(float speed)` | 设置语速，取值 `[1, 3]`，默认 `1` = 正常。 |
| `String callExperimentalAPI(String jsonParams)` | 实验性 API 调用，入参为 JSON 字符串，返回 JSON 字符串。见[第五章](#五实验性-api)。 |

### 4.2 监听器 `TXRealtimeTTSListener`

> 回调在 SDK 内部线程触发，请勿在回调中执行耗时操作；更新 UI 需自行切主线程。

| 回调 | 说明 |
|---|---|
| `void onStarted()` | 合成开始（鉴权通过 / 会话已建立）。 |
| `void onPlaybackProgress(String textId, String textSlice)` | 文本播放进度，逐片流式回调。 |
| `void onSynthesizedAudioFrame(TXSynthesizeAudioFrame audioFrame)` | 合成音频帧回调。仅当 `audioOutputMode` 为 `CALLBACK_ONLY` 或 `PLAYBACK_AND_CALLBACK` 时触发。 |
| `void onCompleted(int code, String msg)` | 合成结束（成功或失败）。`code == OK` 表示成功；否则表示任意阶段失败。`msg` 可为 `null`。 |

### 4.3 参数与数据结构

#### `TXRealtimeTTSParams`

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `mode` | `TXRealtimeTTSMode` | `ONLINE` | 引擎模式，支持 `ONLINE` / `OFFLINE`。 |
| `online` | `TXRealtimeTTSOnlineCredential` | 空对象 | `mode=ONLINE` 必填。 |
| `offline` | `TXRealtimeTTSOfflineLicense` | 空对象 | `mode=OFFLINE`必填。 |
| `voiceName` | `String` | `null` | 音色名，**必填**。 |
| `audioOutputMode` | `TXRealtimeTTSAudioOutputMode` | `PLAYBACK_ONLY` | 音频输出模式。 |

#### `TXRealtimeTTSOnlineCredential`（在线鉴权，`mode=ONLINE` 必填）

| 字段 | 类型 | 说明 |
|---|---|---|
| `appId` | `String` | 应用 appid。 |
| `userId` | `String` | 用户 id。 |
| `userSig` | `String` | 用户签名（服务端下发，避免密钥打包进客户端）。 |

#### `TXRealtimeTTSOfflineLicense`（离线鉴权）

| 字段 | 类型 | 说明 |
|---|---|---|
| `licenseUrl` | `String` | 离线授权文件下载 / 校验地址。 |
| `licenseKey` | `String` | 离线授权 key。 |

#### `TXSynthesizeAudioFrame`（音频输出帧）

| 字段 | 类型 | 说明 |
|---|---|---|
| `pcmData` | `byte[]` | 本次音频 PCM 字节；数组仅在回调内有效，跨线程 / 异步使用须深拷贝（如 `Arrays.copyOf`）。 |
| `pcmDataSize` | `int` | `pcmData` 字节数。 |
| `sampleRate` | `int` | 采样率（Hz），如 16000/48000。 |
| `channelCount` | `int` | 通道数，1=单声道，2=双声道。 |
| `bitWidth` | `int` | 采样位宽（bit），固定 16。 |
| `textId` | `String` | 文本标识（对应 `appendText` 的 `textId`）。 |
| `textSlice` | `String` | 当前合成数据所属的文本片段。 |
| `progress` | `double` | `textSlice` 在 `textId` 文本段中的百分比 `[0.0, 1.0]`。 |

### 4.4 枚举

#### `TXRealtimeTTSMode`（引擎模式）

| 值 | 说明 |
|---|---|
| `ONLINE` | 纯在线（云端引擎，需联网与 UserSig 鉴权）。 |
| `OFFLINE` | 纯离线（本地引擎，无网可用）。 |
| `MIX` | 预留，暂未开放。 |

#### `TXRealtimeTTSAudioOutputMode`（音频输出模式）

| 值 | 说明 |
|---|---|
| `PLAYBACK_ONLY` | 仅播放音频，不回调音频帧（默认）。 |
| `CALLBACK_ONLY` | 仅回调音频帧，不播放。 |
| `PLAYBACK_AND_CALLBACK` | 既播放又回调音频帧。 |

### 4.5 错误码 `TXRealtimeTTSError`

| 常量 | 值 | 说明 |
|---|---|---|
| `OK` | 0 | 成功。 |
| `INVALID_STATE` | 1 | 状态不允许（如已处于 started 状态时再次调用 start）。 |
| `INVALID_PARAM` | 2 | 配置字段非法。 |
| `NOT_IMPLEMENT` | 3 | 功能尚未实现（如 pause/resume/在线模式等）。 |
| `OFFLINE_UNKNOWN_ERROR` | 100 | 未识别的离线引擎状态（防御性兜底）。 |
| `OFFLINE_INVALID_STATE` | 101 | 离线引擎内部状态非法。 |
| `OFFLINE_RESOURCE_ERROR` | 102 | 通用/音色资源加载失败。 |
| `OFFLINE_MODEL_ERROR` | 103 | 模型加载或推理失败。 |
| `OFFLINE_LICENSE_INVALID` | 104 | 授权文件无效。 |
| `OFFLINE_LICENSE_EXPIRED` | 105 | 授权文件已过期。 |
| `ONLINE_UNKNOWN_ERROR` | 200 | 未识别的在线引擎状态（防御性兜底）。 |
| `ONLINE_NETWORK_ERROR` | 201 | 网络错误。 |
| `ONLINE_SERVER_ERROR` | 202 | 服务端错误。 |
| `ONLINE_INVALID_USER_SIG` | 203 | UserSig 无效（过期/解析失败/校验失败等）。 |

> 说明：错误码分段规划，基础错误码位于 `[1, 100)`，离线 TTS 错误码位于 `[100, 200)`，在线 TTS 错误码位于 `[200, 300)`。`onCompleted` 的 `code` 使用 `TXRealtimeTTSError` 中的整型值。

---

## 六、实验性 API

`callExperimentalAPI(String jsonParams)` 用于设置离线资源包路径等可选项。**离线模式下 `setOfflineCommonResourcePath` 与 `setOfflineVoiceResourcePath` 为必需**，须在 `start` 之前调用；`path` 须指向具体的资源 zip 文件（而非解压后的目录），SDK 内部会自行解压。在线模式无需配置离线资源包。入参为 JSON 字符串，格式：

```json
{ "api": "<接口名>", "params": { ... } }
```

支持的接口：

| api | params | 说明 |
|---|---|---|
| `setOfflineCommonResourcePath` | `{"path": "/path/to/common_resources_x.x.x.zip"}` | 设置离线通用资源包路径，`path` 须指向具体的 zip 文件（非目录）。 |
| `setOfflineVoiceResourcePath` | `{"path": "/path/to/voice_xxxxxx.zip"}` | 设置离线音色资源包路径，`path` 须指向具体的 zip 文件（非目录）。 |

示例：

```java
// 离线模式在 start 之前设置资源包（path 指向具体的 zip 文件）
String json = "{\"api\":\"setOfflineCommonResourcePath\","
            + "\"params\":{\"path\":\"/sdcard/tts/tts_resource/common_resources_x.x.x.zip\"}}";
mTTS.callExperimentalAPI(json);
```

---

## 七、最佳实践与注意事项

1. **模式选择**：在线用 `ONLINE`（需联网 + UserSig 鉴权），离线用 `OFFLINE`（本地资源，无网可用）；`MIX` 暂未开放。
2. **音色必填**：`voiceName` 未设置会导致 `start` 返回 `INVALID_PARAM`。
3. **在线鉴权必填**：`ONLINE` 模式须填 `online.appId` / `online.userId` / `online.userSig`；UserSig 无效会返回 `ONLINE_INVALID_USER_SIG`。
4. **离线鉴权必填**：`OFFLINE` 模式须填 `offline.licenseUrl` 与 `offline.licenseKey`。
5. **离线资源包必配**：`OFFLINE` 模式须在 `start` 之前通过实验性 API 配置好通用资源包与音色资源包的 zip 文件路径（`path` 指向具体的 zip 文件，非解压后的目录）；在线模式无需配置。
6. **流式结束标记**：一段文本输入完成后，最后一片须置 `isEnd=true`。
7. **音频帧深拷贝**：`onSynthesizedAudioFrame` 中的 `pcmData` 仅在回调内有效，如需异步处理请立即深拷贝。
8. **生命周期**：使用完毕先 `stop()` 再 `destroy()`，避免资源泄漏。
