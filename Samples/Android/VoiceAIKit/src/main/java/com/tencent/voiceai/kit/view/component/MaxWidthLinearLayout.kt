// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAIKit - 支持 maxWidth 的 LinearLayout（用于限制用户气泡最大宽度）

package com.tencent.voiceai.kit.view.component

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout

internal class MaxWidthLinearLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    /** 最大宽度（px）；<=0 表示不限制。 */
    var maxWidthPx: Int = 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var spec = widthMeasureSpec
        if (maxWidthPx in 1 until MeasureSpec.getSize(widthMeasureSpec)) {
            val mode = MeasureSpec.getMode(widthMeasureSpec)
            spec = MeasureSpec.makeMeasureSpec(maxWidthPx, mode)
        }
        super.onMeasure(spec, heightMeasureSpec)
    }
}
