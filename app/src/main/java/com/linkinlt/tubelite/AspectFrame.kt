package com.linkinlt.tubelite

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/** Giữ đúng tỉ lệ khung hình video. */
class AspectFrame(ctx: Context, attrs: AttributeSet?) : FrameLayout(ctx, attrs) {
    var ratio = 16f / 9f
        set(value) { if (value > 0 && value != field) { field = value; requestLayout() } }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        var nw = w; var nh = (w / ratio).toInt()
        if (nh > h) { nh = h; nw = (h * ratio).toInt() }
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(nw, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(nh, MeasureSpec.EXACTLY)
        )
    }
}
