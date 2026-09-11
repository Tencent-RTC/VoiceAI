// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAIKit - AI 对话 Fragment（开箱即用的整页组件）

package com.tencent.voiceai.kit.view

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tencent.voiceai.kit.R
import com.tencent.voiceai.kit.view.component.AIChatInputBar
import com.tencent.voiceai.kit.view.component.ChatBubbleAdapter

/**
 * AI 对话页（文字 / 按住说话 + 流式回复 + TTS 播报）。
 *
 * 本组件**不含标题栏**，标题栏（返回、进入实时对话、自动朗读开关等）由宿主提供。
 * 宿主可通过 [toggleAutoSpeak] / [isAutoSpeak] 控制「自动朗读」，
 * 并通过 [onAutoSpeakChanged] 监听其状态变化以更新自己的按钮 UI。
 *
 * 使用方式：直接嵌入宿主容器
 * ```
 * supportFragmentManager.beginTransaction()
 *     .replace(R.id.container, AIChatFragment.newInstance())
 *     .commit()
 * ```
 */
open class AIChatFragment : Fragment() {

    /**
     * 「自动朗读」状态变化回调，供宿主标题栏的喇叭按钮同步 UI。
     * 绑定时会立即回调一次当前状态。
     */
    var onAutoSpeakChanged: ((autoSpeak: Boolean) -> Unit)? = null
        set(value) {
            field = value
            // 若控制器已就绪，立即同步一次当前状态
            if (::controller.isInitialized) value?.invoke(controller.autoSpeak)
        }

    private lateinit var controller: AIChatController
    private lateinit var adapter: ChatBubbleAdapter

    private lateinit var recycler: RecyclerView
    private lateinit var inputBar: AIChatInputBar
    private lateinit var toast: TextView

    private val toastHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private val recordPermissionLauncher =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) inputBar.startVoiceInput()
            else controller.showToast("需要麦克风权限才能使用语音输入")
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.vak_fragment_ai_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        controller = AIChatController(requireContext())

        recycler = view.findViewById(R.id.vak_recycler)
        inputBar = view.findViewById(R.id.vak_input_bar)
        toast = view.findViewById(R.id.vak_toast)

        adapter = ChatBubbleAdapter(onRetry = { controller.retryLast() })
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        setupInputBar()

        controller.setListener(object : AIChatController.Listener {
            override fun onBubblesChanged(bubbles: List<ChatBubble>) {
                adapter.submitList(bubbles) { scrollToBottom() }
            }

            override fun onAutoSpeakChanged(autoSpeak: Boolean) {
                // 标题栏在宿主侧，转发给宿主更新喇叭按钮 UI
                onAutoSpeakChanged?.invoke(autoSpeak)
            }

            override fun onToast(message: String) {
                showToast(message)
            }
        })
    }

    private fun setupInputBar() {
        inputBar.setVoiceMode(controller.voiceMode)
        inputBar.onSendText = { controller.sendText(it) }
        // 切换结果由组件自身状态承载，这里把控制器返回的新模式回传给它
        inputBar.onToggleMode = { inputBar.setVoiceMode(controller.toggleVoiceMode()) }
        // 语音输入（含 ASR）由组件自持，这里只负责麦克风权限
        inputBar.onStartVoice = { startVoiceWithPermission() }
        inputBar.onTextRecognized = { controller.sendText(it) }
        inputBar.onNotice = { showToast(it) }
    }

    private fun startVoiceWithPermission() {
        val granted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) inputBar.startVoiceInput()
        else recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    /**
     * 切换「自动朗读 AI 回复」开关（供宿主标题栏的喇叭按钮调用）。
     * 状态变化会通过 [onAutoSpeakChanged] 回调出去。
     */
    fun toggleAutoSpeak() {
        if (::controller.isInitialized) controller.toggleAutoSpeak()
    }

    /** 当前是否开启「自动朗读」；控制器未就绪时默认返回 true。 */
    fun isAutoSpeak(): Boolean =
        if (::controller.isInitialized) controller.autoSpeak else true

    private fun scrollToBottom() {
        val count = adapter.itemCount
        if (count > 0) recycler.scrollToPosition(count - 1)
    }

    private fun showToast(message: String) {
        toast.text = message
        toast.visibility = View.VISIBLE
        toastHandler.removeCallbacksAndMessages(null)
        toastHandler.postDelayed({ toast.visibility = View.GONE }, 2500)
    }

    override fun onResume() {
        super.onResume()
        controller.onEnterPage()
    }

    override fun onPause() {
        super.onPause()
        // 离开页面时收尾语音识别（避免后台继续采集）
        inputBar.stopVoiceInput()
        controller.onLeavePage()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        toastHandler.removeCallbacksAndMessages(null)
        inputBar.release()
        controller.release()
    }

    companion object {
        @JvmStatic
        fun newInstance(): AIChatFragment = AIChatFragment()
    }
}
