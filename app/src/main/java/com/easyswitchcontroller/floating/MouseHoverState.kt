package com.easyswitchcontroller.floating

/** EXIT 좌표를 재사용하지 않는다. 이전 창의 늦은 EXIT는 새 창의 호버를 지우지 않는다. */
internal class MouseHoverState<Window : Any> {
    private var window: Window? = null
    var point: FloatingMenuGeometry.Point? = null
        private set

    fun update(point: FloatingMenuGeometry.Point?, window: Window? = null) {
        this.point = point
        this.window = window
    }

    fun exit(window: Window): Boolean {
        if (this.window != null && this.window !== window) return false
        val changed = point != null
        update(null)
        return changed
    }

    fun removed(window: Window): Boolean = if (this.window === window) exit(window) else false
}
