package com.easyswitchcontroller.ui.notice

/** 같은 크기의 상태 갱신에서는 창을 움직이지 않고, 추가 행은 첫 헤더 아래로 확장한다. */
internal class NoticeWindowLayout(private val initialBottom: Int, private val minimumBottom: Int) {
    data class Bounds(val width: Int, val height: Int, val bottom: Int)

    private var anchorHeight: Int? = null
    private var current: Bounds? = null

    fun update(width: Int, height: Int): Bounds? {
        val anchor = anchorHeight ?: height.also { anchorHeight = it }
        val next = Bounds(width, height, (initialBottom - (height - anchor)).coerceAtLeast(minimumBottom))
        if (next == current) return null
        current = next
        return next
    }
}
