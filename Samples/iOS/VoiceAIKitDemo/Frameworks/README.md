# 手动嵌入 SDK 时使用此目录

本 Demo **默认通过 CocoaPods 拉取**腾讯云语音 AI SDK（`TXLiteAVSDK_VoiceAI_iOS`），
一般情况下你不需要往这里放任何文件。

若你选择「手动嵌入 xcframework」方式，请把下载到的 SDK 放到本目录，使其结构为：

```
VoiceAIKitDemo/Frameworks/TXLiteAVSDK_VoiceAI_iOS.xcframework/
```

下载方式见官方文档：<https://cloud.tencent.com/document/product/647/137680>

放置完成后，在本目录（`Samples/iOS/`）执行：

```bash
python3 gen_xcodeproj.py        # 不带 --use-pods：手动嵌入模式
open VoiceAIKitDemo.xcodeproj
```

> 该 xcframework 体积较大，已从 Git 中忽略（见 `.gitignore`），不会随仓库提交。
