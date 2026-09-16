// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAI Demo - 语音波形 (对齐 Android view/component/VoiceWaveView.kt)
//
// 32 根圆角竖条，随音量起伏，带平滑处理。音量通过 volume(0-100) 驱动。

import SwiftUI

/// 观察音量的简单模型，供波形视图订阅。
final class WaveLevel: ObservableObject {
    static let barCount = 32
    @Published private(set) var bars: [CGFloat] = Array(repeating: 0.05, count: WaveLevel.barCount)

    private let smoothing: CGFloat = 0.35

    /// 输入 0-100 的音量，内部做归一化 + 每根随机权重 + 平滑。
    func setVolume(_ volume: Int32) {
        let norm = max(0, min(1, CGFloat(volume) / 100.0))
        var next = bars
        for i in 0..<next.count {
            // 中间高两侧低的包络 + 抖动
            let center = CGFloat(i) / CGFloat(next.count - 1)
            let envelope = 0.4 + 0.6 * sin(center * .pi)
            let target = max(0.05, norm * envelope * CGFloat.random(in: 0.6...1.0))
            next[i] = next[i] * (1 - smoothing) + target * smoothing
        }
        DispatchQueue.main.async { self.bars = next }
    }

    func reset() {
        DispatchQueue.main.async {
            self.bars = Array(repeating: 0.05, count: WaveLevel.barCount)
        }
    }
}

struct VoiceWaveView: View {
    @ObservedObject var level: WaveLevel
    var color: Color = Theme.brandBlue

    var body: some View {
        GeometryReader { geo in
            let barWidth = geo.size.width / CGFloat(WaveLevel.barCount) * 0.55
            let spacing = geo.size.width / CGFloat(WaveLevel.barCount) * 0.45
            HStack(alignment: .center, spacing: spacing) {
                ForEach(0..<WaveLevel.barCount, id: \.self) { i in
                    Capsule()
                        .fill(color)
                        .frame(width: barWidth,
                               height: max(3, level.bars[i] * geo.size.height))
                        .animation(.easeOut(duration: 0.08), value: level.bars[i])
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
        }
    }
}
