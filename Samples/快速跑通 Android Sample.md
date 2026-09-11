# VoiceAI Android Demo 快速开始

**10 分钟内跑通语音识别（ASR）+ 语音播报（TTS）**，并可选接入大模型对话（LLM）。

---
<table>
<tr>

<td rowspan="1" colspan="1" ><br><img src="https://write-document-release-1258344699.cos.ap-guangzhou.tencentcos.cn/100027984178/df05647fad9a11f1a174525400074c32.jpeg" alt="" />
</td>

<td rowspan="1" colspan="1" ><br><img src="https://write-document-release-1258344699.cos.ap-guangzhou.tencentcos.cn/100027984178/57880178ac0d11f1ad6d52540073fd3b.jpeg" alt="" />
</td>

<td rowspan="1" colspan="1" ><br><img src="https://write-document-release-1258344699.cos.ap-guangzhou.tencentcos.cn/100027984178/35e97759ac1011f18492525400a31896.jpeg" alt="" />
</td>

<td rowspan="1" colspan="1" ><br><img src="https://write-document-release-1258344699.cos.ap-guangzhou.tencentcos.cn/100027984178/f16bc0e5ad9a11f1b05552540073fd3b.jpeg" alt="" />
</td>

</tr>
</table>

## 1. 环境要求

| 项 | 版本 |
|---|---|
| Android Studio | Iguana 及以上（AGP `8.5.2`） |
| JDK | **17** |
| Gradle | 8.9（`gradle/wrapper/gradle-wrapper.properties` 已指定） |
| Kotlin | 1.9.24，Compose Compiler 1.5.14 |
| compileSdk / targetSdk / minSdk | 34 / 34 / **24** |
| 设备 | **真机**（需要麦克风与网络，模拟器通常无可用麦克风） |

---

## 2. 目录结构

```
VoiceAIKit/                       ← 本目录（Android 工程根）
├─ app/                           Demo 宿主：Compose 单 Activity + 多 Page
│  └─ src/main/java/.../demo/util/Config.java   ★ 唯一需要你填写的配置
├─ VoiceAIKit/                    Kit 模块：AIChatFragment / RealtimeChatActivity / VoiceInputBar
│                                 （LiteAVSDK_VoiceAI 依赖已由本模块的 build.gradle.kts 声明，宿主无需关心）
└─ gradle.properties              工程级 Gradle 开关
```

---

## 3. 你需要准备的东西

1. **腾讯云 SDKAppID + SecretKey**（必填）：在 [腾讯云 TRTC 控制台](https://console.cloud.tencent.com/trtc) 创建应用后获取，用于 ASR / 在线 TTS 鉴权
2. **一个 LLM 服务**（可选）：OpenAI 兼容协议的 `/v1/chat/completions`，支持 `stream=true`（Demo 默认模型 `hy3-preview`，可换成你自己的）

   - **只有想体验 AI 对话（语音提问 → 大模型回复 → 语音播报）时才需要配置**；不配置也能正常使用 ASR 语音识别与 TTS 语音播报能力。
   - 还没有 LLM 服务？可直接到 [腾讯云 TokenHub 控制台](https://console.cloud.tencent.com/tokenhub/quick-start) 快捷创建，开通后在「接口调用」页即可拿到 **请求地址（补全为 `/v1/chat/completions`）**、**API Key** 与 **模型名**，分别填入下面的 `OPEN_AIP_URL` / `OPEN_AIP_KEY` / `OPEN_AIP_MODEL`。

> SDK 依赖不用管：`:VoiceAIKit` 模块的 `build.gradle.kts` 已声明 LiteAVSDK_VoiceAI 的 Maven 坐标，Sync 时自动拉取。

---

## 4. 两步跑通

### 步骤 1：填写配置

编辑 `app/src/main/java/com/tencent/voiceai/demo/util/Config.java`。

**必填**（跑通 ASR + TTS 只需要这三项）：

| 字段 | 含义 | 从哪来 |
|---|---|---|
| `SDKAPPID` | 腾讯云应用 ID | TRTC 控制台 |
| `SECRETKEY` | 计算 UserSig 的密钥（**仅调试用**） | TRTC 控制台 |
| `USERID` | 演示用用户 ID，随意 | — |

**可选**（LLM 大模型对话配置）：

> 只有想体验 AI 对话（语音提问 → 大模型回复 → 语音播报）时才需要填写；**不需要 AI 对话能力时，以下三项保持为空即可**，ASR 语音识别与 TTS 语音播报不受任何影响。

| 字段 | 含义 | 从哪来 |
|---|---|---|
| `OPEN_AIP_URL` | LLM 服务地址，如 `https://xxx/v1/chat/completions` | 你的 LLM 服务（可在 [腾讯云 TokenHub 控制台](https://console.cloud.tencent.com/tokenhub/quick-start) 快捷创建） |
| `OPEN_AIP_KEY` | LLM 服务鉴权 Key | 同上 |
| `OPEN_AIP_MODEL` | 模型名 | 同上 |

只改这一个文件即可，`MainActivity` 会在启动时把它们注入 `VoiceAIKit.init(...)`。

### 步骤 2：编译运行

- **Android Studio**：`File → Open` 选本目录 → Sync → Run `app`。
- **命令行**：本工程**没有提交 `gradlew`**，先生成一次：
  ```bash
  gradle wrapper            # 本地装了 Gradle 时执行一次即可
  ./gradlew :app:installDebug
  ```
- 首次进入「AI 对话」会申请麦克风权限，必须允许；网络权限已在 Manifest 声明。

---

## 5. 常见问题

| 现象 | 原因与处理 |
|---|---|
| Sync 时报 SDK 依赖解析失败 | 检查网络与 `settings.gradle.kts` 中的仓库配置能否访问 LiteAVSDK_VoiceAI 所在的 Maven 源 |
| 提示「未配置 LLM 服务地址与密钥」 | LLM 为可选项：仅在使用 AI 对话时才需要填写 `Config.OPEN_AIP_URL` / `OPEN_AIP_KEY` / `OPEN_AIP_MODEL`，不去体验对话能力可忽略该提示 |
| ASR / TTS 报鉴权失败 | `SDKAPPID` / `SECRETKEY` 填错，或 UserSig 过期；Demo 在客户端算 UserSig 只为调试 |
| 有文字回复但没声音 | 检查网络；确认音色 ID 是**云端在线音色**；确认标题栏「自动朗读」已开启 |
| 切换音色后没变化 | 音色在**下次进入对话页**生效；且必须是云端音色 ID（Kit 已移除离线合成，不再读取本地资源包） |
| 声纹降噪无效果 | 先到「设置 → 声纹注册」完成注册 |
| 编译报 JDK 版本错误 | 需 JDK 17：`Android Studio → Settings → Gradle → Gradle JDK` |
