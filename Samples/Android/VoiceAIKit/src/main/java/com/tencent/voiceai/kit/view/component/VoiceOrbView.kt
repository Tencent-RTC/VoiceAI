// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAIKit - 中央动态圆球 View（呼吸式缩放 + 外扩光晕）
//
// 表示正在聆听 / 对话中；配色沿用蓝紫渐变（6E8BF5 / 2B6CF6 / 9B4DEB）。

package com.tencent.voiceai.kit.view.component

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

class VoiceOrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    companion object {
        private const val ORB_LIGHT = 0xFF6E8BF5.toInt()
        private const val ORB_CORE = 0xFF2B6CF6.toInt()
        private const val ORB_DARK = 0xFF9B4DEB.toInt()

        /** 呼吸动画的缩放区间：0.92f ~ 1.04f。 */
        private const val BREATH_MIN_SCALE = 0.92f
        private const val BREATH_SCALE_RANGE = 0.12f

        /** 外层 / 内层光晕、主体相对基准半径的系数。 */
        private const val HALO_OUTER_SCALE = 1.18f
        private const val HALO_INNER_SCALE = 1.04f
        private const val BODY_SCALE = 0.78f

        /**
         * 绘制内容的最大外扩系数 = 最大呼吸缩放(1.04) × 最外层光晕(1.18)。
         * 基准半径需除以该值，保证最外层光晕始终落在 View 边界内，不被裁切。
         */
        private const val MAX_OUTER_SCALE = 1.2272f

        /** wrap_content 时的默认边长（已含主体 + 外扩光晕的空间）。 */
        private const val DEFAULT_SIZE_DP = 200
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var breath = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1600
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener {
            breath = it.animatedValue as Float
            invalidate()
        }
    }

    /**
     * 自适应尺寸：未指定具体大小时使用默认边长，并取宽高的较小值保持正方形，
     * 这样外部无需设置固定尺寸，也不会因尺寸偏小而裁掉外围光晕。
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val defaultSize = (DEFAULT_SIZE_DP * resources.displayMetrics.density + 0.5f).toInt()
        val w = resolveSize(defaultSize, widthMeasureSpec)
        val h = resolveSize(defaultSize, heightMeasureSpec)
        val size = minOf(w, h)
        setMeasuredDimension(size, size)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val availW = (width - paddingLeft - paddingRight).coerceAtLeast(0)
        val availH = (height - paddingTop - paddingBottom).coerceAtLeast(0)
        val cx = paddingLeft + availW / 2f
        val cy = paddingTop + availH / 2f

        // 以「最外层光晕刚好贴边」反推基准半径，任何尺寸下外围效果都不会被裁切
        val half = minOf(availW, availH) / 2f
        val radius = half / MAX_OUTER_SCALE
        val scale = BREATH_MIN_SCALE + BREATH_SCALE_RANGE * breath
        val haloAlpha = 0.10f + 0.18f * breath

        // 两层外扩光晕
        paint.shader = null
        paint.color = ORB_CORE
        paint.alpha = ((haloAlpha * 0.55f) * 255).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, radius * scale * HALO_OUTER_SCALE, paint)

        paint.alpha = (haloAlpha * 255).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, radius * scale * HALO_INNER_SCALE, paint)

        // 主体：蓝紫径向渐变
        val bodyRadius = radius * scale * BODY_SCALE
        paint.alpha = 255
        paint.shader = RadialGradient(
            cx, cy, bodyRadius,
            intArrayOf(ORB_LIGHT, ORB_CORE, ORB_DARK),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, bodyRadius, paint)

        // 左上高光
        paint.shader = null
        paint.color = Color.WHITE
        paint.alpha = (0.30f * 255).toInt()
        canvas.drawCircle(
            cx - bodyRadius * 0.18f,
            cy - bodyRadius * 0.24f,
            bodyRadius * 0.34f,
            paint
        )
    }
}
