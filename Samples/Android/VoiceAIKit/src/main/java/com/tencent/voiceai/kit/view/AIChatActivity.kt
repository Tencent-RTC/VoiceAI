// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAIKit - AI 对话 Activity（兜底整页入口）

package com.tencent.voiceai.kit.view

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.tencent.voiceai.kit.R

/**
 * 开箱即用的 AI 对话整页 Activity，供不使用自有导航的接入方直接打开：
 * ```
 * AIChatActivity.start(context)
 * ```
 *
 * [AIChatFragment] 本身**不含标题栏**，本 Activity 自带一个标题栏
 * （返回 + 标题 + 自动朗读开关），并通过 Fragment 暴露的
 * toggleAutoSpeak / isAutoSpeak / onAutoSpeakChanged 与朗读开关联动。
 */
open class AIChatActivity : AppCompatActivity() {

    private lateinit var btnSpeaker: ImageButton
    private var chatFragment: AIChatFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.vak_activity_ai_chat)

        findViewById<ImageButton>(R.id.vak_btn_back).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        btnSpeaker = findViewById(R.id.vak_btn_speaker)

        val fragment = (supportFragmentManager
            .findFragmentById(R.id.vak_fragment_container) as? AIChatFragment)
            ?: AIChatFragment.newInstance().also {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.vak_fragment_container, it)
                    .commitNow()
            }
        chatFragment = fragment

        // 自动朗读：点击切换 + 状态回调同步图标
        fragment.onAutoSpeakChanged = { autoSpeak -> updateSpeakerButton(autoSpeak) }
        updateSpeakerButton(fragment.isAutoSpeak())
        btnSpeaker.setOnClickListener { fragment.toggleAutoSpeak() }
    }

    private fun updateSpeakerButton(autoSpeak: Boolean) {
        btnSpeaker.setImageResource(
            if (autoSpeak) R.drawable.vak_ic_volume_up else R.drawable.vak_ic_volume_off
        )
        btnSpeaker.setBackgroundResource(
            if (autoSpeak) R.drawable.vak_bg_circle_active else R.drawable.vak_bg_circle_light
        )
    }

    override fun onDestroy() {
        chatFragment?.onAutoSpeakChanged = null
        super.onDestroy()
    }

    companion object {
        @JvmStatic
        fun start(context: Context) {
            context.startActivity(Intent(context, AIChatActivity::class.java))
        }
    }
}
