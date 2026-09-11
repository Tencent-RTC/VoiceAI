// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAIKit - AI 对话气泡列表适配器

package com.tencent.voiceai.kit.view.component

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tencent.voiceai.kit.R
import com.tencent.voiceai.kit.view.ChatBubble

internal class ChatBubbleAdapter(
    private val onRetry: () -> Unit
) : ListAdapter<ChatBubble, ChatBubbleAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ChatBubble>() {
            override fun areItemsTheSame(a: ChatBubble, b: ChatBubble) = a.id == b.id
            override fun areContentsTheSame(a: ChatBubble, b: ChatBubble) = a == b
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.vak_item_chat_bubble, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position), onRetry)
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val container: MaxWidthLinearLayout = itemView.findViewById(R.id.vak_bubble_container)
        private val text: TextView = itemView.findViewById(R.id.vak_bubble_text)
        private val sending: View = itemView.findViewById(R.id.vak_bubble_sending)
        private val errorBox: View = itemView.findViewById(R.id.vak_bubble_error)
        private val errorDetail: TextView = itemView.findViewById(R.id.vak_bubble_error_detail)
        private val retry: TextView = itemView.findViewById(R.id.vak_bubble_retry)

        private val density = itemView.resources.displayMetrics.density

        fun bind(bubble: ChatBubble, onRetry: () -> Unit) {
            val ctx = itemView.context
            val isUser = bubble.role == "user"

            // 左右对齐
            val lp = container.layoutParams as FrameLayout.LayoutParams
            if (isUser) {
                lp.gravity = Gravity.END
                lp.width = FrameLayout.LayoutParams.WRAP_CONTENT
                container.maxWidthPx = (260 * density).toInt()
                container.setBackgroundResource(R.drawable.vak_bg_bubble_user)
            } else {
                lp.gravity = Gravity.START
                lp.width = FrameLayout.LayoutParams.MATCH_PARENT
                container.maxWidthPx = 0
                container.setBackgroundResource(R.drawable.vak_bg_bubble_ai)
            }
            container.layoutParams = lp

            val showSending = bubble.streaming && bubble.content.isEmpty()
            val showError = bubble.error

            when {
                showSending -> {
                    text.visibility = View.GONE
                    sending.visibility = View.VISIBLE
                    errorBox.visibility = View.GONE
                }
                showError -> {
                    text.visibility = View.GONE
                    sending.visibility = View.GONE
                    errorBox.visibility = View.VISIBLE
                    if (bubble.content.isNotBlank()) {
                        errorDetail.visibility = View.VISIBLE
                        errorDetail.text = bubble.content
                    } else {
                        errorDetail.visibility = View.GONE
                    }
                    retry.setOnClickListener { onRetry() }
                }
                else -> {
                    text.visibility = View.VISIBLE
                    sending.visibility = View.GONE
                    errorBox.visibility = View.GONE
                    text.text = bubble.content
                    text.setTextColor(
                        ContextCompat.getColor(
                            ctx,
                            if (isUser) R.color.vak_white else R.color.vak_text_primary
                        )
                    )
                }
            }
        }
    }
}
