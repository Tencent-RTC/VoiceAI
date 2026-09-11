// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAIKit - 实时 AI 对话 Activity（兜底整页入口）

package com.tencent.voiceai.kit.view

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.tencent.voiceai.kit.R

/**
 * 开箱即用的实时 AI 对话整页 Activity，供不使用自有导航的接入方直接打开：
 * ```
 * RealtimeChatActivity.start(context)
 * ```
 *
 * 本 Activity 有意不引用 AIChat 相关类，保持两页彼此独立。
 */
open class RealtimeChatActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.vak_activity_container)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.vak_fragment_container, RealtimeChatFragment.newInstance())
                .commit()
        }
    }

    companion object {
        @JvmStatic
        fun start(context: Context) {
            context.startActivity(Intent(context, RealtimeChatActivity::class.java))
        }
    }
}
