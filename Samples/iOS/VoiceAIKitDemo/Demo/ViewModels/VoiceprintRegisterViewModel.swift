// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAI Demo - 声纹注册 ViewModel (对齐 Android VoiceprintRegisterViewModel.kt)
//
// 使用 AVAudioRecorder 录制 16kHz / 单声道 / 16bit PCM WAV。
// 要求录音时长 ≥10s；注册即把该 WAV 路径写入 VoiceprintStore，供实时对话声纹降噪使用。

import Foundation
import AVFoundation

final class VoiceprintRegisterViewModel: NSObject, ObservableObject {

    static let minDuration: TimeInterval = 10

    /// 建议朗读文本（≥10s）。
    let promptText = "腾讯云语音识别与合成，为智能语音交互提供稳定可靠的能力。今天天气不错，我正在测试声纹注册功能，希望识别更加准确清晰。"

    @Published var recording = false
    @Published var elapsed: TimeInterval = 0
    @Published var hasRecorded = false
    @Published var playing = false
    @Published var toast: String?
    @Published var registered = VoiceprintStore.hasVoiceprint

    private var recorder: AVAudioRecorder?
    private var player: AVAudioPlayer?
    private var timer: Timer?
    private var recordURL: URL?

    var canRegister: Bool { hasRecorded && elapsed >= Self.minDuration }
    var progress: Double { min(1.0, elapsed / Self.minDuration) }

    // MARK: - 录制

    func toggleRecord() {
        recording ? stopRecord() : startRecord()
    }

    private func startRecord() {
        requestMic { [weak self] granted in
            guard let self = self else { return }
            guard granted else { self.toast = "请开启麦克风权限"; return }
            self.beginRecording()
        }
    }

    private func beginRecording() {
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker])
            try session.setActive(true)

            let url = VoiceprintStore.newFileURL()
            recordURL = url
            let settings: [String: Any] = [
                AVFormatIDKey: kAudioFormatLinearPCM,
                AVSampleRateKey: 16000,
                AVNumberOfChannelsKey: 1,
                AVLinearPCMBitDepthKey: 16,
                AVLinearPCMIsBigEndianKey: false,
                AVLinearPCMIsFloatKey: false
            ]
            let rec = try AVAudioRecorder(url: url, settings: settings)
            rec.delegate = self
            rec.record()
            recorder = rec

            elapsed = 0
            hasRecorded = false
            recording = true
            timer = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { [weak self] _ in
                guard let self = self, let r = self.recorder else { return }
                self.elapsed = r.currentTime
            }
        } catch {
            toast = "录音启动失败: \(error.localizedDescription)"
        }
    }

    private func stopRecord() {
        recorder?.stop()
        timer?.invalidate(); timer = nil
        recording = false
        hasRecorded = (recordURL != nil)
        if elapsed < Self.minDuration {
            toast = "录音需 ≥10 秒，请重新录制"
        }
    }

    // MARK: - 试听

    func togglePlay() {
        if playing { stopPlay(); return }
        guard let url = recordURL else { return }
        do {
            player = try AVAudioPlayer(contentsOf: url)
            player?.delegate = self
            player?.play()
            playing = true
        } catch {
            toast = "播放失败: \(error.localizedDescription)"
        }
    }

    private func stopPlay() {
        player?.stop()
        player = nil
        playing = false
    }

    // MARK: - 注册

    func register() {
        guard canRegister, let url = recordURL else {
            toast = "请先录制 ≥10 秒的语音"
            return
        }
        VoiceprintStore.setCurrent(url)
        registered = true
        toast = "声纹注册成功"
    }

    private func requestMic(_ completion: @escaping (Bool) -> Void) {
        let session = AVAudioSession.sharedInstance()
        switch session.recordPermission {
        case .granted: completion(true)
        case .denied: completion(false)
        default:
            session.requestRecordPermission { g in DispatchQueue.main.async { completion(g) } }
        }
    }
}

extension VoiceprintRegisterViewModel: AVAudioRecorderDelegate, AVAudioPlayerDelegate {
    func audioRecorderDidFinishRecording(_ recorder: AVAudioRecorder, successfully flag: Bool) {}
    func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        DispatchQueue.main.async { [weak self] in self?.playing = false }
    }
}
