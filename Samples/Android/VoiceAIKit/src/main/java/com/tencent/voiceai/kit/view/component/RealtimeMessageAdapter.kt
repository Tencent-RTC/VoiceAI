// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAIKit - 实时对话消息列表适配器（纯文本行）

package com.tencent.voiceai.kit.view.component

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tencent.voiceai.kit.R
import com.tencent.voiceai.kit.view.RealtimeMessage

internal class RealtimeMessageAdapter :
    ListAdapter<RealtimeMessage, RealtimeMessageAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<RealtimeMessage>() {
            override fun areItemsTheSame(a: RealtimeMessage, b: RealtimeMessage) = a.id == b.id
            override fun areContentsTheSame(a: RealtimeMessage, b: RealtimeMessage) = a == b
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.vak_item_realtime_message, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val text: TextView = itemView.findViewById(R.id.vak_rt_message_text)

        fun bind(message: RealtimeMessage) {
            val ctx = itemView.context
            val isUser = message.role == "user"
            text.text = message.content
            val colorRes = when {
                isUser -> R.color.vak_text_secondary
                else -> R.color.vak_text_primary
            }
            val color = ContextCompat.getColor(ctx, colorRes)
            // 识别中的用户消息更淡一层
            text.setTextColor(
                if (isUser && message.partial) (color and 0x00FFFFFF) or (0x8C shl 24)
                else color
            )
        }
    }
}
