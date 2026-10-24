package com.easyswitchcontroller.ui.notice

import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.ReplacementSpan
import kotlin.math.ceil

/** 글꼴에 따라 가늘어지지 않도록 ○와 ×를 같은 굵기의 선으로 그린다. 원래 문자는 유지한다. */
class NoticeIndicatorSpan(private val successful: Boolean) : ReplacementSpan() {
    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        if (fm != null) paint.getFontMetricsInt(fm)
        return ceil(paint.textSize).toInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        val stroke = Paint(paint).apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = textSize * 0.16f
            strokeCap = Paint.Cap.ROUND
            color = if (successful) 0xFF00E676.toInt() else 0xFFE53935.toInt()
        }
        val centerX = x + ceil(paint.textSize) / 2f
        val centerY = y + (paint.ascent() + paint.descent()) / 2f
        val radius = (paint.textSize * 0.9f - stroke.strokeWidth) / 2f
        if (successful) {
            canvas.drawCircle(centerX, centerY, radius, stroke)
        } else {
            val halfDiagonal = radius * 0.70710677f
            canvas.drawLine(
                centerX - halfDiagonal, centerY - halfDiagonal,
                centerX + halfDiagonal, centerY + halfDiagonal, stroke,
            )
            canvas.drawLine(
                centerX - halfDiagonal, centerY + halfDiagonal,
                centerX + halfDiagonal, centerY - halfDiagonal, stroke,
            )
        }
    }
}
