// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAIKit - AI 对话底部输入栏组件（文字输入 / 按住说话）
//
// 本组件自持 ASR 语音输入能力（TXRealtimeASR）：
//   - 按住说话的按下 / 抬起（内部 / 外部）手势、识别状态、音量波形、文案都由本组件自理；
//   - 麦克风权限仍需宿主授予：按下时通过 [onStartVoice] 通知宿主，
//     宿主确认权限后调用 [startVoiceInput]；
//   - 识别结果通过 [onTextRecognized] 抛给宿主，由宿主决定如何发送。

package com.tencent.voiceai.kit.view.component

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.tencent.voiceai.TXRealtimeASR
import com.tencent.voiceai.kit.R
import com.tencent.voiceai.kit.VoiceAIKit

/**
 * AI 对话底部输入栏，内置语音识别（按住说话）。
 *
 * 用法：
 * ```
 * inputBar.onSendText = { controller.sendText(it) }
 * inputBar.onToggleMode = { inputBar.setVoiceMode(controller.toggleVoiceMode()) }
 * inputBar.onStartVoice = { /* 宿主确认麦克风权限后 */ inputBar.startVoiceInput() }
 * inputBar.onTextRecognized = { controller.sendText(it) }
 * inputBar.onNotice = { showToast(it) }
 * ```
 */
class AIChatInputBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    /** 点击发送：回调输入框中的文本（组件会自行清空输入框）。 */
    var onSendText: ((text: String) -> Unit)? = null

    /** 点击键盘 / 麦克风切换按钮；宿主决定是否切换并把结果回传给 [setVoiceMode]。 */
    var onToggleMode: (() -> Unit)? = null

    /** 「按住说话」按下：宿主需确认麦克风权限后调用 [startVoiceInput]。 */
    var onStartVoice: (() -> Unit)? = null

    /** 语音识别完成且识别出文本（手指在按钮内抬起）。 */
    var onTextRecognized: ((text: String) -> Unit)? = null

    /** 需要提示用户的信息（如鉴权缺失、识别失败）。 */
    var onNotice: ((message: String) -> Unit)? = null

    private lateinit var input: EditText
    private lateinit var holdToTalk: TextView
    private lateinit var wave: VoiceWaveView
    private lateinit var btnToggleMode: ImageButton
    private lateinit var btnSend: ImageButton

    private val mainHandler = Handler(Looper.getMainLooper())

    private var voiceMode = false
    private var recognizing = false

    private var asr: TXRealtimeASR? = null
    private var asrFinalText = ""
    private var recognizedText = ""
    private var asrStopAction = AsrStopAction.SEND

    private enum class AsrStopAction { SEND, DISCARD }

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.vak_view_ai_chat_input_bar, this, true)

        wave = findViewById(R.id.vak_wave)
        input = findViewById(R.id.vak_input)
        holdToTalk = findViewById(R.id.vak_hold_to_talk)
        btnToggleMode = findViewById(R.id.vak_btn_toggle_mode)
        btnSend = findViewById(R.id.vak_btn_send)

        setupListeners()
        applyVoiceMode(voiceMode)
    }

    private fun setupListeners() {
        btnToggleMode.setOnClickListener { onToggleMode?.invoke() }
        btnSend.setOnClickListener {
            val text = input.text.toString()
            input.setText("")
            onSendText?.invoke(text)
        }

        // 「按住说话」触摸交互：按下开始识别，抬起时判断是否仍在按钮内决定发送 / 取消
        holdToTalk.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    // 权限由宿主把关，宿主确认后回调 startVoiceInput()
                    onStartVoice?.invoke()
                    updateHoldText(recognizing = true, movedOutside = false)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val outside = !isInside(v, event.x, event.y)
                    updateHoldText(recognizing = true, movedOutside = outside)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    val inside = isInside(v, event.x, event.y) &&
                        event.action == MotionEvent.ACTION_UP
                    if (inside) stopVoiceInput() else cancelVoiceInput()
                    true
                }
                else -> false
            }
        }
    }

    /* ---------------- 对外状态 ---------------- */

    /** 当前是否为语音输入模式（true = 按住说话）。 */
    fun isVoiceMode(): Boolean = voiceMode

    /** 是否正在语音识别。 */
    fun isRecognizing(): Boolean = recognizing

    /** 设置输入模式：true 显示「按住说话」并收起键盘，false 显示文本输入框与发送按钮。 */
    fun setVoiceMode(voiceMode: Boolean) {
        this.voiceMode = voiceMode
        applyVoiceMode(voiceMode)
    }

    /* ---------------- 语音输入 ---------------- */

    /** 开始语音识别（调用前请确保已获得麦克风权限）。 */
    fun startVoiceInput() {
        if (recognizing) return
        asrFinalText = ""
        recognizedText = ""
        setRecognizing(true)

        val kitParams = VoiceAIKit.getParams()
        if (kitParams == null) {
            setRecognizing(false)
            onNotice?.invoke("缺少鉴权信息，请先调用 VoiceAIKit.init(VoiceAIKitParams)")
            return
        }

        val voiceId = kitParams.userId
        val params = TXRealtimeASR.Params().apply {
            sdkAppId = kitParams.appId.toString()
            userSig = kitParams.userSig
            this.voiceId = voiceId
            enableCustomCapture = false
        }
        try {
            asrInstance().startRealtimeASR(params)
        } catch (e: Exception) {
            setRecognizing(false)
            onNotice?.invoke("启动识别失败: ${e.message}")
        }
    }

    /** 结束识别并发送识别到的文本。 */
    fun stopVoiceInput() {
        if (!recognizing) return
        asrStopAction = AsrStopAction.SEND
        asr?.stopRealtimeASR()
    }

    /** 取消识别，丢弃结果。 */
    fun cancelVoiceInput() {
        if (!recognizing) return
        asrStopAction = AsrStopAction.DISCARD
        asr?.stopRealtimeASR()
    }

    /** 释放 ASR 与波形资源（页面销毁时调用，可重复调用）。 */
    fun release() {
        recognizing = false
        wave.stop()
        // 先摘监听再停止，避免停止回调继续向宿主抛结果
        asr?.let {
            it.removeListener(asrListener)
            it.stopRealtimeASR()
            it.destroy()
        }
        asr = null
        asrFinalText = ""
        recognizedText = ""
    }

    private fun asrInstance(): TXRealtimeASR =
        asr ?: TXRealtimeASR().apply {
            addListener(asrListener)
            asr = this
        }

    private val asrListener = object : TXRealtimeASR.Listener {
        override fun onRealtimeASRStarted(session: String) {
            mainHandler.post { setRecognizing(true) }
        }

        override fun onReceiveRealtimeASRMessage(msg: TXRealtimeASR.Message) {
            val text = msg.sourceText
            val completed = msg.isCompleted
            mainHandler.post {
                if (completed) asrFinalText += text
                recognizedText = asrFinalText + if (completed) "" else text
            }
        }

        override fun onRealtimeASRStopped() {
            mainHandler.post { finishRecognition() }
        }

        override fun onRealtimeASRError(code: Int, msg: String?) {
            mainHandler.post {
                onNotice?.invoke("语音识别错误 $code: $msg")
                asrStopAction = AsrStopAction.DISCARD
                finishRecognition()
            }
        }

        override fun onRealtimeASRVolume(volume: Int) {
            val v = volume.coerceIn(0, 100)
            mainHandler.post { wave.setVolume(v) }
        }
    }

    private fun finishRecognition() {
        setRecognizing(false)
        val action = asrStopAction
        asrStopAction = AsrStopAction.SEND
        val text = recognizedText.trim()
        asrFinalText = ""
        recognizedText = ""
        if (action == AsrStopAction.SEND && text.isNotEmpty()) {
            onTextRecognized?.invoke(text)
        }
    }

    /* ---------------- 内部 UI ---------------- */

    private fun applyVoiceMode(voiceMode: Boolean) {
        if (voiceMode) {
            input.visibility = View.GONE
            holdToTalk.visibility = View.VISIBLE
            btnSend.visibility = View.GONE
            btnToggleMode.setImageResource(R.drawable.vak_ic_keyboard_blue)
            hideKeyboard()
        } else {
            // 切回键盘输入时若仍在识别，先收尾（与切换前保持一致：发送已识别内容）
            if (recognizing) stopVoiceInput()
            input.visibility = View.VISIBLE
            holdToTalk.visibility = View.GONE
            btnSend.visibility = View.VISIBLE
            btnToggleMode.setImageResource(R.drawable.vak_ic_mic_blue)
        }
    }

    private fun setRecognizing(value: Boolean) {
        recognizing = value
        updateWave(value)
        updateHoldText(value, movedOutside = false)
    }

    private fun updateWave(recognizing: Boolean) {
        if (recognizing) {
            wave.visibility = View.VISIBLE
            wave.start()
        } else {
            wave.stop()
            wave.visibility = View.GONE
        }
    }

    private fun updateHoldText(recognizing: Boolean, movedOutside: Boolean) {
        holdToTalk.text = when {
            recognizing && movedOutside -> "松开手指，取消发送"
            recognizing -> "聆听中…"
            else -> "按住 说话"
        }
        val colorRes = when {
            recognizing && movedOutside -> R.color.vak_retry_red
            recognizing -> R.color.vak_brand_blue
            else -> R.color.vak_text_secondary
        }
        holdToTalk.setTextColor(ContextCompat.getColor(context, colorRes))
        holdToTalk.setBackgroundResource(
            when {
                !recognizing -> R.drawable.vak_bg_input_field
                movedOutside -> R.drawable.vak_bg_bubble_ai
                else -> R.drawable.vak_bg_input_field
            }
        )
    }

    private fun isInside(v: View, x: Float, y: Float): Boolean =
        x >= 0f && y >= 0f && x <= v.width && y <= v.height

    private fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(windowToken, 0)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        release()
    }
}
