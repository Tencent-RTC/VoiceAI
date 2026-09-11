// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAIKit - 语音输入波形 View
//
// 一组随实时音量起伏的圆角竖条，历史采样向左滚动。
// 采样采用「固定间隔 + 静音超时归零」而非直接跟随回调，
// 这样即使 SDK 在无声时停止回调音量，波形也能平滑回落到基线。

package com.tencent.voiceai.kit.view.component

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.tencent.voiceai.kit.R

class VoiceWaveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    companion object {
        private const val BAR_COUNT = 32
        private const val SAMPLE_INTERVAL_MS = 60L
        private const val SILENCE_TIMEOUT_MS = 400L
        private const val SMOOTH_FACTOR = 0.35f
    }

    private val barColor = ContextCompat.getColor(context, R.color.vak_brand_blue)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = barColor }
    private val rect = RectF()

    private val samples = ArrayDeque<Float>()
    @Volatile private var currentVolume = 0
    private var smoothed = 0f
    private var lastActiveAt = 0L
    private var running = false

    private val handler = Handler(Looper.getMainLooper())
    private val density = resources.displayMetrics.density

    private val sampleTask = object : Runnable {
        override fun run() {
            if (!running) return
            val now = System.currentTimeMillis()
            if (currentVolume > 0) lastActiveAt = now
            val target = if (now - lastActiveAt > SILENCE_TIMEOUT_MS) {
                0f
            } else {
                currentVolume.coerceIn(0, 100) / 100f
            }
            smoothed += (target - smoothed) * SMOOTH_FACTOR
            samples.addLast(smoothed)
            while (samples.size > BAR_COUNT) samples.removeFirst()
            invalidate()
            handler.postDelayed(this, SAMPLE_INTERVAL_MS)
        }
    }

    /** 更新实时音量（0-100）。 */
    fun setVolume(volume: Int) {
        currentVolume = volume
    }

    /** 开始波形采样与绘制。 */
    fun start() {
        if (running) return
        running = true
        smoothed = 0f
        lastActiveAt = 0L
        samples.clear()
        handler.post(sampleTask)
    }

    /** 停止采样。 */
    fun stop() {
        running = false
        handler.removeCallbacks(sampleTask)
        currentVolume = 0
        samples.clear()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stop()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val barWidth = 4f * density
        val gap = 4f * density
        val step = barWidth + gap
        val totalWidth = BAR_COUNT * step - gap
        val startX = (width - totalWidth) / 2f
        val centerY = height / 2f
        val minBarHeight = 4f * density
        val maxBarHeight = height * 0.86f

        val list = samples.toList()
        for (i in 0 until BAR_COUNT) {
            val index = i - (BAR_COUNT - list.size)
            val v = if (index >= 0) list[index] else 0f
            val h = minBarHeight + (maxBarHeight - minBarHeight) * v
            val left = startX + i * step
            rect.set(left, centerY - h / 2f, left + barWidth, centerY + h / 2f)
            paint.alpha = ((0.35f + 0.65f * v) * 255).toInt().coerceIn(0, 255)
            canvas.drawRoundRect(rect, barWidth / 2f, barWidth / 2f, paint)
        }
    }
}
