// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAIKit - AI 对话控制器（无 UI 依赖，供 AIChatFragment 驱动）
//
// 串联两段能力：
//   1) LLM：OpenAI 兼容协议流式对话（TXAIChatClient，SSE 流式增量）。
//   2) TTS：把回复播报出来（TXRealtimeTTS，常驻引擎 + 按句喂送，在线合成）。
//
// 语音输入（ASR）不在本控制器内：由输入栏组件 AIChatInputBar 自持，
// 识别结果通过 AIChatInputBar.onTextRecognized 交给宿主，再调用 [sendText] 发送。
//
// 会话历史只保留最近 MAX_HISTORY 条（user/assistant），请求时拼接 [system] + history。
//
// 该类不依赖任何 UI 框架，状态变化通过 [Listener] 回调到界面。
// 生命周期由持有者（AIChatFragment）通过 onEnterPage() / onLeavePage() / release() 驱动。

package com.tencent.voiceai.kit.view

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.tencent.voiceai.TXRealtimeTTS
import com.tencent.voiceai.kit.VoiceAIKit
import com.tencent.voiceai.kit.common.VoiceAIKitDefaults
import com.tencent.voiceai.kit.common.TXAIChatClient
import com.tencent.voiceai.kit.common.TXAIChatClient.ChatMessage as LlmChatMessage
import com.tencent.voiceai.kit.common.VoiceAIPrefs
import com.tencent.voiceai.kit.debug.GenerateTestUserSig

/** UI 展示的一条对话气泡。 */
data class ChatBubble(
    val id: Long,
    val role: String,               // "user" / "assistant"
    val content: String,
    val streaming: Boolean = false, // assistant 是否正在流式输出
    val error: Boolean = false      // assistant 该条是否请求失败
)

/**
 * AI 对话控制器。
 *
 * @param context 使用 applicationContext，避免持有 Activity 引用。
 */
class AIChatController(context: Context) {

    /** 界面状态回调；所有回调均在主线程触发。 */
    interface Listener {
        /** 气泡列表整体变化（新增 / 更新 / 删除）。 */
        fun onBubblesChanged(bubbles: List<ChatBubble>)

        /** 自动朗读开关变化。 */
        fun onAutoSpeakChanged(autoSpeak: Boolean)

        /** 一次性提示。 */
        fun onToast(message: String)
    }

    companion object {
        private const val TAG = "AIChatController"
        private const val MAX_HISTORY = 20
        private const val VOLUME_ON = 80f
        private const val VOLUME_OFF = 0f
        private val SENTENCE_ENDERS = charArrayOf('。', '！', '？', '.', '!', '?', '\n', '；', ';')
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val appPrefs = VoiceAIPrefs.getInstance(appContext)

    private val kitParams: VoiceAIKit.VoiceAIKitParams get() = VoiceAIKit.requireParams()

    private var listener: Listener? = null

    fun setListener(l: Listener?) {
        listener = l
        // 绑定时同步一次当前状态
        l?.onBubblesChanged(bubbles.toList())
        l?.onAutoSpeakChanged(autoSpeak)
    }

    private val ttsVoice: String get() = kitParams.ttsVoiceId

    // ---- 会话历史（不含 system）----
    private val history = mutableListOf<LlmChatMessage>()
    private val systemPrompt get() = LlmChatMessage("system", VoiceAIKitDefaults.SYSTEM_PROMPT)

    private fun newLlmClient() = TXAIChatClient(
        kitParams.llm.apiUrl,
        kitParams.llm.apiKey,
        kitParams.llm.model
    )

    private var currentLlm: TXAIChatClient? = null
    private var currentTurn = 0

    /* ---------------- 对外状态（普通字段，变化经 listener 回调） ---------------- */

    val bubbles = mutableListOf<ChatBubble>()

    /** 底部输入栏模式：false = 文字输入，true = 语音输入（按住说话）。取自本地存储。 */
    var voiceMode: Boolean = appPrefs.chatInputMode
        private set

    var autoSpeak: Boolean = true
        private set

    var thinking: Boolean = false
        private set

    var speaking: Boolean = false
        private set

    private var ttsTextSeq = 0

    /* ---------------- TTS：常驻引擎 ---------------- */

    private var tts: TXRealtimeTTS? = null

    private val ttsListener = object : TXRealtimeTTS.TXRealtimeTTSListener {
        override fun onStarted() {
            mainHandler.post { speaking = true }
        }

        override fun onPlaybackProgress(textId: String?, textSlice: String?) {}

        override fun onSynthesizedAudioFrame(audioFrame: TXRealtimeTTS.TXSynthesizeAudioFrame?) {}

        override fun onCompleted(code: Int, msg: String?) {
            mainHandler.post {
                speaking = false
                if (code != TXRealtimeTTS.TXRealtimeTTSError.OK) {
                    emitToast("语音播报失败: $msg")
                }
            }
            log("tts onCompleted code=$code msg=$msg")
        }
    }

    init {
        appendBubble(
            role = "assistant",
            content = "你好，我是 VoiceAI 助手。可以直接输入文字，也可以切换到语音模式按住说话。"
        )
    }

    /* ---------------- 文本对话 ---------------- */

    /** 切换键盘 / 语音输入模式并持久化。 */
    fun toggleVoiceMode(): Boolean {
        voiceMode = !voiceMode
        appPrefs.chatInputMode = voiceMode
        return voiceMode
    }

    /** 发送一段文本内容，触发 LLM 流式回复。 */
    fun sendText(content: String) {
        val text = content.trim()
        if (text.isEmpty()) return
        sendUserMessage(text)
    }

    private fun sendUserMessage(content: String) {
        appendBubble(role = "user", content = content)
        appendHistory(LlmChatMessage("user", content))
        startLlmRound()
    }

    fun retryLast() {
        if (thinking) return
        val idx = bubbles.indexOfLast { it.role == "assistant" }
        if (idx < 0 || !bubbles[idx].error) return
        bubbles.removeAt(idx)
        if (history.isNotEmpty() && history.last().role == "assistant") {
            history.removeAt(history.lastIndex)
        }
        notifyBubbles()
        startLlmRound()
    }

    fun toggleAutoSpeak() {
        autoSpeak = !autoSpeak
        applyTtsVolume()
        listener?.onAutoSpeakChanged(autoSpeak)
    }

    /* ---------------- LLM ---------------- */

    private fun startLlmRound() {
        if (!kitParams.llm.isConfigured) {
            emitToast("未配置 LLM 服务地址与密钥，无法进行 AI 对话")
            return
        }

        val turn = ++currentTurn
        currentLlm?.cancel()
        interruptSpeaking()

        val assistantId = nextId()
        bubbles.add(ChatBubble(id = assistantId, role = "assistant", content = "", streaming = true))
        thinking = true
        notifyBubbles()

        val messages = ArrayList<LlmChatMessage>().apply {
            add(systemPrompt)
            addAll(history)
        }

        val ttsTurnId = "chat_${ttsTextSeq++}"
        val replyBuilder = StringBuilder()
        val ttsBuffer = StringBuilder()

        val client = newLlmClient()
        currentLlm = client

        Thread {
            client.streamChat(messages, object : TXAIChatClient.StreamCallback {
                override fun onDelta(delta: String) {
                    if (turn != currentTurn) return
                    replyBuilder.append(delta)
                    val cur = replyBuilder.toString()
                    mainHandler.post {
                        if (turn != currentTurn) return@post
                        updateBubble(assistantId, cur, streaming = true)
                    }
                    ttsBuffer.append(delta)
                    flushTtsSentences(ttsTurnId, ttsBuffer)
                }

                override fun onCompleted(fullText: String) {
                    if (turn != currentTurn) return
                    mainHandler.post {
                        if (turn != currentTurn) return@post
                        updateBubble(assistantId, fullText, streaming = false)
                        appendHistory(LlmChatMessage("assistant", fullText))
                        thinking = false
                    }
                    flushTtsRemainder(ttsTurnId, ttsBuffer)
                }

                override fun onError(message: String) {
                    if (turn != currentTurn) return
                    mainHandler.post {
                        if (turn != currentTurn) return@post
                        val cur = replyBuilder.toString()
                        updateBubble(
                            id = assistantId,
                            content = if (cur.isEmpty()) "请求失败：$message" else cur,
                            streaming = false,
                            error = cur.isEmpty()
                        )
                        if (replyBuilder.isNotEmpty()) {
                            appendHistory(LlmChatMessage("assistant", cur))
                        }
                        thinking = false
                        emitToast("对话失败: $message")
                    }
                }
            })
        }.start()
    }

    /* ---------------- TTS：常驻引擎 ---------------- */

    private fun startTtsEngine() {
        val voice = ttsVoice
        val sdkAppId = kitParams.appId

        val engine = tts ?: TXRealtimeTTS().apply { setListener(ttsListener) }.also { tts = it }

        val params = TXRealtimeTTS.TXRealtimeTTSParams().apply {
            mode = TXRealtimeTTS.TXRealtimeTTSMode.ONLINE
            audioOutputMode = TXRealtimeTTS.TXRealtimeTTSAudioOutputMode.PLAYBACK_ONLY
            voiceName = voice
            online.appId = sdkAppId.toString()
            online.userId = kitParams.userId
            online.userSig = kitParams.userSig
        }

        val code = engine.start(params)
        log("TTS(online) engine start code=$code")
        if (code != TXRealtimeTTS.TXRealtimeTTSError.OK) {
            emitToast("语音播报启动失败: $code")
        } else {
            applyTtsVolume()
        }
    }

    private fun restartTtsEngine() {
        tts?.stop()
        speaking = false
        startTtsEngine()
    }

    private fun applyTtsVolume() {
        tts?.setVolume(if (autoSpeak) VOLUME_ON else VOLUME_OFF)
    }

    private fun flushTtsSentences(textId: String, buffer: StringBuilder) {
        val engine = tts ?: return
        while (true) {
            val s = buffer.toString()
            val cut = s.indexOfFirst { it in SENTENCE_ENDERS }
            if (cut < 0) break
            val sentence = s.substring(0, cut + 1)
            buffer.delete(0, cut + 1)
            if (sentence.isNotBlank()) {
                engine.appendText(textId, sentence, false)
            }
        }
    }

    private fun flushTtsRemainder(textId: String, buffer: StringBuilder) {
        val engine = tts ?: return
        val rest = buffer.toString()
        buffer.setLength(0)
        if (rest.isNotBlank()) {
            engine.appendText(textId, rest, false)
        }
    }

    private fun interruptSpeaking() {
        tts?.clear()
        speaking = false
    }

    /* ---------------- 工具 / 生命周期 ---------------- */

    private fun appendBubble(role: String, content: String) {
        bubbles.add(ChatBubble(id = nextId(), role = role, content = content))
        notifyBubbles()
    }

    private fun updateBubble(
        id: Long,
        content: String,
        streaming: Boolean = false,
        error: Boolean = false
    ) {
        val idx = bubbles.indexOfLast { it.id == id }
        if (idx >= 0) {
            bubbles[idx] = bubbles[idx].copy(content = content, streaming = streaming, error = error)
            notifyBubbles()
        }
    }

    private fun appendHistory(msg: LlmChatMessage) {
        history.add(msg)
        while (history.size > MAX_HISTORY) {
            history.removeAt(0)
        }
    }

    private fun notifyBubbles() {
        listener?.onBubblesChanged(bubbles.toList())
    }

    private fun nextId(): Long = System.nanoTime()

    /** 弹出一条提示（如权限被拒）。 */
    fun showToast(msg: String) {
        mainHandler.post { emitToast(msg) }
    }

    private fun emitToast(msg: String) {
        listener?.onToast(msg)
    }

    private fun log(msg: String) {
        Log.d(TAG, msg)
    }

    /** 进入对话界面：按当前音色启动在线 TTS 引擎。 */
    fun onEnterPage() {
        restartTtsEngine()
    }

    /** 离开对话界面：停止播报、取消在途请求（保留引擎实例）。 */
    fun onLeavePage() {
        currentTurn++
        currentLlm?.cancel()
        stopTtsEngine()
        log("onLeavePage: 已停止播报")
    }

    private fun stopTtsEngine() {
        tts?.stop()
        speaking = false
    }

    /** 彻底释放 TTS 实例（Fragment 销毁时调用）。 */
    fun release() {
        currentTurn++
        currentLlm?.cancel()
        tts?.let {
            it.stop()
            it.setListener(null)
            it.destroy()
        }
        tts = null
        listener = null
        log("release: 资源已释放")
    }
}
