# VoiceAI iOS Demo 快速开始

**10 分钟内跑通语音识别（ASR）+ 语音播报（TTS）**，并可选接入大模型对话（LLM）。

串联腾讯云 **实时语音识别 → 大模型 → 实时语音合成** 的完整链路，SwiftUI 实现。

---

## 1. 环境要求

| 项 | 版本 |
| --- | --- |
| Xcode | 15.0 及以上 |
| iOS 部署目标 | **16.0** |
| CocoaPods | 1.10 及以上（`sudo gem install cocoapods`） |
| Python | 3（macOS 自带，用于生成工程） |
| 设备 | **真机**（需要麦克风与网络，模拟器通常无可用麦克风） |

---

## 2. 你需要准备的东西

1. **腾讯云 SDKAppID + SecretKey**（必填）：在 [腾讯云 TRTC 控制台](https://console.cloud.tencent.com/trtc) 创建应用后获取，用于 ASR / 在线 TTS 鉴权。
2. **一个 LLM 服务**（可选）：OpenAI 兼容协议的 `/v1/chat/completions`，支持 `stream=true`。

   - **只有想体验「AI 对话 / 实时对话」时才需要配置**；不配置也能正常使用语音识别与语音播报。
   - 还没有 LLM 服务？可到 [腾讯云 TokenHub 控制台](https://console.cloud.tencent.com/tokenhub/quick-start) 快捷创建，开通后拿到**请求地址（补全为 `/v1/chat/completions`）**、**API Key** 与**模型名**。

---

## 3. 跑起来（三步）

### 第 1 步：填入配置

打开 **`VoiceAIKitDemo/Demo/Config.swift`**（★ 唯一需要你修改的文件）：

```swift
// 必填：腾讯云账号（ASR / TTS 鉴权）
static let sdkAppId: Int = 0                 // ← 换成你的 SDKAppID（纯数字）
static let secretKey: String = ""            // ← 换成你的 SecretKey

// 选填：大模型对话（AI 对话 / 实时对话需要）
static let openApiUrl: String = "https://tokenhub.tencentmaas.com/v1/chat/completions"
static let openApiKey: String = ""           // ← 换成你的 LLM API Key
static let openApiModel: String = "hy3-preview"
```

> 未填写 `sdkAppId` / `secretKey` 时，首页顶部会出现黄色提示条，语音能力不可用。

### 第 2 步：生成工程并拉取 SDK

在本目录（`Samples/iOS/`）执行：

```bash
python3 gen_xcodeproj.py --use-pods   # 生成工程（CocoaPods 方式）
pod install                           # 拉取 TXLiteAVSDK_VoiceAI_iOS
open VoiceAIKitDemo.xcworkspace       # 注意：必须打开 .xcworkspace，不是 .xcodeproj
```

### 第 3 步：签名并运行

1. 在 Xcode 中选择 `VoiceAIKitDemo` target → **Signing & Capabilities** → 选择你的开发团队。
2. 连接 iPhone，**真机**运行。
3. 首次使用会请求麦克风权限（`Info.plist` 已声明 `NSMicrophoneUsageDescription`）。

> 想换 Bundle ID？编辑 `gen_xcodeproj.py` 顶部的 `BUNDLE_ID` 后重新执行第 2 步。

---

## 4. 目录结构

```
Samples/iOS/
├── gen_xcodeproj.py                 # 生成 xcodeproj（--use-pods 走 CocoaPods）
├── Podfile                          # CocoaPods 接入 VoiceAI SDK
├── README.md
└── VoiceAIKitDemo/
    ├── VoiceAIKitDemoApp.swift      # App 入口（注入 Kit 配置 + 音频会话）
    ├── Info.plist
    ├── Assets.xcassets/
    ├── Frameworks/                  # 手动嵌入 xcframework 时使用（见其内部 README）
    ├── VoiceAIKit/                  # 能力封装层
    │   ├── VoiceAIKit.swift         # 全局配置单例（Params / LLMParams）
    │   ├── AsrEngine.swift          # TXRealtimeASR 封装
    │   ├── TtsEngine.swift          # TXRealtimeTTS 在线合成封装
    │   ├── LlmClient.swift          # OpenAI 兼容流式对话客户端（SSE）
    │   ├── AIChatController.swift   # 文字/语音对话：ASR 文本 → LLM → 逐句 TTS
    │   ├── RealtimeChatController.swift # 全双工实时对话状态机
    │   └── Components/              # VoiceInputBar / VoiceOrbView / VoiceWaveView
    └── Demo/                        # 界面层
        ├── Config.swift             # ★ SDKAppId / SecretKey / LLM 配置
        ├── GenerateTestUserSig.swift# 本地生成测试用 UserSig
        ├── Pages/                   # 7 个页面
        └── ViewModels/
```

---

## 5. 功能页面

| 页面 | 说明 |
| --- | --- |
| 首页 HomePage | 两张功能卡片（语音输入 / AI 对话）+ 右上设置入口 |
| 语音输入 VoiceInputPage | 底部 `VoiceInputBar`，键盘 / 按住说话双模式，实时识别回显 |
| AI 对话 ChatPage | 文字或语音输入，LLM 流式回复气泡，自动朗读开关，可进入实时通话 |
| 实时通话 RealtimeChatPage | 麦克风常开的全双工语音对话，支持打断、字幕、麦克风 / 朗读开关 |
| 设置 SettingPage | 默认音色、实时对话降噪（关闭 / 远场 / 声纹）、声纹注册入口 |
| 音色设置 TtsVoiceSettingPage | 在线音色单选列表 |
| 声纹注册 VoiceprintRegisterPage | 录制 16kHz 单声道 WAV（≥10s）→ 试听 → 注册 |

---

## 6. 常见问题

**`No such module 'TXLiteAVSDK_VoiceAI_iOS'`**
打开的是 `.xcodeproj` 而不是 `.xcworkspace`。CocoaPods 的 SDK 挂在 workspace 上，请关闭工程后重新 `open VoiceAIKitDemo.xcworkspace`。

**`pod install` 失败 / 拉不到 SDK**
检查网络与 CocoaPods 源，可尝试 `pod repo update` 后重试；`Podfile` 中指定了 SDK 版本 `TXLiteAVSDK_VoiceAI_iOS`。

**能编译但一说话就报错 / 鉴权失败**
检查 `Config.swift` 的 `sdkAppId` 与 `secretKey` 是否正确、该应用是否已开通语音 AI 能力；UserSig 有效期为 7 天，过期会鉴权失败。

**AI 对话提示「未配置 LLM」**
`openApiUrl` 与 `openApiKey` 需同时非空，填写后重新运行即可；仅体验语音识别 / 播报时无需配置。

**模拟器上运行没有声音 / 无法识别**
语音采集与播报需要**真机**，请使用 iPhone 运行。

---

## 7. 安全提示

- `Config.swift` 中的 **SecretKey 硬编码仅供调试**。正式版本请在服务端计算 UserSig 后下发，参考：[UserSig 官方文档](https://cloud.tencent.com/document/product/269/32688)。
- **LLM API Key 同样不应打进 App**，生产环境请通过后端代理转发请求。
- 本仓库已通过 `.gitignore` 忽略 `Pods/`、`*.xcworkspace/`、`*.xcodeproj/` 与 SDK 二进制，请确认提交前没有把密钥写入版本库。

---

## 8. SDK 能力对应

| 能力 | SDK 类 | 封装位置 |
| --- | --- | --- |
| 实时语音识别 ASR | `TXRealtimeASR` | `VoiceAIKit/AsrEngine.swift` |
| 在线语音合成 TTS | `TXRealtimeTTS` | `VoiceAIKit/TtsEngine.swift` |
| 降噪 / 声纹 | `TXRealtimeASR.callExperimentalAPI` | `VoiceAIKit/AsrEngine.swift` |
| 鉴权 UserSig | 本地 HMAC 生成（仅测试用） | `Demo/GenerateTestUserSig.swift` |

接入文档：<https://cloud.tencent.com/document/product/647/137680>
