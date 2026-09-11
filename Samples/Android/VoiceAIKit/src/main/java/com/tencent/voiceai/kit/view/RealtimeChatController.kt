// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAIKit - 实时 AI 对话控制器（无 UI 依赖，供 RealtimeChatFragment 驱动）
//
// 持续聆听 + 流式 LLM + 在线 TTS 播报；支持远场/声纹降噪，输入源为麦克风。
//   1) ASR：持续聆听，一句话识别完成（isCompleted=true）即作为用户输入发出。
//   2) LLM：OpenAI 兼容协议流式对话。
//   3) TTS：常驻引擎 + 按句喂送。
//
// 该类不依赖任何 UI 框架，状态变化通过 [Listener] 回调到界面。
// 生命周期由持有者（RealtimeChatFragment）通过 startRecording() / release() 驱动。

package com.tencent.voiceai.kit.view

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.tencent.voiceai.TXRealtimeASR
import com.tencent.voiceai.TXRealtimeTTS
import com.tencent.voiceai.kit.VoiceAIKit
import com.tencent.voiceai.kit.common.VoiceAIKitDefaults
import com.tencent.voiceai.kit.common.TXAIChatClient
import com.tencent.voiceai.kit.common.TXAIChatClient.ChatMessage as LlmChatMessage
import com.tencent.voiceai.kit.common.VoiceAIPrefs
import org.json.JSONObject
import java.io.File

/** UI 展示的一条对话消息。 */
data class RealtimeMessage(
    val id: Long,
    val role: String, // "user" / "assistant"
    val content: String,
    val streaming: Boolean = false, // assistant 是否正在流式输出
    val partial: Boolean = false // user 消息是否为「正在识别中」的临时态
)

/** 降噪算法。 */
enum class DenoiseMode {
    NONE, FAR_FIELD, VOICEPRINT
}

class RealtimeChatController(context: Context) : TXRealtimeASR.Listener {

    /** 界面状态回调；所有回调均在主线程触发。 */
    interface Listener {
        fun onMessagesChanged(messages: List<RealtimeMessage>)
        fun onRecordingChanged(recording: Boolean)
        fun onThinkingChanged(thinking: Boolean)
        fun onSpeakingChanged(speaking: Boolean)
        fun onTtsEnabledChanged(enabled: Boolean)
        fun onDenoiseModeChanged(mode: DenoiseMode)
        fun onToast(message: String)
    }

    companion object {
        private const val TAG = "RealtimeChatController"
        private const val MAX_HISTORY = 20
        private const val VOLUME_ON = 80f
        private const val VOLUME_OFF = 0f
        private val SENTENCE_ENDERS =
            charArrayOf('，', '。', '！', '？', '.', '!', '?', '\n', '；', ';')
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val appPrefs = VoiceAIPrefs.getInstance(appContext)
    private var textIdSeq = 0

    private val kitParams: VoiceAIKit.VoiceAIKitParams get() = VoiceAIKit.requireParams()

    private var listener: Listener? = null

    fun setListener(l: Listener?) {
        listener = l
        l?.onMessagesChanged(messages.toList())
        l?.onRecordingChanged(recording)
        l?.onThinkingChanged(thinking)
        l?.onSpeakingChanged(speaking)
        l?.onTtsEnabledChanged(ttsEnabled)
        l?.onDenoiseModeChanged(denoiseMode)
    }

    private val ttsVoice: String get() = kitParams.ttsVoiceId

    private val history = mutableListOf<LlmChatMessage>()
    private val systemPrompt get() = LlmChatMessage("system", VoiceAIKitDefaults.SYSTEM_PROMPT)

    /* ---------------- 对外状态 ---------------- */

    val messages = mutableListOf<RealtimeMessage>()

    var recording: Boolean = false
        private set

    var thinking: Boolean = false
        private set

    var speaking: Boolean = false
        private set

    var partialInput: String = ""
        private set

    var ttsEnabled: Boolean = true
        private set

    var denoiseMode: DenoiseMode = loadDenoiseMode()
        private set

    fun toggleTts(enabled: Boolean) {
        ttsEnabled = enabled
        applyTtsVolume()
        listener?.onTtsEnabledChanged(enabled)
    }

    private fun applyTtsVolume() {
        tts?.setVolume(if (ttsEnabled) VOLUME_ON else VOLUME_OFF)
    }

    /* ---------------- 降噪 ---------------- */

    fun selectDenoiseMode(mode: DenoiseMode) {
        if (denoiseMode == mode) return
        denoiseMode = mode
        saveDenoiseMode(mode)
        listener?.onDenoiseModeChanged(mode)
        if (recording) applyDenoiseConfig()
    }

    private fun loadDenoiseMode(): DenoiseMode =
        when (VoiceAIPrefs.getInstance(appContext).realtimeDenoiseMode) {
            "farField" -> DenoiseMode.FAR_FIELD
            "voiceprint" -> DenoiseMode.VOICEPRINT
            else -> DenoiseMode.NONE
        }

    private fun saveDenoiseMode(mode: DenoiseMode) {
        val value = when (mode) {
            DenoiseMode.NONE -> "none"
            DenoiseMode.FAR_FIELD -> "farField"
            DenoiseMode.VOICEPRINT -> "voiceprint"
        }
        VoiceAIPrefs.getInstance(appContext).realtimeDenoiseMode = value
    }

    /* ---------------- LLM ---------------- */

    private fun newLlmClient() = TXAIChatClient(
        kitParams.llm.apiUrl,
        kitParams.llm.apiKey,
        kitParams.llm.model
    )

    private var currentLlm: TXAIChatClient? = null
    private var currentTurn = 0

    private var pendingUserId: Long = -1L
    private var pendingAssistantId: Long = -1L

    /* ---------------- SDK ---------------- */

    private val asr: TXRealtimeASR = TXRealtimeASR()

    private var tts: TXRealtimeTTS? = null

    private var destroyed = false

    /** 停止后是否自动重开一次（降噪配置变更等场景使用）。 */
    private var restartAfterStop = false

    init {
        asr.addListener(this)
        mainHandler.post { startTtsEngine() }
    }

    /* ---------------- ASR：持续聆听 ---------------- */

    fun notifyMicPermissionDenied() {
        emitToast("未授予麦克风权限，无法进行语音对话")
    }

    fun toggleMic() {
        if (recording) stopRecording() else startRecording()
    }

    fun startRecording() {
        if (recording || destroyed) return
        currentTurn++
        currentLlm?.cancel()
        interruptSpeaking()
        setThinking(false)
        pendingUserId = -1L
        pendingAssistantId = -1L
        partialInput = ""
        setRecording(true)

        val voiceId = kitParams.userId
        val asrParams = TXRealtimeASR.Params().apply {
            sdkAppId = kitParams.appId.toString()
            userSig = kitParams.userSig
            this.voiceId = voiceId
            sourceLanguage = ""
            enableCustomCapture = false
        }
        applyDenoiseConfig()
        asr.startRealtimeASR(asrParams)
        log("ASR start voiceId=$voiceId")
    }

    fun stopRecording() {
        if (!recording) return
        asr.stopRealtimeASR()
    }

    private fun restartRecording() {
        if (destroyed) return
        if (recording) {
            restartAfterStop = true
            asr.stopRealtimeASR()
        } else {
            startRecording()
        }
    }

    private fun applyDenoiseConfig() {
        when (denoiseMode) {
            DenoiseMode.NONE -> {
                asr.callExperimentalAPI(
                    "{\"api\":\"setExtraParams\",\"params\":{\"clientDenoiseStrategy\":0}}"
                )
                asr.callExperimentalAPI(
                    "{\"api\":\"enableVoiceprintDenoise\",\"params\":{\"enable\":false}}"
                )
            }
            DenoiseMode.FAR_FIELD -> {
                asr.callExperimentalAPI(
                    "{\"api\":\"setExtraParams\",\"params\":{\"clientDenoiseStrategy\":2}}"
                )
                asr.callExperimentalAPI(
                    "{\"api\":\"enableVoiceprintDenoise\",\"params\":{\"enable\":false}}"
                )
            }
            DenoiseMode.VOICEPRINT -> {
                val params = JSONObject()
                    .put("enable", true)
                    .put("voiceprint_file_path", voiceprintFilePath())
                    .put("infer_frame_counter", 5)
                    .put("target_threshold", 0.4)
                    .put("infer_window_size_ms", 1000)
                    .put("algorithm_version", 1)
                val apiCall = JSONObject()
                    .put("api", "enableVoiceprintDenoise")
                    .put("params", params)
                asr.callExperimentalAPI(apiCall.toString())
            }
        }
    }

    /** 已注册声纹 WAV 文件的绝对路径；文件保存在 filesDir 下，未注册返回空串。 */
    private fun voiceprintFilePath(): String {
        val name = appPrefs.voiceprintFileName
        if (name.isEmpty()) return ""
        val file = File(appContext.filesDir, name)
        return if (file.exists()) file.absolutePath else ""
    }

    private var partialMessageId: Long = -1L

    override fun onRealtimeASRStarted(session: String) {
        log("ASR started session=$session")
    }

    override fun onReceiveRealtimeASRMessage(message: TXRealtimeASR.Message) {
        val text = message.sourceText ?: ""
        if (message.isCompleted) {
            val question = text.trim()
            mainHandler.post {
                partialInput = ""
                if (question.isNotEmpty()) finalizePartialAndSend(question)
                else removePartialMessage()
            }
        } else {
            mainHandler.post {
                partialInput = text
                if (text.isNotEmpty()) updatePartialUserMessage(text)
            }
        }
    }

    private fun updatePartialUserMessage(text: String) {
        if (partialMessageId < 0L) {
            if (pendingUserId >= 0L) silentInterruptForMerge() else interruptResponse()
            val id = nextId()
            partialMessageId = id
            messages.add(RealtimeMessage(id = id, role = "user", content = text, partial = true))
        } else {
            val idx = messages.indexOfLast { it.id == partialMessageId }
            if (idx >= 0) messages[idx] = messages[idx].copy(content = text)
        }
        notifyMessages()
    }

    private fun silentInterruptForMerge() {
        currentLlm?.cancel()
        interruptSpeaking()
        setThinking(false)
    }

    private fun removePartialMessage() {
        if (partialMessageId >= 0L) {
            val idx = messages.indexOfLast { it.id == partialMessageId }
            if (idx >= 0) messages.removeAt(idx)
            partialMessageId = -1L
            notifyMessages()
        }
    }

    override fun onRealtimeASRStopped() {
        mainHandler.post {
            setRecording(false)
            partialInput = ""
            removePartialMessage()
            if (restartAfterStop) {
                restartAfterStop = false
                startRecording()
            }
        }
    }

    override fun onRealtimeASRError(code: Int, msg: String?) {
        mainHandler.post {
            setRecording(false)
            partialInput = ""
            removePartialMessage()
            emitToast("语音识别错误 $code: $msg")
        }
    }

    override fun onRealtimeASRVolume(volume: Int) {
        // 实时对话页不做波形展示
    }

    /* ---------------- LLM ---------------- */

    fun interruptResponse() {
        currentTurn++
        currentLlm?.cancel()
        interruptSpeaking()
        setThinking(false)
        val idx = messages.indexOfLast { it.role == "assistant" && it.streaming }
        if (idx >= 0) {
            val m = messages[idx]
            if (m.content.isBlank()) messages.removeAt(idx)
            else messages[idx] = m.copy(streaming = false)
            notifyMessages()
        }
        pendingUserId = -1L
        pendingAssistantId = -1L
        log("interruptResponse: 已打断当前回复")
    }

    fun sendUserMessage(text: String) {
        val content = text.trim()
        if (content.isEmpty()) return
        val uid = nextId()
        messages.add(RealtimeMessage(id = uid, role = "user", content = content))
        notifyMessages()
        appendHistory(LlmChatMessage("user", content))
        startLlmRound(reusedUserId = uid)
    }

    private fun finalizePartialAndSend(question: String) {
        if (pendingUserId >= 0L) {
            if (partialMessageId >= 0L) {
                val pIdx = messages.indexOfLast { it.id == partialMessageId }
                if (pIdx >= 0) messages.removeAt(pIdx)
                partialMessageId = -1L
            }

            val uIdx = messages.indexOfLast { it.id == pendingUserId }
            val merged: String
            if (uIdx >= 0) {
                merged = joinUserText(messages[uIdx].content, question)
                messages[uIdx] = messages[uIdx].copy(content = merged, partial = false)
            } else {
                merged = question
                messages.add(RealtimeMessage(id = nextId(), role = "user", content = merged))
            }

            if (history.isNotEmpty() && history.last().role == "user") {
                history[history.size - 1] = LlmChatMessage("user", merged)
            } else {
                history.add(LlmChatMessage("user", merged))
            }

            if (pendingAssistantId >= 0L) {
                val aIdx = messages.indexOfLast { it.id == pendingAssistantId }
                if (aIdx >= 0 && messages[aIdx].content.isBlank()) messages.removeAt(aIdx)
                pendingAssistantId = -1L
            }
            pendingUserId = -1L
            notifyMessages()
            log("finalizePartialAndSend: 合并上一句, text=$merged")
            startLlmRound(reusedUserId = uIdx.takeIf { it >= 0 }?.let { messages[it].id })
            return
        }

        val newUserId: Long
        if (partialMessageId >= 0L) {
            val idx = messages.indexOfLast { it.id == partialMessageId }
            if (idx >= 0) {
                messages[idx] = messages[idx].copy(content = question, partial = false)
                newUserId = messages[idx].id
            } else {
                newUserId = nextId()
                messages.add(RealtimeMessage(id = newUserId, role = "user", content = question))
            }
            partialMessageId = -1L
        } else {
            newUserId = nextId()
            messages.add(RealtimeMessage(id = newUserId, role = "user", content = question))
        }
        notifyMessages()
        appendHistory(LlmChatMessage("user", question))
        startLlmRound(reusedUserId = newUserId)
    }

    private fun joinUserText(prev: String, next: String): String {
        val p = prev.trim()
        val n = next.trim()
        if (p.isEmpty()) return n
        if (n.isEmpty()) return p
        val enders = "。！？.!?；;"
        return if (enders.indexOf(p.last()) >= 0) "$p$n" else "$p，$n"
    }

    private fun startLlmRound(reusedUserId: Long? = null) {
        if (!kitParams.llm.isConfigured) {
            emitToast("未配置 LLM 服务地址与密钥，无法进行 AI 对话")
            return
        }

        val turn = ++currentTurn
        currentLlm?.cancel()
        interruptSpeaking()

        val assistantId = nextId()
        messages.add(
            RealtimeMessage(id = assistantId, role = "assistant", content = "", streaming = true)
        )
        setThinking(true)
        notifyMessages()

        pendingUserId = reusedUserId ?: -1L
        pendingAssistantId = assistantId

        val reqMessages = ArrayList<LlmChatMessage>().apply {
            add(systemPrompt)
            addAll(history)
        }

        val ttsTurnId = "chat_rt_${textIdSeq++}"
        val replyBuilder = StringBuilder()
        val ttsBuffer = StringBuilder()

        val client = newLlmClient()
        currentLlm = client

        Thread {
            client.streamChat(reqMessages, object : TXAIChatClient.StreamCallback {
                override fun onDelta(delta: String) {
                    if (turn != currentTurn) return
                    replyBuilder.append(delta)
                    val cur = replyBuilder.toString()
                    mainHandler.post {
                        if (turn != currentTurn) return@post
                        clearPendingIfMatch(assistantId)
                        updateAssistantMessage(assistantId, cur, streaming = true)
                    }
                    ttsBuffer.append(delta)
                    flushTtsSentences(ttsTurnId, ttsBuffer)
                }

                override fun onCompleted(fullText: String) {
                    if (turn != currentTurn) return
                    mainHandler.post {
                        if (turn != currentTurn) return@post
                        clearPendingIfMatch(assistantId)
                        updateAssistantMessage(assistantId, fullText, streaming = false)
                        appendHistory(LlmChatMessage("assistant", fullText))
                        setThinking(false)
                    }
                    flushTtsRemainder(ttsTurnId, ttsBuffer)
                }

                override fun onError(message: String) {
                    if (turn != currentTurn) return
                    mainHandler.post {
                        if (turn != currentTurn) return@post
                        clearPendingIfMatch(assistantId)
                        val cur = replyBuilder.toString()
                        val shown = if (cur.isEmpty()) "(请求失败) $message" else cur
                        updateAssistantMessage(assistantId, shown, streaming = false)
                        if (replyBuilder.isNotEmpty()) {
                            appendHistory(LlmChatMessage("assistant", cur))
                        }
                        setThinking(false)
                        emitToast("对话失败: $message")
                    }
                }
            })
        }.start()
    }

    private fun clearPendingIfMatch(assistantId: Long) {
        if (pendingAssistantId == assistantId) {
            pendingUserId = -1L
            pendingAssistantId = -1L
        }
    }

    private fun updateAssistantMessage(id: Long, content: String, streaming: Boolean) {
        val idx = messages.indexOfLast { it.id == id }
        if (idx >= 0) {
            messages[idx] = messages[idx].copy(content = content, streaming = streaming)
            notifyMessages()
        }
    }

    private fun appendHistory(msg: LlmChatMessage) {
        history.add(msg)
        while (history.size > MAX_HISTORY) {
            history.removeAt(0)
        }
    }

    /* ---------------- TTS：常驻引擎 ---------------- */

    private val ttsListener = object : TXRealtimeTTS.TXRealtimeTTSListener {
        override fun onStarted() {
            mainHandler.post { setSpeaking(true) }
        }

        override fun onPlaybackProgress(textId: String?, textSlice: String?) {}

        override fun onSynthesizedAudioFrame(audioFrame: TXRealtimeTTS.TXSynthesizeAudioFrame?) {}

        override fun onCompleted(code: Int, msg: String?) {
            mainHandler.post {
                setSpeaking(false)
                if (code != TXRealtimeTTS.TXRealtimeTTSError.OK) {
                    emitToast("语音播报失败: $msg")
                }
            }
        }
    }

    private fun startTtsEngine() {
        if (destroyed || tts != null) return

        val voice = ttsVoice
        val sdkAppId = kitParams.appId
        val engine = TXRealtimeTTS().apply { setListener(ttsListener) }
        tts = engine

        val params = TXRealtimeTTS.TXRealtimeTTSParams().apply {
            mode = TXRealtimeTTS.TXRealtimeTTSMode.ONLINE
            audioOutputMode = TXRealtimeTTS.TXRealtimeTTSAudioOutputMode.PLAYBACK_ONLY
            voiceName = voice
            online.appId = sdkAppId.toString()
            online.userId = kitParams.userId
            online.userSig = kitParams.userSig
        }

        val code = engine.start(params)
        log("TTS(online) start code=$code")
        if (code != TXRealtimeTTS.TXRealtimeTTSError.OK) {
            emitToast("语音播报启动失败: $code")
        } else {
            applyTtsVolume()
        }
    }

    private fun flushTtsSentences(textId: String, buffer: StringBuilder) {
        val engine = tts ?: return
        while (true) {
            val s = buffer.toString()
            val cut = s.indexOfFirst { it in SENTENCE_ENDERS }
            if (cut < 0) break
            val sentence = s.substring(0, cut + 1)
            buffer.delete(0, cut + 1)
            if (sentence.isNotBlank()) engine.appendText(textId, sentence, false)
        }
    }

    private fun flushTtsRemainder(textId: String, buffer: StringBuilder) {
        val engine = tts ?: return
        val rest = buffer.toString()
        buffer.setLength(0)
        if (rest.isNotBlank()) engine.appendText(textId, rest, false)
    }

    private fun interruptSpeaking() {
        tts?.clear()
        setSpeaking(false)
    }

    private fun stopSpeaking() {
        tts?.let {
            it.stop()
            it.setListener(null)
            it.destroy()
        }
        tts = null
        setSpeaking(false)
    }

    /* ---------------- 生命周期 ---------------- */

    /** 页面退出时调用：停止识别与播报，释放 ASR / TTS 实例。 */
    fun release() {
        if (destroyed) return
        destroyed = true

        currentTurn++
        currentLlm?.cancel()
        restartAfterStop = false
        try {
            if (recording) asr.stopRealtimeASR()
        } catch (_: Exception) {}
        asr.removeListener(this)
        asr.destroy()
        stopSpeaking()
        listener = null
        log("release: 资源已释放")
    }

    /* ---------------- 状态回调辅助 ---------------- */

    private fun notifyMessages() {
        listener?.onMessagesChanged(messages.toList())
    }

    private fun setRecording(value: Boolean) {
        recording = value
        listener?.onRecordingChanged(value)
    }

    private fun setThinking(value: Boolean) {
        thinking = value
        listener?.onThinkingChanged(value)
    }

    private fun setSpeaking(value: Boolean) {
        speaking = value
        listener?.onSpeakingChanged(value)
    }

    private fun emitToast(msg: String) {
        listener?.onToast(msg)
    }

    private fun nextId(): Long = System.nanoTime()

    private fun log(msg: String) {
        Log.d(TAG, msg)
    }
}
