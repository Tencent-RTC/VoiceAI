// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAIKit - 声纹文件存储
//
// 统一管理声纹注册 WAV 的沙盒路径，供声纹注册页写入、实时对话声纹降噪读取。
// WAV 要求：16kHz 单声道 16bit PCM，时长 ≥10s。

import Foundation

enum VoiceprintStore {

    /// 声纹目录：Documents/voiceprint/
    static var directory: URL {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        let dir = docs.appendingPathComponent("voiceprint")
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    /// 生成一个新的声纹文件 URL。
    static func newFileURL() -> URL {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyyMMdd_HHmmss"
        let name = "voiceprint_\(formatter.string(from: Date())).wav"
        return directory.appendingPathComponent(name)
    }

    /// 当前已注册声纹文件的 URL（不存在则 nil）。
    static func currentURL() -> URL? {
        let name = VoiceAIPrefs.shared.voiceprintFileName
        guard !name.isEmpty else { return nil }
        let url = directory.appendingPathComponent(name)
        return FileManager.default.fileExists(atPath: url.path) ? url : nil
    }

    /// 当前声纹文件路径（不存在则空串，SDK 侧走自动注册）。
    static func currentPath() -> String {
        return currentURL()?.path ?? ""
    }

    /// 记录最近注册文件名。
    static func setCurrent(_ url: URL) {
        VoiceAIPrefs.shared.voiceprintFileName = url.lastPathComponent
    }

    /// 是否已注册声纹。
    static var hasVoiceprint: Bool { currentURL() != nil }
}
