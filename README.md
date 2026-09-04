# VoiceAI

VoiceAI 是 **TXLiteAVSDK** 中的实时语音 AI 能力集合，为应用提供「听」与「说」两条链路：实时语音识别（ASR）与实时语音合成（TTS），并提供统一的日志配置入口 `TXVoiceAILog`。

- **四端同构**：C++ / Java / Objective-C / ArkTS 四套封装语义一致，一次理解多端复用
- **流式实时**：ASR 边说边出字，TTS 边输入文本边合成边播放
- **在线 / 离线**：TTS 支持云端引擎与本地引擎两种模式
- **回调友好**：业务回调均已切到 UI 线程，无需关心线程安全

---

## 平台支持

| 平台 | 语言 | 产物 |
|---|---|---|
| Windows / 桌面 | C++ | 动态库 + 导入库 |
| Android | Java | AAR |
| iOS / macOS | Objective-C | Framework |
| HarmonyOS | ArkTS | 共享库 + TS 封装 |

## SDK 下载

当前版本：`13.5.0.223`

| 平台 | 更新时间 | 下载地址 | 文档指引 |
|---|---|---|---|
| Windows / 桌面（C++） | 2026-09-03 | [下载 ZIP](https://dl.gmertc.com/voiceai/13.5.0/VoiceAI_Win_sdk_13.5.0.223.zip) | [ASR 文档](Doc/ASR/ASR_Windows.md) <br> [TTS 文档](Doc/TTS/TTS_Windows.md) |
| Android（Java） | 2026-09-03 | [Maven](https://central.sonatype.com/artifact/com.tencent.liteav/LiteAVSDK_VoiceAI)<br>[下载 ZIP](https://dl.gmertc.com/voiceai/13.5.0/VoiceAI_Android_sdk_13.5.0.223.zip) | [ASR 文档](Doc/ASR/ASR_Android.md) <br> [TTS 文档](Doc/TTS/TTS_Android.md) |
| iOS / macOS（ObjC） | 2026-09-03 | iOS：[下载 ZIP](https://dl.gmertc.com/voiceai/13.5.0/VoiceAI_iOS_sdk_13.5.0.223.zip)<br>macOS：[下载 ZIP](https://dl.gmertc.com/voiceai/13.5.0/VoiceAI_Mac_sdk_13.5.0.223.zip) | [ASR 文档](Doc/ASR/ASR_iOS_macOS.md) <br> [TTS 文档](Doc/TTS/TTS_iOS_macOS.md) |
| HarmonyOS（ArkTS） | 2026-09-03 | [OHPM](https://ohpm.openharmony.cn/#/cn/detail/@tencentcloud%2Fliteavsdk_voiceai)<br>[下载 ZIP](https://dl.gmertc.com/voiceai/13.5.0/VoiceAI_OHOS_sdk_13.5.0.223.zip) | [ASR 文档](Doc/ASR/ASR_HarmonyOS.md) <br> [TTS 文档](Doc/TTS/TTS_HarmonyOS.md) |

> Android 与 HarmonyOS 推荐直接使用 Maven / ohpm 集成，无需下载 ZIP 包；ZIP 包内含 AAR / HAR、头文件与 Demo 工程，适合离线集成。

---

## 能力一览

### 实时语音识别（ASR）

- 实时流式识别，区分「中间结果」与「稳态结果」
- 支持中文、英文等语言，留空为自动识别
- 识别结果携带说话人 ID 与时间戳，便于定位与区分说话人
- 实时音量回调，可用于绘制音量波形
- 支持自定义音频采集，自行采集 PCM 后送入引擎
- 支持多监听器注册

### 实时语音合成（TTS）

- **在线模式**：云端引擎合成，音色丰富，需联网与 UserSig 鉴权
- **离线模式**：本地引擎合成，无需联网，需 License 授权与本地资源包
- 流式文本输入：边输入边合成边播放
- 三种音频输出方式：仅播放、仅回调 PCM 数据、播放并回调

---

## 日志配置

`TXVoiceAILog` 是进程内所有 VoiceAI 能力的统一日志入口，提供三个静态接口：

- `setLogLevel`：设置日志输出级别（默认关闭）
- `setLogPath`：设置日志落盘目录
- `setLogCallback`：注册回调，实时捕获 SDK 日志

一次配置对进程内所有 VoiceAI 能力生效。日志仅在本地落盘或通过回调抛给业务层，**不会主动上传云端**。

未调用 `setLogPath` 时使用以下默认落盘目录：

| 平台 | 默认路径 |
|---|---|
| Windows | `%appdata%/voiceai/liteav/log` |
| Android | `/sdcard/Android/data/<packageName>/files/voiceai/log/liteav/` |
| iOS / macOS | `Documents/voiceai/log` |
| HarmonyOS | `/data/app/el2/100/base/<bundleName>/files/voiceai/liteav/log/` |

详细用法见 [VoiceAI_Log.md](Doc/VoiceAI_Log.md)。

---

## 公共接口

| 能力 | C++ | Android (Java) | iOS / macOS (ObjC) | HarmonyOS (ArkTS) |
|---|---|---|---|---|
| 语音识别 | `ITXRealtimeASR` | `TXRealtimeASR` | `TXRealtimeASR` | `TXRealtimeASR` |
| 语音合成 | `ITXRealtimeTTS` | `TXRealtimeTTS` | `TXRealtimeTTS` | `TXRealtimeTTS` |
| 日志配置 | `liteav::TXVoiceAILog` | `com.tencent.voiceai.TXVoiceAILog` | `TXVoiceAILog` | `TXVoiceAILog` |

C++ 侧公共类型位于 `liteav` 命名空间下。识别与合成实例均通过 C 风格工厂函数创建与销毁。

---

## 接入流程

```text
1. 配置日志（可选，但建议最先执行）
   setLogPath → setLogLevel → setLogCallback

2. 准备鉴权
   · ASR / 在线 TTS：SDKAppID + UserID + UserSig
   · 离线 TTS：License URL + License Key，并配置本地资源包路径

3. 创建实例并注册监听

4. 启动会话，交互，处理回调

5. 销毁实例
```

> 日志配置必须早于任何其它 VoiceAI 接口调用，否则初始化阶段的日志会丢失。

---

## 注意事项

1. **UserSig 安全**：切勿在客户端硬编码密钥计算 UserSig，应由业务服务端生成后动态下发，否则密钥泄露会导致云资源被盗用。
2. **尽早配置日志**：在应用启动阶段完成日志配置；日志级别默认关闭，不显式打开则无任何输出。
3. **线程模型**：ASR / TTS 的业务回调已切至 UI 线程；日志回调在 SDK 日志线程触发，回调内禁止耗时、阻塞或加锁等待操作。
4. **指针生命周期**：参数结构体中的字符串指针在调用期间须保持有效；回调中的字符串与 PCM 指针仅在回调内有效，异步使用须自行深拷贝。
5. **离线 TTS 资源**：离线模式必须在启动前配置好通用资源与音色资源路径。
6. **流式结束标记**：TTS 一段文本输入完成后，最后一片须标记为结束。
7. **会话 ID 唯一性**：ASR 每次启动建议使用新的 UUID 作为会话 ID。
8. **隐私合规**：日志不会主动上传云端；如需回传服务器或随工单提交，请自行脱敏。
9. **销毁顺序**：先停止会话 → 置空监听 → 销毁实例，避免回调打到已释放对象。
