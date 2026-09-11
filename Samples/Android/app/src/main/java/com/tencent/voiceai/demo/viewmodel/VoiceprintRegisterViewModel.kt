// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAI Demo - 声纹注册 ViewModel

package com.tencent.voiceai.demo.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tencent.voiceai.kit.common.VoiceAIPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 声纹注册 ViewModel。
 *
 * 负责：
 * - 使用 [AudioRecord] 采集 16kHz / 单声道 / 16bit PCM，写为 WAV 文件；
 * - 强制最短录制时长 [MIN_DURATION_MS]（10 秒），未达时长不允许结束；
 * - 使用 [MediaPlayer] 回放已注册的声纹文件；
 * - 成功注册的文件名带时间戳，并通过 [VoiceAIPrefs] 记录最近一次注册结果；
 * - 退出页面时停止录制并丢弃当前未完成的录音数据。
 *
 * 采集参数固定为 16kHz、单声道、PCM 16bit，符合声纹注册对音频格式的要求。
 */
class VoiceprintRegisterViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = VoiceAIPrefs.getInstance(application)

    /** 已注册文件名（无则为 null）。 */
    var registeredFileName by mutableStateOf<String?>(null)
        private set

    /** 已注册文件时长文本，如 "12.3s"。 */
    var registeredDurationText by mutableStateOf<String?>(null)
        private set

    /** 是否正在录制。 */
    var isRecording by mutableStateOf(false)
        private set

    /** 是否正在播放。 */
    var isPlaying by mutableStateOf(false)
        private set

    /** 当前录制已进行的毫秒数（用于界面显示与最短时长判断）。 */
    var recordedMs by mutableStateOf(0L)
        private set

    /** 一次性提示信息（错误 / 结果），界面消费后可清空。 */
    var toastMessage by mutableStateOf<String?>(null)
        private set

    private var recordJob: Job? = null
    private var audioRecord: AudioRecord? = null
    @Volatile
    private var recording = false

    private var mediaPlayer: MediaPlayer? = null

    /** 录制过程中使用的临时文件（未完成/被丢弃的数据都写在这里）。 */
    private val tempFile: File
        get() = File(getApplication<Application>().filesDir, TEMP_FILE_NAME)

    /** 当前已注册文件（依据偏好中记录的文件名）。 */
    private val registeredFile: File?
        get() {
            val name = prefs.voiceprintFileName
            if (name.isEmpty()) return null
            return File(getApplication<Application>().filesDir, name)
        }

    init {
        refreshRegistered()
    }

    /** 从偏好 + 磁盘刷新已注册文件的信息（文件名 + 时长）。 */
    private fun refreshRegistered() {
        val file = registeredFile
        if (file != null && file.exists() && file.length() > WAV_HEADER_SIZE) {
            registeredFileName = file.name
            val dataBytes = file.length() - WAV_HEADER_SIZE
            val seconds = dataBytes.toDouble() / (SAMPLE_RATE * CHANNELS * BYTES_PER_SAMPLE)
            registeredDurationText = String.format(Locale.US, "%.1fs", seconds)
        } else {
            registeredFileName = null
            registeredDurationText = null
        }
    }

    /** 界面显示用：本次录制进度文本，如 "00:08"。 */
    fun elapsedText(): String {
        val totalSec = recordedMs / 1000
        val mm = totalSec / 60
        val ss = totalSec % 60
        return String.format(Locale.US, "%02d:%02d", mm, ss)
    }

    /** 是否已达到最短注册时长。 */
    fun reachedMinDuration(): Boolean = recordedMs >= MIN_DURATION_MS

    fun consumeToast() {
        toastMessage = null
    }

    /** 开始录制。若正在播放则先停止播放。 */
    @SuppressLint("MissingPermission")
    fun startRecording() {
        if (isRecording) return
        stopPlayback()

        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) {
            toastMessage = "无法初始化录音设备"
            return
        }
        val bufferSize = minBuf * 2

        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        } catch (e: Exception) {
            toastMessage = "录音初始化失败：${e.message}"
            null
        } ?: return

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            toastMessage = "录音初始化失败"
            return
        }

        audioRecord = record
        recording = true
        isRecording = true
        recordedMs = 0L

        recordJob = viewModelScope.launch(Dispatchers.IO) {
            val tmp = tempFile
            var totalDataBytes = 0L
            try {
                RandomAccessFile(tmp, "rw").use { raf ->
                    raf.setLength(0)
                    // 先占位写入 44 字节 WAV 头，录制结束后回填。
                    raf.write(ByteArray(WAV_HEADER_SIZE))

                    record.startRecording()
                    val buffer = ByteArray(bufferSize)
                    val startTs = System.currentTimeMillis()
                    while (recording) {
                        val read = record.read(buffer, 0, buffer.size)
                        if (read > 0) {
                            raf.write(buffer, 0, read)
                            totalDataBytes += read
                        }
                        val elapsed = System.currentTimeMillis() - startTs
                        withContext(Dispatchers.Main) { recordedMs = elapsed }
                    }

                    // 回填 WAV 头。
                    raf.seek(0)
                    raf.write(buildWavHeader(totalDataBytes))
                }
            } catch (e: Exception) {
                tmp.delete()
                withContext(Dispatchers.Main) { toastMessage = "录制失败：${e.message}" }
            } finally {
                try {
                    record.stop()
                } catch (_: Exception) {
                }
                record.release()
                if (audioRecord === record) audioRecord = null
                withContext(Dispatchers.Main) { isRecording = false }
            }
        }
    }

    /**
     * 停止录制。
     * - 未达最短时长：丢弃本次录制并提示；
     * - 达到最短时长：将临时文件保存为带时间戳的正式文件并记录到偏好。
     */
    fun stopRecording() {
        if (!isRecording) return
        val reached = reachedMinDuration()
        recording = false

        viewModelScope.launch {
            recordJob?.join()
            if (!reached) {
                withContext(Dispatchers.IO) { tempFile.delete() }
                toastMessage = "注册时长至少需要 ${MIN_DURATION_MS / 1000} 秒"
                return@launch
            }

            val savedName = withContext(Dispatchers.IO) { commitTempAsRegistered() }
            if (savedName != null) {
                refreshRegistered()
                toastMessage = "声纹注册成功"
            } else {
                toastMessage = "保存录音失败"
            }
        }
    }

    /**
     * 退出页面时调用：停止录制并丢弃当前未完成的录音数据（不生成正式文件）。
     * 同时停止正在进行的播放。
     */
    fun cancelRecording() {
        stopPlayback()
        if (!isRecording && recordJob == null) {
            // 未在录制，仍清理可能残留的临时文件。
            viewModelScope.launch(Dispatchers.IO) { tempFile.delete() }
            return
        }
        recording = false
        val job = recordJob
        viewModelScope.launch {
            job?.join()
            withContext(Dispatchers.IO) { tempFile.delete() }
        }
    }

    /**
     * 将临时录音文件提交为带时间戳的正式声纹文件。
     * 返回保存后的文件名；失败返回 null。必须在 IO 线程调用。
     */
    private fun commitTempAsRegistered(): String? {
        val tmp = tempFile
        if (!tmp.exists() || tmp.length() <= WAV_HEADER_SIZE) return null

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val newName = "voiceprint_$timestamp.wav"
        val target = File(getApplication<Application>().filesDir, newName)

        // 删除上一份已注册文件，避免残留占用空间。
        registeredFile?.takeIf { it.exists() }?.delete()

        if (target.exists()) target.delete()
        val ok = tmp.renameTo(target)
        if (!ok) return null

        prefs.voiceprintFileName = newName
        return newName
    }

    /** 播放已注册文件。 */
    fun togglePlayback() {
        if (isPlaying) {
            stopPlayback()
            return
        }
        val file = registeredFile
        if (file == null || !file.exists()) {
            toastMessage = "暂无可播放的声纹文件"
            return
        }
        try {
            val player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener { stopPlayback() }
                setOnErrorListener { _, _, _ ->
                    stopPlayback()
                    true
                }
                prepare()
                start()
            }
            mediaPlayer = player
            isPlaying = true
        } catch (e: Exception) {
            toastMessage = "播放失败：${e.message}"
            stopPlayback()
        }
    }

    private fun stopPlayback() {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
            } catch (_: Exception) {
            }
            it.release()
        }
        mediaPlayer = null
        isPlaying = false
    }

    override fun onCleared() {
        super.onCleared()
        recording = false
        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }
        audioRecord?.release()
        audioRecord = null
        stopPlayback()
        // 丢弃可能残留的临时录音数据。
        tempFile.delete()
    }

    /** 构造 44 字节标准 WAV 头（PCM 16bit）。 */
    private fun buildWavHeader(dataBytes: Long): ByteArray {
        val totalDataLen = dataBytes + 36
        val byteRate = SAMPLE_RATE * CHANNELS * BYTES_PER_SAMPLE
        val bb = ByteBuffer.allocate(WAV_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray(Charsets.US_ASCII))
        bb.putInt(totalDataLen.toInt())
        bb.put("WAVE".toByteArray(Charsets.US_ASCII))
        bb.put("fmt ".toByteArray(Charsets.US_ASCII))
        bb.putInt(16)                                  // Subchunk1Size (PCM)
        bb.putShort(1)                                 // AudioFormat = PCM
        bb.putShort(CHANNELS.toShort())                // NumChannels
        bb.putInt(SAMPLE_RATE)                         // SampleRate
        bb.putInt(byteRate)                            // ByteRate
        bb.putShort((CHANNELS * BYTES_PER_SAMPLE).toShort()) // BlockAlign
        bb.putShort((BYTES_PER_SAMPLE * 8).toShort())  // BitsPerSample
        bb.put("data".toByteArray(Charsets.US_ASCII))
        bb.putInt(dataBytes.toInt())
        return bb.array()
    }

    companion object {
        /** 采样率 16kHz。 */
        const val SAMPLE_RATE = 16000

        /** 单声道。 */
        const val CHANNELS = 1

        /** 16bit = 2 字节。 */
        const val BYTES_PER_SAMPLE = 2

        /** 最短注册时长 10 秒。 */
        const val MIN_DURATION_MS = 10_000L

        private const val WAV_HEADER_SIZE = 44

        /** 提示词，用于注册过程中照着念。 */
        const val PROMPT_TEXT =
            "今天天气晴朗，我们一起去公园参加跑步比赛，" +
                "比赛结束后在树下休息，微风轻轻吹过，树叶沙沙作响，" +
                "这种宁静的感觉让我觉得非常放松和愉快"

        private const val TEMP_FILE_NAME = "voiceprint_register.tmp.wav"
    }
}
