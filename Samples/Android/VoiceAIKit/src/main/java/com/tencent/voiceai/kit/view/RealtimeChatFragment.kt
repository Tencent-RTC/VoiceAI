// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAIKit - 实时 AI 对话 Fragment（开箱即用的整页组件）

package com.tencent.voiceai.kit.view

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tencent.voiceai.kit.R
import com.tencent.voiceai.kit.view.component.RealtimeMessageAdapter
import com.tencent.voiceai.kit.view.component.VoiceOrbView

/**
 * 实时 AI 对话页（进入即持续聆听 + 流式回复 + TTS 播报）。
 *
 * 使用方式：直接嵌入宿主容器
 * ```
 * supportFragmentManager.beginTransaction()
 *     .replace(R.id.container, RealtimeChatFragment.newInstance())
 *     .commit()
 * ```
 *
 * 本 Fragment 有意不引用 AIChatFragment，保持两页彼此独立。
 *
 * 本组件**不含降噪入口 UI**；降噪模式通过 [selectDenoiseMode] / [getDenoiseMode]
 * 对外提供，宿主可自行实现入口，并通过 [onDenoiseModeChanged] 同步状态。
 */
open class RealtimeChatFragment : Fragment() {

    /** 退出（关闭）按钮点击回调；未设置时默认调用 activity 的返回。 */
    var onExitClick: (() -> Unit)? = null

    /**
     * 降噪模式变化回调，供宿主自建的降噪入口同步 UI。
     * 绑定时会立即回调一次当前模式。
     */
    var onDenoiseModeChanged: ((mode: DenoiseMode) -> Unit)? = null
        set(value) {
            field = value
            // 若控制器已就绪，立即同步一次当前模式
            if (::controller.isInitialized) value?.invoke(controller.denoiseMode)
        }

    private lateinit var controller: RealtimeChatController
    private lateinit var adapter: RealtimeMessageAdapter

    private lateinit var orb: VoiceOrbView
    private lateinit var recycler: RecyclerView
    private lateinit var emptyHint: TextView
    private lateinit var contentContainer: View

    private lateinit var toggleText: TextView
    private lateinit var hint: TextView
    private lateinit var btnMic: ImageButton
    private lateinit var btnTts: ImageButton
    private lateinit var btnInterrupt: ImageButton
    private lateinit var btnExit: ImageButton
    private lateinit var toast: TextView

    private var showText = false
    private val toastHandler = android.os.Handler(android.os.Looper.getMainLooper())

    // 当前状态缓存（用于组合出提示语与打断交互）
    private var recording = false
    private var thinking = false
    private var speaking = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.vak_fragment_realtime_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        controller = RealtimeChatController(requireContext())

        orb = view.findViewById(R.id.vak_rt_orb)
        recycler = view.findViewById(R.id.vak_rt_recycler)
        emptyHint = view.findViewById(R.id.vak_rt_empty_hint)
        contentContainer = view.findViewById(R.id.vak_rt_content)
        toggleText = view.findViewById(R.id.vak_rt_toggle_text)
        hint = view.findViewById(R.id.vak_rt_hint)
        btnMic = view.findViewById(R.id.vak_rt_btn_mic)
        btnTts = view.findViewById(R.id.vak_rt_btn_tts)
        btnInterrupt = view.findViewById(R.id.vak_rt_btn_interrupt)
        btnExit = view.findViewById(R.id.vak_rt_btn_exit)
        toast = view.findViewById(R.id.vak_rt_toast)

        adapter = RealtimeMessageAdapter()
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        setupControls()
        applyTextMode()

        controller.setListener(object : RealtimeChatController.Listener {
            override fun onMessagesChanged(messages: List<RealtimeMessage>) {
                val visible = messages.filter { it.content.isNotEmpty() }
                adapter.submitList(visible) {
                    if (visible.isNotEmpty()) recycler.scrollToPosition(visible.size - 1)
                    emptyHint.visibility =
                        if (showText && visible.isEmpty()) View.VISIBLE else View.GONE
                }
            }

            override fun onRecordingChanged(value: Boolean) {
                recording = value; updateHint(); updateMicButton()
            }

            override fun onThinkingChanged(value: Boolean) {
                thinking = value; updateHint(); updateInterruptButton()
            }

            override fun onSpeakingChanged(value: Boolean) {
                speaking = value; updateHint(); updateInterruptButton()
            }

            override fun onTtsEnabledChanged(enabled: Boolean) {
                updateTtsButton(enabled)
            }

            override fun onDenoiseModeChanged(mode: DenoiseMode) {
                // 降噪入口在宿主侧，转发给宿主更新自己的 UI
                this@RealtimeChatFragment.onDenoiseModeChanged?.invoke(mode)
            }

            override fun onToast(message: String) {
                showToast(message)
            }
        })

        // 进入即请求麦克风权限并开始聆听
        startRecordingWithPermission()
    }

    private fun setupControls() {
        // 「字」开关：切换圆球 / 文本
        toggleText.setOnClickListener {
            showText = !showText
            applyTextMode()
        }

        btnMic.setOnClickListener {
            if (hasMicPermission()) controller.toggleMic()
            else controller.notifyMicPermissionDenied()
        }
        btnTts.setOnClickListener { controller.toggleTts(!controller.ttsEnabled) }
        btnInterrupt.setOnClickListener { controller.interruptResponse() }
        btnExit.setOnClickListener {
            onExitClick?.invoke() ?: activity?.onBackPressedDispatcher?.onBackPressed()
        }
        // 关闭图标本身是白色，白色圆底上会看不见，这里统一着色
        tintButton(btnExit, R.color.vak_exit)

        // 内容区点击：AI 回复中点击可打断
        contentContainer.setOnClickListener {
            if (thinking || speaking) controller.interruptResponse()
        }
    }

    private fun applyTextMode() {
        if (showText) {
            orb.visibility = View.GONE
            recycler.visibility = View.VISIBLE
            emptyHint.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
            toggleText.setBackgroundResource(R.drawable.vak_bg_circle_active)
            toggleText.setTextColor(ContextCompat.getColor(requireContext(), R.color.vak_brand_blue))
        } else {
            orb.visibility = View.VISIBLE
            recycler.visibility = View.GONE
            emptyHint.visibility = View.GONE
            toggleText.setBackgroundResource(R.drawable.vak_bg_circle_light)
            toggleText.setTextColor(ContextCompat.getColor(requireContext(), R.color.vak_neutral_icon))
        }
    }

    private fun updateHint() {
        if (!::hint.isInitialized) return
        hint.text = when {
            thinking -> "AI 正在思考"
            speaking -> "AI 正在回复"
            !recording -> "已停止，点击麦克风开启"
            else -> "正在聆听，直接说话即可"
        }
    }

    private fun updateMicButton() {
        btnMic.setImageResource(if (recording) R.drawable.vak_ic_mic else R.drawable.vak_ic_mic_off)
        tintButton(btnMic, if (recording) R.color.vak_mic_on else R.color.vak_mic_off)
    }

    private fun updateTtsButton(enabled: Boolean) {
        btnTts.setImageResource(
            if (enabled) R.drawable.vak_ic_volume_up else R.drawable.vak_ic_volume_off
        )
        tintButton(btnTts, if (enabled) R.color.vak_tts_on else R.color.vak_tts_off)
    }

    private fun updateInterruptButton() {
        val active = thinking || speaking
        tintButton(btnInterrupt, if (active) R.color.vak_interrupt_active else R.color.vak_mic_off)
    }

    private fun tintButton(button: ImageButton, colorRes: Int) {
        button.setColorFilter(ContextCompat.getColor(requireContext(), colorRes))
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    private val recordPermissionLauncher =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) controller.startRecording() else controller.notifyMicPermissionDenied()
        }

    private fun startRecordingWithPermission() {
        if (hasMicPermission()) controller.startRecording()
        else recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun showToast(message: String) {
        toast.text = message
        toast.visibility = View.VISIBLE
        toastHandler.removeCallbacksAndMessages(null)
        toastHandler.postDelayed({ toast.visibility = View.GONE }, 2500)
    }

    /* ---------------- 对外 API：降噪 ---------------- */

    /**
     * 切换降噪模式（供宿主自建入口调用）。
     * 状态变化会通过 [onDenoiseModeChanged] 回调出去；录音中会立即生效。
     */
    fun selectDenoiseMode(mode: DenoiseMode) {
        if (::controller.isInitialized) controller.selectDenoiseMode(mode)
    }

    /** 当前降噪模式；控制器未就绪时返回 [DenoiseMode.NONE]。 */
    fun getDenoiseMode(): DenoiseMode =
        if (::controller.isInitialized) controller.denoiseMode else DenoiseMode.NONE

    override fun onDestroyView() {
        super.onDestroyView()
        onDenoiseModeChanged = null
        toastHandler.removeCallbacksAndMessages(null)
        controller.release()
    }

    companion object {
        @JvmStatic
        fun newInstance(): RealtimeChatFragment = RealtimeChatFragment()
    }
}
