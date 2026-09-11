// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAI Demo - 开箱即用的语音输入栏（传统 View + XML）
//
// 参照 Compose 版 InputBar / AsrConfirmDialog / VoiceInputViewModel 的交互实现：
//   1. 左侧「键盘 / 语音」切换；
//   2. 文本模式：输入框 + 内联麦克风（识别结果直接写入输入框）；
//   3. 语音模式：「按住说话」，松手进入结果确认弹窗；
//   4. 内嵌 AsrConfirmDialog（识别中实时展示 / 松手后可编辑确认）。
//
// 「开箱即用」：本 View 内部自持 TXRealtimeASR 并管理完整识别状态机，
// 使用方只需：
//   1. 启动时通过 VoiceAIKit.init(params) 注入初始化参数；
//   2. 实现 OnVoiceInputListener.onSendMessage(text) 接收最终文本；
//   3. 在 onRequestRecordAudioPermission() 中申请麦克风权限。

package com.tencent.voiceai.kit.widget

import android.Manifest
import android.app.Dialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.tencent.voiceai.TXRealtimeASR
import com.tencent.voiceai.kit.R
import com.tencent.voiceai.kit.VoiceAIKit

/**
 * 开箱即用的语音输入栏。
 *
 * 接入方式：
 * ```kotlin
 * // 应用启动时注入一次初始化参数（Kit 不内置任何账号信息）
 * // 单独使用本 View 时只需前三个必填项，LLM 相关配置可省略
 * VoiceAIKit.init(VoiceAIKit.VoiceAIKitParams(appId, userId, userSig))
 *
 * val bar = findViewById<VoiceInputBar>(R.id.voice_input_bar)
 * bar.setOnVoiceInputListener(object : VoiceInputBar.OnVoiceInputListener {
 *     override fun onSendMessage(text: String) { ... }
 *     override fun onRequestRecordAudioPermission() {
 *         ActivityCompat.requestPermissions(activity,
 *             arrayOf(Manifest.permission.RECORD_AUDIO), 1)
 *     }
 * })
 * // 权限授予后调用，以便继续待处理的语音输入
 * bar.onRecordAudioPermissionResult(granted)
 * // Activity 销毁时释放
 * bar.release()
 * ```
 */
class VoiceInputBar : LinearLayout {

    /**
     * 使用方回调。
     *
     * 两个可选方法带默认实现；模块已开启 `-Xjvm-default=all-compatibility`，
     * 因此 Java 宿主实现本接口时可以只覆写 [onSendMessage]。
     */
    interface OnVoiceInputListener {
        /** 发送一条文本消息（文本输入发送 或 语音确认发送）。 */
        fun onSendMessage(text: String)

        /**
         * 需要麦克风权限：使用方应在此发起权限申请，授予后回调
         * [onRecordAudioPermissionResult]。
         */
        fun onRequestRecordAudioPermission() {}

        /** 内部提示（如权限被拒、识别出错），使用方可用 Toast 展示。 */
        fun onToast(message: String) {}
    }

    // --- 视图 ---
    private lateinit var btnToggle: ImageView
    private lateinit var inputContainer: FrameLayout
    private lateinit var etInput: EditText
    private lateinit var btnMic: ImageView
    private lateinit var btnHold: TextView
    private lateinit var btnSend: ImageView

    private var listener: OnVoiceInputListener? = null

    // --- 交互状态 ---
    private var voiceMode = false // 是否处于「按住说话」模式
    private var pressMovedOutside = false // 按住期间手指是否移出按钮
    private var holdPressStarted = false // 本次按住是否已真正开始识别

    // 识别进行中点了发送：需等 onRealtimeASRStopped 带回尾部结果后再真正发送
    private var pendingSendAfterStop = false

    // 待授权后自动执行的动作
    private enum class PendingAction { NONE, HOLD, INLINE }

    private var pendingAction = PendingAction.NONE

    // 忽略以编程方式写入 EditText 引发的 TextWatcher 回调
    private var suppressInputWatcher = false

    // --- ASR 状态机 ---
    private val mainHandler = Handler(Looper.getMainLooper())
    private var asr: TXRealtimeASR? = null

    private var recognizing = false
    private var inlineRecognizing = false
    private var recognizedText = ""
    private var asrConfirmText = ""
    private var showAsrConfirm = false

    private var asrFinalText = ""
    private var inlineMode = false
    private var inlineBaseText = ""

    @Volatile
    private var asrReleased = false

    @Volatile
    private var asrPendingStart = false

    @Volatile
    private var asrStopRequested = false

    private enum class AsrStopAction { CONFIRM, DISCARD }

    private var asrStopAction = AsrStopAction.CONFIRM

    /** 内联识别空闲超时任务，到点结束识别。 */
    private val inlineIdleTimeoutRunnable = Runnable { onInlineIdleTimeout() }

    private var confirmDialog: AsrConfirmDialog? = null

    // --- ASR 回调 ---
    private val asrListener = object : TXRealtimeASR.Listener {

        override fun onRealtimeASRStarted(voiceId: String) {
            mainHandler.post {
                asrPendingStart = false
                if (asrReleased || asrStopRequested) return@post
                recognizing = true
                if (inlineMode) inlineRecognizing = true
                refreshUi()
            }
            Log.d(TAG, "asr started voiceId=$voiceId")
        }

        override fun onReceiveRealtimeASRMessage(message: TXRealtimeASR.Message) {
            if (asrReleased) return
            if (message.isCompleted) asrFinalText += message.sourceText
            mainHandler.post {
                recognizedText = asrFinalText + (if (message.isCompleted) "" else message.sourceText)
                if (inlineMode) {
                    setInputTextProgrammatically(inlineBaseText + recognizedText)
                    // 收到新数据：重置空闲超时计时
                    scheduleInlineIdleTimeout()
                }
                refreshUi()
            }
            Log.d(TAG, "asr message text=${message.sourceText} completed=${message.isCompleted}")
        }

        override fun onRealtimeASRStopped() {
            mainHandler.post {
                cancelInlineIdleTimeout()
                asrPendingStart = false
                if (asrReleased) {
                    // 已释放：丢弃挂起的发送
                    pendingSendAfterStop = false
                    return@post
                }
                recognizing = false
                if (inlineMode) {
                    inlineRecognizing = false
                    // 此处 recognizedText 已包含停止期间补发的尾部结果
                    setInputTextProgrammatically(inlineBaseText + recognizedText)
                    finishInline()
                } else {
                    commitAsrResult()
                }
                refreshUi()
                // 尾部数据已并入输入框，执行之前挂起的发送
                flushPendingSendAfterStop()
            }
            Log.d(TAG, "asr stopped")
        }

        override fun onRealtimeASRError(errorCode: Int, errorMsg: String) {
            mainHandler.post {
                cancelInlineIdleTimeout()
                asrPendingStart = false
                if (asrReleased) {
                    // 已释放：丢弃挂起的发送
                    pendingSendAfterStop = false
                    return@post
                }
                recognizing = false
                if (inlineMode) {
                    inlineRecognizing = false
                    setInputTextProgrammatically(inlineBaseText + recognizedText)
                    finishInline()
                } else {
                    commitAsrResult()
                }
                refreshUi()
                toast("识别出错: $errorMsg (code=$errorCode)")
                // 识别异常结束：已有内容照常发送，避免发送动作被永久挂起
                flushPendingSendAfterStop()
            }
            Log.d(TAG, "asr error code=$errorCode msg=$errorMsg")
        }

        override fun onRealtimeASRVolume(volume: Int) {
            // 可按需展示音量，这里忽略
        }
    }

    constructor(context: Context) : super(context) {
        initView(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initView(context)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        initView(context)
    }

    private fun initView(context: Context) {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.BOTTOM
        setBackgroundColor(Color.WHITE)
        val padH = dp(14)
        val padV = dp(9)
        setPadding(padH, padV, padH, padV)

        LayoutInflater.from(context).inflate(R.layout.voice_input_bar_view, this, true)

        btnToggle = findViewById(R.id.vib_btn_toggle)
        inputContainer = findViewById(R.id.vib_input_container)
        etInput = findViewById(R.id.vib_et_input)
        btnMic = findViewById(R.id.vib_btn_mic)
        btnHold = findViewById(R.id.vib_btn_hold)
        btnSend = findViewById(R.id.vib_btn_send)

        asr = TXRealtimeASR().also { it.addListener(asrListener) }

        bindEvents()
        refreshUi()
    }

    fun setOnVoiceInputListener(listener: OnVoiceInputListener?) {
        this.listener = listener
    }

    private fun bindEvents() {
        // 键盘 / 语音 切换
        btnToggle.setOnClickListener {
            // 切换前完整退出 ASR，避免残留
            stopAsr()
            pressMovedOutside = false
            voiceMode = !voiceMode
            if (voiceMode) hideKeyboard()
            refreshUi()
        }

        // 发送（文本）
        btnSend.setOnClickListener { sendText() }

        // 内联麦克风：点击弹出键盘并启动内联识别，再次点击停止
        btnMic.setOnClickListener {
            if (inlineRecognizing) {
                stopInlineVoiceInput()
                return@setOnClickListener
            }
            etInput.requestFocus()
            showKeyboard()
            if (hasRecordPermission()) {
                startInlineVoiceInput()
            } else {
                pendingAction = PendingAction.INLINE
                requestRecordPermission()
            }
        }

        // 输入框文本变化：仅关心用户手动输入
        etInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (suppressInputWatcher) return
                // 用户手动编辑时同步到内联基准，避免内联识别覆盖用户输入
                if (!inlineRecognizing) inlineBaseText = s.toString()
            }
        })

        // 按住说话：按下开始识别，跟踪手指移入/移出，抬起决定确认或取消
        btnHold.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pressMovedOutside = false
                    onVoiceDown()
                    refreshHoldButton()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!holdPressStarted) return@setOnTouchListener true
                    val inside = isInside(event.x, event.y, v)
                    if (pressMovedOutside == inside) {
                        pressMovedOutside = !inside
                        refreshHoldButton()
                        refreshDialog()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val releasedInside = isInside(event.x, event.y, v)
                    if (holdPressStarted) {
                        if (releasedInside && event.actionMasked == MotionEvent.ACTION_UP) {
                            stopVoiceInput()
                        } else {
                            cancelVoiceInput()
                        }
                    }
                    holdPressStarted = false
                    pressMovedOutside = false
                    refreshHoldButton()
                    true
                }
                else -> false
            }
        }
    }

    // ============================ 文本发送 ============================

    private fun sendText() {
        // 已在等待识别结束后发送，忽略重复点击
        if (pendingSendAfterStop) return

        if (recognizing) {
            // 识别中：stopRealtimeASR() 是异步的，尾部结果要等 onRealtimeASRStopped 才回来，
            // 所以先登记待发送并停止录音，由回调把尾部结果并入输入框后再真正发送，
            // 否则会在尾部数据到达前就把内容发出去
            pendingSendAfterStop = true
            stopVoiceInputForSend()
            refreshUi()
            return
        }
        // 仅处于建连中（尚未 started）：没有可回收的尾部数据，停止后直接发送
        if (asrPendingStart) {
            stopVoiceInputForSend()
            refreshUi()
        }
        sendTextInternal()
    }

    /** 取出输入框内容回调给使用方，随后清空输入框。 */
    private fun sendTextInternal() {
        val t = etInput.text.toString().trim()
        if (t.isEmpty()) return
        listener?.onSendMessage(t)
        setInputTextProgrammatically("")
        inlineBaseText = ""
    }

    // ============================ 权限 ============================

    private fun hasRecordPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestRecordPermission() {
        listener?.onRequestRecordAudioPermission()
    }

    /** 使用方在权限回调中调用，用于继续待处理的语音输入。 */
    fun onRecordAudioPermissionResult(granted: Boolean) {
        val action = pendingAction
        pendingAction = PendingAction.NONE
        if (!granted) {
            toast("需要麦克风权限才能使用语音输入")
            return
        }
        when (action) {
            PendingAction.HOLD -> {
                // 按住场景需用户重新按下，这里不自动开始
            }
            PendingAction.INLINE -> {
                etInput.requestFocus()
                showKeyboard()
                startInlineVoiceInput()
            }
            else -> Unit
        }
    }

    // ============================ 按住说话 ============================

    private fun onVoiceDown() {
        if (hasRecordPermission()) {
            startVoiceInput()
            holdPressStarted = true
        } else {
            holdPressStarted = false
            pendingAction = PendingAction.HOLD
            requestRecordPermission()
        }
    }

    /** 开始实时语音识别（按住说话模式）。 */
    private fun startVoiceInput() {
        if (recognizing) return
        inlineMode = false
        startAsrInternal()
    }

    /** 开始实时语音识别（输入框内联模式）。 */
    private fun startInlineVoiceInput() {
        if (recognizing) return
        inlineMode = true
        inlineBaseText = etInput.text.toString()
        startAsrInternal()
    }

    private fun startAsrInternal() {
        val engine = asr ?: return
        val kitParams = VoiceAIKit.getParams()
        if (kitParams == null) {
            toast("未初始化 VoiceAIKit，请先调用 VoiceAIKit.init(VoiceAIKit.VoiceAIKitParams)")
            Log.w(TAG, "asr start aborted: kit not initialized")
            return
        }
        asrReleased = false
        asrPendingStart = true
        asrStopRequested = false
        asrStopAction = AsrStopAction.CONFIRM

        val params = TXRealtimeASR.Params()
        val voiceId = kitParams.userId
        params.sdkAppId = kitParams.appId.toString()
        params.userSig = kitParams.userSig
        params.voiceId = voiceId
        params.sourceLanguage = "zh"
        params.enableCustomCapture = false

        asrFinalText = ""
        recognizedText = ""
        // 乐观置为「识别中」，避免建连鉴权期间界面无反馈
        recognizing = true
        if (inlineMode) inlineRecognizing = true
        refreshUi()

        try {
            engine.startRealtimeASR(params)
        } catch (e: Exception) {
            asrPendingStart = false
            recognizing = false
            inlineRecognizing = false
            refreshUi()
            toast("启动识别失败: ${e.message}")
            Log.d(TAG, "asr start failed: ${e.message}")
            return
        }
        // 内联模式：开始计时，长时间收不到识别结果则自动退出
        if (inlineMode) scheduleInlineIdleTimeout()
        Log.d(TAG, "asr start requested")
    }

    /** 结束识别，结果进入确认弹窗（松手时手指仍在按钮上）。 */
    private fun stopVoiceInput() {
        if (!recognizing) return
        asrStopAction = AsrStopAction.CONFIRM
        asrStopRequested = true
        asr?.stopRealtimeASR()
        recognizing = false
        // 乐观地先展示确认弹窗，避免停止回调到达前的闪烁
        val t = recognizedText.trim()
        if (t.isNotEmpty()) {
            asrConfirmText = t
            showAsrConfirm = true
        }
        refreshUi()
    }

    /** 取消识别，丢弃结果（松手时手指已移出按钮）。 */
    private fun cancelVoiceInput() {
        if (!recognizing) return
        asrStopAction = AsrStopAction.DISCARD
        asrStopRequested = true
        asr?.stopRealtimeASR()
        recognizing = false
        asrConfirmText = ""
        showAsrConfirm = false
        refreshUi()
    }

    /** 停止内联识别：文本保留在输入框，不弹确认框。 */
    private fun stopInlineVoiceInput() {
        if (!recognizing) return
        cancelInlineIdleTimeout()
        asrStopRequested = true
        asr?.stopRealtimeASR()
        recognizing = false
        inlineRecognizing = false
        setInputTextProgrammatically(inlineBaseText + recognizedText.trim())
        refreshUi()
    }

    // ============================ 内联识别空闲超时 ============================

    /**
     * （重新）开始内联识别的空闲计时。
     *
     * 每收到一次识别结果都会重置计时，因此判定的是「连续
     * [INLINE_ASR_IDLE_TIMEOUT_MS] 毫秒没有新数据」，而非「开启后总共若干秒」
     * ——这样既能覆盖「开口前一直没声音」，也能覆盖「说完静默一段时间」两种情形。
     */
    private fun scheduleInlineIdleTimeout() {
        cancelInlineIdleTimeout()
        mainHandler.postDelayed(inlineIdleTimeoutRunnable, INLINE_ASR_IDLE_TIMEOUT_MS)
    }

    /** 取消内联识别的空闲计时（识别停止、出错或视图销毁时调用）。 */
    private fun cancelInlineIdleTimeout() {
        mainHandler.removeCallbacks(inlineIdleTimeoutRunnable)
    }

    /** 空闲超时到点：静默结束内联识别（不提示），已识别内容保留在输入框。 */
    private fun onInlineIdleTimeout() {
        if (!inlineMode || !recognizing) return
        Log.d(TAG, "inline asr idle timeout, stop recognizing")
        stopInlineVoiceInput()
    }

    /**
     * 发送文本前结束识别：停止录音，等待尾部结果回收。
     *
     * 关键点：`stopRealtimeASR()` 是异步的，调用返回后 SDK 仍会补发尾部识别结果。
     * 因此这里刻意**不**合并文本、也**不**清理 `inlineBaseText` 与 `inlineMode`，
     * 让 onRealtimeASRStopped 走内联分支把（含尾部的）完整结果并入输入框，
     * 再由 [flushPendingSendAfterStop] 取走发送。
     *
     * 与 [stopAsr] 的区别是不把 ASR 标记为已释放，后续仍可继续识别。
     */
    private fun stopVoiceInputForSend() {
        asrStopRequested = true
        asrStopAction = AsrStopAction.DISCARD
        if (asr != null && (recognizing || asrPendingStart)) {
            asr?.stopRealtimeASR()
        }
        asrPendingStart = false
        recognizing = false
        inlineRecognizing = false
    }

    /**
     * 若此前因识别进行中而挂起了发送，则在识别结束后（尾部数据已并入输入框）立即执行。
     */
    private fun flushPendingSendAfterStop() {
        if (!pendingSendAfterStop) return
        pendingSendAfterStop = false
        sendTextInternal()
        refreshUi()
    }

    /** 完整退出语音识别并清理状态（切换模式 / 退出页面时调用）。 */
    fun stopAsr() {
        cancelInlineIdleTimeout()
        asrReleased = true
        asrStopRequested = true
        if (asr != null && (recognizing || asrPendingStart)) {
            asr?.stopRealtimeASR()
        }
        if (inlineMode) {
            setInputTextProgrammatically(inlineBaseText + recognizedText.trim())
        }
        asrPendingStart = false
        recognizing = false
        inlineRecognizing = false
        inlineMode = false
        inlineBaseText = ""
        asrFinalText = ""
        recognizedText = ""
        asrConfirmText = ""
        showAsrConfirm = false
        pendingSendAfterStop = false
        refreshUi()
    }

    private fun commitAsrResult() {
        val action = asrStopAction
        asrStopAction = AsrStopAction.CONFIRM
        val text = recognizedText.trim()
        asrFinalText = ""
        recognizedText = ""
        if (action == AsrStopAction.CONFIRM && text.isNotEmpty()) {
            asrConfirmText = text
            showAsrConfirm = true
        } else if (action == AsrStopAction.DISCARD) {
            asrConfirmText = ""
            showAsrConfirm = false
        }
    }

    private fun finishInline() {
        inlineMode = false
        inlineBaseText = ""
        asrFinalText = ""
        recognizedText = ""
    }

    // ============================ 确认弹窗回调 ============================

    private fun onAsrConfirmChange(text: String) {
        asrConfirmText = text
    }

    private fun confirmAsrSend() {
        val t = asrConfirmText.trim()
        asrConfirmText = ""
        showAsrConfirm = false
        refreshUi()
        if (t.isNotEmpty()) listener?.onSendMessage(t)
    }

    private fun cancelAsrConfirm() {
        asrConfirmText = ""
        showAsrConfirm = false
        refreshUi()
    }

    // ============================ UI 刷新 ============================

    private fun refreshUi() {
        refreshMode()
        refreshHoldButton()
        refreshMic()
        refreshDialog()
    }

    /** 文本模式 / 语音模式 的显隐切换。 */
    private fun refreshMode() {
        if (voiceMode) {
            inputContainer.visibility = GONE
            btnSend.visibility = GONE
            btnHold.visibility = VISIBLE
            btnToggle.setImageResource(R.drawable.voice_input_bar_ic_keyboard)
        } else {
            inputContainer.visibility = VISIBLE
            btnSend.visibility = VISIBLE
            btnHold.visibility = GONE
            btnToggle.setImageResource(R.drawable.voice_input_bar_ic_voice_wave)
        }
    }

    private fun refreshHoldButton() {
        if (recognizing && !inlineMode && pressMovedOutside) {
            btnHold.setBackgroundResource(R.drawable.voice_input_bar_bg_hold_cancel)
            btnHold.text = "松开手指，取消发送"
            btnHold.setTextColor(COLOR_RED)
        } else if (recognizing && !inlineMode) {
            btnHold.setBackgroundResource(R.drawable.voice_input_bar_bg_hold_recognizing)
            btnHold.text = "聆听中…"
            btnHold.setTextColor(COLOR_BLUE)
        } else {
            btnHold.setBackgroundResource(R.drawable.voice_input_bar_bg_hold_normal)
            btnHold.text = "按住 说话"
            btnHold.setTextColor(COLOR_TEXT_SECONDARY)
        }
    }

    private fun refreshMic() {
        if (inlineRecognizing) {
            btnMic.setBackgroundResource(R.drawable.voice_input_bar_bg_mic_active)
            btnMic.setColorFilter(COLOR_BLUE)
            btnMic.contentDescription = "停止语音输入"
        } else {
            btnMic.setBackgroundColor(Color.TRANSPARENT)
            btnMic.setColorFilter(COLOR_GREY_ICON)
            btnMic.contentDescription = "语音输入"
        }
    }

    /** 依据状态决定确认弹窗的显示 / 隐藏 / 内容更新。 */
    private fun refreshDialog() {
        // 内联识别期间不弹窗，文本直接写入输入框
        val shouldShow = (recognizing && !inlineRecognizing) || showAsrConfirm
        if (shouldShow) {
            val dialog = confirmDialog ?: AsrConfirmDialog(context).also { confirmDialog = it }
            if (!dialog.isShowing) dialog.show()
            dialog.update(recognizing, pressMovedOutside, recognizedText, asrConfirmText)
        } else if (confirmDialog?.isShowing == true) {
            confirmDialog?.dismiss()
        }
    }

    // ============================ 辅助 ============================

    private fun setInputTextProgrammatically(text: String) {
        suppressInputWatcher = true
        etInput.setText(text)
        etInput.setSelection(text.length)
        suppressInputWatcher = false
    }

    private fun showKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(etInput, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(windowToken, 0)
    }

    private fun toast(msg: String) {
        listener?.onToast(msg)
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // 视图重新挂载（如从别处移动过来）后，若已释放则重建 ASR，保证可继续使用
        if (asr == null) {
            asr = TXRealtimeASR().also { it.addListener(asrListener) }
        }
        asrReleased = false
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // 脱离窗口时停止录音并收起弹窗，但保留 ASR 实例以便重新挂载后继续使用；
        // 彻底销毁请显式调用 release()。
        if (asr != null) stopAsr()
    }

    /** 释放 ASR 资源（Activity onDestroy 或视图移除时调用，可重复调用）。 */
    fun release() {
        cancelInlineIdleTimeout()
        pendingSendAfterStop = false
        confirmDialog?.let { dialog ->
            if (dialog.isShowing) dialog.dismiss()
            confirmDialog = null
        }
        asr?.let { engine ->
            try {
                asrReleased = true
                if (recognizing) engine.stopRealtimeASR()
            } catch (ignored: Exception) {
                // 忽略释放阶段的异常
            }
            engine.removeListener(asrListener)
            engine.destroy()
            asr = null
        }
    }

    // ============================ 内嵌确认弹窗 ============================

    /**
     * 语音识别弹窗（内嵌于 VoiceInputBar）：
     * - 识别中：展示实时识别文本 + 松手提示；手指移出改为「松开手指取消发送」。
     * - 识别结束：可编辑识别结果 + 底部「取消 / 发送」按钮。
     */
    private inner class AsrConfirmDialog(context: Context) :
        Dialog(context, android.R.style.Theme_Translucent_NoTitleBar) {

        private lateinit var tvLive: TextView
        private lateinit var tvHint: TextView
        private lateinit var etEdit: EditText
        private lateinit var buttons: LinearLayout
        private lateinit var cardContainer: LinearLayout
        private var bound = false
        private var editWatcherActive = true

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            val content = LayoutInflater.from(context)
                .inflate(R.layout.voice_input_bar_dialog_asr_confirm, null, false)
            setContentView(content)

            window?.apply {
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setDimAmount(0f)
                // 编辑确认时随输入法上移
                setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }

            tvLive = content.findViewById(R.id.vib_asr_live)
            tvHint = content.findViewById(R.id.vib_asr_hint)
            etEdit = content.findViewById(R.id.vib_asr_edit)
            buttons = content.findViewById(R.id.vib_asr_buttons)
            cardContainer = content.findViewById(R.id.vib_asr_card_container)

            // 卡片底部固定在距屏幕底部约 1/3 处
            val screenHeight = context.resources.displayMetrics.heightPixels
            val lp = cardContainer.layoutParams as ViewGroup.MarginLayoutParams
            lp.bottomMargin = (screenHeight * 0.33f).toInt()
            cardContainer.layoutParams = lp

            etEdit.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (editWatcherActive) onAsrConfirmChange(s.toString())
                }
            })

            content.findViewById<View>(R.id.vib_asr_cancel).setOnClickListener { cancelAsrConfirm() }
            content.findViewById<View>(R.id.vib_asr_send).setOnClickListener { confirmAsrSend() }

            // 点击遮罩空白：识别中不可关闭，确认态视为取消
            content.findViewById<View>(R.id.vib_asr_root).setOnClickListener {
                if (!recognizing) cancelAsrConfirm()
            }

            setCancelable(false)
            setOnCancelListener { if (!recognizing) cancelAsrConfirm() }

            bound = true
        }

        /** 依据当前状态刷新弹窗内容。 */
        fun update(recognizing: Boolean, movedOutside: Boolean, liveText: String, confirmText: String) {
            if (!bound) return
            if (recognizing) {
                tvLive.visibility = VISIBLE
                tvHint.visibility = VISIBLE
                etEdit.visibility = GONE
                buttons.visibility = GONE

                tvLive.text = if (liveText.isEmpty()) "请说话…" else liveText
                tvHint.text = if (movedOutside) "松开手指取消发送" else "松开手指结束识别"
                tvHint.setTextColor(if (movedOutside) COLOR_RED else COLOR_TEXT_SECONDARY)
            } else {
                tvLive.visibility = GONE
                tvHint.visibility = GONE
                etEdit.visibility = VISIBLE
                buttons.visibility = VISIBLE

                if (etEdit.text.toString() != confirmText) {
                    editWatcherActive = false
                    etEdit.setText(confirmText)
                    etEdit.setSelection(confirmText.length)
                    editWatcherActive = true
                }
            }
        }
    }

    companion object {
        private const val TAG = "VoiceInputBar"

        // --- 配色（与 Compose 版保持一致） ---
        private val COLOR_BLUE = 0xFF2B6CF6.toInt()
        private val COLOR_GREY_ICON = 0xFF8A93A6.toInt()
        private val COLOR_TEXT_SECONDARY = 0xFF6B7488.toInt()
        private val COLOR_RED = 0xFFE85C5C.toInt()

        // COLOR_TEXT_PRIMARY 暂未使用，保留以便后续扩展
        @Suppress("unused")
        private val COLOR_TEXT_PRIMARY = 0xFF1A1F36.toInt()

        /**
         * 内联识别（文字输入界面）的空闲超时：距上次收到识别结果超过该时长仍未收到新数据，
         * 则认为用户已停止说话，自动退出识别。单位毫秒。
         */
        private const val INLINE_ASR_IDLE_TIMEOUT_MS = 5000L

        private fun isInside(x: Float, y: Float, v: View): Boolean =
            x >= 0f && y >= 0f && x <= v.width && y <= v.height
    }
}
