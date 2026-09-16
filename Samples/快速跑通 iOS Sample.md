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
| 设备 | **真机**（需要麦克风与网络，模拟器通常无可用麦克风） |

---

## 2. 你需要准备的东西

1. **腾讯云 SDKAppID + SecretKey**（必填）：在 [腾讯云 TRTC 控制台](https://console.cloud.tencent.com/trtc) 创建应用后获取，用于 ASR / 在线 TTS 鉴权。
2. **一个 LLM 服务**（可选）：OpenAI 兼容协议的 `/v1/chat/completions`，支持 `stream=true`。

   - **只有想体验「AI 对话 / 实时对话」时才需要配置**；不配置也能正常使用语音识别与语音播报。
   - 还没有 LLM 服务？可到 [腾讯云 TokenHub 控制台](https://console.cloud.tencent.com/tokenhub/quick-start) 快捷创建，开通后拿到**请求地址（补全为 `/v1/chat/completions`）**、**API Key** 与**模型名**。

> SDK 依赖不用管：`Podfile` 已声明 `TXLiteAVSDK_VoiceAI_iOS`，`pod install` 时自动拉取。

---

## 3. 两步跑通

### 步骤 1：填入配置

打开 **`iOS/VoiceAIKitDemo/Demo/Config.swift`**（★ 唯一需要你修改的文件）：

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

### 步骤 2：拉取 SDK 并运行

在 `Samples/iOS/` 目录执行：

```bash
pod install                       # 拉取 TXLiteAVSDK_VoiceAI_iOS
open VoiceAIKitDemo.xcworkspace   # 注意：必须打开 .xcworkspace，不是 .xcodeproj
```

> `pod install` 会顺带改写 `project.pbxproj`（写入 Pods 的 xcconfig 引用），属正常现象，详见第 9 节。

然后在 Xcode 中：

1. 选择 `VoiceAIKitDemo` target → **Signing & Capabilities** → 选择你的开发团队。
2. 连接 iPhone，**真机**运行。
3. 首次使用会请求麦克风权限（`Info.plist` 已声明 `NSMicrophoneUsageDescription`）。

> 想换 Bundle ID？编辑 `iOS/gen_xcodeproj.py` 顶部的 `BUNDLE_ID`，按第 9 节重生成工程。

---

## 4. 目录结构

```
Samples/
├── 快速跑通 iOS Sample.md          ← 本文档
└── iOS/                            ← 工程根（pod install 在此目录执行）
    ├── VoiceAIKitDemo.xcodeproj/   # 已入库，clone 后直接可用
    ├── VoiceAIKitDemo.xcworkspace/ # pod install 生成，未入库
    ├── Podfile                     # CocoaPods 接入 VoiceAI SDK
    ├── gen_xcodeproj.py            # 维护者工具：增删 Swift 文件后重生成工程
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

- `Config.swift` 中的 **SecretKey 硬编码仅供调试**。正式版本请在服务端计算 UserSig 后下发，参考：[UserSig 官方文档](https://cloud.tencent.com/document/product/647/50686)。
- **LLM API Key 同样不应打进 App**，生产环境请通过后端代理转发请求。
- `.gitignore` 忽略 `Pods/`、`VoiceAIKitDemo.xcworkspace/` 与 SDK 二进制；`VoiceAIKitDemo.xcodeproj` 与 `Podfile.lock` 已入库，请确认提交前没有把密钥写入版本库。

---

## 8. SDK 能力对应

| 能力 | SDK 类 | 封装位置 |
| --- | --- | --- |
| 实时语音识别 ASR | `TXRealtimeASR` | `VoiceAIKit/AsrEngine.swift` |
| 在线语音合成 TTS | `TXRealtimeTTS` | `VoiceAIKit/TtsEngine.swift` |
| 降噪 / 声纹 | `TXRealtimeASR.callExperimentalAPI` | `VoiceAIKit/AsrEngine.swift` |
| 鉴权 UserSig | 本地 HMAC 生成（仅测试用） | `Demo/GenerateTestUserSig.swift` |

接入文档：<https://cloud.tencent.com/document/product/647/137680>

---

## 9. 维护者说明（仅跑 Demo 可忽略）

`VoiceAIKitDemo.xcodeproj` 已入库，使用者无需执行生成脚本。只有当仓库中的 Swift 文件发生**新增 / 删除 / 重命名**时，维护者才需要：

```bash
cd Samples/iOS
python3 gen_xcodeproj.py                  # 重新生成工程（默认 CocoaPods 模式）
git add VoiceAIKitDemo.xcodeproj          # 连同源码一起提交
```

- 生成的 UUID 是确定性的，重复执行不会产生无意义 diff。
- CI 中可校验入库工程是否最新（非 0 退出即过期）：

  ```bash
  python3 gen_xcodeproj.py --check
  ```

- 需要手动嵌入 xcframework（不走 CocoaPods）时使用 `--embed-framework`。

> **`pod install` 会改写 `project.pbxproj`**（写入 Pods 的 xcconfig 引用与 `[CP]` 脚本阶段），这是 CocoaPods 的正常行为。
> 仓库里提交的是**未经 CocoaPods 改写**的干净版本，因此：
> - 使用者跑完 `pod install` 后 `git status` 会显示工程有改动，这是预期的，**不要提交**；
> - 维护者提交工程改动前，先执行一次 `python3 gen_xcodeproj.py` 回到干净状态；
> - 本地被 `pod install` 改写后若直接打开 `.xcodeproj`，会因缺少 Pods 的 xcconfig 而报错，重跑一次 `pod install` 即可恢复。
