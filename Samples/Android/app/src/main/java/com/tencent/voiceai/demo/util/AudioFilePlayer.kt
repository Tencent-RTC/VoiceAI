// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAI Demo - 文件音频读取与外放
//
// 从 Uri 读取音频（WAV / 裸 PCM），解析出纯 PCM 数据，并按约 100ms 一块：
//   - 一路回调给调用方喂送 ASR（使用原始 PCM，不影响识别）；
//   - 一路放大后写入 AudioTrack 外放（PLAYBACK_GAIN 倍幅度，削顶保护）。
//
// 该类只负责「读文件 + 外放 + 分块回调」，不持有 ASR / 录音状态；
// 由调用方通过 [Callback] 提供状态判断与副作用。

package com.tencent.voiceai.demo.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import android.util.Log

/**
 * 文件音频读取 + 外放器。
 *
 * @param context 用于通过 contentResolver 读取 Uri。
 * @param callback 提供运行期状态（是否已停止 / 令牌是否有效 / ASR 是否建连）与副作用
 *                 （喂送 ASR、提示、播放结束回调）。
 */
class AudioFilePlayer(
    private val context: Context,
    private val callback: Callback
) {

    companion object {
        private const val TAG = "AudioFilePlayer"

        /** 文件外放的音量放大倍数：2f = 在原始幅度上放大 1 倍（超范围削顶）。 */
        private const val PLAYBACK_GAIN = 2f

        // 文件输入：WAV 按真实采样率/声道解析；裸 PCM 无头时回退到以下默认。
        private const val DEFAULT_SAMPLE_RATE = 16000
        private const val DEFAULT_CHANNELS = 1
    }

    /** 调用方需实现的回调：外放器据此判断是否继续、并把数据喂给 ASR。 */
    interface Callback {
        /** 是否已销毁（销毁后应尽快停止喂送 / 外放）。 */
        fun isDestroyed(): Boolean

        /** 当前是否处于录音（识别）中；为 false 时应停止喂送。 */
        fun isRecording(): Boolean

        /** ASR 是否已建连开始采集（文件输入需等建连后再喂 PCM）。 */
        fun isAsrStarted(): Boolean

        /** [token] 是否仍是当前有效令牌；切换文件后旧令牌应立即失效。 */
        fun isTokenValid(token: Int): Boolean

        /** 把一块原始 PCM 喂给 ASR。 */
        fun feedPcmToAsr(pcm: ByteArray, sampleRate: Int, channels: Int)

        /** 展示一次性提示（切回主线程由调用方负责）。 */
        fun showToast(message: String)

        /** 文件正常播完（未被打断）时回调，通常用于切回麦克风采集。 */
        fun onFileFinished()
    }

    /** 当前正在外放的播放器；切回麦克风时用于即时静音（释放仍由播放线程负责）。 */
    @Volatile
    private var activePlayer: AudioTrack? = null

    /** 立即停止当前文件外放（仅 stop，不 release；释放由播放线程 finally 负责），用于切回麦克风。 */
    fun stopPlayback() {
        activePlayer?.let { try { it.stop() } catch (_: Exception) {} }
        activePlayer = null
    }

    /**
     * 异步读取选定文件并在 ASR 建连后分块喂送 PCM + 外放。
     * @param uri 音频文件（WAV / 裸 PCM）。
     * @param token 本次喂送令牌，切换文件后失效即停止。
     */
    fun feedFileAsync(uri: Uri, token: Int) {
        Thread {
            try {
                val audio = readAudioFromUri(uri)
                if (audio.pcm.isEmpty()) {
                    callback.showToast("音频文件为空或无法读取")
                    return@Thread
                }
                // 等待 ASR 建连完成再喂数据（最多等 5s）
                var waited = 0
                while (!callback.isAsrStarted() && !callback.isDestroyed() && waited < 5000) {
                    if (!callback.isTokenValid(token)) return@Thread
                    Thread.sleep(50)
                    waited += 50
                }
                if (!callback.isTokenValid(token) || callback.isDestroyed()) {
                    if (!callback.isDestroyed()) callback.showToast("识别启动超时，未开始喂送文件")
                    return@Thread
                }
                val player = createFilePlayer(audio.sampleRate, audio.channels)
                activePlayer = player
                try {
                    player.play()
                } catch (e: Exception) {
                    log("文件播放启动失败: ${e.message}")
                }
                feedPcmChunks(audio, token, player)
            } catch (e: Exception) {
                callback.showToast("读取音频文件失败: ${e.message}")
            }
        }.start()
    }

    /** 解析出的音频：纯 PCM 数据 + 真实采样率 / 声道 / 位深（16bit）。 */
    private data class ParsedAudio(
        val pcm: ByteArray,
        val sampleRate: Int,
        val channels: Int,
        val bitsPerSample: Int
    )

    /** 读取 Uri 并解析为 PCM；WAV 取真实参数，裸 PCM 无头时回退到默认 16k/单声道。 */
    private fun readAudioFromUri(uri: Uri): ParsedAudio {
        val resolver = context.contentResolver
        val raw =
            resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return ParsedAudio(ByteArray(0), DEFAULT_SAMPLE_RATE, DEFAULT_CHANNELS, 16)
        return parseWavOrRaw(raw)
    }

    /** 解析 WAV：遍历 chunk 读取 fmt 的采样率/声道/位深，并截取 data 段纯 PCM。 */
    private fun parseWavOrRaw(raw: ByteArray): ParsedAudio {
        if (raw.size <= 12 || !matchId(raw, 0, "RIFF") || !matchId(raw, 8, "WAVE")) {
            return ParsedAudio(raw, DEFAULT_SAMPLE_RATE, DEFAULT_CHANNELS, 16)
        }
        var p = 12
        var sampleRate = DEFAULT_SAMPLE_RATE
        var channels = DEFAULT_CHANNELS
        var bits = 16
        var dataStart = -1
        var dataLen = 0
        while (p + 8 <= raw.size) {
            val size = le32(raw, p + 4)
            if (matchId(raw, p, "fmt ")) {
                channels = le16(raw, p + 10)
                sampleRate = le32(raw, p + 12)
                bits = le16(raw, p + 22)
            } else if (matchId(raw, p, "data")) {
                dataStart = p + 8
                dataLen = size
                break
            }
            p += 8 + size + (size and 1)
        }
        if (dataStart < 0) return ParsedAudio(raw, DEFAULT_SAMPLE_RATE, DEFAULT_CHANNELS, 16)
        val end = minOf(raw.size, dataStart + dataLen)
        var pcm = raw.copyOfRange(dataStart, end)
        if (bits != 16) log("文件位深 $bits 非 16bit，识别/播放可能异常")
        // 多声道下混为单声道（ASR 仅接受单声道），采样率保持真实值。
        if (channels > 1 && bits == 16) {
            pcm = downmixToMono(pcm, channels)
            channels = 1
        }
        return ParsedAudio(pcm, sampleRate, channels, bits)
    }

    private fun matchId(b: ByteArray, off: Int, id: String): Boolean {
        if (off + 4 > b.size) return false
        for (i in 0..3) if (b[off + i] != id[i].code.toByte()) return false
        return true
    }

    private fun le16(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)

    private fun le32(b: ByteArray, off: Int): Int = le16(b, off) or (le16(b, off + 2) shl 16)

    /** 16bit 多声道 PCM 下混为单声道（各声道取平均）。 */
    private fun downmixToMono(pcm: ByteArray, channels: Int): ByteArray {
        val samples = pcm.size / 2 / channels
        val out = ByteArray(samples * 2)
        for (i in 0 until samples) {
            var sum = 0
            for (c in 0 until channels) {
                val o = (i * channels + c) * 2
                val s = (pcm[o].toInt() and 0xFF) or ((pcm[o + 1].toInt() and 0xFF) shl 8)
                sum += s - 32768
            }
            val v = (sum / channels) + 32768
            val clamped = v.coerceIn(0, 65535)
            val idx = i * 2
            out[idx] = (clamped and 0xFF).toByte()
            out[idx + 1] = ((clamped ushr 8) and 0xFF).toByte()
        }
        return out
    }

    /** 按约 100ms 一块喂送 PCM：一路送 ASR、一路外放；采样率/声道取文件真实值，[token] 失效即停。 */
    private fun feedPcmChunks(audio: ParsedAudio, token: Int, player: AudioTrack) {
        val pcm = audio.pcm
        val bytesPerSec = audio.sampleRate * audio.channels * (audio.bitsPerSample / 8)
        val chunkBytes = maxOf(1, bytesPerSec / 10)
        var offset = 0
        try {
            while (offset < pcm.size &&
                !callback.isDestroyed() &&
                callback.isRecording() &&
                callback.isTokenValid(token)
            ) {
                val end = minOf(offset + chunkBytes, pcm.size)
                val chunk = pcm.copyOfRange(offset, end)
                // ASR 使用原始 PCM，外放使用放大 1 倍后的副本（不影响识别）。
                callback.feedPcmToAsr(chunk, audio.sampleRate, audio.channels)
                val amplified = amplifyPcm16(chunk, PLAYBACK_GAIN)
                player.write(amplified, 0, amplified.size)
                offset = end
                try {
                    Thread.sleep(100)
                } catch (_: InterruptedException) {
                    break
                }
            }
        } finally {
            // 无论正常播完、切换文件还是退出，都释放本路播放器
            if (activePlayer === player) activePlayer = null
            try {
                player.stop()
                player.release()
            } catch (_: Exception) {
            }
        }
        // 文件播完：结束本次识别（触发 isCompleted 结果发送），并自动切回麦克风采集模式
        if (!callback.isDestroyed() && callback.isRecording() && callback.isTokenValid(token)) {
            callback.onFileFinished()
        }
    }

    /**
     * 对 16bit 小端 PCM 数据按 [gain] 倍放大幅度，并做削顶保护（[-32768, 32767]）。
     * 返回新的字节数组，不修改入参（避免影响送往 ASR 的原始数据）。
     */
    private fun amplifyPcm16(data: ByteArray, gain: Float): ByteArray {
        if (gain == 1f) return data
        val out = ByteArray(data.size)
        var i = 0
        while (i + 1 < data.size) {
            val lo = data[i].toInt() and 0xFF
            val hi = data[i + 1].toInt()               // 高字节保留符号
            val sample = (hi shl 8) or lo
            var scaled = (sample * gain).toInt()
            if (scaled > 32767) scaled = 32767
            else if (scaled < -32768) scaled = -32768
            out[i] = (scaled and 0xFF).toByte()
            out[i + 1] = ((scaled shr 8) and 0xFF).toByte()
            i += 2
        }
        // 处理可能存在的落单尾字节
        if (i < data.size) out[i] = data[i]
        return out
    }

    /** 创建用于播放文件 PCM 的 AudioTrack，按文件真实采样率/声道配置。 */
    private fun createFilePlayer(sampleRate: Int, channels: Int): AudioTrack {
        val channelMask =
            if (channels == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setChannelMask(channelMask)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            channelMask,
            AudioFormat.ENCODING_PCM_16BIT
        )
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(format)
            .setBufferSizeInBytes(
                maxOf(minBuf, sampleRate * (if (channels == 2) 2 else 1) * 2 / 10 * 4)
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private fun log(msg: String) {
        Log.d(TAG, msg)
    }
}
